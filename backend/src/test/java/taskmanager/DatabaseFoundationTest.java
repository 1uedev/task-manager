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
        assertEquals(List.of("0001", "0002", "0003", "0004", "0005", "0007", "0008", "0009", "0010", "0011", "0012", "0013", "0014", "0015", "0016", "0017", "0018", "0019", "0020", "0021", "0022", "0023", "0024", "0025", "0026", "0027", "0028", "0030", "0032", "0033", "0034", "0035", "0036", "0037", "0038", "0039", "0040", "0041", "0042", "0043", "0044", "0045", "0046", "0047", "0048", "0049", "0050", "0051", "0052", "0053", "0054", "0055", "0056", "0057", "0058"), versions);
        assertEquals(0, jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class));
    }

    @Test
    void onlyOwnedSchemaObjectsExistAndNoDefaultGrantsAreSeeded() {
        List<String> tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = current_schema() order by table_name",
            String.class);
        assertEquals(List.of("appearance_audit_event", "appearance_mail_template", "appearance_setting", "file_attachment", "file_audit_event", "file_image", "flyway_schema_history", "helpdesk_audit_event", "helpdesk_contact", "helpdesk_contact_address", "helpdesk_customer",
            "helpdesk_customer_project", "helpdesk_mailbox", "helpdesk_message", "helpdesk_portal_position", "helpdesk_portal_post", "helpdesk_portal_post_attachment", "helpdesk_portal_seen", "helpdesk_reply", "helpdesk_reply_image", "helpdesk_reply_mail", "helpdesk_ticket_customer", "identity_account", "identity_audit_event", "identity_ldap_setting", "identity_login_throttle",
            "identity_session", "identity_setting", "job", "job_audit_event", "job_schedule", "job_worker", "permissions_action", "permissions_audit", "permissions_field",
            "permissions_group", "permissions_membership", "permissions_revision", "platform_company", "project",
            "project_audit_event", "project_configuration", "project_configuration_draft", "project_member", "requirement", "requirement_counter", "requirement_event", "requirement_revision", "search_project", "search_project_tag", "search_state", "search_tag", "search_ticket", "search_ticket_custom", "search_ticket_tag", "sla_audit_event", "sla_calendar", "sla_calendar_version", "sla_clock", "sla_cycle", "sla_escalation", "sla_rule", "sla_rule_version", "sla_ticket", "tag", "tag_audit_event", "tag_project", "tag_ticket", "ticket", "ticket_change", "ticket_counter", "time_entry", "time_entry_revision", "wiki_event", "wiki_image", "wiki_page", "wiki_revision", "wiki_revision_image", "wiki_variant"), tables);
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
