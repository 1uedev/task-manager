package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.TaskManagerApplication;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.account.Db;
import taskmanager.identity.account.PasswordHasher;
import taskmanager.identity.session.SessionDuration;
import taskmanager.identity.session.SessionService;
import taskmanager.identity.session.Tokens;

/** Review-only observations on 5381db3/370a69c. Passing assertions CONFIRM residual bugs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(IdentityFixesFollowupProbeTest.Configuration.class)
class IdentityFixesFollowupProbeTest {
    private static final MutableClock CLOCK = new MutableClock();
    private static final String PASSWORD = "Review-Strong-Password-47";
    private static final String DATABASE = emptyDatabase();

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean @Primary MutableClock followupClock() { return CLOCK; }
    }

    static String emptyDatabase() {
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
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void reset() {
        for (String table : new String[] {"identity_session", "identity_audit_event", "identity_login_throttle", "identity_account"})
            jdbc.update("delete from " + table);
        jdbc.update("update identity_setting set value='24' where name='session_duration_hours'");
        CLOCK.set(Instant.parse("2026-10-19T12:00:00Z"));
    }

    @Test
    void n1PasswordRotationCanEscapeConcurrentDurationShortening() throws Exception {
        var account = accounts.create("review", "review-rotation", null, false);
        var initial = sessions.login(account.account().username(), account.oneTimePassword(), "review-setup");
        var restricted = sessions.resolve(initial.sessionToken()).orElseThrow();
        var full = sessions.changePassword(restricted.sessionId(), account.oneTimePassword(), PASSWORD, "review-setup");
        var session = sessions.resolve(full.sessionToken()).orElseThrow();
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var rotationPid = new AtomicInteger();
        var shorteningPid = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(3)) {
            var blocker = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("select restricted from identity_session where id=? for update", Boolean.class, session.sessionId());
                blocked.countDown(); await(release);
            }));
            try {
                assertTrue(blocked.await(5, TimeUnit.SECONDS));
                var rotation = pool.submit(() -> sessions.changePassword(
                    session.sessionId(), PASSWORD, "Review-New-Password-92", "review-rotation"));
                assertBlocked(rotationPid, "update identity_session set revoked_at%", "rotation did not reach revocation");
                var shortening = pool.submit(() -> duration.change("review", 1));
                assertBlocked(shorteningPid, "update identity_session set expires_at%", "shortening did not reach session update");
                release.countDown();
                blocker.get(10, TimeUnit.SECONDS);
                var replacement = rotation.get(10, TimeUnit.SECONDS);
                shortening.get(10, TimeUnit.SECONDS);
                assertEquals("1", jdbc.queryForObject("select value from identity_setting where name='session_duration_hours'", String.class));
                var storedEnd = jdbc.queryForObject("select expires_at from identity_session where token_hash=?",
                    (rs, row) -> Db.instant(rs, "expires_at"), Tokens.hash(replacement.sessionToken()));
                assertEquals(full.view().startedAt().plus(Duration.ofHours(24)), storedEnd,
                    "Observation: replacement missed the shortening update");
                assertEquals(full.view().startedAt().plus(Duration.ofHours(1)),
                    sessions.resolve(replacement.sessionToken()).orElseThrow().view().expiresAt());
                CLOCK.advance(Duration.ofHours(2));
                assertTrue(sessions.resolve(replacement.sessionToken()).isEmpty(), "Control: token is expired under 1h");
                duration.change("review", 24);
                assertEquals(AuthenticationLevel.FULL, sessions.resolve(replacement.sessionToken()).orElseThrow().view().level());
                System.out.println("FOLLOWUP N1: concurrent rotation escaped shortening; expired replacement token revived after 1 -> 24");
            } finally { release.countDown(); }
        }
    }

    private void assertBlocked(AtomicInteger pid, String queryPrefix, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var waiting = jdbc.queryForList("""
                select pid from pg_stat_activity where datname=current_database()
                and ltrim(query) like ? and cardinality(pg_blocking_pids(pid))>0
                """, Integer.class, queryPrefix);
            if (waiting.size()==1) { pid.set(waiting.getFirst()); return; }
            Thread.sleep(10);
        }
        fail(message);
    }

    @Test
    void n2UpgradeFromV0003DoesNotConsumeAnAlreadyUsedOneTimePassword() {
        String url = emptyDatabase();
        Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).target("0003").load().migrate();
        var upgrade = new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
        UUID account = UUID.randomUUID(), session = UUID.randomUUID();
        String token = Tokens.random(), password = "Review-Legacy-Credential-47";
        Instant now = Instant.now();
        upgrade.update("""
            insert into identity_account(id,username,password_hash,password_change_required,one_time_expires_at,
                active,administrator,created_at,updated_at) values (?,?,?,true,?,true,false,?,?)
            """, account, "review-legacy", hasher.hash(password), Db.ts(now.plus(Duration.ofHours(72))), Db.ts(now), Db.ts(now));
        // State left by one successful login on V0003; no new-column simulation.
        upgrade.update("""
            insert into identity_session(id,account_id,token_hash,csrf_hash,started_at,expires_at,
                restricted,restricted_expires_at,created_at) values (?,?,?,?,?,?,true,?,?)
            """, session, account, Tokens.hash(token), Tokens.hash(Tokens.random()), Db.ts(now),
            Db.ts(now.plus(Duration.ofHours(24))), Db.ts(now.plus(Duration.ofMinutes(30))), Db.ts(now));
        Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).load().migrate();
        assertNull(upgrade.queryForObject("select one_time_used_at from identity_account where id=?", java.sql.Timestamp.class, account));
        try (var context = new SpringApplicationBuilder(TaskManagerApplication.class).run(
                "--server.port=0", "--spring.datasource.url="+url,
                "--spring.datasource.username="+TestDatabase.username(), "--spring.datasource.password="+TestDatabase.password())) {
            var upgradedSessions = context.getBean(SessionService.class);
            assertEquals(AuthenticationLevel.PASSWORD_CHANGE_REQUIRED, upgradedSessions.resolve(token).orElseThrow().view().level());
            var duplicate = upgradedSessions.login("review-legacy", password, "review-upgrade");
            assertEquals(AuthenticationLevel.PASSWORD_CHANGE_REQUIRED,
                upgradedSessions.resolve(duplicate.sessionToken()).orElseThrow().view().level());
            assertEquals(2, upgrade.queryForObject("select count(*) from identity_session where account_id=? and revoked_at is null", Integer.class, account));
            System.out.println("FOLLOWUP N2: real V0003 -> V0004 upgrade allowed the already-used credential to start a second session");
        }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("Review latch timeout"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
