package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseFoundationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired JdbcTemplate jdbc;
    @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto;
    @Value("${spring.flyway.clean-disabled}") boolean cleanDisabled;

    @Test
    void runsAgainstThePinnedPostgresqlVersion() {
        String version = jdbc.queryForObject("show server_version", String.class);
        assertTrue(version.startsWith(TestDatabase.expectedServerVersion()), version);
    }

    @Test
    void flywayAppliedExactlyTheVersionedMigrations() {
        List<String> versions = jdbc.queryForList(
            "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertEquals(List.of("0001", "0002", "0003", "0004", "0005", "0007", "0008", "0009", "0010", "0011", "0012", "0013", "0014", "0015", "0016", "0017", "0018", "0019", "0020", "0021", "0022", "0023", "0024"), versions);
        assertEquals(0, jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class));
    }

    @Test
    void onlyOwnedSchemaObjectsExistAndNoDefaultGrantsAreSeeded() {
        List<String> tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = current_schema() order by table_name",
            String.class);
        assertEquals(List.of("appearance_audit_event", "appearance_setting", "file_attachment", "file_audit_event", "flyway_schema_history", "identity_account", "identity_audit_event", "identity_ldap_setting", "identity_login_throttle",
            "identity_session", "identity_setting", "permissions_action", "permissions_audit", "permissions_field",
            "permissions_group", "permissions_membership", "permissions_revision", "platform_company", "project",
            "project_audit_event", "project_configuration", "project_configuration_draft", "project_member", "search_project", "search_project_tag", "search_state", "search_tag", "search_ticket", "search_ticket_custom", "search_ticket_tag", "tag", "tag_audit_event", "tag_project", "tag_ticket", "ticket", "ticket_change", "ticket_counter"), tables);
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_account", Integer.class), "no account is seeded");
        assertEquals(0, jdbc.queryForObject("select count(*) from platform_company", Integer.class), "no company is seeded");
        assertEquals(0, jdbc.queryForObject("select count(*) from permissions_revision", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from permissions_action", Integer.class));
    }

    @Test
    void ormNeverChangesTheSchemaAndCleanIsDisabled() {
        assertEquals("validate", ddlAuto);
        assertTrue(cleanDisabled);
    }
}
