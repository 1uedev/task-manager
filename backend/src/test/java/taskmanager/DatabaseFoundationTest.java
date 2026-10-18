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
    void flywayAppliedExactlyTheKnownMigrations() {
        List<String> versions = jdbc.queryForList(
            "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertEquals(List.of("0001", "0003"), versions);
        assertEquals(0, jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class));
    }

    @Test
    void onlyTheMigrationHistoryAndTheIdentityTablesExist() {
        List<String> tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = current_schema() order by table_name",
            String.class);
        assertEquals(List.of("flyway_schema_history", "identity_account", "identity_audit_event", "identity_login_throttle",
            "identity_session", "identity_setting"), tables);
    }

    @Test
    void ormNeverChangesTheSchemaAndCleanIsDisabled() {
        assertEquals("validate", ddlAuto);
        assertTrue(cleanDisabled);
    }
}
