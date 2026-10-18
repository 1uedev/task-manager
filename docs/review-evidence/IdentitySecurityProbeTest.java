package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import taskmanager.identity.session.LoginThrottle;
import taskmanager.identity.session.SessionDuration;
import taskmanager.identity.session.SessionService;
import taskmanager.platform.ApiProblemException;

/**
 * Review-only counterexamples for PR #75, not production tests or an acceptance suite.
 * Assertions intentionally describe the VULNERABLE observations on 508f0df.
 * A passing probe confirms a finding; after a fix replace it with a negative regression test.
 * Copy temporarily to backend/src/test/java/taskmanager/identity/ to reproduce, then remove.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({IdentitySecurityProbeTest.Configuration.class, IdentitySecurityProbeTest.ProtectedProbe.class})
class IdentitySecurityProbeTest {
    private static final MutableClock CLOCK = new MutableClock();
    private static final String STRONG = "Review-Strong-Password-47";
    private static final String DATABASE = freshDatabase();

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean @Primary MutableClock reviewClock() { return CLOCK; }
    }

    /** Synthetic route: tests the documented default guard without adding a production endpoint. */
    @RestController
    static class ProtectedProbe {
        @GetMapping("/api/v1/review-protected")
        Map<String, String> protectedResource() { return Map.of("review", "handler-reached"); }
    }

    private static String freshDatabase() {
        try { return TestDatabase.createEmptyDatabase(); }
        catch (SQLException failure) { throw new IllegalStateException(failure); }
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
        for (String table : new String[] {"identity_session", "identity_audit_event", "identity_login_throttle", "identity_account"})
            jdbc.update("delete from " + table);
        jdbc.update("update identity_setting set value='24' where name='session_duration_hours'");
        CLOCK.set(Instant.parse("2026-10-18T12:00:00Z"));
    }

    private record Full(UUID account, String username, SessionService.Issued issued) {}

    private Full fullAccount() {
        var account = accounts.create("review", "review-" + UUID.randomUUID(), null, true);
        var restricted = sessions.login(account.account().username(), account.oneTimePassword(), "review-setup");
        var resolved = sessions.resolve(restricted.sessionToken()).orElseThrow();
        var full = sessions.changePassword(resolved.sessionId(), account.oneTimePassword(), STRONG, "review-setup");
        return new Full(account.account().id(), account.account().username(), full);
    }

    @Test
    void r1OldPasswordCanCreateFullSessionAfterCommittedAdministratorReset() throws Exception {
        Full target = fullAccount();
        String subject = LoginThrottle.userSubject(target.username());
        jdbc.update("insert into identity_login_throttle(subject,failures,last_failure_at,blocked_until) values (?,1,?,null)",
            subject, java.sql.Timestamp.from(CLOCK.instant()));
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var blocker = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("select failures from identity_login_throttle where subject=? for update", Integer.class, subject);
                locked.countDown(); await(release);
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var login = pool.submit(() -> sessions.login(target.username(), STRONG, "review-race"));
                // This DELETE is AFTER verification in login(): prove it really waits before starting the session.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists(select 1 from pg_stat_activity
                        where datname=current_database() and pid<>pg_backend_pid()
                        and query like 'delete from identity_login_throttle%'
                        and cardinality(pg_blocking_pids(pid))>0)
                        """, Boolean.class));
                    if (waiting) break;
                    Thread.sleep(10);
                }
                assertTrue(waiting, "Did not reach the intended post-verification checkpoint");
                accounts.resetPassword("review", target.account());
                assertTrue(sessions.resolve(target.issued().sessionToken()).isEmpty(), "Control: reset revoked old session");
                release.countDown();
                var late = login.get(10, TimeUnit.SECONDS);
                blocker.get(10, TimeUnit.SECONDS);
                assertTrue(jdbc.queryForObject("select password_change_required from identity_account where id=?", Boolean.class, target.account()));
                assertEquals(AuthenticationLevel.FULL, sessions.resolve(late.sessionToken()).orElseThrow().view().level());
                System.out.println("REVIEW R1: old-password login after reset = FULL while account requires password change");
            } finally { release.countDown(); }
        }
    }

    @Test
    void r2ExtendingAfterShorteningRevivesAnExpiredSession() {
        Full target = fullAccount();
        CLOCK.advance(Duration.ofMinutes(10));
        duration.change("review", 1);
        var shortened = sessions.resolve(target.issued().sessionToken()).orElseThrow();
        assertEquals(Duration.ofHours(1), Duration.between(target.issued().view().startedAt(), shortened.view().expiresAt()));
        CLOCK.advance(Duration.ofHours(2));
        assertTrue(sessions.resolve(target.issued().sessionToken()).isEmpty(), "Control: token expired under 1h setting");
        duration.change("review", 24);
        assertEquals(AuthenticationLevel.FULL, sessions.resolve(target.issued().sessionToken()).orElseThrow().view().level());
        System.out.println("REVIEW R2: same expired token becomes FULL again after duration 24 -> 1 -> 24");
    }

    @Test
    void r3OneTimePasswordOpensMultipleSessionsAndCanRestartAfterThirtyMinutes() {
        var target = accounts.create("review", "review-once", null, false);
        var first = sessions.login(target.account().username(), target.oneTimePassword(), "review-once");
        var second = sessions.login(target.account().username(), target.oneTimePassword(), "review-once");
        assertNotEquals(first.sessionToken(), second.sessionToken());
        assertEquals(2, jdbc.queryForObject("select count(*) from identity_session where revoked_at is null", Integer.class));
        CLOCK.advance(Duration.ofMinutes(31));
        assertTrue(sessions.resolve(first.sessionToken()).isEmpty());
        var third = sessions.login(target.account().username(), target.oneTimePassword(), "review-once");
        assertEquals(AuthenticationLevel.PASSWORD_CHANGE_REQUIRED, sessions.resolve(third.sessionToken()).orElseThrow().view().level());
        System.out.println("REVIEW R3: one credential accepted for three logins, including after restricted-session expiry");
    }

    @Test
    void r4EncodedAndMatrixPathsBypassDefaultGuardAndLoginCsrf() {
        assertEquals(401, new ApiClient(port).get("/api/v1/review-protected").status());
        for (String path : new String[] {"/api;review/v1/review-protected", "/%61pi/v1/review-protected"}) {
            var response = new ApiClient(port).get(path);
            assertEquals(200, response.status(), path + ": " + response.body());
            assertEquals("handler-reached", response.json().get("review").asString());
            assertNull(response.header("Cache-Control"));
        }
        var target = accounts.create("review", "review-path", null, false);
        ApiClient noCsrf = new ApiClient(port);
        noCsrf.sendCsrf = false;
        noCsrf.headers.put("Origin", "https://review-attacker.invalid");
        var response = noCsrf.post("/api;review/v1/session", Map.of("username", target.account().username(), "password", target.oneTimePassword()));
        assertEquals(200, response.status(), response.body());
        assertTrue(response.json().get("authenticated").asBoolean());
        System.out.println("REVIEW R4: canonical protected route=401; path aliases=200; aliased login without CSRF/with foreign Origin=200");
    }

    @Test
    void r5SourceAddressIsNotDelayedAfterFiveFailuresAcrossDifferentNames() {
        for (int attempt=0; attempt<6; attempt++) {
            int current = attempt;
            var refused = assertThrows(ApiProblemException.class, () ->
                sessions.login("review-missing-" + current, "wrong-review-password", "review-source"));
            assertEquals(401, refused.status());
        }
        throttleCheckAfterFailures();
        System.out.println("REVIEW R5: six failures across names from one source = six 401 responses, no 429 delay");
    }

    private void throttleCheckAfterFailures() {
        assertEquals(6, jdbc.queryForObject("select failures from identity_login_throttle where subject=?", Integer.class,
            LoginThrottle.sourceSubject("review-source")));
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Review latch timeout"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
