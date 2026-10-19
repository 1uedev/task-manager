package taskmanager.permissions;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.TestDatabase;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PermissionsIntegrationTest.IdentityFixture.class)
class PermissionsIntegrationTest {
    private static String database;
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws SQLException {
        if (database == null) database = TestDatabase.createEmptyDatabase();
        registry.add("spring.datasource.url", () -> database);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    private static final UUID COMPANY = UUID.randomUUID(), PROJECT = UUID.randomUUID(), ORG = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID(), USER = UUID.randomUUID(), DELEGATE = UUID.randomUUID();
    private static final UUID ADMINS = UUID.randomUUID(), READERS = UUID.randomUUID(), RESTRICTED = UUID.randomUUID();
    private static final UUID DELEGATES = UUID.randomUUID(), SESSION = UUID.randomUUID();
    @Autowired Permissions permissions;
    @Autowired PermissionAdministration administration;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired IdentityFixture identity;

    @TestConfiguration(proxyBeanMethods = false)
    static class IdentityFixture {
        volatile boolean current = true;
        @Bean IdentityAccess syntheticIdentity(JdbcTemplate jdbc) {
            return new IdentityAccess() {
                public boolean isCurrent(AccessContext context) {
                    if (!current || !context.companyId().equals(COMPANY) || !context.sessionId().equals(SESSION)) return false;
                    var states = jdbc.queryForList("select active from test_identity_state where user_id=? for share", Boolean.class, context.userId());
                    return states.size() == 1 && states.getFirst();
                }
                public boolean userExists(UUID company, UUID user) {
                    return COMPANY.equals(company) && !jdbc.queryForList("select active from test_identity_state where user_id=? for share", Boolean.class, user).isEmpty();
                }
                public boolean userActive(UUID company, UUID user) {
                    return COMPANY.equals(company) && jdbc.queryForList("select active from test_identity_state where user_id=? for share", Boolean.class, user).contains(true);
                }
            };
        }
    }

    @BeforeEach
    void seedOnlySyntheticPolicy() {
        identity.current = true;
        for (String table : new String[] {"audit", "field", "action", "membership", "group", "revision"})
            jdbc.update("delete from permissions_" + table);
        jdbc.execute("create table if not exists test_business_effect (id uuid primary key)");
        jdbc.execute("create table if not exists test_identity_state (user_id uuid primary key, active boolean not null)");
        jdbc.execute("create table if not exists test_resource_state (project_id uuid primary key, member boolean not null)");
        jdbc.update("delete from test_business_effect");
        jdbc.update("delete from test_identity_state");
        jdbc.update("delete from test_resource_state");
        for (UUID user : Set.of(ADMIN, USER, DELEGATE)) jdbc.update("insert into test_identity_state values (?,true)", user);
        jdbc.update("insert into test_resource_state values (?,true)", PROJECT);
        jdbc.update("insert into permissions_revision(company_id) values (?)", COMPANY);
        for (UUID group : Set.of(ADMINS, READERS, RESTRICTED, DELEGATES))
            jdbc.update("insert into permissions_group(company_id,id,name) values (?,?,?)", COMPANY, group, "synthetic-" + group);
        member(ADMINS, ADMIN); member(READERS, USER); member(RESTRICTED, USER); member(DELEGATES, DELEGATE);
        action(ADMINS, null, "permissions.manage"); action(ADMINS, null, "permissions.audit.read");
        action(READERS, PROJECT, "ticket.edit"); action(READERS, PROJECT, "ticket.read");
        action(DELEGATES, PROJECT, "permissions.manage"); action(DELEGATES, PROJECT, "ticket.read");
        field(READERS, null, "title", "READ", "ALLOW"); field(READERS, PROJECT, "title", "WRITE", "ALLOW");
        field(READERS, PROJECT, "secret", "READ", "ALLOW"); field(READERS, PROJECT, "secret", "WRITE", "ALLOW");
        field(RESTRICTED, PROJECT, "secret", "READ", "DENY");
        field(DELEGATES, PROJECT, "title", "READ", "ALLOW"); field(DELEGATES, PROJECT, "title", "WRITE", "ALLOW");
    }

    @Test
    void evaluatesUnionFieldsAndFreshContextWithoutCaching() {
        Decision decision = permissions.inspect(actor(USER), scope(), "ticket.edit", Set.of("title", "secret", "absent"));
        assertTrue(decision.allowed());
        assertEquals(Set.of("title"), decision.readableFields());
        assertEquals(Set.of("title"), decision.writableFields());
        AccessContext stale = actor(USER);
        mutate(() -> administration.setMember(actor(ADMIN), READERS, USER, false));
        assertFalse(permissions.inspect(stale, scope(), "ticket.read", Set.of()).allowed());
        assertFalse(permissions.inspect(actor(USER), scope(), "ticket.read", Set.of()).allowed());
    }

    @Test
    void absentOrAmbiguousIdentityAdapterDeniesWithoutTestFallback() {
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var isolated = new taskmanager.permissions.internal.JdbcPermissions(jdbc, beans.getBeanProvider(IdentityAccess.class));
        assertFalse(inTransaction(() -> isolated.inspect(actor(USER), scope(), "ticket.read", Set.of())).allowed());
        beans.registerSingleton("one", (IdentityAccess) actor -> true);
        beans.registerSingleton("two", (IdentityAccess) actor -> true);
        assertFalse(inTransaction(() -> isolated.inspect(actor(USER), scope(), "ticket.read", Set.of())).allowed());
        var primary = new org.springframework.beans.factory.support.RootBeanDefinition(IdentityAccess.class,
            () -> (IdentityAccess) actor -> true);
        primary.setPrimary(true);
        beans.registerBeanDefinition("primary", primary);
        assertFalse(inTransaction(() -> isolated.inspect(actor(USER), scope(), "ticket.read", Set.of())).allowed());
    }

    @Test
    void failsClosedForMissingInvalidAndForeignContextAndLoadsResourceAfterIdentity() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<ResourceContext> resource = () -> { calls.incrementAndGet(); return scope().get(); };
        assertFalse(permissions.inspect(null, resource, "ticket.read", Set.of()).allowed());
        identity.current = false;
        assertFalse(permissions.inspect(actor(USER), resource, "ticket.read", Set.of()).allowed());
        identity.current = true;
        AccessContext passwordChange = new AccessContext(COMPANY, USER, SESSION, 0, true, false, null);
        assertFalse(permissions.inspect(passwordChange, resource, "ticket.read", Set.of()).allowed());
        assertEquals(0, calls.get());
        assertFalse(permissions.inspect(actor(USER), () -> null, "ticket.read", Set.of()).allowed());
        assertFalse(permissions.inspect(actor(USER), () -> new ResourceContext(UUID.randomUUID(), PROJECT, ORG, true, "ticket"), "ticket.read", Set.of()).allowed());
        assertFalse(permissions.inspect(actor(USER), () -> new ResourceContext(COMPANY, PROJECT, ORG, false, "ticket"), "ticket.read", Set.of()).allowed());
        var external = new AccessContext(COMPANY, USER, SESSION, 0, false, true, UUID.randomUUID());
        assertFalse(permissions.inspect(external, scope(), "ticket.read", Set.of()).allowed());
    }

    @Test
    void requiresAnOuterTransactionAndRollsBackTheEntireForbiddenChange() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
            () -> permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title")));
        assertThrows(PermissionDenied.class, () -> mutate(() -> {
            jdbc.update("insert into test_business_effect values (?)", UUID.randomUUID());
            permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title", "secret"));
        }));
        assertEquals(0, effects());
        mutate(() -> {
            permissions.require(actor(USER), scope(), "ticket.edit", Set.of("title"), Set.of("title"));
            jdbc.update("insert into test_business_effect values (?)", UUID.randomUUID());
        });
        assertEquals(1, effects());
        var repeatable = new TransactionTemplate(transactions);
        repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThrows(PermissionDenied.class, () -> repeatable.executeWithoutResult(status ->
            permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title"))));
    }

    @Test
    void administratorHasNoImplicitBusinessAccessAndAllEditsAreAuditedAtomically() {
        assertFalse(permissions.inspect(actor(ADMIN), scope(), "ticket.read", Set.of()).allowed());
        UUID group = inTransaction(() -> administration.createGroup(actor(ADMIN), "Synthetic group")).id();
        mutate(() -> administration.setMember(actor(ADMIN), group, USER, true));
        mutate(() -> administration.setAction(actor(ADMIN), group, scope(), "ticket.comment", true));
        mutate(() -> administration.setField(actor(ADMIN), group, scope(), "comment", PermissionAdministration.Operation.READ, PermissionAdministration.Effect.ALLOW));
        assertTrue(permissions.inspect(actor(USER), scope(), "ticket.comment", Set.of("comment")).readableFields().contains("comment"));
        var audit = administration.audit(actor(ADMIN), 100);
        assertEquals(4, audit.size());
        assertEquals(4, audit.getFirst().rightsVersion());
        assertTrue(audit.stream().allMatch(row -> row.actorId().equals(ADMIN)));
        assertThrows(PermissionDenied.class, () -> administration.audit(actor(USER), 100));
        assertThrows(PermissionDenied.class, () -> administration.audit(actor(ADMIN), 101));
        assertThrows(IllegalStateException.class, () -> mutate(() -> {
            administration.createGroup(actor(ADMIN), "Rolled back name");
            throw new IllegalStateException("synthetic rollback");
        }));
        assertEquals(4, permissions.currentVersion(COMPANY));
        assertEquals(4, administration.audit(actor(ADMIN), 100).size());
        assertFalse(administration.groups(actor(ADMIN)).stream().anyMatch(row -> row.name().equals("Rolled back name")));
    }

    @Test
    void delegatesCannotEscalateCompanyMembershipActionsOrFieldRights() {
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.createGroup(actor(DELEGATE), "forbidden")));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setMember(actor(DELEGATE), READERS, DELEGATE, true)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setAction(actor(DELEGATE), READERS,
            () -> ResourceContext.company(COMPANY), "ticket.read", true)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setAction(actor(DELEGATE), READERS, scope(), "permissions.manage", true)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setAction(actor(DELEGATE), READERS, scope(), "ticket.edit", true)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setField(actor(DELEGATE), RESTRICTED, scope(), "secret",
            PermissionAdministration.Operation.READ, PermissionAdministration.Effect.REMOVE)));
        mutate(() -> administration.setAction(actor(DELEGATE), RESTRICTED, scope(), "ticket.read", true));
        mutate(() -> administration.setField(actor(DELEGATE), READERS, scope(), "title",
            PermissionAdministration.Operation.WRITE, PermissionAdministration.Effect.DENY));
        assertFalse(permissions.inspect(actor(USER), scope(), "ticket.edit", Set.of("title")).writableFields().contains("title"));
        assertThrows(PermissionDenied.class, () -> administration.audit(actor(DELEGATE), 100));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setMember(actor(ADMIN), UUID.randomUUID(), USER, true)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setMember(actor(ADMIN), READERS, UUID.randomUUID(), true)));
        assertEquals(2, permissions.currentVersion(COMPANY));
    }

    @Test
    void theLastCompanyAdministrationCannotRemoveItselfAndGhostGroupsDoNotCount() {
        long before = permissions.currentVersion(COMPANY);
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setAction(actor(ADMIN), ADMINS,
            () -> ResourceContext.company(COMPANY), "permissions.manage", false)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setMember(actor(ADMIN), ADMINS, ADMIN, false)));
        assertEquals(before, permissions.currentVersion(COMPANY));
        assertEquals(1, companyManagerGroups());
        // A group whose only member is no known account is not an administration.
        UUID ghosts = UUID.randomUUID();
        jdbc.update("insert into permissions_group(company_id,id,name) values (?,?,?)", COMPANY, ghosts, "synthetic-ghosts");
        member(ghosts, UUID.randomUUID());
        action(ghosts, null, "permissions.manage");
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setAction(actor(ADMIN), ADMINS,
            () -> ResourceContext.company(COMPANY), "permissions.manage", false)));
        // Unrelated membership changes stay possible while an administration remains.
        mutate(() -> administration.setMember(actor(ADMIN), READERS, USER, false));
        assertEquals(before + 1, permissions.currentVersion(COMPANY));
    }

    @Test
    void anotherAdministrationMakesRemovalPossibleAndTheRemainingOneIsProtected() {
        UUID second = UUID.randomUUID(), secondGroup = UUID.randomUUID();
        jdbc.update("insert into test_identity_state values (?,true)", second);
        jdbc.update("insert into permissions_group(company_id,id,name) values (?,?,?)", COMPANY, secondGroup, "synthetic-second");
        member(secondGroup, second);
        action(secondGroup, null, "permissions.manage");
        mutate(() -> administration.setMember(actor(ADMIN), ADMINS, ADMIN, false));
        assertEquals(1, companyManagerGroups());
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setMember(actor(second), secondGroup, second, false)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setAction(actor(second), secondGroup,
            () -> ResourceContext.company(COMPANY), "permissions.manage", false)));
        assertEquals(1, companyManagerGroups());
    }

    @Test
    void aDeactivatedAccountDoesNotKeepTheCompanyAdministrationAlive() {
        UUID second = UUID.randomUUID(), secondGroup = UUID.randomUUID();
        jdbc.update("insert into test_identity_state values (?,false)", second);
        jdbc.update("insert into permissions_group(company_id,id,name) values (?,?,?)", COMPANY, secondGroup, "synthetic-second");
        member(secondGroup, second);
        action(secondGroup, null, "permissions.manage");
        long before = permissions.currentVersion(COMPANY);
        int audits = jdbc.queryForObject("select count(*) from permissions_audit", Integer.class);
        // Both withdrawal paths of the last active administration are refused although a deactivated holder is known.
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setMember(actor(ADMIN), ADMINS, ADMIN, false)));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setAction(actor(ADMIN), ADMINS,
            () -> ResourceContext.company(COMPANY), "permissions.manage", false)));
        assertEquals(before, permissions.currentVersion(COMPANY));
        assertEquals(audits, jdbc.queryForObject("select count(*) from permissions_audit", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from permissions_membership where group_id=? and user_id=?", Integer.class, ADMINS, ADMIN));
        assertTrue(permissions.inspect(actor(ADMIN), () -> ResourceContext.company(COMPANY), "permissions.manage", Set.of()).allowed());
        // Once that account is active again it counts, and the withdrawal is possible.
        jdbc.update("update test_identity_state set active=true where user_id=?", second);
        mutate(() -> administration.setMember(actor(ADMIN), ADMINS, ADMIN, false));
        assertEquals(before + 1, permissions.currentVersion(COMPANY));
    }

    @Test
    void aCompanyDenyStaysProtectedAfterItsAuthorLosesTheCompanyRoleButKeepsADelegation() {
        var write = PermissionAdministration.Operation.WRITE;
        var deny = PermissionAdministration.Effect.DENY;
        UUID second = UUID.randomUUID(), secondGroup = UUID.randomUUID();
        jdbc.update("insert into test_identity_state values (?,true)", second);
        jdbc.update("insert into permissions_group(company_id,id,name) values (?,?,?)", COMPANY, secondGroup, "synthetic-second");
        member(secondGroup, second);
        action(secondGroup, null, "permissions.manage");
        member(DELEGATES, ADMIN);
        mutate(() -> administration.setField(actor(ADMIN), READERS, scope(), "title", write, deny));
        assertEquals(Boolean.TRUE, fieldRuleFromCompany(READERS, "title", "WRITE"));
        // An independent administration removes only the company role; the person keeps the project delegation.
        mutate(() -> administration.setMember(actor(second), ADMINS, ADMIN, false));
        assertFalse(permissions.inspect(actor(ADMIN), () -> ResourceContext.company(COMPANY), "permissions.manage", Set.of()).allowed());
        assertTrue(permissions.inspect(actor(ADMIN), scope(), "permissions.manage", Set.of()).allowed());
        for (var attempt : PermissionAdministration.Effect.values())
            assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setField(actor(ADMIN), READERS, scope(), "title", write, attempt)));
        assertEquals("DENY", fieldRule(READERS, "title", "WRITE").effect());
        // A deny the same person sets now, as a delegate, can be withdrawn again; one of unknown origin cannot.
        mutate(() -> administration.setField(actor(ADMIN), RESTRICTED, scope(), "title", write, deny));
        assertEquals(Boolean.FALSE, fieldRuleFromCompany(RESTRICTED, "title", "WRITE"));
        mutate(() -> administration.setField(actor(ADMIN), RESTRICTED, scope(), "title", write, PermissionAdministration.Effect.REMOVE));
        assertNull(fieldRule(RESTRICTED, "title", "WRITE"));
        jdbc.update("insert into permissions_field(company_id,group_id,project_id,resource_type,field_key,operation,effect,set_by) values (?,?,?,?,?,?,?,?)",
            COMPANY, RESTRICTED, PROJECT, "ticket", "title", "WRITE", "DENY", ADMIN);
        assertNull(fieldRuleFromCompany(RESTRICTED, "title", "WRITE"));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setField(actor(ADMIN), RESTRICTED, scope(), "title", write,
            PermissionAdministration.Effect.REMOVE)));
    }

    @Test
    void upgradeFromV0005KeepsAnExistingRuleWithUnknownRoleAndItsAuthor() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        org.flywaydb.core.Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).target("0005").load().migrate();
        var upgrade = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
        UUID company = UUID.randomUUID(), group = UUID.randomUUID();
        upgrade.update("insert into permissions_revision(company_id) values (?)", company);
        upgrade.update("insert into permissions_group(company_id,id,name) values (?,?,?)", company, group, "synthetic-legacy");
        upgrade.update("insert into permissions_field(company_id,group_id,project_id,resource_type,field_key,operation,effect,set_by) values (?,?,?,'ticket','title','WRITE','DENY',?)",
            company, group, PROJECT, DELEGATE);
        var applied = upgrade.queryForList("select version,checksum from flyway_schema_history where success order by installed_rank");
        org.flywaydb.core.Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).load().migrate();
        assertEquals(applied, upgrade.queryForList("select version,checksum from flyway_schema_history where success and version<>'0007' order by installed_rank"));
        assertEquals("DENY", upgrade.queryForObject("select effect from permissions_field where group_id=?", String.class, group));
        assertEquals(DELEGATE, upgrade.queryForObject("select set_by from permissions_field where group_id=?", UUID.class, group));
        assertNull(upgrade.queryForObject("select set_by_company from permissions_field where group_id=?", Boolean.class, group));
    }

    @Test
    void twoAdministrationsRevokingEachOtherConcurrentlyNeverLeaveNone() throws Exception {
        UUID second = UUID.randomUUID(), secondGroup = UUID.randomUUID();
        jdbc.update("insert into test_identity_state values (?,true)", second);
        jdbc.update("insert into permissions_group(company_id,id,name) values (?,?,?)", COMPANY, secondGroup, "synthetic-second");
        member(secondGroup, second);
        action(secondGroup, null, "permissions.manage");
        AccessContext firstActor = actor(ADMIN), secondActor = actor(second);
        CountDownLatch revoked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger otherPid = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> mutate(() -> {
                administration.setAction(firstActor, ADMINS, () -> ResourceContext.company(COMPANY), "permissions.manage", false);
                revoked.countDown(); await(release);
            }));
            assertTrue(revoked.await(10, TimeUnit.SECONDS));
            Future<?> other = pool.submit(() -> mutate(() -> {
                otherPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                administration.setAction(secondActor, secondGroup, () -> ResourceContext.company(COMPANY), "permissions.manage", false);
            }));
            try { assertBlocked(otherPid); } finally { release.countDown(); }
            first.get(10, TimeUnit.SECONDS);
            ExecutionException rejected = assertThrows(ExecutionException.class, () -> other.get(10, TimeUnit.SECONDS));
            assertInstanceOf(PermissionDenied.class, rejected.getCause());
        } finally { release.countDown(); }
        assertEquals(1, companyManagerGroups());
    }

    @Test
    void projectDelegatesWithdrawOnlyOwnDeniesNeverThoseOfOthers() {
        var write = PermissionAdministration.Operation.WRITE;
        var read = PermissionAdministration.Operation.READ;
        var deny = PermissionAdministration.Effect.DENY;
        var allow = PermissionAdministration.Effect.ALLOW;
        var remove = PermissionAdministration.Effect.REMOVE;
        // A deny set by the company administration stays out of reach for removal, replacement and takeover.
        mutate(() -> administration.setField(actor(ADMIN), READERS, scope(), "title", write, deny));
        for (var attempt : new PermissionAdministration.Effect[] {remove, allow, deny})
            assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setField(actor(DELEGATE), READERS, scope(), "title", write, attempt)));
        assertEquals("ADMIN", fieldRule(READERS, "title", "WRITE").setBy());
        // A deny without recorded origin counts as set by someone else.
        field(READERS, PROJECT, "title", "READ", "DENY");
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setField(actor(DELEGATE), READERS, scope(), "title", read, remove)));
        // Own denies can be withdrawn, new ones set and ALLOW rows of others changed as before.
        mutate(() -> administration.setField(actor(DELEGATE), RESTRICTED, scope(), "title", write, deny));
        assertEquals("DELEGATE", fieldRule(RESTRICTED, "title", "WRITE").setBy());
        mutate(() -> administration.setField(actor(DELEGATE), RESTRICTED, scope(), "title", write, remove));
        assertNull(fieldRule(RESTRICTED, "title", "WRITE"));
        // Once the company administration takes over the rule, the delegate loses the right to withdraw it.
        mutate(() -> administration.setField(actor(DELEGATE), RESTRICTED, scope(), "title", write, deny));
        mutate(() -> administration.setField(actor(ADMIN), RESTRICTED, scope(), "title", write, deny));
        assertThrows(PermissionDenied.class, () -> mutate(() -> administration.setField(actor(DELEGATE), RESTRICTED, scope(), "title", write, remove)));
        // The company administration itself is unrestricted.
        mutate(() -> administration.setField(actor(ADMIN), RESTRICTED, scope(), "title", write, remove));
        assertNull(fieldRule(RESTRICTED, "title", "WRITE"));
    }

    private record Rule(String effect, String setBy) {}
    private Rule fieldRule(UUID group, String field, String operation) {
        var rows = jdbc.query("select effect, set_by from permissions_field where company_id=? and group_id=? and project_id=? and resource_type='ticket' and field_key=? and operation=?",
            (rs, row) -> new Rule(rs.getString(1), name(rs.getObject(2, UUID.class))), COMPANY, group, PROJECT, field, operation);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private Boolean fieldRuleFromCompany(UUID group, String field, String operation) {
        return jdbc.queryForObject("select set_by_company from permissions_field where company_id=? and group_id=? and project_id=? and resource_type='ticket' and field_key=? and operation=?",
            Boolean.class, COMPANY, group, PROJECT, field, operation);
    }
    private static String name(UUID user) { return ADMIN.equals(user) ? "ADMIN" : DELEGATE.equals(user) ? "DELEGATE" : user == null ? null : "OTHER"; }
    private int companyManagerGroups() {
        return jdbc.queryForObject("select count(*) from permissions_action where project_id is null and action_key='permissions.manage'", Integer.class)
            - jdbc.queryForObject("select count(*) from permissions_action a where project_id is null and action_key='permissions.manage' and not exists (select 1 from permissions_membership m where m.company_id=a.company_id and m.group_id=a.group_id and m.user_id in (select user_id from test_identity_state))", Integer.class);
    }

    @Test
    void revocationCommittedFirstPreventsWaitingOldWrite() throws Exception {
        AccessContext old = actor(USER);
        CountDownLatch revoked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> revoke = pool.submit(() -> mutate(() -> {
                administration.setMember(actor(ADMIN), READERS, USER, false);
                revoked.countDown(); await(release);
            }));
            assertTrue(revoked.await(10, TimeUnit.SECONDS));
            Future<?> write = pool.submit(() -> mutate(() -> {
                writerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                permissions.require(old, scope(), "ticket.edit", Set.of(), Set.of("title"));
                jdbc.update("insert into test_business_effect values (?)", UUID.randomUUID());
            }));
            try { assertBlocked(writerPid); } finally { release.countDown(); }
            revoke.get(10, TimeUnit.SECONDS);
            ExecutionException rejected = assertThrows(ExecutionException.class, () -> write.get(10, TimeUnit.SECONDS));
            assertInstanceOf(PermissionDenied.class, rejected.getCause());
        } finally { release.countDown(); }
        assertEquals(0, effects());
    }

    @Test
    void writeCommittedFirstFinishesBeforeRevocationAndLaterWriteIsDenied() throws Exception {
        CountDownLatch checked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger revokePid = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> write = pool.submit(() -> mutate(() -> {
                permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title"));
                jdbc.update("insert into test_business_effect values (?)", UUID.randomUUID());
                checked.countDown(); await(release);
            }));
            assertTrue(checked.await(10, TimeUnit.SECONDS));
            Future<?> revoke = pool.submit(() -> mutate(() -> {
                revokePid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                administration.setMember(actor(ADMIN), READERS, USER, false);
            }));
            try { assertBlocked(revokePid); } finally { release.countDown(); }
            write.get(10, TimeUnit.SECONDS); revoke.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertEquals(1, effects());
        assertThrows(PermissionDenied.class, () -> mutate(() ->
            permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title"))));
    }

    private void assertBlocked(AtomicInteger pid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (pid.get() != 0 && Boolean.TRUE.equals(jdbc.queryForObject(
                    "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid.get()))) return;
            Thread.sleep(10);
        }
        fail("Expected a real PostgreSQL lock wait, not just scheduler timing");
    }

    @Test
    void identityOrResourceRevocationCommittedFirstRejectsWaitingWrite() throws Exception {
        for (boolean identityChange : new boolean[] {true, false}) {
            CountDownLatch changed = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger writerPid = new AtomicInteger();
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                Future<?> revoke = pool.submit(() -> mutate(() -> {
                    if (identityChange) jdbc.update("update test_identity_state set active=false where user_id=?", USER);
                    else jdbc.update("update test_resource_state set member=false where project_id=?", PROJECT);
                    changed.countDown(); await(release);
                }));
                assertTrue(changed.await(10, TimeUnit.SECONDS));
                Future<?> write = pool.submit(() -> mutate(() -> {
                    writerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title"));
                    jdbc.update("insert into test_business_effect values (?)", UUID.randomUUID());
                }));
                try { assertBlocked(writerPid); } finally { release.countDown(); }
                revoke.get(10, TimeUnit.SECONDS);
                ExecutionException rejected = assertThrows(ExecutionException.class, () -> write.get(10, TimeUnit.SECONDS));
                assertInstanceOf(PermissionDenied.class, rejected.getCause());
            } finally { release.countDown(); }
            jdbc.update("update test_identity_state set active=true where user_id=?", USER);
            jdbc.update("update test_resource_state set member=true where project_id=?", PROJECT);
        }
        assertEquals(0, effects());
    }

    @Test
    void identityAndResourceAdaptersHoldTheirOwnLocksThroughBusinessCommit() throws Exception {
        for (boolean identityChange : new boolean[] {true, false}) {
            CountDownLatch checked = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger changePid = new AtomicInteger();
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                Future<?> write = pool.submit(() -> mutate(() -> {
                    permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title"));
                    jdbc.update("insert into test_business_effect values (?)", UUID.randomUUID());
                    checked.countDown(); await(release);
                }));
                assertTrue(checked.await(10, TimeUnit.SECONDS));
                Future<?> revoke = pool.submit(() -> mutate(() -> {
                    changePid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    if (identityChange) jdbc.update("update test_identity_state set active=false where user_id=?", USER);
                    else jdbc.update("update test_resource_state set member=false where project_id=?", PROJECT);
                }));
                try { assertBlocked(changePid); } finally { release.countDown(); }
                write.get(10, TimeUnit.SECONDS); revoke.get(10, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            assertThrows(PermissionDenied.class, () -> mutate(() ->
                permissions.require(actor(USER), scope(), "ticket.edit", Set.of(), Set.of("title"))));
            jdbc.update("update test_identity_state set active=true where user_id=?", USER);
            jdbc.update("update test_resource_state set member=true where project_id=?", PROJECT);
        }
        assertEquals(2, effects());
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Latch timeout"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    private AccessContext actor(UUID user) { return new AccessContext(COMPANY, user, SESSION, permissions.currentVersion(COMPANY), false, false, null); }
    private Supplier<ResourceContext> scope() { return () -> new ResourceContext(COMPANY, PROJECT, ORG,
        Boolean.TRUE.equals(jdbc.queryForObject("select member from test_resource_state where project_id=? for share", Boolean.class, PROJECT)), "ticket"); }
    private void mutate(Runnable body) { new TransactionTemplate(transactions).executeWithoutResult(status -> body.run()); }
    private <T> T inTransaction(Supplier<T> body) { return new TransactionTemplate(transactions).execute(status -> body.get()); }
    private int effects() { return jdbc.queryForObject("select count(*) from test_business_effect", Integer.class); }
    private void member(UUID group, UUID user) { jdbc.update("insert into permissions_membership values (?,?,?)", COMPANY, group, user); }
    private void action(UUID group, UUID project, String action) { jdbc.update("insert into permissions_action values (?,?,?,?)", COMPANY, group, project, action); }
    private void field(UUID group, UUID project, String field, String operation, String effect) {
        jdbc.update("insert into permissions_field values (?,?,?,?,?,?,?)", COMPANY, group, project, "ticket", field, operation, effect);
    }
}
