package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Start, stop and restart of the real application on an empty database. */
class ApplicationLifecycleTest {
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void migratesAnEmptyDatabaseOnceAndRestartsWithoutChanges() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));

        ConfigurableApplicationContext first = startPlain(url);
        assertHealthy(first);
        first.close();
        assertFalse(first.isActive());
        List<Timestamp> installedAfterFirstStart = installedOn(jdbc);
        assertEquals(30, installedAfterFirstStart.size());

        ConfigurableApplicationContext second = startPlain(url);
        assertHealthy(second);
        second.close();
        assertFalse(second.isActive());
        assertEquals(installedAfterFirstStart, installedOn(jdbc));
    }

    @Test
    void refusesToStartWithoutDatabaseSettingsAndNamesThem() {
        Throwable failure = assertThrows(Throwable.class, () -> new SpringApplicationBuilder(TaskManagerApplication.class)
            .run("--server.port=0"));
        String message = failure.getMessage();
        assertTrue(message.contains("TASK_MANAGER_DB_URL"), message);
        assertTrue(message.contains("TASK_MANAGER_DB_USER"), message);
        assertTrue(message.contains("TASK_MANAGER_DB_PASSWORD"), message);
    }

    @Test
    void theWorkerRunsJobsWithoutAWebServerTheWebProcessOnlyWhenAllowedAndCommandsNever() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
        String[] database = { "--spring.datasource.url=" + url, "--spring.datasource.username=" + TestDatabase.username(),
            "--spring.datasource.password=" + TestDatabase.password() };

        ConfigurableApplicationContext worker = new SpringApplicationBuilder(TaskManagerApplication.class)
            .web(org.springframework.boot.WebApplicationType.NONE).properties("taskmanager.process=worker").run(database);
        try {
            assertTrue(worker.containsBean("jobExecutor"));
            assertTrue(worker.containsBean("filesLifecycle"), "the worker prepares the file store for the clean up");
            assertFalse(worker.getEnvironment().containsProperty("local.server.port"));
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
            while (jdbc.queryForObject("select count(*) from job where kind = 'recurring.files.cleanup' and state = 'succeeded'", Integer.class) == 0) {
                assertTrue(System.nanoTime() < deadline, "the worker did not run the clean up");
                Thread.sleep(100);
            }
            assertEquals(1, jdbc.queryForObject("select count(*) from job_worker", Integer.class));
        } finally {
            worker.close();
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from job_worker", Integer.class), "the executor signs off when it stops");

        ConfigurableApplicationContext command = new SpringApplicationBuilder(TaskManagerApplication.class)
            .web(org.springframework.boot.WebApplicationType.NONE).properties("taskmanager.process=command").run(database);
        try {
            assertFalse(command.containsBean("jobExecutor"));
            assertFalse(command.containsBean("filesLifecycle"));
        } finally {
            command.close();
        }

        // The tests turn the executor of the web process off; an installation turns it off with TASK_MANAGER_JOBS_WORKER=false.
        ConfigurableApplicationContext web = start(url);
        ConfigurableApplicationContext webWithJobs = start(url, "--taskmanager.jobs.worker-enabled=true");
        try {
            assertFalse(web.containsBean("jobExecutor"));
            assertTrue(webWithJobs.containsBean("jobExecutor"));
        } finally {
            web.close();
            webWithJobs.close();
        }
        assertEquals(2, TaskManagerApplication.runWorker(new String[] { "worker", "unexpected" }));
    }

    private static ConfigurableApplicationContext start(String url, String... more) {
        String[] base = { "--server.port=0", "--spring.datasource.url=" + url, "--spring.datasource.username=" + TestDatabase.username(),
            "--spring.datasource.password=" + TestDatabase.password() };
        String[] all = java.util.Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return new SpringApplicationBuilder(TaskManagerApplication.class).run(all);
    }

    private static ConfigurableApplicationContext startPlain(String url) {
        return new SpringApplicationBuilder(TaskManagerApplication.class).run(
            "--server.port=0",
            "--spring.datasource.url=" + url,
            "--spring.datasource.username=" + TestDatabase.username(),
            "--spring.datasource.password=" + TestDatabase.password());
    }

    private void assertHealthy(ConfigurableApplicationContext context) throws Exception {
        String port = context.getEnvironment().getRequiredProperty("local.server.port");
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/readiness")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"UP\""), response.body());
    }

    private static List<Timestamp> installedOn(JdbcTemplate jdbc) {
        return jdbc.queryForList("select installed_on from flyway_schema_history order by installed_rank", Timestamp.class);
    }
}
