package taskmanager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for integration tests, started once per test run. CI always uses the pinned
 * container. Developers without Docker may point the tests at a local server with
 * {@code -Dtaskmanager.test.jdbc-url}, {@code -Dtaskmanager.test.username} and
 * {@code -Dtaskmanager.test.password}; such a run is not evidence for the pinned version.
 */
public final class TestDatabase {
    // Official postgres:18.6 manifest, the same digest as in the TASK-021 qualification.
    private static final String IMAGE = "postgres@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336";
    private static final String EXTERNAL_URL = System.getProperty("taskmanager.test.jdbc-url");
    private static PostgreSQLContainer container;

    private TestDatabase() {
    }

    public static synchronized String jdbcUrl() {
        if (EXTERNAL_URL != null) {
            return EXTERNAL_URL;
        }
        if (container == null) {
            container = new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"));
            container.start();
        }
        return container.getJdbcUrl();
    }

    public static String username() {
        return EXTERNAL_URL != null ? System.getProperty("taskmanager.test.username") : container().getUsername();
    }

    public static String password() {
        return EXTERNAL_URL != null ? System.getProperty("taskmanager.test.password", "") : container().getPassword();
    }

    /** The server version the tests must observe. Only an explicit local override may change it. */
    public static String expectedServerVersion() {
        return EXTERNAL_URL != null ? System.getProperty("taskmanager.test.server-version", "18.6") : "18.6";
    }

    public static boolean usesPinnedContainer() {
        return EXTERNAL_URL == null;
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    /** Creates an empty database on the same server and returns its JDBC URL. */
    public static String createEmptyDatabase() throws SQLException {
        String name = "lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
             Statement statement = connection.createStatement()) {
            statement.execute("create database " + name);
        }
        String url = jdbcUrl();
        int query = url.indexOf('?');
        String base = query < 0 ? url : url.substring(0, query);
        String suffix = query < 0 ? "" : url.substring(query);
        return base.substring(0, base.lastIndexOf('/') + 1) + name + suffix;
    }

    private static PostgreSQLContainer container() {
        jdbcUrl();
        return container;
    }
}
