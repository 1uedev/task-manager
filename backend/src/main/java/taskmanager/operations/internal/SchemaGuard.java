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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Component;
import taskmanager.operations.ContainerCommand;
import taskmanager.operations.SchemaVersionException;

/**
 * Decides at every start who may change the schema (TASK-132, DEC-162, rule 3; TASK-136, findings F1 and F2). Three cases:
 *
 * <ul>
 *   <li>The container command {@code migrate}, which only the entry point can select (it registers the
 *       {@link ContainerCommand} of its context; no setting can), is the one schema change of an update. It refuses a
 *       database that is newer than the image or holds a failed migration, and otherwise applies what is pending. Flyway
 *       holds a lock on its history while it migrates, so a second {@code migrate} at the same time waits and then finds
 *       nothing left to do.</li>
 *   <li>The mode {@code verify} of the operations template ({@code TASK_MANAGER_SCHEMA_MODE=verify}): the web process, the
 *       worker and every other command change nothing and refuse to start when a migration is missing, when the database
 *       is newer than the image or when a migration failed.</li>
 *   <li>The mode {@code migrate}, the default: the demonstration keeps its behaviour and migrates at the start, as before.
 *       The operations template never uses it, and its runtime database account may not change the schema anyway
 *       (TASK-136, F3).</li>
 * </ul>
 *
 * <p>For the command and the mode {@code verify} the check is a condition of the start, not a side effect of the Flyway
 * configuration: switched off Flyway or settings that change the set of migrations, the target, the history table or the
 * baseline are refused before anything runs, and a context whose refresh ends without the check is refused as well.
 */
@Component
class SchemaGuard implements FlywayMigrationStrategy, ApplicationListener<ContextRefreshedEvent> {
    static final String MIGRATE_COMMAND = "migrate";

    /**
     * The Flyway settings an image carries in application.yaml, with the one value they may have when the schema is
     * checked. Every other Flyway setting is refused then: it could hide migrations (target, locations, ignore patterns),
     * point to another history or database, or accept a history the image did not write (baseline, out of order).
     */
    private static final Map<String, String> FIXED = Map.of(
        "locations", "classpath:db/migration",
        "cleandisabled", "true",
        "baselineonmigrate", "false",
        "validatemigrationnaming", "true");

    private final String mode;
    private final boolean command;
    private final SchemaMigration migration;
    private volatile boolean checked;

    SchemaGuard(@Value("${taskmanager.schema.mode:migrate}") String mode, ObjectProvider<ContainerCommand> containerCommand,
            ConfigurableEnvironment environment, SchemaMigration migration) {
        this.mode = mode.strip().toLowerCase(Locale.ROOT);
        ContainerCommand registered = containerCommand.getIfAvailable();
        this.command = registered != null && MIGRATE_COMMAND.equals(registered.name());
        this.migration = migration;
        if (!command && !this.mode.equals("migrate") && !this.mode.equals("verify")) {
            // The raw value stays out of the message: a misplaced secret must not end up in the log (H1).
            throw new SchemaVersionException("The setting TASK_MANAGER_SCHEMA_MODE (taskmanager.schema.mode) has an unknown "
                + "value; allowed are 'migrate' and 'verify'.");
        }
        if (enforced()) {
            refuseChangedFlywaySettings(environment);
        }
    }

    private boolean enforced() {
        return command || mode.equals("verify");
    }

    @Override
    public void migrate(Flyway flyway) {
        if (command) {
            MigrationInfoService info = flyway.info();
            refuseNewerOrFailed(info);
            String before = version(info.current());
            int applied;
            try {
                applied = flyway.migrate().migrationsExecuted;
            } catch (FlywayException failed) {
                // A migration of this run failed (H1). PostgreSQL rolls the failed migration back, so the history ends at
                // the last successful one; the report names that one and the way back.
                throw new SchemaVersionException("A migration failed in this run; the schema stays at version "
                    + version(flyway.info().current()) + ". Restore the backup point taken before the update or start the "
                    + "previous version again. Cause: " + firstLine(org.springframework.core.NestedExceptionUtils
                    .getMostSpecificCause(failed).getMessage()));
            }
            migration.record(before, version(flyway.info().current()), applied);
            checked = true;
            return;
        }
        if (mode.equals("verify")) {
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
    private static void refuseChangedFlywaySettings(ConfigurableEnvironment environment) {
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
            if (value == null || !fixed.getValue().equalsIgnoreCase(value.strip())) {
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

    private static void refuseNewerOrFailed(MigrationInfoService info) {
        List<MigrationInfo> applied = Arrays.asList(info.applied());
        List<MigrationInfo> newer = applied.stream()
            .filter(m -> m.getState() == MigrationState.FUTURE_SUCCESS || m.getState() == MigrationState.FUTURE_FAILED)
            .toList();
        if (!newer.isEmpty()) {
            throw new SchemaVersionException("The database has the schema version " + version(info.current()) + " and is "
                + "newer than this image, which knows migrations up to " + knownUpTo(info) + " (unknown: " + versions(newer)
                + "). Use the image of the version that wrote the schema; an update is never undone by an older image, only "
                + "by restoring the backup point taken before it.");
        }
        List<MigrationInfo> failed = applied.stream().filter(m -> m.getState().isFailed()).toList();
        if (!failed.isEmpty()) {
            throw new SchemaVersionException("The migration(s) " + versions(failed) + " failed in an earlier run. Restore the "
                + "backup point taken before the update; the schema is not in a known state.");
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
