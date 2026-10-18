package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.session.SessionDuration;
import taskmanager.identity.session.SessionService;
import taskmanager.platform.ApiProblemException;

/**
 * Regression tests for the five findings of the security review in PR #77 (R1 to R5). Each test is the
 * counterexample of the review turned around: it fails on the code of the review and passes with the
 * corrections. Production defaults are used on purpose; in particular the throttle thresholds are not raised
 * as they are in {@link IdentityApiTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({IdentityHardeningTest.Clocks.class, IdentityHardeningTest.ProtectedProbe.class})
class IdentityHardeningTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Clocks {
        @Bean
        @Primary
        MutableClock hardeningClock() {
            return CLOCK;
        }
    }

    /** A synthetic route below /api that exists only in this test. It stands for any later business endpoint. */
    @RestController
    static class ProtectedProbe {
        @GetMapping("/api/v1/hardening-protected")
        Map<String, String> protectedResource() {
            return Map.of("handler", "reached");
        }
    }

    private static final MutableClock CLOCK = new MutableClock();
    private static final String STRONG = "Review-Strong-Password-47";
    private static final String DATABASE = freshDatabase();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    @Autowired AccountService accounts;
    @Autowired SessionService sessions;
    @Autowired SessionDuration duration;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Value("${local.server.port}") int port;

    @BeforeEach
    void resetSyntheticDatabase() {
        for (String table : new String[] {"identity_session", "identity_audit_event", "identity_login_throttle", "identity_account"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("update identity_setting set value = '24' where name = 'session_duration_hours'");
        CLOCK.set(Instant.parse("2026-10-18T12:00:00Z"));
    }

    private record Full(UUID account, String username, SessionService.Issued issued) {
    }

    private Full fullAccount() {
        var created = accounts.create("test", "hardening-" + UUID.randomUUID().toString().substring(0, 8), null, true);
        var restricted = sessions.login(created.account().username(), created.oneTimePassword(), "setup");
        var resolved = sessions.resolve(restricted.sessionToken()).orElseThrow();
        var full = sessions.changePassword(resolved.sessionId(), created.oneTimePassword(), STRONG, "setup");
        return new Full(created.account().id(), created.account().username(), full);
    }

    // ------------------------------------------------------------------------------------------------ R1

    @Test
    void r1ALoginWhosePasswordWasVerifiedBeforeAResetCannotStartASession() throws Exception {
        Full target = fullAccount();
        CountDownLatch reset = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            // The reset is written but not committed yet and holds the account row.
            var administrator = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                accounts.resetPassword("review", target.account());
                reset.countDown();
                await(release);
            }));
            try {
                assertTrue(reset.await(5, TimeUnit.SECONDS));
                // The login still sees the old, committed password, verifies it and must then wait for the account row.
                var login = pool.submit(() -> sessions.login(target.username(), STRONG, "race"));
                assertTrue(waitsForTheAccountRow(), "the login did not reach the point after the password check");
                release.countDown();
                administrator.get(10, TimeUnit.SECONDS);
                ExecutionException refused = assertThrows(ExecutionException.class, () -> login.get(10, TimeUnit.SECONDS));
                ApiProblemException problem = assertInstanceOf(ApiProblemException.class, refused.getCause());
                assertEquals(401, problem.status());
                assertEquals("auth.invalid_credentials", problem.code());
            } finally {
                release.countDown();
            }
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_session where account_id = ? and revoked_at is null",
            Integer.class, target.account()), "no session may exist after the reset");
        assertTrue(jdbc.queryForObject("select password_change_required from identity_account where id = ?", Boolean.class, target.account()));
    }

    @Test
    void r1BAFullSessionNeverBelongsToAnAccountThatStillHasToChangeItsPassword() {
        var created = accounts.create("test", "hardening-pending", null, false);
        var restricted = sessions.login(created.account().username(), created.oneTimePassword(), "pending");
        assertEquals(AuthenticationLevel.PASSWORD_CHANGE_REQUIRED, sessions.resolve(restricted.sessionToken()).orElseThrow().view().level());
        // A full session that would exist next to a pending change (what the race of the review produced) is not accepted.
        jdbc.update("update identity_session set restricted = false, restricted_expires_at = null");
        assertTrue(sessions.resolve(restricted.sessionToken()).isEmpty());
    }

    private boolean waitsForTheAccountRow() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from pg_stat_activity
                where datname = current_database() and pid <> pg_backend_pid()
                and query ilike '%from identity_account where id =%for update%'
                and cardinality(pg_blocking_pids(pid)) > 0)
                """, Boolean.class));
            if (waiting) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ R2

    @Test
    void r2AnExpiredSessionStaysExpiredWhenThePeriodIsExtendedAgain() {
        Full target = fullAccount();
        CLOCK.advance(Duration.ofMinutes(10));
        duration.change("review", 1);
        assertEquals(Duration.ofHours(1), Duration.between(target.issued().view().startedAt(),
            sessions.resolve(target.issued().sessionToken()).orElseThrow().view().expiresAt()));
        CLOCK.advance(Duration.ofHours(2));
        assertTrue(sessions.resolve(target.issued().sessionToken()).isEmpty(), "expired under the one hour setting");
        duration.change("review", 24);
        assertTrue(sessions.resolve(target.issued().sessionToken()).isEmpty(), "extending must not revive the session");
    }

    @Test
    void r2BExtendingAfterShorteningDoesNotLengthenASessionThatIsStillOpen() {
        Full target = fullAccount();
        Instant started = target.issued().view().startedAt();
        duration.change("review", 2);
        duration.change("review", 24);
        CLOCK.advance(Duration.ofMinutes(30));
        assertEquals(started.plus(Duration.ofHours(2)), sessions.resolve(target.issued().sessionToken()).orElseThrow().view().expiresAt());
        CLOCK.advance(Duration.ofHours(2));
        assertTrue(sessions.resolve(target.issued().sessionToken()).isEmpty());
        // Later logins do get the extended period.
        Full later = fullAccount();
        assertEquals(Duration.ofHours(24), Duration.between(later.issued().view().startedAt(), later.issued().view().expiresAt()));
    }

    // ------------------------------------------------------------------------------------------------ R3

    @Test
    void r3AOneTimePasswordOpensExactlyOneSessionAndIsGoneAfterItEnds() {
        var created = accounts.create("test", "hardening-once", null, false);
        String name = created.account().username();
        var first = sessions.login(name, created.oneTimePassword(), "once");
        assertEquals(AuthenticationLevel.PASSWORD_CHANGE_REQUIRED, sessions.resolve(first.sessionToken()).orElseThrow().view().level());
        assertCode("auth.one_time_password_used", () -> sessions.login(name, created.oneTimePassword(), "once"));
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_session", Integer.class));

        CLOCK.advance(Duration.ofMinutes(31));
        assertTrue(sessions.resolve(first.sessionToken()).isEmpty(), "the restricted session ended");
        assertCode("auth.one_time_password_used", () -> sessions.login(name, created.oneTimePassword(), "once"));

        // An administrator issues a new password; it again opens exactly one session.
        var fresh = accounts.resetPassword("review", created.account().id());
        assertNotEquals(created.oneTimePassword(), fresh.oneTimePassword());
        assertEquals(AuthenticationLevel.PASSWORD_CHANGE_REQUIRED,
            sessions.resolve(sessions.login(name, fresh.oneTimePassword(), "once").sessionToken()).orElseThrow().view().level());
        assertCode("auth.one_time_password_used", () -> sessions.login(name, fresh.oneTimePassword(), "once"));
    }

    @Test
    void r3BTheForcedChangeStillWorksInTheSessionThatConsumedThePassword() {
        var created = accounts.create("test", "hardening-change", null, false);
        var restricted = sessions.login(created.account().username(), created.oneTimePassword(), "change");
        var resolved = sessions.resolve(restricted.sessionToken()).orElseThrow();
        var full = sessions.changePassword(resolved.sessionId(), created.oneTimePassword(), STRONG, "change");
        assertEquals(AuthenticationLevel.FULL, sessions.resolve(full.sessionToken()).orElseThrow().view().level());
        assertEquals(AuthenticationLevel.FULL,
            sessions.resolve(sessions.login(created.account().username(), STRONG, "change").sessionToken()).orElseThrow().view().level());
    }

    @Test
    void r3CParallelLoginsWithTheSameOneTimePasswordOpenOnlyOneSession() throws Exception {
        var created = accounts.create("test", "hardening-parallel", null, false);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var attempts = java.util.stream.IntStream.range(0, 4).mapToObj(i -> pool.submit(() -> {
                try {
                    sessions.login(created.account().username(), created.oneTimePassword(), "parallel-" + i);
                    return "ok";
                } catch (ApiProblemException refused) {
                    return refused.code();
                }
            })).toList();
            long successes = 0;
            for (var attempt : attempts) {
                if ("ok".equals(attempt.get(30, TimeUnit.SECONDS))) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_session", Integer.class));
    }

    // ------------------------------------------------------------------------------------------------ R4

    @Test
    void r4EveryAliasOfAnApiPathIsProtectedLikeTheCanonicalOne() {
        assertEquals(401, new ApiClient(port).get("/api/v1/hardening-protected").status());
        for (String path : new String[] {
                "/api;x/v1/hardening-protected", "/%61pi/v1/hardening-protected", "/%61%70%69/v1/hardening-protected",
                "/api/v1;x/hardening-protected", "/api/v1/hardening-protected;x=y", "/api/%76%31/hardening-protected",
                "//api/v1/hardening-protected", "/./api/v1/hardening-protected", "/api/./v1/hardening-protected"}) {
            var response = new ApiClient(port).get(path);
            assertNotEquals(200, response.status(), path + ": " + response.body());
            assertFalse(response.body().contains("reached"), path);
            if (response.status() != 400 && response.status() != 404) {
                assertEquals(401, response.status(), path);
                assertEquals("no-store", response.header("Cache-Control"), path);
            }
        }
    }

    @Test
    void r4AliasedLoginNeedsTheCsrfTokenAndAMatchingOrigin() {
        var created = accounts.create("test", "hardening-path", null, false);
        Map<String, String> credentials = Map.of("username", created.account().username(), "password", created.oneTimePassword());
        for (String path : new String[] {"/api;x/v1/session", "/%61pi/v1/session", "/api/v1/session;x=y"}) {
            ApiClient withoutCsrf = new ApiClient(port);
            withoutCsrf.sendCsrf = false;
            assertEquals(403, withoutCsrf.post(path, credentials).status(), path + " without CSRF token");
            ApiClient foreign = new ApiClient(port);
            foreign.get("/api/v1/session");
            foreign.headers.put("Origin", "https://attacker.invalid");
            assertEquals(403, foreign.post(path, credentials).status(), path + " with a foreign Origin");
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_session", Integer.class), "none of them started a session");
    }

    // ------------------------------------------------------------------------------------------------ R5

    @Test
    void r5ASourceAddressIsDelayedAfterFiveFailuresAcrossDifferentNames() {
        for (int attempt = 0; attempt < 5; attempt++) {
            int current = attempt;
            ApiProblemException refused = assertThrows(ApiProblemException.class,
                () -> sessions.login("hardening-missing-" + current, "wrong-password-value", "one-source"));
            assertEquals(401, refused.status());
        }
        ApiProblemException delayed = assertThrows(ApiProblemException.class,
            () -> sessions.login("hardening-missing-next", "wrong-password-value", "one-source"));
        assertEquals(429, delayed.status());
        assertEquals("auth.too_many_attempts", delayed.code());
        // Another address is not affected.
        assertEquals(401, assertThrows(ApiProblemException.class,
            () -> sessions.login("hardening-missing-other", "wrong-password-value", "other-source")).status());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static void assertCode(String code, Runnable action) {
        ApiProblemException refused = assertThrows(ApiProblemException.class, action::run);
        assertEquals(401, refused.status());
        assertEquals(code, refused.code());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timeout");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
