package taskmanager.operations.internal;

import java.io.PrintStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The container command {@code migrate} (TASK-132, DEC-162, rule 3): the one schema change of an update. The work happens
 * in {@link SchemaGuard} while the context starts; this runner only reports it. A refusal (database newer than the image,
 * failed migration) ends the start before it, with exit code 2 from the entry point.
 */
@Component
@ConditionalOnProperty(name = "taskmanager.operations-command", havingValue = SchemaGuard.MIGRATE_COMMAND)
class MigrateCommandRunner implements ApplicationRunner {
    private final SchemaMigration migration;
    private final PrintStream out;

    MigrateCommandRunner(SchemaMigration migration) {
        this.migration = migration;
        this.out = System.out;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = migration.result().orElseThrow(() -> new IllegalStateException("The schema was not checked at the start."));
        out.println("Schema version before: " + result.before());
        out.println("Migrations applied: " + result.applied());
        out.println("Schema version now: " + result.after());
        out.println(result.applied() == 0
            ? "The schema was already up to date; nothing changed."
            : "The schema is up to date. Start the web process and the worker, then check the core flows.");
    }
}
