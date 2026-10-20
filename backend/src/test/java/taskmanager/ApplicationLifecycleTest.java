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

        ConfigurableApplicationContext first = start(url);
        assertHealthy(first);
        first.close();
        assertFalse(first.isActive());
        List<Timestamp> installedAfterFirstStart = installedOn(jdbc);
        assertEquals(11, installedAfterFirstStart.size());

        ConfigurableApplicationContext second = start(url);
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

    private static ConfigurableApplicationContext start(String url) {
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
