package taskmanager.permissions;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import taskmanager.TestDatabase;

/** Review-only evidence: main's applied V0007 and the real pending V0006 from PR #85. */
class MigrationOrderingProbeTest {
    @Test
    void appliedV0007RejectsPendingV0006ButAnEmptyDatabaseAcceptsTheCombinedSet() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
        Path root = Path.of(System.getProperty("java.io.tmpdir"));
        Path migrations = Files.createDirectory(root.resolve("task038-migration-review-" + UUID.randomUUID()));
        try (var sources = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path source : sources.filter(path -> path.getFileName().toString().endsWith(".sql")).toList())
                Files.copy(source, migrations.resolve(source.getFileName()));
        }
        var current = configure(url, migrations);
        assertFalse(current.getConfiguration().isOutOfOrder());
        current.migrate();
        var applied = jdbc.queryForList("select version,checksum from flyway_schema_history where success order by installed_rank");
        assertEquals(6, applied.size());
        assertEquals("0007", applied.getLast().get("version"));

        // Exact SQL snapshot of PR #85 head 46335383dd949dbe3ae86a378fc0468c3089fafb.
        Files.copy(Path.of("../docs/review-evidence/V0006__appearance_settings.sql"),
            migrations.resolve("V0006__appearance_settings.sql"));
        var next = configure(url, migrations);
        var validation = next.validateWithResult();
        assertFalse(validation.validationSuccessful);
        assertTrue(validation.invalidMigrations.stream().anyMatch(item -> "0006".equals(item.version)
            && "RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(item.errorDetails.errorCode.toString())),
            validation.getAllErrorMessages());
        assertThrows(FlywayException.class, next::migrate);
        assertEquals(applied, jdbc.queryForList("select version,checksum from flyway_schema_history where success order by installed_rank"));
        assertNull(jdbc.queryForObject("select to_regclass('public.appearance_setting')::text", String.class));
        System.out.println("REVIEW M1: adding the real pending V0006 after applied V0007 is rejected: RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED; history unchanged");

        String freshUrl = TestDatabase.createEmptyDatabase();
        configure(freshUrl, migrations).migrate();
        var fresh = new JdbcTemplate(new DriverManagerDataSource(freshUrl, TestDatabase.username(), TestDatabase.password()));
        assertEquals(7, fresh.queryForObject("select count(*) from flyway_schema_history where success", Integer.class));
        assertEquals("#f97316", fresh.queryForObject("select accent_color from appearance_setting where id=1", String.class));
        System.out.println("REVIEW CONTROL: the same complete V0001-V0007 set succeeds on an empty database, including the appearance seed");
    }

    private static Flyway configure(String url, Path migrations) {
        return Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password())
            .locations("filesystem:" + migrations.toAbsolutePath()).cleanDisabled(true).baselineOnMigrate(false)
            .validateMigrationNaming(true).load();
    }
}
