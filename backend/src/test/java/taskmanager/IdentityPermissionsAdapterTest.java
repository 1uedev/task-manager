package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.identity.IdentityVerification;
import taskmanager.identity.MutableClock;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.session.SessionDuration;
import taskmanager.identity.session.SessionService;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.IdentityAccess;
import taskmanager.permissions.PermissionAdministration;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.CompanyContext;

/**
 * TASK-039: the real adapter between identity and permissions against a real PostgreSQL. No identity fixture is
 * registered here. The concurrency tests hold one transaction open, start the opposite operation in a second one and
 * wait until PostgreSQL itself reports that the second one is blocked, in both orders.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "taskmanager.identity.source-failure-threshold=100000")
@Import(IdentityPermissionsAdapterTest.Clocks.class)
class IdentityPermissionsAdapterTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Clocks {
        @Bean
        @Primary
        MutableClock adapterClock() {
            return CLOCK;
        }
    }

    private static final MutableClock CLOCK = new MutableClock();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final String CHANGED = "Another-Strong-Password-52";
    private static final String DATABASE = freshDatabase();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

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

    @AfterAll
    static void stopThreads() {
        POOL.shutdownNow();
    }

    @Autowired ApplicationContext context;
    @Autowired AccountService accounts;
    @Autowired SessionService sessions;
    @Autowired SessionDuration duration;
    @Autowired Permissions permissions;
    @Autowired PermissionAdministration administration;
    @Autowired CompanyContext companies;
    @Autowired IdentityVerification verification;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    /** A person with a complete login. */
    private record Person(UUID id, String username, UUID session) {
    }

    /** A transaction that stays open, with the locks it took, until it is released. */
    private record Held<T>(Future<T> result, CountDownLatch release) {
        T finish() throws Exception {
            release.countDown();
            return result.get(15, TimeUnit.SECONDS);
        }
    }

    /** One way to take access away, with the statement PostgreSQL shows while it waits at each end of the conflict. */
    private record Revocation(String name, Consumer<Person> apply, String revokerWaitsAt, String checkWaitsAt, boolean movesClock) {
    }

    @BeforeEach
    void emptyInstallation() {
        CLOCK.set(Instant.now());
        for (String table : new String[] {"audit", "field", "action", "membership", "group", "revision"}) {
            jdbc.update("delete from permissions_" + table);
        }
        jdbc.update("delete from platform_company");
        for (String table : new String[] {"identity_session", "identity_audit_event", "identity_login_throttle", "identity_account"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("update identity_setting set value = '24' where name = 'session_duration_hours'");
    }

    // ------------------------------------------------------------------------------------------ helpers

    private Person bootstrap(String username) {
        return complete(accounts.bootstrapAdministrator(username));
    }

    private Person account(String username, boolean administrator) {
        return complete(accounts.create("test", username, null, administrator));
    }

    /** Completes the forced password change, so the person holds a full session. */
    private Person complete(AccountService.Issued issued) {
        String username = issued.account().username();
        var restricted = sessions.login(username, issued.oneTimePassword(), "198.51.100.1");
        UUID restrictedSession = sessions.resolve(restricted.sessionToken()).orElseThrow().sessionId();
        var full = sessions.changePassword(restrictedSession, issued.oneTimePassword(), STRONG, "198.51.100.1");
        return new Person(issued.account().id(), username, sessions.resolve(full.sessionToken()).orElseThrow().sessionId());
    }

    private UUID company() {
        return companies.find().orElseThrow();
    }

    private AccessContext ctx(Person person) {
        return new AccessContext(company(), person.id(), person.session(), permissions.currentVersion(company()), false, false, null);
    }

    private java.util.function.Supplier<ResourceContext> companyScope() {
        return () -> ResourceContext.company(company());
    }

    private boolean allowed(Person person, String action) {
        return permissions.inspect(ctx(person), companyScope(), action, Set.of()).allowed();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction().execute(status -> work.get());
    }

    private void inTransaction(Runnable work) {
        transaction().executeWithoutResult(status -> work.run());
    }

    private UUID companyGroup() {
        return jdbc.queryForObject("select id from permissions_group where company_id = ? and name = 'Unternehmensverwaltung'", UUID.class, company());
    }

    /** A group with the action ticket.read for the whole company, and the person as its member. */
    private UUID grantTicketRead(Person admin, Person... readers) {
        UUID group = inTransaction(() -> administration.createGroup(ctx(admin), "Leser " + UUID.randomUUID())).id();
        inTransaction(() -> administration.setAction(ctx(admin), group, companyScope(), "ticket.read", true));
        for (Person reader : readers) {
            inTransaction(() -> administration.setMember(ctx(admin), group, reader.id(), true));
        }
        return group;
    }

    /** Runs the work in a transaction that stays open after the work until {@link Held#finish()}. */
    private <T> Held<T> hold(Supplier<T> work) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<T> result = POOL.submit(() -> transaction().execute(status -> {
            try {
                T value = work.get();
                started.countDown();
                if (!await(release)) {
                    throw new IllegalStateException("not released");
                }
                return value;
            } finally {
                started.countDown();
            }
        }));
        assertTrue(started.await(15, TimeUnit.SECONDS), "the holding transaction did not reach its open state");
        assertFalse(result.isDone(), "the holding transaction ended before it was released");
        return new Held<>(result, release);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** True once some other backend runs a statement containing the text and PostgreSQL reports it as blocked. */
    private boolean waitsAt(String statementPart) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from pg_stat_activity
                where datname = current_database() and pid <> pg_backend_pid()
                and query ilike ? and cardinality(pg_blocking_pids(pid)) > 0)
                """, Boolean.class, "%" + statementPart + "%"));
            if (waiting) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static Throwable cause(Future<?> future) throws InterruptedException {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(15, TimeUnit.SECONDS));
        return failure.getCause();
    }

    private List<Revocation> revocations() {
        return List.of(
            new Revocation("logout", person -> sessions.logout(person.session()),
                "update identity_session set revoked_at", "for share of s", false),
            new Revocation("deactivation", person -> accounts.deactivate("test", person.id()),
                "permissions_revision where company_id = $1 for update", "permissions_revision where company_id = $1 for share", false),
            new Revocation("password reset", person -> accounts.resetPassword("test", person.id()),
                "identity_account where id = $1 for update", "identity_account where id = $1 for share", false),
            new Revocation("password change", person -> sessions.changePassword(person.session(), STRONG, CHANGED, "198.51.100.7"),
                "identity_account where id = $1 for update", "identity_account where id = $1 for share", false),
            new Revocation("shortened period", person -> duration.change("test", 1),
                "update identity_session set expires_at", "for share of s", true));
    }

    // ------------------------------------------------------------------------------------------ bootstrap

    @Test
    void theProductionStartRegistersExactlyOneAdapterAndNoTestFixture() {
        var adapters = context.getBeansOfType(IdentityAccess.class);
        assertEquals(1, adapters.size(), adapters.keySet().toString());
        assertEquals(IdentityPermissionsAdapter.class, AopUtils.getTargetClass(adapters.values().iterator().next()));
        assertEquals(1, context.getBeansOfType(taskmanager.identity.AccountDeactivationGuard.class).size());
        assertEquals(1, context.getBeansOfType(taskmanager.identity.FirstAdministratorHandler.class).size());
    }

    @Test
    void bootstrapCreatesCompanyRightsAndTheSingleAdministrationGroupInOneStepAndRefusesASecondCall() {
        Person chef = bootstrap("chef");
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_company", Integer.class));
        assertEquals(3L, jdbc.queryForObject("select revision from permissions_revision where company_id = ?", Long.class, company()));
        assertEquals(List.of("Unternehmensverwaltung"), jdbc.queryForList("select name from permissions_group", String.class));
        assertEquals(List.of("permissions.manage|"), jdbc.queryForList(
            "select action_key || '|' || coalesce(project_id::text, '') from permissions_action", String.class));
        assertEquals(List.of(chef.id()), jdbc.queryForList("select user_id from permissions_membership", UUID.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from permissions_field", Integer.class), "no business rights");
        assertEquals(List.of("group.created", "action.changed", "membership.changed"),
            jdbc.queryForList("select operation from permissions_audit order by rights_version", String.class));
        assertTrue(allowed(chef, "permissions.manage"));
        assertFalse(allowed(chef, "permissions.audit.read"), "the audit right is not granted");
        assertFalse(allowed(chef, "ticket.read"), "no implicit business access");

        ApiProblemException refused = assertThrows(ApiProblemException.class, () -> accounts.bootstrapAdministrator("second"));
        assertEquals("bootstrap.accounts_exist", refused.code());
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_company", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from permissions_group", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_account", Integer.class));
    }

    @Test
    void aBootstrapThatFailsHalfWayLeavesNoAccountCompanyOrRights() {
        // A company row that already exists makes the creation of the company fail after the account was written.
        jdbc.update("insert into platform_company (id) values (?)", UUID.randomUUID());
        assertThrows(RuntimeException.class, () -> accounts.bootstrapAdministrator("chef"));
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_account", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_audit_event", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from permissions_revision", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from permissions_group", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_company", Integer.class));
    }

    @Test
    void onlyOneCompanyCanExist() {
        bootstrap("chef");
        assertThrows(RuntimeException.class, () -> jdbc.update("insert into platform_company (id) values (?)", UUID.randomUUID()));
        assertThrows(RuntimeException.class, () -> inTransaction(() -> companies.create()));
        // Outside a transaction the creation fails as a wiring error instead of committing on its own.
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> companies.create());
    }

    // ------------------------------------------------------------------------------------------ the adapter as such

    @Test
    void theAdapterAnswersOnlyForTheCompanyOfTheInstallationAndAFullSessionOfTheSameAccount() {
        Person chef = bootstrap("chef");
        Person other = account("other", false);
        long version = permissions.currentVersion(company());
        assertTrue(allowed(chef, "permissions.manage"));
        // Anything that does not match the server side facts is refused.
        assertFalse(permissions.inspect(new AccessContext(UUID.randomUUID(), chef.id(), chef.session(), version, false, false, null),
            companyScope(), "permissions.manage", Set.of()).allowed(), "foreign company");
        assertFalse(permissions.inspect(new AccessContext(company(), chef.id(), other.session(), version, false, false, null),
            companyScope(), "permissions.manage", Set.of()).allowed(), "session of another account");
        assertFalse(permissions.inspect(new AccessContext(company(), chef.id(), UUID.randomUUID(), version, false, false, null),
            companyScope(), "permissions.manage", Set.of()).allowed(), "unknown session");
        assertFalse(permissions.inspect(new AccessContext(company(), chef.id(), chef.session(), version, true, false, null),
            companyScope(), "permissions.manage", Set.of()).allowed(), "pending password change");
        assertFalse(permissions.inspect(new AccessContext(company(), chef.id(), chef.session(), version, false, true, UUID.randomUUID()),
            companyScope(), "permissions.manage", Set.of()).allowed(), "external account");
        assertFalse(permissions.inspect(new AccessContext(company(), chef.id(), chef.session(), version, false, false, UUID.randomUUID()),
            companyScope(), "permissions.manage", Set.of()).allowed(), "organization without portal accounts");
        assertFalse(permissions.inspect(new AccessContext(company(), chef.id(), chef.session(), version - 1, false, false, null),
            companyScope(), "permissions.manage", Set.of()).allowed(), "stale rights version");
        assertTrue(allowed(chef, "permissions.manage"));
    }

    @Test
    void withoutACompanyNothingIsGrantedAndNothingIsProtected() {
        Person chef = bootstrap("chef");
        Person other = account("other", false);
        UUID company = company();
        long version = permissions.currentVersion(company);
        jdbc.update("delete from platform_company");
        AccessContext claim = new AccessContext(company, chef.id(), chef.session(), version, false, false, null);
        assertFalse(permissions.inspect(claim, () -> ResourceContext.company(company), "permissions.manage", Set.of()).allowed());
        // An installation that was never bootstrapped has no administration to protect: deactivation is not blocked.
        assertEquals(false, accounts.deactivate("test", other.id()).active());
    }

    @Test
    void aSessionWithoutARestrictedLevelIsRequired() {
        Person chef = bootstrap("chef");
        var issued = accounts.create("test", "newcomer", null, false);
        var restricted = sessions.login("newcomer", issued.oneTimePassword(), "198.51.100.2");
        UUID restrictedSession = sessions.resolve(restricted.sessionToken()).orElseThrow().sessionId();
        assertFalse(inTransaction(() -> verification.isCurrentFullSession(issued.account().id(), restrictedSession)),
            "a restricted session is never a full one");
        assertTrue(inTransaction(() -> verification.accountExists(issued.account().id())));
        assertTrue(inTransaction(() -> verification.accountIsActive(issued.account().id())));
        assertFalse(inTransaction(() -> verification.accountExists(UUID.randomUUID())));
        assertTrue(allowed(chef, "permissions.manage"));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
            () -> verification.accountExists(issued.account().id()));
    }

    // ------------------------------------------------------------------------------------------ revocation

    @Test
    void everyWayToTakeAccessAwayEndsTheRightsAfterItsCommit() {
        Person chef = bootstrap("chef");
        int count = 0;
        for (Revocation revocation : revocations()) {
            Person reader = account("reader-" + (count++), false);
            grantTicketRead(chef, reader);
            assertTrue(allowed(reader, "ticket.read"), revocation.name());
            AccessContext before = ctx(reader);
            if (revocation.movesClock()) {
                CLOCK.advance(Duration.ofHours(2));
            }
            revocation.apply().accept(reader);
            assertFalse(permissions.inspect(before, companyScope(), "ticket.read", Set.of()).allowed(), revocation.name());
            assertFalse(allowed(reader, "ticket.read"), revocation.name());
            // The administrator is untouched, except by the shortened period, which applies to everybody.
            if (!revocation.movesClock()) {
                assertTrue(allowed(chef, "permissions.manage"), revocation.name());
            }
            if (revocation.movesClock()) {
                CLOCK.advance(Duration.ofHours(-2));
                chef = bootstrapSession(chef);
                duration.change("test", 24);
            }
        }
    }

    /** A new full session for a person whose session was ended by a test step. */
    private Person bootstrapSession(Person person) {
        var issued = accounts.resetPassword("test", person.id());
        return complete(issued);
    }

    @Test
    void theFixedPeriodEndsTheRightsWithoutAnyRevocation() {
        Person chef = bootstrap("chef");
        CLOCK.advance(Duration.ofHours(23));
        assertTrue(allowed(chef, "permissions.manage"));
        CLOCK.advance(Duration.ofHours(2));
        assertFalse(allowed(chef, "permissions.manage"));
    }

    @Test
    void aReactivatedAccountNeedsANewLoginBecauseItsSessionsEnded() {
        Person chef = bootstrap("chef");
        Person reader = account("reader", false);
        grantTicketRead(chef, reader);
        accounts.deactivate("test", reader.id());
        accounts.reactivate("test", reader.id());
        assertFalse(allowed(reader, "ticket.read"));
    }

    // ------------------------------------------------------------------------------------------ concurrency: access against revocation

    @Test
    void aBusinessCheckThatHoldsItsLocksMakesEveryRevocationWaitForItsCommit() throws Exception {
        Person chef = bootstrap("chef");
        int count = 0;
        for (Revocation revocation : revocations()) {
            Person reader = account("reader-" + (count++), false);
            grantTicketRead(chef, reader);
            AccessContext reading = ctx(reader);
            Held<Boolean> business = hold(() -> {
                permissions.require(reading, companyScope(), "ticket.read", Set.of(), Set.of());
                return true;
            });
            if (revocation.movesClock()) {
                CLOCK.advance(Duration.ofHours(2));
            }
            Future<?> revoke = POOL.submit(() -> revocation.apply().accept(reader));
            assertTrue(waitsAt(revocation.revokerWaitsAt()), revocation.name() + " did not wait for the check that holds its locks");
            assertFalse(revoke.isDone(), revocation.name());
            assertTrue(business.finish(), revocation.name());
            revoke.get(15, TimeUnit.SECONDS);
            assertFalse(permissions.inspect(reading, companyScope(), "ticket.read", Set.of()).allowed(), revocation.name());
            assertFalse(allowed(reader, "ticket.read"), revocation.name());
            if (revocation.movesClock()) {
                CLOCK.advance(Duration.ofHours(-2));
                duration.change("test", 24);
                chef = bootstrapSession(chef);
            }
        }
    }

    @Test
    void aRevocationThatHoldsItsLocksMakesTheCheckWaitAndThenDeny() throws Exception {
        Person chef = bootstrap("chef");
        int count = 0;
        for (Revocation revocation : revocations()) {
            Person reader = account("reader-" + (count++), false);
            grantTicketRead(chef, reader);
            AccessContext reading = ctx(reader);
            assertTrue(allowed(reader, "ticket.read"), revocation.name());
            Held<Boolean> revoke = hold(() -> {
                revocation.apply().accept(reader);
                return true;
            });
            if (revocation.movesClock()) {
                CLOCK.advance(Duration.ofHours(2));
            }
            Future<Boolean> check = POOL.submit(() -> permissions.inspect(reading, companyScope(), "ticket.read", Set.of()).allowed());
            assertTrue(waitsAt(revocation.checkWaitsAt()), revocation.name() + ": the check did not wait for the uncommitted revocation");
            assertFalse(check.isDone(), revocation.name());
            assertTrue(revoke.finish(), revocation.name());
            assertFalse(check.get(15, TimeUnit.SECONDS), revocation.name() + ": the check read the state from before the revocation");
            if (revocation.movesClock()) {
                CLOCK.advance(Duration.ofHours(-2));
                duration.change("test", 24);
                chef = bootstrapSession(chef);
            }
        }
    }

    @Test
    void aRightsChangeWaitsForARunningCheckOfTheAffectedAccountAndTheOtherWayRound() throws Exception {
        Person chef = bootstrap("chef");
        Person reader = account("reader", false);
        UUID group = grantTicketRead(chef, reader);
        AccessContext reading = ctx(reader);
        Held<Boolean> business = hold(() -> {
            permissions.require(reading, companyScope(), "ticket.read", Set.of(), Set.of());
            return true;
        });
        AccessContext admin = ctx(chef);
        Future<?> change = POOL.submit(() -> inTransaction(() -> administration.setMember(admin, group, reader.id(), false)));
        assertTrue(waitsAt("permissions_revision where company_id = $1 for update"));
        assertTrue(business.finish());
        change.get(15, TimeUnit.SECONDS);
        assertFalse(allowed(reader, "ticket.read"));

        // Other order: the open rights change holds the exclusive revision lock, the check waits and sees the result.
        AccessContext second = ctx(chef);
        Held<Boolean> open = hold(() -> {
            administration.setMember(second, group, reader.id(), true);
            return true;
        });
        Future<Boolean> check = POOL.submit(() -> permissions.inspect(ctx(reader), companyScope(), "ticket.read", Set.of()).allowed());
        assertTrue(waitsAt("permissions_revision where company_id = $1 for share"));
        assertTrue(open.finish());
        // The context was built before the commit of the change, so it is stale now and the check denies.
        assertFalse(check.get(15, TimeUnit.SECONDS));
        assertTrue(allowed(reader, "ticket.read"));
    }

    @Test
    void aCheckThatWaitedForAShorteningReadsTheClockAfterTheWaitNotBefore() throws Exception {
        // Codex review of PR #97, F1: the end of the session was compared with a time read before the lock wait.
        Person chef = bootstrap("chef");
        Person reader = account("reader", false);
        grantTicketRead(chef, reader);
        AccessContext reading = ctx(reader);
        CLOCK.advance(Duration.ofHours(1).minusSeconds(1));
        assertTrue(allowed(reader, "ticket.read"), "one second before the end of the shortened period");
        Held<Boolean> shortening = hold(() -> {
            duration.change("test", 1);
            return true;
        });
        Future<Boolean> check = POOL.submit(() -> {
            try {
                inTransaction(() -> permissions.require(reading, companyScope(), "ticket.read", Set.of(), Set.of()));
                return true;
            } catch (PermissionDenied denied) {
                return false;
            }
        });
        assertTrue(waitsAt("for share of s"), "the check must wait at the session row for the open shortening");
        CLOCK.advance(Duration.ofSeconds(2));
        assertTrue(shortening.finish());
        assertFalse(check.get(15, TimeUnit.SECONDS), "the period ended while the check waited, so the check must deny");
    }

    // ------------------------------------------------------------------------------------------ concurrency: last administration

    private record Admins(Person chef, Person second) {
    }

    /** Two accounts that both carry the identity marker, and are both in the company group. */
    private Admins twoAdministrators() {
        Person chef = bootstrap("chef");
        Person second = account("second", true);
        inTransaction(() -> administration.setMember(ctx(chef), companyGroup(), second.id(), true));
        return new Admins(chef, second);
    }

    @Test
    void theLastCompanyAdministrationCannotBeDeactivatedEvenWhenTheIdentityMarkerWouldAllowIt() {
        Person chef = bootstrap("chef");
        Person marked = account("marked", true); // administrator marker, but not in the company group
        ApiProblemException refused = assertThrows(ApiProblemException.class, () -> accounts.deactivate("test", chef.id()));
        assertEquals("account.last_administrator", refused.code());
        assertTrue(allowed(chef, "permissions.manage"), "nothing was deactivated and the session is intact");
        // An account outside the administration may be deactivated.
        assertFalse(accounts.deactivate("test", marked.id()).active());
        // Once a second account is in the administration, the first may go, and then the second is the last.
        Person second = account("second", true);
        inTransaction(() -> administration.setMember(ctx(chef), companyGroup(), second.id(), true));
        assertFalse(accounts.deactivate("test", chef.id()).active());
        assertEquals("account.last_administrator",
            assertThrows(ApiProblemException.class, () -> accounts.deactivate("test", second.id())).code());
        assertTrue(allowed(second, "permissions.manage"));
    }

    @Test
    void aDeactivatedAccountDoesNotKeepTheAdministrationAlive() {
        Admins admins = twoAdministrators();
        accounts.deactivate("test", admins.chef().id());
        Person helper = account("helper", true);
        // The only active holder is the second account: removing it as a member must fail although a deactivated holder remains.
        assertThrows(PermissionDenied.class, () -> inTransaction(
            () -> administration.setMember(ctx(admins.second()), companyGroup(), admins.second().id(), false)));
        assertEquals(2, jdbc.queryForObject("select count(*) from permissions_membership", Integer.class));
        assertNotEquals(helper.id(), admins.second().id());
    }

    @Test
    void deactivationAndMembershipRemovalCannotLeaveNoActiveAdministrationWhicheverRunsFirst() throws Exception {
        // Order 1: the membership change is open, the deactivation waits and is then refused.
        Admins admins = twoAdministrators();
        UUID group = companyGroup();
        Held<Boolean> removal = hold(() -> {
            administration.setMember(ctx(admins.chef()), group, admins.second().id(), false);
            return true;
        });
        Future<?> deactivation = POOL.submit(() -> accounts.deactivate("test", admins.chef().id()));
        assertTrue(waitsAt("permissions_revision where company_id = $1 for update"), "the guard must wait for the open rights change");
        assertTrue(removal.finish());
        assertEquals("account.last_administrator", ((ApiProblemException) cause(deactivation)).code());
        assertTrue(allowed(admins.chef(), "permissions.manage"));

        // Order 2: the deactivation is open, the removal waits and is then refused.
        inTransaction(() -> administration.setMember(ctx(admins.chef()), group, admins.second().id(), true));
        Held<Boolean> open = hold(() -> {
            accounts.deactivate("test", admins.chef().id());
            return true;
        });
        Future<?> selfRemoval = POOL.submit(() -> inTransaction(
            () -> administration.setMember(ctx(admins.second()), group, admins.second().id(), false)));
        assertTrue(waitsAt("permissions_revision where company_id = $1 for update"), "the removal must wait for the open deactivation");
        assertTrue(open.finish());
        assertInstanceOf(PermissionDenied.class, cause(selfRemoval));
        assertEquals(1, jdbc.queryForObject("select count(*) from permissions_membership where user_id = ?", Integer.class, admins.second().id()));
        assertTrue(allowed(admins.second(), "permissions.manage"));
    }

    @Test
    void deactivationAndTheWithdrawalOfTheAdministrationActionCannotLeaveNoActiveAdministrationEither() throws Exception {
        Admins admins = twoAdministrators();
        UUID company = companyGroup();
        UUID extra = inTransaction(() -> administration.createGroup(ctx(admins.chef()), "Zweite Verwaltung")).id();
        inTransaction(() -> administration.setAction(ctx(admins.chef()), extra, companyScope(), "permissions.manage", true));
        inTransaction(() -> administration.setMember(ctx(admins.chef()), extra, admins.second().id(), true));
        inTransaction(() -> administration.setMember(ctx(admins.chef()), company, admins.second().id(), false));

        // Order 1: the withdrawal is open, the deactivation of the other holder waits and is then refused.
        Held<Boolean> withdrawal = hold(() -> {
            administration.setAction(ctx(admins.chef()), extra, companyScope(), "permissions.manage", false);
            return true;
        });
        Future<?> deactivation = POOL.submit(() -> accounts.deactivate("test", admins.chef().id()));
        assertTrue(waitsAt("permissions_revision where company_id = $1 for update"));
        assertTrue(withdrawal.finish());
        assertEquals("account.last_administrator", ((ApiProblemException) cause(deactivation)).code());

        // Order 2: the deactivation is open, the withdrawal by the other holder waits and is then refused.
        inTransaction(() -> administration.setAction(ctx(admins.chef()), extra, companyScope(), "permissions.manage", true));
        Held<Boolean> open = hold(() -> {
            accounts.deactivate("test", admins.chef().id());
            return true;
        });
        Future<?> withdrawing = POOL.submit(() -> inTransaction(
            () -> administration.setAction(ctx(admins.second()), extra, companyScope(), "permissions.manage", false)));
        assertTrue(waitsAt("permissions_revision where company_id = $1 for update"));
        assertTrue(open.finish());
        assertInstanceOf(PermissionDenied.class, cause(withdrawing));
        assertTrue(allowed(admins.second(), "permissions.manage"));
    }
}
