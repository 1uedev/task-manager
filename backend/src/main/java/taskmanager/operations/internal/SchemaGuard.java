package taskmanager.operations.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Component;
import taskmanager.operations.ContainerCommand;
import taskmanager.operations.OperationsStart;
import taskmanager.operations.SchemaVersionException;

/**
 * Decides at every start who may change the schema (TASK-132, DEC-162, rule 3; TASK-136, findings F1 and F2; TASK-137,
 * findings N1, N2 and H1). Three cases, each chosen by the entry point and never by a setting alone:
 *
 * <ul>
 *   <li>The container command {@code migrate}, which only the entry point can select (it registers the
 *       {@link ContainerCommand} of its context; no setting can), is the one schema change of an update. It refuses a
 *       database that is newer than the image or holds a failed migration, and otherwise applies what is pending. Flyway
 *       holds a lock on its history while it migrates, so a second {@code migrate} at the same time waits and then finds
 *       nothing left to do.</li>
 *   <li>Every other process the operations template starts: its entry point puts {@code operations} in front of the
 *       command and registers {@link OperationsStart}. Web process, worker and every other command change nothing and
 *       refuse to start when a migration is missing, when the database is newer than the image or when a migration failed.
 *       A schema mode other than {@code verify} from any source refuses the start as well (N1).</li>
 *   <li>Without that argument the setting {@code taskmanager.schema.mode} decides: {@code migrate} or no value keeps the
 *       demonstration and the tests as before and migrates at the start; {@code verify} checks like the operations
 *       template.</li>
 * </ul>
 *
 * <p>Whenever the schema is checked, the check is a condition of the start, not a side effect of the Flyway configuration:
 * switched off Flyway, settings that change the set of migrations, the target, the history table or the baseline, a bound
 * Flyway configuration that differs from the one the image fixes and an empty set of migrations are refused before anything
 * runs, and a context whose refresh ends without the check is refused as well.
 */
@Component
class SchemaGuard implements FlywayMigrationStrategy, ApplicationListener<ContextRefreshedEvent> {
    static final String MIGRATE_COMMAND = "migrate";

    /**
     * The Flyway settings an image carries in application.yaml, with the one value they may have when the schema is
     * checked. Every other Flyway setting is refused then: it could hide migrations (target, locations, ignore patterns),
     * point to another history or database, or accept a history the image did not write (baseline, out of order).
     */
    private static final String LOCATION = "classpath:db/migration";

    private static final Map<String, String> FIXED = Map.of(
        "locations", LOCATION,
        "cleandisabled", "true",
        "baselineonmigrate", "false",
        "validatemigrationnaming", "true");

    private final String mode;
    private final boolean command;
    private final boolean operations;
    private final SchemaMigration migration;
    private volatile boolean checked;

    SchemaGuard(@Value("${taskmanager.schema.mode:}") String mode, ObjectProvider<ContainerCommand> containerCommand,
            ObjectProvider<OperationsStart> operationsStart, ConfigurableEnvironment environment, SchemaMigration migration) {
        this.mode = mode.strip().toLowerCase(Locale.ROOT);
        ContainerCommand registered = containerCommand.getIfAvailable();
        this.command = registered != null && MIGRATE_COMMAND.equals(registered.name());
        this.operations = operationsStart.getIfAvailable() != null;
        this.migration = migration;
        if (!command && !this.mode.isEmpty() && !this.mode.equals("migrate") && !this.mode.equals("verify")) {
            // The raw value stays out of the message: a misplaced secret must not end up in the log (H1).
            throw new SchemaVersionException("The setting TASK_MANAGER_SCHEMA_MODE (taskmanager.schema.mode) has an unknown "
                + "value; allowed are 'migrate' and 'verify'.");
        }
        if (operations && !command && !this.mode.isEmpty() && !this.mode.equals("verify")) {
            // N1: a process of the operations template never migrates at the start, whatever source sets the mode.
            throw new SchemaVersionException("A process of the operations template only checks the schema; the setting "
                + "TASK_MANAGER_SCHEMA_MODE (taskmanager.schema.mode) may only be 'verify' here. Run the command 'migrate' "
                + "for a schema change and remove the setting.");
        }
        if (enforced()) {
            refuseChangedFlywaySettings(environment);
        }
    }

    private boolean verifying() {
        return operations || mode.equals("verify");
    }

    private boolean enforced() {
        return command || verifying();
    }

    @Override
    public void migrate(Flyway flyway) {
        if (enforced()) {
            refuseChangedConfiguration(flyway.getConfiguration());
        }
        if (command) {
            MigrationInfoService info = flyway.info();
            refuseNewerOrFailed(info);
            String before = version(info.current());
            int applied;
            try {
                applied = flyway.migrate().migrationsExecuted;
            } catch (FlywayException failed) {
                // A migration of this run failed (H1). PostgreSQL rolls the failed migration back, so the history ends at
                // the last successful one; the migrations before it in this run stay applied, so the previous version no
                // longer fits the database. The report names the version and the documented way back (N10).
                throw new SchemaVersionException("A migration failed in this run; the schema stays at version "
                    + version(flyway.info().current()) + " and no longer fits the previous version. Do not start any "
                    + "version on this database: return with the backup point taken before the update as described in "
                    + "docs/INSTALL.md, section 'Rückkehr'. Cause: " + firstLine(org.springframework.core.NestedExceptionUtils
                    .getMostSpecificCause(failed).getMessage()));
            }
            migration.record(before, version(flyway.info().current()), applied);
            checked = true;
            return;
        }
        if (verifying()) {
            MigrationInfoService info = flyway.info();
            refuseNewerOrFailed(info);
            List<MigrationInfo> pending = Arrays.asList(info.pending());
            if (!pending.isEmpty()) {
                throw new SchemaVersionException("The database has the schema version " + version(info.current()) + ", this image "
                    + "needs " + version(pending.getLast()) + ": " + pending.size() + " migration(s) are missing ("
                    + versions(pending) + "). Take a backup point, stop web process and worker and run the command "
                    + "'migrate' once; this process never changes the schema itself.");
            }
            flyway.validate();
            checked = true;
            return;
        }
        flyway.migrate();
    }

    /**
     * Compares the Flyway configuration Spring bound with the one the image fixes, value by value and with its type (N2):
     * exactly the location {@code classpath:db/migration} in this spelling, the default history table, schema, target,
     * naming, placeholders and resolvers, no baseline, no out of order and the default ignore patterns. A configuration that
     * finds no migration at all is refused as well.
     */
    static void refuseChangedConfiguration(Configuration bound) {
        FluentConfiguration fixed = Flyway.configure().locations(LOCATION).cleanDisabled(true).baselineOnMigrate(false)
            .validateMigrationNaming(true);
        Set<String> refused = new TreeSet<>();
        compare(refused, "locations", Arrays.stream(bound.getLocations()).map(Object::toString).toList(),
            Arrays.stream(fixed.getLocations()).map(Object::toString).toList());
        compare(refused, "table", bound.getTable(), fixed.getTable());
        compare(refused, "schemas", List.of(bound.getSchemas()), List.of(fixed.getSchemas()));
        compare(refused, "default-schema", bound.getDefaultSchema(), fixed.getDefaultSchema());
        compare(refused, "target", String.valueOf(bound.getTarget()), String.valueOf(fixed.getTarget()));
        compare(refused, "cherry-pick", bound.getCherryPick() == null ? List.of() : List.of(bound.getCherryPick()),
            fixed.getCherryPick() == null ? List.of() : List.of(fixed.getCherryPick()));
        compare(refused, "baseline-on-migrate", bound.isBaselineOnMigrate(), false);
        compare(refused, "baseline-version", String.valueOf(bound.getBaselineVersion()), String.valueOf(fixed.getBaselineVersion()));
        compare(refused, "out-of-order", bound.isOutOfOrder(), false);
        compare(refused, "clean-disabled", bound.isCleanDisabled(), true);
        compare(refused, "validate-migration-naming", bound.isValidateMigrationNaming(), true);
        compare(refused, "validate-on-migrate", bound.isValidateOnMigrate(), true);
        compare(refused, "ignore-migration-patterns", Arrays.stream(bound.getIgnoreMigrationPatterns()).map(Object::toString).toList(),
            Arrays.stream(fixed.getIgnoreMigrationPatterns()).map(Object::toString).toList());
        compare(refused, "sql-migration-prefix", bound.getSqlMigrationPrefix(), fixed.getSqlMigrationPrefix());
        compare(refused, "sql-migration-separator", bound.getSqlMigrationSeparator(), fixed.getSqlMigrationSeparator());
        compare(refused, "sql-migration-suffixes", List.of(bound.getSqlMigrationSuffixes()), List.of(fixed.getSqlMigrationSuffixes()));
        compare(refused, "repeatable-sql-migration-prefix", bound.getRepeatableSqlMigrationPrefix(), fixed.getRepeatableSqlMigrationPrefix());
        compare(refused, "placeholder-replacement", bound.isPlaceholderReplacement(), fixed.isPlaceholderReplacement());
        compare(refused, "skip-default-resolvers", bound.isSkipDefaultResolvers(), false);
        compare(refused, "resolvers", bound.getResolvers().length, 0);
        compare(refused, "java-migrations", bound.getJavaMigrations().length, 0);
        compare(refused, "group", bound.isGroup(), false);
        compare(refused, "mixed", bound.isMixed(), false);
        compare(refused, "init-sql", bound.getInitSql(), fixed.getInitSql());
        if (!refused.isEmpty()) {
            throw new SchemaVersionException("The schema check refuses changed Flyway settings (" + String.join(", ", refused)
                + "). The image fixes the set of migrations, their history and the target; remove these settings.");
        }
    }

    private static void compare(Set<String> refused, String name, Object bound, Object fixed) {
        if (!java.util.Objects.equals(bound, fixed)) {
            refused.add(name);
        }
    }

    /** No process that must check the schema starts without having checked it (F2). */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (enforced() && !checked && event.getApplicationContext().getParent() == null) {
            throw new SchemaVersionException("The schema was not checked at the start; this process refuses to run without "
                + "the check. Do not switch off Flyway or change its settings.");
        }
    }

    /**
     * Refuses every Flyway setting outside the fixed ones, from any source (command line, environment, files), and a fixed
     * one with another value (F2).
     */
    static void refuseChangedFlywaySettings(ConfigurableEnvironment environment) {
        Set<String> refused = new TreeSet<>();
        if (!"true".equalsIgnoreCase(environment.getProperty("spring.flyway.enabled", "true").strip())) {
            refused.add("enabled");
        }
        for (var source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                String key = normalized(name);
                if (!key.startsWith("springflyway")) {
                    continue;
                }
                String setting = key.substring("springflyway".length());
                if (setting.equals("enabled")) {
                    continue;
                }
                if (!FIXED.containsKey(setting)) {
                    refused.add(setting);
                }
            }
        }
        for (var fixed : FIXED.entrySet()) {
            String value = environment.getProperty("spring.flyway." + dashed(fixed.getKey()));
            // The location in exactly this spelling: Spring binds "classpath:DB/migration" as another place (N2).
            boolean same = fixed.getKey().equals("locations") ? fixed.getValue().equals(value == null ? null : value.strip())
                : value != null && fixed.getValue().equalsIgnoreCase(value.strip());
            if (!same) {
                refused.add(fixed.getKey());
            }
        }
        if (!refused.isEmpty()) {
            throw new SchemaVersionException("The schema check refuses changed Flyway settings (" + String.join(", ", refused)
                + "). The image fixes the set of migrations, their history and the target; remove these settings.");
        }
    }

    private static String firstLine(String message) {
        return message == null ? "unknown" : message.strip().lines().findFirst().orElse("unknown");
    }

    private static String normalized(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String dashed(String key) {
        return switch (key) {
            case "cleandisabled" -> "clean-disabled";
            case "baselineonmigrate" -> "baseline-on-migrate";
            case "validatemigrationnaming" -> "validate-migration-naming";
            default -> key;
        };
    }

    static void refuseNewerOrFailed(MigrationInfoService info) {
        if (Arrays.stream(info.all()).noneMatch(m -> m.getState().isResolved())) {
            // N2: a set of migrations that resolves to nothing never stands for this image.
            throw new SchemaVersionException("The image finds none of its migrations; the set of migrations is empty or "
                + "unknown. Do not change the Flyway settings or the image.");
        }
        List<MigrationInfo> applied = Arrays.asList(info.applied());
        List<MigrationInfo> newerFailed = applied.stream().filter(m -> m.getState() == MigrationState.FUTURE_FAILED).toList();
        if (!newerFailed.isEmpty()) {
            // H1: a migration this image does not know failed in an earlier run of a newer image.
            throw new SchemaVersionException("The migration(s) " + versions(newerFailed) + ", unknown to this image, failed in "
                + "an earlier run of a newer image; the schema is not in a known state. Restore the backup point taken before "
                + "that update as described in docs/INSTALL.md, section 'Rückkehr'.");
        }
        List<MigrationInfo> newer = applied.stream().filter(m -> m.getState() == MigrationState.FUTURE_SUCCESS).toList();
        if (!newer.isEmpty()) {
            throw new SchemaVersionException("The database has the schema version " + version(info.current()) + " and is "
                + "newer than this image, which knows migrations up to " + knownUpTo(info) + " (unknown: " + versions(newer)
                + "). Use the image of the version that wrote the schema; an update is never undone by an older image, only "
                + "by restoring the backup point taken before it.");
        }
        List<MigrationInfo> failed = applied.stream().filter(m -> m.getState().isFailed()).toList();
        if (!failed.isEmpty()) {
            throw new SchemaVersionException("The migration(s) " + versions(failed) + " failed in an earlier run. Restore the "
                + "backup point taken before the update as described in docs/INSTALL.md, section 'Rückkehr'; the schema is not "
                + "in a known state.");
        }
    }

    private static String knownUpTo(MigrationInfoService info) {
        return Arrays.stream(info.all())
            .filter(m -> m.getState().isResolved())
            .map(SchemaGuard::version)
            .reduce((first, second) -> second)
            .orElse("none");
    }

    private static String versions(List<MigrationInfo> migrations) {
        return String.join(", ", migrations.stream().map(SchemaGuard::version).toList());
    }

    static String version(MigrationInfo migration) {
        return migration == null || migration.getVersion() == null ? "none" : migration.getVersion().getVersion();
    }
}
