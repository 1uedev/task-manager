package taskmanager.operations.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.UnaryOperator;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import taskmanager.operations.SchemaVersionException;

/**
 * The comparison of the Flyway settings and of the bound Flyway configuration with what the image fixes (TASK-137, finding
 * N2 of the review of TASK-136), without a database.
 */
class SchemaGuardSettingsTest {
    /** A Java migration added in code, which the image never has. */
    @SuppressWarnings("checkstyle:TypeName")
    static final class V9999__Extra extends org.flywaydb.core.api.migration.BaseJavaMigration {
        @Override
        public void migrate(org.flywaydb.core.api.migration.Context context) {
        }
    }

    private static FluentConfiguration fixed() {
        return Flyway.configure().locations("classpath:db/migration").cleanDisabled(true).baselineOnMigrate(false)
            .validateMigrationNaming(true);
    }

    private static StandardEnvironment environment(Map<String, Object> extra, boolean asEnvironment) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("image", Map.of(
            "spring.flyway.locations", "classpath:db/migration", "spring.flyway.clean-disabled", "true",
            "spring.flyway.baseline-on-migrate", "false", "spring.flyway.validate-migration-naming", "true")));
        environment.getPropertySources().addFirst(asEnvironment
            ? new SystemEnvironmentPropertySource("test-environment", extra) : new MapPropertySource("test", extra));
        return environment;
    }

    @Test
    void theConfigurationOfTheImageIsAccepted() {
        assertDoesNotThrow(() -> SchemaGuard.refuseChangedConfiguration(fixed()));
        assertDoesNotThrow(() -> SchemaGuard.refuseChangedFlywaySettings(environment(Map.of(), false)));
    }

    @Test
    void everyChangedValueOfTheBoundConfigurationIsRefused() {
        Map<String, UnaryOperator<FluentConfiguration>> changes = Map.ofEntries(
            Map.entry("locations", c -> c.locations("classpath:DB/migration")),
            Map.entry("table", c -> c.table("other_history")),
            Map.entry("schemas", c -> c.schemas("other")),
            Map.entry("default-schema", c -> c.defaultSchema("other")),
            Map.entry("target", c -> c.target("0069")),
            Map.entry("baseline-on-migrate", c -> c.baselineOnMigrate(true)),
            Map.entry("baseline-version", c -> c.baselineVersion("70")),
            Map.entry("out-of-order", c -> c.outOfOrder(true)),
            Map.entry("clean-disabled", c -> c.cleanDisabled(false)),
            Map.entry("validate-migration-naming", c -> c.validateMigrationNaming(false)),
            Map.entry("validate-on-migrate", c -> c.validateOnMigrate(false)),
            Map.entry("ignore-migration-patterns", c -> c.ignoreMigrationPatterns("*:missing")),
            Map.entry("sql-migration-prefix", c -> c.sqlMigrationPrefix("M")),
            Map.entry("sql-migration-separator", c -> c.sqlMigrationSeparator("_")),
            Map.entry("sql-migration-suffixes", c -> c.sqlMigrationSuffixes(".txt")),
            Map.entry("repeatable-sql-migration-prefix", c -> c.repeatableSqlMigrationPrefix("A")),
            Map.entry("placeholder-replacement", c -> c.placeholderReplacement(false)),
            Map.entry("skip-default-resolvers", c -> c.skipDefaultResolvers(true)),
            Map.entry("group", c -> c.group(true)),
            Map.entry("mixed", c -> c.mixed(true)),
            Map.entry("init-sql", c -> c.initSql("set role postgres")),
            // TASK-139, F5 of the review of TASK-137: placeholders, callbacks and the creation of schemas, and the rest.
            Map.entry("placeholders", c -> c.placeholders(Map.of("owner", "postgres"))),
            Map.entry("placeholder-prefix", c -> c.placeholderPrefix("#{")),
            Map.entry("placeholder-suffix", c -> c.placeholderSuffix("]")),
            Map.entry("skip-default-callbacks", c -> c.skipDefaultCallbacks(true)),
            Map.entry("callbacks", c -> c.callbacks(new org.flywaydb.core.api.callback.BaseCallback() {
                @Override
                public void handle(org.flywaydb.core.api.callback.Event event, org.flywaydb.core.api.callback.Context context) {
                }
            })),
            Map.entry("callback-locations", c -> c.callbackLocations("classpath:db/callback")),
            Map.entry("create-schemas", c -> c.createSchemas(false)),
            Map.entry("execute-in-transaction", c -> c.executeInTransaction(false)),
            Map.entry("encoding", c -> c.encoding("ISO-8859-1")),
            Map.entry("installed-by", c -> c.installedBy("someone")),
            Map.entry("fail-on-missing-locations", c -> c.failOnMissingLocations(true)),
            Map.entry("skip-executing-migrations", c -> c.skipExecutingMigrations(true)),
            Map.entry("tablespace", c -> c.tablespace("elsewhere")),
            Map.entry("resolvers", c -> c.resolvers(context -> java.util.List.of())),
            Map.entry("java-migrations", c -> c.javaMigrations(new V9999__Extra())));
        for (var change : changes.entrySet()) {
            var failure = assertThrows(SchemaVersionException.class,
                () -> SchemaGuard.refuseChangedConfiguration(change.getValue().apply(fixed())), change.getKey());
            assertTrue(failure.getMessage().contains(change.getKey()), change.getKey() + ": " + failure.getMessage());
        }
    }

    @Test
    void onlyTheValuesOfConnectionAndLoadingMayDiffer() {
        // Every value of the configuration not named as connection or loading is compared, also one a later Flyway adds.
        var changed = fixed().dataSource("jdbc:postgresql://elsewhere/db", "someone", "secret").connectRetries(3)
            .connectRetriesInterval(5).lockRetryCount(5).driver("org.postgresql.Driver")
            .jdbcProperties(Map.of("ssl", "true"));
        assertDoesNotThrow(() -> SchemaGuard.refuseChangedConfiguration(changed));
        for (String name : SchemaGuard.CONNECTION_ONLY) {
            assertTrue(java.util.Arrays.stream(org.flywaydb.core.api.configuration.Configuration.class.getMethods())
                .anyMatch(method -> method.getParameterCount() == 0 && method.getName().toLowerCase(java.util.Locale.ROOT)
                    .replaceFirst("^(get|is)", "").equals(name.replace("-", ""))), name + " is no value of Flyway");
        }
    }

    @Test
    void settingsFromTheEnvironmentAListOrAnotherSpellingAreRefused() {
        for (var setting : Map.<String, Object>of(
                "SPRING_FLYWAY_TARGET", "0001",
                "SPRING_FLYWAY_LOCATIONS_0_", "classpath:db/migration",
                "SPRING_FLYWAY_ENABLED", "false").entrySet()) {
            assertThrows(SchemaVersionException.class, () -> SchemaGuard.refuseChangedFlywaySettings(
                environment(Map.of(setting.getKey(), setting.getValue()), true)), setting.getKey());
        }
        for (var setting : Map.<String, Object>of(
                "spring.flyway.locations[0]", "classpath:db/migration",
                "spring.flyway.locations", "classpath:DB/migration",
                "spring.flyway.outOfOrder", "true",
                "spring.flyway.target", "0001").entrySet()) {
            assertThrows(SchemaVersionException.class, () -> SchemaGuard.refuseChangedFlywaySettings(
                environment(Map.of(setting.getKey(), setting.getValue()), false)), setting.getKey());
        }
    }

    /** A set of migrations that resolves to nothing never stands for the image (N2). */
    @Test
    void anEmptySetOfMigrationsIsRefused() throws Exception {
        String url = taskmanager.TestDatabase.createEmptyDatabase();
        var flyway = Flyway.configure().dataSource(url, taskmanager.TestDatabase.username(), taskmanager.TestDatabase.password())
            .locations("classpath:db/none").load();
        var failure = assertThrows(SchemaVersionException.class, () -> SchemaGuard.refuseNewerOrFailed(flyway.info()));
        assertTrue(failure.getMessage().contains("finds none of its migrations"), failure.getMessage());
    }
}
