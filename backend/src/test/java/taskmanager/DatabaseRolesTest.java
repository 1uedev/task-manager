package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The database roles of the operations template (TASK-136, finding F3 of the review of TASK-132): deploy/db-roles.sql as
 * the database creation and an update of the template run it, the command {@code migrate} with the migration role and the
 * processes with the runtime role, which may not change the schema.
 */
class DatabaseRolesTest {
    private static final String APP_PASSWORD = "app-secret-for-tests";
    private static final String MIGRATE_PASSWORD = "migrate-secret-for-tests";
    private static final String BACKUP_PASSWORD = "backup-secret-for-tests";

    /** Runs deploy/db-roles.sql as db-roles.sh does, with the psql variables written out. */
    private static synchronized void runRoleScript(String url) throws Exception {
        String script = Files.readString(Path.of("..", "deploy", "db-roles.sql"));
        String database = url.substring(url.lastIndexOf('/') + 1).replaceAll("\\?.*", "");
        script = script
            .replace(":'owner'", "'" + TestDatabase.username() + "'")
            .replace(":\"owner\"", "\"" + TestDatabase.username() + "\"")
            .replace(":\"DBNAME\"", "\"" + database + "\"")
            .replace(":'app_password'", "'" + APP_PASSWORD + "'")
            .replace(":'migrate_password'", "'" + MIGRATE_PASSWORD + "'")
            .replace(":'backup_password'", "'" + BACKUP_PASSWORD + "'");
        try (Connection connection = DriverManager.getConnection(url, TestDatabase.username(), TestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.execute(script);
        }
    }

    private static String[] as(String url, String user, String password, String... more) {
        List<String> properties = new ArrayList<>(List.of("spring.datasource.url=" + url, "spring.datasource.username=" + user,
            "spring.datasource.password=" + password));
        properties.addAll(List.of(more));
        return properties.toArray(String[]::new);
    }

    /** The output of the last quiet run, for the message of a failed assertion. */
    private static String output = "";

    private static int quietly(java.util.function.IntSupplier run) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            return run.getAsInt();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            output = captured.toString(StandardCharsets.UTF_8);
        }
    }

    private static JdbcTemplate jdbc(String url, String user, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    private static void assertRuntimeRoleCannotChangeTheSchema(String url) {
        JdbcTemplate app = jdbc(url, "taskmanager_app", APP_PASSWORD);
        assertEquals("taskmanager_app", app.queryForObject("select current_user", String.class));
        assertEquals(Boolean.FALSE, app.queryForObject("select rolsuper or rolcreaterole or rolcreatedb from pg_roles "
            + "where rolname = current_user", Boolean.class));
        for (String change : List.of("create table forbidden (id int)", "alter table identity_account add column forbidden int",
                "drop table tag", "truncate table ticket", "create role forbidden")) {
            var refused = assertThrows(Exception.class, () -> app.execute(change), change);
            String reason = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(refused).getMessage();
            assertTrue(reason.contains("permission denied") || reason.contains("must be owner"), change + ": " + reason);
        }
        // Rows are its work.
        assertEquals(Integer.valueOf(1), app.queryForObject("select count(*) from identity_account where username = 'chef'",
            Integer.class));
        // The history of the migrations and the hold of the clean up are only read; the hold functions are not its work
        // (TASK-137, N3).
        app.queryForObject("select count(*) from flyway_schema_history", Integer.class);
        app.queryForObject("select count(*) from files_purge_hold", Integer.class);
        for (String change : List.of("update flyway_schema_history set success = false where version = '0001'",
                "delete from flyway_schema_history", "insert into files_purge_hold values ('app', now())",
                "delete from files_purge_hold", "select files_purge_hold_begin('app')", "select files_purge_hold_end('app')")) {
            var refused = assertThrows(Exception.class, () -> app.execute(change), change);
            String reason = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(refused).getMessage();
            assertTrue(reason.contains("permission denied"), change + ": " + reason);
        }
        // Schema and extension have their settled owners.
        JdbcTemplate admin = jdbc(url, TestDatabase.username(), TestDatabase.password());
        assertEquals("taskmanager_migrate", admin.queryForObject(
            "select pg_get_userbyid(nspowner) from pg_namespace where nspname = 'public'", String.class));
        assertEquals(TestDatabase.username(), admin.queryForObject(
            "select pg_get_userbyid(extowner) from pg_extension where extname = 'pg_trgm'", String.class));
        assertEquals(List.of(), admin.queryForList("select p.proname from pg_proc p join pg_depend d on d.classid = "
            + "'pg_proc'::regclass and d.objid = p.oid and d.deptype = 'e' join pg_extension e on e.oid = d.refobjid "
            + "where p.proowner <> e.extowner", String.class));
    }

    /** The commands and the job executor of the operations template with the runtime role (TASK-137, N3). */
    private static void assertRuntimeRoleRunsTheCommandsAndTheJobs(String url) throws Exception {
        assertEquals(0, quietly(() -> TaskManagerApplication.runAdminCommand(true, new String[] { "reset-admin-password", "chef" },
            as(url, "taskmanager_app", APP_PASSWORD))), () -> output);
        assertEquals(0, quietly(() -> TaskManagerApplication.runSearchCommand(true, new String[] { "rebuild-search" },
            as(url, "taskmanager_app", APP_PASSWORD))), () -> output);
        JdbcTemplate admin = jdbc(url, TestDatabase.username(), TestDatabase.password());
        admin.execute("delete from job; delete from job_schedule; delete from job_worker");
        var builder = new org.springframework.boot.builder.SpringApplicationBuilder(TaskManagerApplication.class)
            .web(org.springframework.boot.WebApplicationType.NONE).properties("taskmanager.process=worker");
        taskmanager.operations.OperationsStart.register(builder.application());
        try (var worker = builder.run(java.util.Arrays.stream(as(url, "taskmanager_app", APP_PASSWORD,
                "taskmanager.jobs.worker-enabled=true")).map(property -> "--" + property).toArray(String[]::new))) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
            while (!Integer.valueOf(1).equals(admin.queryForObject("select count(*) from job where kind = "
                    + "'recurring.files.cleanup' and state = 'succeeded'", Integer.class))) {
                assertTrue(System.nanoTime() < deadline, "the worker with the runtime role did not run the clean up");
                Thread.sleep(200);
            }
            assertEquals("taskmanager_app", jdbc(url, "taskmanager_app", APP_PASSWORD)
                .queryForObject("select current_user", String.class));
        }
    }

    private static void assertBackupRoleReadsOnlyItsShare(String url) {
        JdbcTemplate admin = jdbc(url, TestDatabase.username(), TestDatabase.password());
        String grants = "select has_table_privilege('taskmanager_backup', 'flyway_schema_history', 'select')::text || "
            + "has_table_privilege('taskmanager_backup', 'operations_backup_content', 'select')::text || "
            + "has_table_privilege('taskmanager_backup', 'identity_account', 'select')::text || "
            + "has_table_privilege('taskmanager_backup', 'operations_restore_pending', 'insert')::text || "
            + "has_function_privilege('taskmanager_backup', 'files_purge_hold_begin(text)', 'execute')::text || "
            + "has_function_privilege('taskmanager_app', 'files_purge_hold_begin(text)', 'execute')::text";
        assertEquals("truetruefalsefalsetruefalse", admin.queryForObject(grants, String.class));
        assertEquals(Boolean.FALSE, admin.queryForObject(
            "select has_function_privilege('public', 'files_purge_hold_begin(text)', 'execute')", Boolean.class));
    }

    @Test
    void aNewInstallationMigratesWithTheMigrationRoleAndRunsWithTheRuntimeRole() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        runRoleScript(url);

        // The runtime role cannot even apply the first migration.
        assertEquals(2, quietly(() -> TaskManagerApplication.runMigrateCommand(new String[] { "migrate" },
            as(url, "taskmanager_app", APP_PASSWORD))));
        assertEquals(0, quietly(() -> TaskManagerApplication.runMigrateCommand(true, new String[] { "migrate" },
            as(url, "taskmanager_migrate", MIGRATE_PASSWORD))), () -> output);
        assertEquals(List.of("taskmanager_migrate"), jdbc(url, TestDatabase.username(), TestDatabase.password())
            .queryForList("select distinct pg_get_userbyid(relowner) from pg_class c join pg_namespace n on n.oid = "
                + "c.relnamespace where n.nspname = 'public' and relkind in ('r', 'v', 'S')", String.class));

        assertEquals(0, quietly(() -> TaskManagerApplication.runAdminCommand(true, new String[] { "bootstrap-admin", "chef" },
            as(url, "taskmanager_app", APP_PASSWORD))));
        assertRuntimeRoleCannotChangeTheSchema(url);
        assertBackupRoleReadsOnlyItsShare(url);
        assertRuntimeRoleRunsTheCommandsAndTheJobs(url);
    }

    @Test
    void anInstallationFromBeforeTheRolesMovesItsObjectsToTheMigrationRole() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        // As the template of TASK-132 did, before V0072: the administration account migrates and owns everything, also the
        // extension pg_trgm. Objects of every kind the transfer must handle are added (TASK-137, N3).
        Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password())
            .locations("classpath:db/migration").target("0071").load().migrate();
        JdbcTemplate admin = jdbc(url, TestDatabase.username(), TestDatabase.password());
        admin.execute("create sequence legacy_free_sequence");
        admin.execute("create table legacy_bound (id bigserial primary key, note text)");
        admin.execute("create type legacy_mood as enum ('calm', 'busy')");
        admin.execute("create domain legacy_short as text check (char_length(value) < 10)");
        admin.execute("create type legacy_pair as (left_value int, right_value int)");
        admin.execute("create table legacy_partitioned (at date not null, mood legacy_mood) partition by range (at)");
        admin.execute("create table legacy_partition_2026 partition of legacy_partitioned for values from ('2026-01-01') "
            + "to ('2027-01-01')");
        admin.execute("create view legacy_view as select id from legacy_bound");
        admin.execute("create materialized view legacy_materialized as select id from legacy_bound");
        admin.execute("create function legacy_function(value legacy_short) returns legacy_pair language sql as "
            + "'select (1, 2)::legacy_pair'");

        runRoleScript(url);
        runRoleScript(url);

        assertEquals(List.of("taskmanager_migrate"), admin.queryForList("select distinct pg_get_userbyid(relowner) from "
            + "pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public' and relkind in "
            + "('r', 'p', 'v', 'm', 'S')", String.class));
        assertEquals(List.of("taskmanager_migrate"), admin.queryForList("select distinct pg_get_userbyid(proowner) from "
            + "pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and not exists (select "
            + "from pg_depend d where d.classid = 'pg_proc'::regclass and d.objid = p.oid and d.deptype = 'e')", String.class));
        assertEquals(List.of("taskmanager_migrate"), admin.queryForList("select distinct pg_get_userbyid(typowner) from "
            + "pg_type t join pg_namespace n on n.oid = t.typnamespace where n.nspname = 'public' and t.typname in "
            + "('legacy_mood', 'legacy_short', 'legacy_pair')", String.class));
        // The bound sequence followed its table, the free one moved itself.
        assertEquals(List.of("taskmanager_migrate"), admin.queryForList("select distinct pg_get_userbyid(relowner) from "
            + "pg_class where relname in ('legacy_bound_id_seq', 'legacy_free_sequence')", String.class));

        // The migration role applies V0072 and later on the objects it took over, and a second migrate finds nothing left.
        assertEquals(0, quietly(() -> TaskManagerApplication.runMigrateCommand(true, new String[] { "migrate" },
            as(url, "taskmanager_migrate", MIGRATE_PASSWORD))), () -> output);
        assertEquals(0, quietly(() -> TaskManagerApplication.runMigrateCommand(true, new String[] { "migrate" },
            as(url, "taskmanager_migrate", MIGRATE_PASSWORD))), () -> output);
        jdbc(url, "taskmanager_migrate", MIGRATE_PASSWORD).execute("comment on table tag is 'owned by the migration role'");
        jdbc(url, "taskmanager_migrate", MIGRATE_PASSWORD).execute("alter type legacy_mood add value 'away'");
        jdbc(url, "taskmanager_migrate", MIGRATE_PASSWORD).execute("refresh materialized view legacy_materialized");

        assertEquals(0, quietly(() -> TaskManagerApplication.runAdminCommand(true, new String[] { "bootstrap-admin", "chef" },
            as(url, "taskmanager_app", APP_PASSWORD))));
        assertRuntimeRoleCannotChangeTheSchema(url);
        assertBackupRoleReadsOnlyItsShare(url);
    }

    @Test
    void theDemonstrationWithoutTheRolesGetsNoGrant() throws SQLException {
        // V0072 grants only when the role exists; a database of another installation in the same server is not touched.
        String url = TestDatabase.createEmptyDatabase();
        assertEquals(0, quietly(() -> TaskManagerApplication.runMigrateCommand(new String[] { "migrate" },
            as(url, TestDatabase.username(), TestDatabase.password()))));
        assertEquals(Boolean.FALSE, jdbc(url, TestDatabase.username(), TestDatabase.password()).queryForObject(
            "select has_function_privilege('public', 'files_purge_hold_begin(text)', 'execute')", Boolean.class));
    }
}
