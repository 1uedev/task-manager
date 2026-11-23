package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import taskmanager.operations.SchemaVersionException;

/**
 * The command {@code migrate} and the start check of the schema version (TASK-132, DEC-162, rule 3, prüffall O11), against
 * empty databases of their own exactly as {@code java -jar app.jar migrate} runs.
 */
class MigrateCommandTest {
    private static final Pattern APPLIED = Pattern.compile("Migrations applied: (\\d+)");

    private record Result(int exitCode, String out, String err) {
        int applied() {
            Matcher matcher = APPLIED.matcher(out);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        }
    }

    private static String[] database(String url, String... more) {
        List<String> properties = new ArrayList<>(List.of("spring.datasource.url=" + url,
            "spring.datasource.username=" + TestDatabase.username(), "spring.datasource.password=" + TestDatabase.password()));
        properties.addAll(List.of(more));
        return properties.toArray(String[]::new);
    }

    /** Runs the command; output is captured per thread is not possible, so concurrent runs pass their own streams. */
    private static synchronized Result migrate(String url) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int exit = TaskManagerApplication.runMigrateCommand(new String[] { "migrate" }, database(url));
            return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /** A command in the mode of the operations template; it changes nothing and only starts on a fitting schema. */
    private static int verifiedCommand(String url) {
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            return TaskManagerApplication.runAdminCommand(new String[] { "bootstrap-admin", "chef" },
                database(url, "taskmanager.schema.mode=verify"));
        } finally {
            System.setOut(originalOut);
        }
    }

    private static List<String> query(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, TestDatabase.username(), TestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rows.next()) {
                values.add(rows.getString(1));
            }
            return values;
        }
    }

    private static void update(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, TestDatabase.username(), TestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int knownMigrations(String url) throws SQLException {
        return Integer.parseInt(query(url, "select count(*) from flyway_schema_history where success").getFirst());
    }

    private static String newestVersion(String url) throws SQLException {
        return query(url, "select version from flyway_schema_history where success order by installed_rank desc limit 1").getFirst();
    }

    private static SchemaVersionException refusal(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SchemaVersionException refused) {
                return refused;
            }
        }
        throw new AssertionError("no refusal of the schema in the failure", failure);
    }

    @Test
    void migrateAppliesEverythingOnceAndASecondRunChangesNothing() throws Exception {
        String url = TestDatabase.createEmptyDatabase();

        Result first = migrate(url);
        assertEquals(0, first.exitCode(), first.err());
        assertTrue(first.out().contains("Schema version before: none"), first.out());
        assertEquals(knownMigrations(url), first.applied(), first.out());
        assertTrue(first.out().contains("Schema version now: " + newestVersion(url)), first.out());

        Result second = migrate(url);
        assertEquals(0, second.exitCode(), second.err());
        assertEquals(0, second.applied(), second.out());
        assertTrue(second.out().contains("The schema was already up to date; nothing changed."), second.out());
        assertEquals(List.of("0"), query(url, "select count(*) - count(distinct version) from flyway_schema_history"));
    }

    @Test
    void twoMigrateRunsAtTheSameTimeApplyEachMigrationOnce() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        var start = new CountDownLatch(1);
        // Both runs open their own context at the same time; Flyway serializes them with its lock on the history.
        var runs = List.of(
            CompletableFuture.supplyAsync(() -> await(start, () -> TaskManagerApplication.runMigrateCommand(
                new String[] { "migrate" }, database(url)))),
            CompletableFuture.supplyAsync(() -> await(start, () -> TaskManagerApplication.runMigrateCommand(
                new String[] { "migrate" }, database(url)))));
        start.countDown();
        for (var run : runs) {
            assertEquals(0, run.get(5, TimeUnit.MINUTES));
        }
        assertEquals(List.of("0"), query(url, "select count(*) - count(distinct version) from flyway_schema_history"));
        assertEquals(List.of("0"), query(url, "select count(*) from flyway_schema_history where not success"));
        assertTrue(knownMigrations(url) > 60);
    }

    private static int await(CountDownLatch start, java.util.function.IntSupplier run) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return run.getAsInt();
    }

    @Test
    void theVerifyModeRefusesMissingMigrationsAndChangesNothing() throws Exception {
        String url = TestDatabase.createEmptyDatabase();

        var failure = assertThrows(RuntimeException.class, () -> verifiedCommand(url));
        String message = refusal(failure).getMessage();
        assertTrue(message.contains("migration(s) are missing"), message);
        assertTrue(message.contains("run the command 'migrate' once"), message);
        assertEquals(List.of("0"), query(url, "select count(*) from information_schema.tables where table_schema = 'public'"));

        assertEquals(0, migrate(url).exitCode());
        assertEquals(0, verifiedCommand(url));
    }

    @Test
    void aDatabaseNewerThanTheImageIsRefusedByMigrateAndByTheVerifyMode() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        assertEquals(0, migrate(url).exitCode());
        int before = knownMigrations(url);
        update(url, "insert into flyway_schema_history (installed_rank, version, description, type, script, checksum, "
            + "installed_by, installed_on, execution_time, success) values (9999, '9999', 'later version', 'SQL', "
            + "'V9999__later_version.sql', 0, 'test', now(), 0, true)");

        Result refused = migrate(url);
        assertEquals(2, refused.exitCode(), refused.out());
        assertTrue(refused.err().contains("Refused: The database has the schema version 9999 and is newer than this image"),
            refused.err());
        assertTrue(refused.err().contains("only by restoring the backup point"), refused.err());
        assertEquals(before + 1, knownMigrations(url));

        var failure = assertThrows(RuntimeException.class, () -> verifiedCommand(url));
        assertTrue(refusal(failure).getMessage().contains("newer than this image"), refusal(failure).getMessage());
    }

    @Test
    void anUnknownModeIsRefused() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            var failure = assertThrows(RuntimeException.class, () -> TaskManagerApplication.runAdminCommand(
                new String[] { "bootstrap-admin", "chef" }, database(url, "taskmanager.schema.mode=auto")));
            assertTrue(refusal(failure).getMessage().contains("must be 'migrate' or 'verify'"), refusal(failure).getMessage());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void migrateRefusesExtraArguments() {
        assertEquals(2, TaskManagerApplication.runMigrateCommand(new String[] { "migrate", "now" }));
    }
}
