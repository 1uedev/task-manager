package taskmanager.permissions;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.TestDatabase;

/** Review of PR #82 at 8a2a572. Passing N1/N2 assertions CONFIRM the reported gaps. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PermissionsFixesFollowupProbeTest.IdentityFixture.class)
class PermissionsFixesFollowupProbeTest {
    static final UUID COMPANY = UUID.randomUUID(), PROJECT = UUID.randomUUID(), SESSION = UUID.randomUUID();
    static final UUID ADMIN = UUID.randomUUID(), BACKUP = UUID.randomUUID(), USER = UUID.randomUUID();
    static final UUID ADMINS = UUID.randomUUID(), BACKUPS = UUID.randomUUID(), DELEGATES = UUID.randomUUID(), READERS = UUID.randomUUID();
    static final String DATABASE = emptyDatabase();

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

    // Same distinction as the author's fixture: isCurrent checks active, userExists checks existence.
    // Both acquire a row lock until the caller's transaction ends; there is no production adapter.
    @TestConfiguration(proxyBeanMethods = false)
    static class IdentityFixture {
        @Bean IdentityAccess followupIdentity(JdbcTemplate jdbc) {
            return new IdentityAccess() {
                public boolean isCurrent(AccessContext context) {
                    if (!COMPANY.equals(context.companyId()) || !SESSION.equals(context.sessionId())) return false;
                    var states = jdbc.queryForList("select active from review_identity_state where user_id=? for share", Boolean.class, context.userId());
                    return states.size() == 1 && states.getFirst();
                }
                public boolean userExists(UUID company, UUID user) {
                    return COMPANY.equals(company) && !jdbc.queryForList(
                        "select active from review_identity_state where user_id=? for share", Boolean.class, user).isEmpty();
                }
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Permissions permissions;
    @Autowired PermissionAdministration administration;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void seed() {
        for (String suffix : new String[] {"audit", "field", "action", "membership", "group", "revision"})
            jdbc.update("delete from permissions_" + suffix);
        jdbc.execute("create table if not exists review_identity_state(user_id uuid primary key, active boolean not null)");
        jdbc.update("delete from review_identity_state");
        for (UUID user : Set.of(ADMIN, BACKUP, USER)) jdbc.update("insert into review_identity_state values (?,true)", user);
        jdbc.update("insert into permissions_revision(company_id) values (?)", COMPANY);
        for (UUID group : Set.of(ADMINS, BACKUPS, DELEGATES, READERS))
            jdbc.update("insert into permissions_group(company_id,id,name) values (?,?,?)", COMPANY, group, "synthetic-"+group);
        member(ADMINS, ADMIN); member(BACKUPS, BACKUP); member(DELEGATES, ADMIN); member(READERS, USER);
        action(ADMINS, null, "permissions.manage"); action(BACKUPS, null, "permissions.manage");
        action(DELEGATES, PROJECT, "permissions.manage"); action(READERS, PROJECT, "ticket.edit");
        for (UUID group : Set.of(DELEGATES, READERS)) {
            field(group, "READ", "ALLOW"); field(group, "WRITE", "ALLOW");
        }
    }

    @Test
    void n1KnownInactiveBackupAllowsWithdrawalOfLastActiveAdministration() {
        for (boolean membership : new boolean[] {true, false}) {
            seed();
            jdbc.update("update review_identity_state set active=false where user_id=?", BACKUP);
            assertFalse(permissions.inspect(actor(BACKUP), () -> ResourceContext.company(COMPANY), "permissions.manage", Set.of()).allowed());
            withdrawOwnAdministration(membership);
            assertEquals(1L, permissions.currentVersion(COMPANY));
            assertEquals(1, jdbc.queryForObject("select count(*) from permissions_audit", Integer.class));
            assertFalse(permissions.inspect(actor(ADMIN), () -> ResourceContext.company(COMPANY), "permissions.manage", Set.of()).allowed());
            assertFalse(permissions.inspect(actor(BACKUP), () -> ResourceContext.company(COMPANY), "permissions.manage", Set.of()).allowed());
        }
        System.out.println("FOLLOWUP N1: membership and action withdrawal committed; only known inactive company administrator remained");
    }

    @Test
    void n2FormerCompanyAdministratorCanReplaceCompanyDenyAsProjectDelegate() {
        changeField(ADMIN, PermissionAdministration.Effect.DENY);
        assertEquals(ADMIN, jdbc.queryForObject("select set_by from permissions_field where group_id=? and operation='WRITE'", UUID.class, READERS));
        assertFalse(permissions.inspect(actor(USER), scope(), "ticket.edit", Set.of("title")).writableFields().contains("title"));
        // An independent, still active administrator removes only ADMIN's company role.
        mutate(() -> administration.setMember(actor(BACKUP), ADMINS, ADMIN, false));
        assertFalse(permissions.inspect(actor(ADMIN), () -> ResourceContext.company(COMPANY), "permissions.manage", Set.of()).allowed());
        assertTrue(permissions.inspect(actor(ADMIN), scope(), "permissions.manage", Set.of()).allowed());
        changeField(ADMIN, PermissionAdministration.Effect.ALLOW);
        assertTrue(permissions.inspect(actor(USER), scope(), "ticket.edit", Set.of("title")).writableFields().contains("title"));
        assertEquals(3L, permissions.currentVersion(COMPANY));
        assertEquals(3, jdbc.queryForObject("select count(*) from permissions_audit", Integer.class));
        System.out.println("FOLLOWUP N2: after loss of company role, same person replaced a company-origin deny with ALLOW using project delegation");
    }

    @Test
    void controlUnknownHolderIsRejectedAndRollbackPreservesMembershipRuleRevisionAndAudit() {
        for (boolean membership : new boolean[] {true, false}) {
            seed();
            jdbc.update("delete from review_identity_state where user_id=?", BACKUP);
            assertThrows(PermissionDenied.class, () -> withdrawOwnAdministration(membership));
            assertEquals(0L, permissions.currentVersion(COMPANY));
            assertEquals(0, jdbc.queryForObject("select count(*) from permissions_audit", Integer.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from permissions_membership where group_id=? and user_id=?", Integer.class, ADMINS, ADMIN));
            assertEquals(1, jdbc.queryForObject("select count(*) from permissions_action where group_id=? and project_id is null and action_key='permissions.manage'", Integer.class, ADMINS));
            assertTrue(permissions.inspect(actor(ADMIN), () -> ResourceContext.company(COMPANY), "permissions.manage", Set.of()).allowed());
        }
        System.out.println("FOLLOWUP CONTROL: unknown backup rejected; both withdrawal paths rolled back policy, revision and audit");
    }

    @Test
    void controlRealV0004ToV0005UpgradeKeepsExistingDenyAndUnknownOrigin() {
        String url = emptyDatabase();
        Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).target("0004").load().migrate();
        var upgrade = new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
        UUID company = UUID.randomUUID(), group = UUID.randomUUID();
        upgrade.update("insert into permissions_revision(company_id) values (?)", company);
        upgrade.update("insert into permissions_group(company_id,id,name) values (?,?,?)", company, group, "synthetic-legacy");
        upgrade.update("insert into permissions_field(company_id,group_id,project_id,resource_type,field_key,operation,effect) values (?,?,?,'ticket','title','WRITE','DENY')", company, group, PROJECT);
        var oldChecksums = upgrade.queryForList("select version,checksum from flyway_schema_history where success order by installed_rank");
        Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).load().migrate();
        assertEquals(oldChecksums, upgrade.queryForList("select version,checksum from flyway_schema_history where success and version<>'0005' order by installed_rank"));
        assertEquals(5, upgrade.queryForObject("select count(*) from flyway_schema_history where success", Integer.class));
        assertEquals("DENY", upgrade.queryForObject("select effect from permissions_field where group_id=?", String.class, group));
        assertNull(upgrade.queryForObject("select set_by from permissions_field where group_id=?", UUID.class, group));
        System.out.println("FOLLOWUP CONTROL: real V0004 -> V0005 upgrade preserved existing deny, NULL provenance and prior migration checksums");
    }

    private void withdrawOwnAdministration(boolean membership) {
        mutate(() -> {
            if (membership) administration.setMember(actor(ADMIN), ADMINS, ADMIN, false);
            else administration.setAction(actor(ADMIN), ADMINS, () -> ResourceContext.company(COMPANY), "permissions.manage", false);
        });
    }
    private void changeField(UUID user, PermissionAdministration.Effect effect) {
        mutate(() -> administration.setField(actor(user), READERS, scope(), "title", PermissionAdministration.Operation.WRITE, effect));
    }
    private AccessContext actor(UUID user) { return new AccessContext(COMPANY, user, SESSION, permissions.currentVersion(COMPANY), false, false, null); }
    private Supplier<ResourceContext> scope() { return () -> new ResourceContext(COMPANY, PROJECT, null, true, "ticket"); }
    private void mutate(Runnable body) { new TransactionTemplate(transactions).executeWithoutResult(status -> body.run()); }
    private void member(UUID group, UUID user) { jdbc.update("insert into permissions_membership values (?,?,?)", COMPANY, group, user); }
    private void action(UUID group, UUID project, String key) { jdbc.update("insert into permissions_action values (?,?,?,?)", COMPANY, group, project, key); }
    private void field(UUID group, String operation, String effect) {
        jdbc.update("insert into permissions_field(company_id,group_id,project_id,resource_type,field_key,operation,effect) values (?,?,?,'ticket','title',?,?)", COMPANY, group, PROJECT, operation, effect);
    }
}
