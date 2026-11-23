package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command {@code after-restore} against a real database and a real file store, exactly as
 * {@code java -jar app.jar after-restore NAME} runs it (TASK-131, DEC-161).
 */
class AfterRestoreCommandTest {
    private record Result(int exitCode, String out, String err) {
    }

    private Result run(String database, Path store, String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            int exit = TaskManagerApplication.runRestoreCommand(args,
                "spring.datasource.url=" + database,
                "spring.datasource.username=" + TestDatabase.username(),
                "spring.datasource.password=" + TestDatabase.password(),
                "taskmanager.files.directory=" + (store == null ? "" : store.toString()));
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
            List<String> values = new ArrayList<>();
            while (rows.next()) {
                values.add(rows.getString(1));
            }
            return values;
        }
    }

    private static void update(String database, String... statements) throws SQLException {
        try (Connection connection = DriverManager.getConnection(database, TestDatabase.username(), TestDatabase.password());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.executeUpdate(sql);
            }
        }
    }

    /** The database the migrations created, with an administrator, so that the schema and the company exist. */
    private String database() throws SQLException {
        String database = TestDatabase.createEmptyDatabase();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, TaskManagerApplication.runAdminCommand(new String[] { "bootstrap-admin", "chef" },
                "spring.datasource.url=" + database,
                "spring.datasource.username=" + TestDatabase.username(),
                "spring.datasource.password=" + TestDatabase.password()));
        } finally {
            System.setOut(originalOut);
        }
        return database;
    }

    /** One open session, one waiting and one sending reply, one suggestion and two attachments with content. */
    private void workOfBeforeTheFailure(String database, Path store, UUID present, UUID gone) throws Exception {
        UUID company = UUID.fromString(query(database, "select id from platform_company").getFirst());
        UUID account = UUID.fromString(query(database, "select id from identity_account").getFirst());
        UUID customer = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID mailbox = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        String people = "'" + company + "', 'Kunde', 1, now(), '" + account + "', now(), '" + account + "'";
        update(database,
            "insert into identity_session (id, account_id, token_hash, csrf_hash, started_at, expires_at, restricted, created_at) "
                + "values (gen_random_uuid(), '" + account + "', decode(repeat('ab', 32), 'hex'), decode(repeat('cd', 32), 'hex'), "
                + "now(), now() + interval '1 day', false, now())",
            "insert into helpdesk_customer (id, company_id, name, version, created_at, created_by, updated_at, updated_by) "
                + "values ('" + customer + "', " + people + ")",
            "insert into helpdesk_contact (id, company_id, customer_id, name, version, created_at, created_by, updated_at, updated_by) "
                + "values ('" + contact + "', '" + company + "', '" + customer + "', 'Kontakt', 1, now(), '" + account + "', now(), '" + account + "')",
            "insert into helpdesk_mailbox (id, company_id, name, enabled, project_id, ticket_type, imap_host, imap_port, imap_security, "
                + "username, folder, max_bytes, version, created_at, created_by, updated_at, updated_by) values ('" + mailbox + "', '"
                + company + "', 'Eingang', true, '" + project + "', 'support', 'imap.example.invalid', 993, 'tls', 'eingang', 'INBOX', "
                + "41943040, 1, now(), '" + account + "', now(), '" + account + "')",
            reply(company, project, ticket, mailbox, contact, account, "pending", "wartet"),
            reply(company, project, ticket, mailbox, contact, account, "sending", "unterwegs"),
            reply(company, project, ticket, mailbox, contact, account, "sent", "raus"),
            "insert into ai_run (id, company_id, account_id, kind, use_case, language, skill_version, model_key, data_path, state, "
                + "started_at) values ('" + run + "', '" + company + "', '" + account + "', 'use', 'ticket_summary', 'de', "
                + "'builtin-1', 'modell', 'local', 'refused', now())",
            "insert into ai_suggestion (run_id, company_id, account_id, sources, content, created_at, expires_at) values ('" + run
                + "', '" + company + "', '" + account + "', '[]'::jsonb, '{}'::jsonb, now(), now() + interval '24 hours')",
            attachment(company, project, ticket, account, present, "vorhanden.txt", "Inhalt A"),
            attachment(company, project, ticket, account, gone, "verloren.txt", "Inhalt B"));
        store(store, present, "Inhalt A");
    }

    private static String reply(UUID company, UUID project, UUID ticket, UUID mailbox, UUID contact, UUID account,
            String state, String subject) {
        return "insert into helpdesk_reply (id, company_id, project_id, ticket_id, mailbox_id, contact_id, from_address, to_address, "
            + "subject, body, message_id, state, job_id, sends, created_at, created_by, updated_at, updated_by, version"
            + (state.equals("sent") ? ", sent_at" : "") + ") values (gen_random_uuid(), '" + company + "', '" + project + "', '"
            + ticket + "', '" + mailbox + "', '" + contact + "', 'eingang@example.invalid', 'kunde@example.invalid', '" + subject
            + "', 'Text', '<" + subject + "@example.invalid>', '" + state + "', gen_random_uuid(), 1, now(), '" + account
            + "', now(), '" + account + "', 1" + (state.equals("sent") ? ", now()" : "") + ")";
    }

    private static String attachment(UUID company, UUID project, UUID ticket, UUID account, UUID id, String name, String content) {
        return "insert into file_attachment (id, company_id, ticket_id, project_id, name, size_bytes, sha256, uploaded_at, uploaded_by) "
            + "values ('" + id + "', '" + company + "', '" + ticket + "', '" + project + "', '" + name + "', "
            + content.getBytes(StandardCharsets.UTF_8).length + ", '" + sha256(content) + "', now(), '" + account + "')";
    }

    private static String sha256(String content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void store(Path store, UUID id, String content) throws Exception {
        Path target = store.resolve("content").resolve(id.toString().substring(0, 2));
        Files.createDirectories(target);
        Files.writeString(target.resolve(id.toString()), content, StandardCharsets.UTF_8);
    }

    @Test
    void refusesWithoutARestoreThatWaits(@TempDir Path store) throws Exception {
        String database = database();
        update(database, "insert into identity_session (id, account_id, token_hash, csrf_hash, started_at, expires_at, restricted, created_at) "
            + "select gen_random_uuid(), id, decode(repeat('ab', 32), 'hex'), decode(repeat('cd', 32), 'hex'), now(), "
            + "now() + interval '1 day', false, now() from identity_account");

        Result result = run(database, store, "after-restore", "Betrieb");

        assertEquals(2, result.exitCode(), result.err());
        assertTrue(result.err().contains("restore.not_pending"), result.err());
        // Nothing of a running installation is touched by a refused command.
        assertEquals(List.of("1"), query(database, "select count(*) from identity_session where revoked_at is null"));
        assertEquals(List.of("0"), query(database, "select count(*) from operations_audit_event"));
    }

    @Test
    void refusesWithoutAFileStore() throws Exception {
        String database = database();
        update(database, "insert into operations_restore_pending (point, restored_at) values ('20261123T120000Z', now())");

        Result result = run(database, null, "after-restore", "Betrieb");

        assertEquals(2, result.exitCode(), result.err());
        assertTrue(result.err().contains("files.directory_missing"), result.err());
        assertEquals(List.of("1"), query(database, "select count(*) from operations_restore_pending"));
    }

    @Test
    void refusesWithoutAnOperator() {
        assertEquals(2, TaskManagerApplication.runRestoreCommand(new String[] { "after-restore" }));
        assertEquals(2, TaskManagerApplication.runRestoreCommand(new String[] { "after-restore", "  " }));
    }

    @Test
    void endsSessionsHoldsRepliesDiscardsSuggestionsAndChecksTheFileStore(@TempDir Path store) throws Exception {
        String database = database();
        UUID present = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        workOfBeforeTheFailure(database, store, present, gone);
        update(database, "insert into operations_restore_pending (point, restored_at) values ('20261123T120000Z', now())");

        Result result = run(database, store, "after-restore", "Betrieb Nord");

        // 3: the command ran, but one content of the database is not in the file store.
        assertEquals(3, result.exitCode(), result.err());
        assertTrue(result.out().contains("Backup point: 20261123T120000Z"), result.out());
        assertTrue(result.out().contains("Sessions ended: 1"), result.out());
        assertTrue(result.out().contains("Mail replies held as uncertain: 2"), result.out());
        assertTrue(result.out().contains("AI suggestions discarded: 1"), result.out());
        assertTrue(result.out().contains("Contents checked: 2"), result.out());
        assertTrue(result.out().contains("missing " + gone), result.out());
        assertFalse(result.out().contains(present.toString()), result.out());
        // Rule 11: the accounts with company rights are named, so that withdrawals after the point can be repeated.
        assertTrue(result.out().contains("Accounts with company rights: 1"), result.out());
        assertTrue(result.out().contains("chef accounts.manage,ai.manage"), result.out());
        assertTrue(result.out().contains("Repeat the account locks"), result.out());

        assertEquals(List.of("0"), query(database, "select count(*) from identity_session where revoked_at is null"));
        assertEquals(List.of("restore"), query(database, "select distinct revoke_reason from identity_session"));
        assertEquals(List.of("raus|sent|", "unterwegs|uncertain|helpdesk.restored", "wartet|uncertain|helpdesk.restored"),
            query(database, "select subject || '|' || state || '|' || coalesce(error_code, '') from helpdesk_reply order by subject"));
        assertEquals(List.of("2"), query(database,
            "select count(*) from helpdesk_audit_event where operation = 'reply.held_after_restore'"));
        assertEquals(List.of("0"), query(database, "select count(*) from ai_suggestion"));
        // The record of the runs stays; only the suggestion is gone.
        assertEquals(List.of("1"), query(database, "select count(*) from ai_run"));
        assertEquals(List.of("0"), query(database, "select count(*) from operations_restore_pending"));
        assertEquals(List.of("restore.released|20261123T120000Z|Betrieb Nord"), query(database,
            "select operation || '|' || point || '|' || operator from operations_audit_event"));
        assertEquals(List.of("1|2|1|2|1|0"), query(database,
            "select (detail->>'sessions') || '|' || (detail->>'replies') || '|' || (detail->>'suggestions') || '|' "
            + "|| (detail->>'contentsChecked') || '|' || (detail->>'contentsMissing') || '|' || (detail->>'contentsDamaged') "
            + "from operations_audit_event"));
    }

    @Test
    void reportsAContentWithAnotherChecksumAsDamagedAndReleasesACompleteStore(@TempDir Path store) throws Exception {
        String database = database();
        UUID present = UUID.randomUUID();
        UUID damaged = UUID.randomUUID();
        workOfBeforeTheFailure(database, store, present, damaged);
        store(store, damaged, "etwas anderes");
        update(database, "insert into operations_restore_pending (point, restored_at) values ('20261123T130000Z', now())");

        Result result = run(database, store, "after-restore", "Betrieb");

        assertEquals(3, result.exitCode(), result.err());
        assertTrue(result.out().contains("damaged " + damaged), result.out());
        assertTrue(result.out().contains("Contents missing: 0"), result.out());

        // With both contents in place the command releases the installation.
        store(store, damaged, "Inhalt B");
        update(database, "insert into operations_restore_pending (point, restored_at) values ('20261123T140000Z', now())");
        Result second = run(database, store, "after-restore", "Betrieb");
        assertEquals(0, second.exitCode(), second.err());
        assertTrue(second.out().contains("Contents missing: 0"), second.out());
        assertTrue(second.out().contains("Contents damaged: 0"), second.out());
        assertTrue(second.out().contains("The installation is released."), second.out());
        assertEquals(List.of("0"), query(database, "select count(*) from operations_restore_pending"));
        assertEquals(List.of("20261123T130000Z", "20261123T140000Z"),
            query(database, "select point from operations_audit_event order by id"));
    }

    @Test
    void theViewOfTheBackupServiceNamesEveryContentTheDatabaseRefersTo(@TempDir Path store) throws Exception {
        String database = database();
        UUID present = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        workOfBeforeTheFailure(database, store, present, removed);
        UUID company = UUID.fromString(query(database, "select id from platform_company").getFirst());
        UUID account = UUID.fromString(query(database, "select id from identity_account").getFirst());
        UUID image = UUID.randomUUID();
        update(database,
            "insert into file_image (id, company_id, owner, media_type, size, sha256, created_at, created_by) values ('" + image
                + "', '" + company + "', 'wiki', 'image/png', 7, '" + sha256("Bild") + "', now(), '" + account + "')",
            // A removed attachment whose content was deleted is no longer part of a backup point.
            "update file_attachment set removed_at = now(), removed_by = '" + account + "', purged_at = now() where id = '" + removed + "'");

        assertEquals(List.of(image.toString(), present.toString()).stream().sorted().toList(),
            query(database, "select id::text from operations_backup_content order by id::text"));
    }
}
