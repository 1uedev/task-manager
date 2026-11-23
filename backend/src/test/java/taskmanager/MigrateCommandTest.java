package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // Two Spring Boot starts in one JVM at the same time would both reconfigure the shared logging system and can
        // break each other's start ("Unable to find Spring Environment in logger context"); containers never share a JVM,
        // so these two runs leave the logging system as it is.
        String loggingSystem = System.getProperty("org.springframework.boot.logging.LoggingSystem");
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        try {
            runTwoMigrationsAtOnce(url);
        } finally {
            if (loggingSystem == null) {
                System.clearProperty("org.springframework.boot.logging.LoggingSystem");
            } else {
                System.setProperty("org.springframework.boot.logging.LoggingSystem", loggingSystem);
            }
        }
    }

    private static void runTwoMigrationsAtOnce(String url) throws Exception {
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
                new String[] { "bootstrap-admin", "chef" }, database(url, "taskmanager.schema.mode=geheim-auto")));
            String message = refusal(failure).getMessage();
            assertTrue(message.contains("allowed are 'migrate' and 'verify'"), message);
            assertFalse(message.contains("geheim"), message);
        } finally {
            System.setOut(originalOut);
        }
    }

    private static String[] arguments(String url, String... more) {
        return java.util.Arrays.stream(database(url, more)).map(property -> "--" + property).toArray(String[]::new);
    }

    private static void quietly(Runnable run) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            run.run();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static void migrateUpTo(String url, String version) {
        org.flywaydb.core.Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password())
            .locations("classpath:db/migration").target(version).load().migrate();
    }

    /** TASK-136, F1: only the entry point selects the command; the former setting grants nothing to other processes. */
    @Test
    void noSettingLetsAWebProcessAWorkerOrAnotherCommandMigrate() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        String[] grant = { "taskmanager.schema.mode=verify", "taskmanager.operations-command=migrate" };

        var web = assertThrows(RuntimeException.class, () -> quietly(() -> new org.springframework.boot.builder
            .SpringApplicationBuilder(TaskManagerApplication.class).run(arguments(url, "server.port=0", grant[0], grant[1]))));
        assertTrue(refusal(web).getMessage().contains("migration(s) are missing"), refusal(web).getMessage());

        var worker = assertThrows(RuntimeException.class, () -> quietly(() -> TaskManagerApplication.runWorker(
            new String[] { "worker" }, database(url, grant))));
        assertTrue(refusal(worker).getMessage().contains("migration(s) are missing"), refusal(worker).getMessage());

        var admin = assertThrows(RuntimeException.class, () -> quietly(() -> TaskManagerApplication.runAdminCommand(
            new String[] { "bootstrap-admin", "chef" }, database(url, grant))));
        assertTrue(refusal(admin).getMessage().contains("migration(s) are missing"), refusal(admin).getMessage());

        assertEquals(List.of("0"), query(url, "select count(*) from information_schema.tables where table_schema = 'public'"));
    }

    /** TASK-136, F2: switched off Flyway and settings that change set, target or history refuse the start. */
    @Test
    void changedFlywaySettingsAreRefusedWhenTheSchemaIsChecked() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        assertEquals(0, migrate(url).exitCode());

        for (String setting : List.of("spring.flyway.enabled=false", "spring.flyway.target=0069", "spring.flyway.table=other_history",
                "spring.flyway.locations=classpath:db/other", "spring.flyway.baseline-on-migrate=true",
                "spring.flyway.out-of-order=true", "spring.flyway.ignore-migration-patterns=*:missing")) {
            var failure = assertThrows(RuntimeException.class, () -> quietly(() -> TaskManagerApplication.runAdminCommand(
                new String[] { "bootstrap-admin", "chef" }, database(url, "taskmanager.schema.mode=verify", setting))));
            assertTrue(refusal(failure).getMessage().contains("refuses changed Flyway settings"), setting + ": "
                + refusal(failure).getMessage());

            var web = assertThrows(RuntimeException.class, () -> quietly(() -> new org.springframework.boot.builder
                .SpringApplicationBuilder(TaskManagerApplication.class)
                .run(arguments(url, "server.port=0", "taskmanager.schema.mode=verify", setting))));
            assertTrue(refusal(web).getMessage().contains("refuses changed Flyway settings"), setting);
        }

        // The command refuses them as well, before it changes anything.
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            assertEquals(2, TaskManagerApplication.runMigrateCommand(new String[] { "migrate" },
                database(url, "spring.flyway.target=0001")));
        } finally {
            System.setErr(originalErr);
        }
    }

    /** TASK-136, F2: a history that does not fit the migrations of the image refuses the start in the mode verify. */
    @Test
    void aChangedHistoryIsRefusedByTheVerifyMode() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        assertEquals(0, migrate(url).exitCode());
        update(url, "update flyway_schema_history set checksum = checksum + 1 where version = '0005'");

        assertThrows(RuntimeException.class, () -> quietly(() -> verifiedCommand(url)));
    }

    /** TASK-136, H1: a migration that fails now and one that failed earlier both end with 2, each with its own message. */
    @Test
    void aMigrationFailingNowAndOneThatFailedEarlierAreReportedApart() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        migrateUpTo(url, "0070");
        update(url, "create table files_purge_hold (blocked int)");

        Result now = migrate(url);
        assertEquals(2, now.exitCode(), now.out());
        assertTrue(now.err().contains("A migration failed in this run; the schema stays at version 0070"), now.err());

        update(url, "drop table files_purge_hold");
        update(url, "insert into flyway_schema_history (installed_rank, version, description, type, script, checksum, "
            + "installed_by, installed_on, execution_time, success) values (9998, '0071', 'files purge hold', 'SQL', "
            + "'V0071__files_purge_hold.sql', 0, 'test', now(), 0, false)");
        Result earlier = migrate(url);
        assertEquals(2, earlier.exitCode(), earlier.out());
        assertTrue(earlier.err().contains("failed in an earlier run"), earlier.err());
    }

    @Test
    void migrateRefusesExtraArguments() {
        assertEquals(2, TaskManagerApplication.runMigrateCommand(new String[] { "migrate", "now" }));
    }
}
