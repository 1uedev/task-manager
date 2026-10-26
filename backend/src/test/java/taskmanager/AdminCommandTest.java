package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The container commands run against a fresh database each, exactly as {@code java -jar app.jar bootstrap-admin NAME} does. */
class AdminCommandTest {
    private record Result(int exitCode, String out, String err) {
        String oneTimePassword() {
            Matcher matcher = Pattern.compile("One time password \\(shown only now\\): (\\S+)").matcher(out);
            assertTrue(matcher.find(), out);
            return matcher.group(1);
        }
    }

    private static Result run(String database, String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int exit = TaskManagerApplication.runAdminCommand(args,
                "spring.datasource.url=" + database,
                "spring.datasource.username=" + TestDatabase.username(),
                "spring.datasource.password=" + TestDatabase.password());
            return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static Result search(String database, String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int exit = TaskManagerApplication.runSearchCommand(args,
                "spring.datasource.url=" + database,
                "spring.datasource.username=" + TestDatabase.username(),
                "spring.datasource.password=" + TestDatabase.password());
            return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static List<String> query(String database, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(database, TestDatabase.username(), TestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<String> values = new java.util.ArrayList<>();
            while (rows.next()) {
                values.add(rows.getString(1));
            }
            return values;
        }
    }

    @Test
    void bootstrapCreatesTheFirstAdministratorOnceAndOnlyWhileNoAccountExists() throws Exception {
        String database = TestDatabase.createEmptyDatabase();
        Result first = run(database, "bootstrap-admin", "Chef");
        assertEquals(0, first.exitCode(), first.err());
        assertTrue(first.out().contains("Account: chef"), first.out());
        String oneTime = first.oneTimePassword();
        assertEquals(20, oneTime.length());
        assertEquals(List.of("chef|true|true"), query(database,
            "select username || '|' || active || '|' || password_change_required from identity_account"));
        // Only a hash is stored, the password is neither in the audit trail nor logged.
        assertFalse(query(database, "select password_hash from identity_account").get(0).contains(oneTime));
        assertEquals(List.of("account.created"), query(database, "select event from identity_audit_event"));
        assertFalse(first.err().contains(oneTime));
        // TASK-039: the same transaction created the company and the initial rights, and nothing else.
        assertEquals(List.of("1"), query(database, "select count(*) from platform_company"));
        assertEquals(List.of("Unternehmensverwaltung|accounts.manage|chef", "Unternehmensverwaltung|appearance.manage|chef",
                "Unternehmensverwaltung|permissions.manage|chef", "Unternehmensverwaltung|projects.manage|chef",
                "Unternehmensverwaltung|session_duration.manage|chef", "Unternehmensverwaltung|tags.manage|chef"), query(database,
            "select g.name || '|' || a.action_key || '|' || u.username from permissions_group g "
            + "join permissions_action a on a.group_id = g.id join permissions_membership m on m.group_id = g.id "
            + "join identity_account u on u.id = m.user_id order by a.action_key"));
        assertEquals(List.of("1"), query(database, "select count(*) from permissions_group"));
        assertEquals(List.of("8"), query(database, "select revision from permissions_revision"));

        Result second = run(database, "bootstrap-admin", "someone-else");
        assertEquals(2, second.exitCode());
        assertTrue(second.err().contains("bootstrap.accounts_exist"), second.err());
        assertFalse(second.out().contains("One time password"));
        assertEquals(List.of("1"), query(database, "select count(*) from identity_account"));
        assertEquals(List.of("1"), query(database, "select count(*) from platform_company"));
    }

    @Test
    void bootstrapRefusesInvalidLoginNames() throws Exception {
        String database = TestDatabase.createEmptyDatabase();
        Result result = run(database, "bootstrap-admin", "no spaces allowed");
        assertEquals(2, result.exitCode(), result.err());
        assertEquals(List.of("0"), query(database, "select count(*) from identity_account"));
    }

    @Test
    void theEmergencyResetIssuesANewPasswordEndsSessionsAndIsAudited() throws Exception {
        String database = TestDatabase.createEmptyDatabase();
        String firstPassword = run(database, "bootstrap-admin", "root").oneTimePassword();
        String oldHash = query(database, "select password_hash from identity_account").get(0);
        try (Connection connection = DriverManager.getConnection(database, TestDatabase.username(), TestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into identity_session (id, account_id, token_hash, csrf_hash, started_at, expires_at, restricted, created_at) "
                + "select gen_random_uuid(), id, decode(repeat('ab', 32), 'hex'), decode(repeat('cd', 32), 'hex'), now(), now() + interval '1 day', false, now() from identity_account");
        }
        Result reset = run(database, "reset-admin-password", "ROOT");
        assertEquals(0, reset.exitCode(), reset.err());
        assertFalse(reset.oneTimePassword().equals(firstPassword));
        assertFalse(query(database, "select password_hash from identity_account").get(0).equals(oldHash));
        assertEquals(List.of("0"), query(database, "select count(*) from identity_session where revoked_at is null"));
        assertEquals(List.of("account.created", "password.emergency_reset"), query(database, "select event from identity_audit_event order by id"));
    }

    @Test
    void theEmergencyResetRefusesUnknownAndNonAdministratorAccounts() throws Exception {
        String database = TestDatabase.createEmptyDatabase();
        run(database, "bootstrap-admin", "root");
        Result unknown = run(database, "reset-admin-password", "ghost");
        assertEquals(2, unknown.exitCode());
        assertTrue(unknown.err().contains("account.not_found"), unknown.err());
        try (Connection connection = DriverManager.getConnection(database, TestDatabase.username(), TestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into identity_account (id, username, password_hash, password_change_required, one_time_expires_at, active, created_at, updated_at) "
                + "values (gen_random_uuid(), 'plain', 'x', true, now() + interval '1 day', true, now(), now())");
        }
        Result plain = run(database, "reset-admin-password", "plain");
        assertEquals(2, plain.exitCode());
        assertTrue(plain.err().contains("account.not_administrator"), plain.err());
    }

    @Test
    void theCommandsNeedExactlyOneLoginName() throws Exception {
        String database = TestDatabase.createEmptyDatabase();
        assertEquals(2, run(database, "bootstrap-admin").exitCode());
        assertEquals(2, run(database, "reset-admin-password", "a", "b").exitCode());
    }

    @Test
    void rebuildSearchRefusesWithoutACompanyAndRebuildsTheProjectionAfterwards() throws Exception {
        String database = TestDatabase.createEmptyDatabase();
        assertEquals(2, search(database, "rebuild-search", "extra").exitCode());
        Result refused = search(database, "rebuild-search");
        assertEquals(2, refused.exitCode(), refused.out());
        assertTrue(refused.err().contains("company.missing"), refused.err());
        assertEquals(0, run(database, "bootstrap-admin", "Chef").exitCode());
        Result rebuilt = search(database, "rebuild-search");
        assertEquals(0, rebuilt.exitCode(), rebuilt.err());
        assertTrue(rebuilt.out().contains("Search projection rebuilt."), rebuilt.out());
        assertEquals(List.of("0"), query(database, "select count(*) from search_project"));
        assertEquals(List.of("1"), query(database, "select count(*) from search_state"));
    }
}
