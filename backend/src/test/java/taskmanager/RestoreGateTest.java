package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The refusal of a restored installation that was not released (TASK-131, rule 10), bound to what the process really is
 * (TASK-135, finding F6 of the review of TASK-131): no setting lets a web server or a worker skip it, and a marker that
 * cannot be read stops the start instead of counting as "no restore".
 */
class RestoreGateTest {

    private static String[] database(String url, String... more) {
        String[] base = { "--spring.datasource.url=" + url, "--spring.datasource.username=" + TestDatabase.username(),
            "--spring.datasource.password=" + TestDatabase.password() };
        String[] all = java.util.Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return all;
    }

    /** A migrated database with a restore that waits for after-restore. */
    private static String restoredDatabase() throws java.sql.SQLException {
        String url = TestDatabase.createEmptyDatabase();
        PrintStream original = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, TaskManagerApplication.runMigrateCommand(new String[] { "migrate" },
                "spring.datasource.url=" + url, "spring.datasource.username=" + TestDatabase.username(),
                "spring.datasource.password=" + TestDatabase.password()));
        } finally {
            System.setErr(original);
        }
        jdbc(url).update("insert into operations_restore_pending (point, restored_at) values ('20261123T120000Z', now())");
        return url;
    }

    private static JdbcTemplate jdbc(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
    }

    private static String reason(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            all.append(cause.getMessage()).append('\n');
        }
        return all.toString();
    }

    @Test
    void theWebServerAndTheWorkerRefuseAWaitingRestore() throws Exception {
        String url = restoredDatabase();

        Throwable web = assertThrows(Throwable.class,
            () -> new SpringApplicationBuilder(TaskManagerApplication.class).run(database(url, "--server.port=0")));
        assertTrue(reason(web).contains("was not released yet"), reason(web));

        Throwable worker = assertThrows(Throwable.class,
            () -> TaskManagerApplication.runWorker(new String[] { "worker" }, "spring.datasource.url=" + url,
                "spring.datasource.username=" + TestDatabase.username(), "spring.datasource.password=" + TestDatabase.password()));
        assertTrue(reason(worker).contains("was not released yet"), reason(worker));
    }

    @Test
    void theSettingOfACommandDoesNotLetAWebServerOrAWorkerSkipTheRefusal() throws Exception {
        String url = restoredDatabase();

        // The web server started with the setting of a command process: it still has its web server, so it does not start.
        Throwable web = assertThrows(Throwable.class, () -> new SpringApplicationBuilder(TaskManagerApplication.class)
            .run(database(url, "--server.port=0", "--taskmanager.process=command")));
        assertTrue(reason(web).contains("does not fit this process"), reason(web));

        // The worker whose setting a higher configuration overrides: no container command was started, so it does not start.
        Throwable worker = assertThrows(Throwable.class, () -> TaskManagerApplication.runWorker(new String[] { "worker" },
            "spring.datasource.url=" + url, "spring.datasource.username=" + TestDatabase.username(),
            "spring.datasource.password=" + TestDatabase.password(), "taskmanager.process=command"));
        assertTrue(reason(worker).contains("does not fit this process"), reason(worker));

        // A process without web server and without a container command that claims to be one does not start either.
        Throwable plain = assertThrows(Throwable.class, () -> new SpringApplicationBuilder(TaskManagerApplication.class)
            .web(WebApplicationType.NONE).run(database(url, "--taskmanager.process=command")));
        assertTrue(reason(plain).contains("does not fit this process"), reason(plain));

        // A worker setting in a web server does not fit either.
        Throwable mixed = assertThrows(Throwable.class, () -> new SpringApplicationBuilder(TaskManagerApplication.class)
            .run(database(url, "--server.port=0", "--taskmanager.process=worker")));
        assertTrue(reason(mixed).contains("does not fit this process"), reason(mixed));

        // The marker is untouched by all of it.
        assertEquals(1, jdbc(url).queryForObject("select count(*) from operations_restore_pending", Integer.class));
    }

    @Test
    void aContainerCommandStillStartsWhileTheRestoreWaits() throws Exception {
        String url = restoredDatabase();
        SpringApplicationBuilder builder = new SpringApplicationBuilder(TaskManagerApplication.class)
            .web(WebApplicationType.NONE).properties("taskmanager.process=command");
        taskmanager.operations.ContainerCommand.register(builder.application(), "test");
        ConfigurableApplicationContext command = builder.run(database(url));
        command.close();
    }

    @Test
    void aMarkerThatCannotBeReadStopsTheStart() throws Exception {
        String url = restoredDatabase();
        jdbc(url).update("revoke select on operations_restore_pending from public");
        jdbc(url).update("alter table operations_restore_pending rename to operations_restore_pending_hidden");

        Throwable web = assertThrows(Throwable.class,
            () -> new SpringApplicationBuilder(TaskManagerApplication.class).run(database(url, "--server.port=0")));
        assertTrue(reason(web).contains("operations_restore_pending"), reason(web));
    }
}
