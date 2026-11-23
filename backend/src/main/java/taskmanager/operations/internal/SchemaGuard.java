package taskmanager.operations.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;
import taskmanager.operations.SchemaVersionException;

/**
 * Decides at every start who may change the schema (TASK-132, DEC-162, rule 3). Three cases:
 *
 * <ul>
 *   <li>The command {@code migrate} is the one schema change of an update. It refuses a database that is newer than the
 *       image, so an older image never works on a newer schema, and otherwise applies what is pending. Flyway holds a
 *       lock on its history while it migrates, so a second {@code migrate} at the same time waits and then finds nothing
 *       left to do.</li>
 *   <li>The mode {@code verify} of the operations template ({@code TASK_MANAGER_SCHEMA_MODE=verify}): the web process, the
 *       worker and every other command change nothing and refuse to start when a migration is missing, when the database
 *       is newer than the image or when a migration failed.</li>
 *   <li>The mode {@code migrate}, the default: the demonstration keeps its behaviour and migrates at the start, as before.</li>
 * </ul>
 */
@Component
class SchemaGuard implements FlywayMigrationStrategy {
    static final String MIGRATE_COMMAND = "migrate";

    private final String mode;
    private final boolean command;
    private final SchemaMigration migration;

    SchemaGuard(@Value("${taskmanager.schema.mode:migrate}") String mode,
            @Value("${taskmanager.operations-command:}") String operationsCommand, SchemaMigration migration) {
        this.mode = mode.strip().toLowerCase(Locale.ROOT);
        this.command = MIGRATE_COMMAND.equals(operationsCommand);
        this.migration = migration;
        if (!command && !this.mode.equals("migrate") && !this.mode.equals("verify")) {
            throw new SchemaVersionException("The setting TASK_MANAGER_SCHEMA_MODE (taskmanager.schema.mode) must be 'migrate' or "
                + "'verify', not '" + mode + "'.");
        }
    }

    @Override
    public void migrate(Flyway flyway) {
        if (command) {
            MigrationInfoService info = flyway.info();
            refuseNewerOrFailed(info);
            String before = version(info.current());
            var result = flyway.migrate();
            migration.record(before, version(flyway.info().current()), result.migrationsExecuted);
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
            return;
        }
        flyway.migrate();
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
            throw new SchemaVersionException("The migration(s) " + versions(failed) + " failed earlier. Restore the backup point "
                + "taken before the update; the schema is not in a known state.");
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
