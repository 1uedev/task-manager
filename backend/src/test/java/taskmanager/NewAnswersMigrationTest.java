package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Migration V0052 (TASK-067, DEC-097; R2 of docs/REVIEW_TASK-088_PR-261.md, TASK-091): mails that already belonged to a ticket
 * before the upgrade, whether they opened it or joined it later, stay unmarked and are no new answers afterwards. A fill of
 * {@code joined_at} for old mails would make this test fail. Synthetic data only.
 */
class NewAnswersMigrationTest {
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void mailsThatBelongedToATicketBeforeTheUpgradeStayUnmarked() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        migrate(url, "0051");
        JdbcTemplate db = db(url);
        UUID company = UUID.randomUUID(), project = UUID.randomUUID(), ticket = UUID.randomUUID(), mailbox = UUID.randomUUID();
        UUID agent = UUID.randomUUID(), opening = UUID.randomUUID(), answer = UUID.randomUUID(), waiting = UUID.randomUUID();
        db.update("insert into ticket (id, company_id, project_id, number, title, type, status, configuration_version, version, created_at, "
            + "created_by, updated_at, updated_by) values (?, ?, ?, 1, 'Anfrage', 'task', 'new', 1, 1, ?, ?, ?, ?)",
            ticket, company, project, Timestamp.from(T0), agent, Timestamp.from(T0), agent);
        db.update("""
                insert into helpdesk_mailbox (id, company_id, name, enabled, project_id, ticket_type, imap_host, imap_port, imap_security,
                    username, folder, smtp_host, smtp_port, smtp_security, address, max_bytes, version, created_at, created_by, updated_at, updated_by)
                values (?, ?, 'Postfach', false, ?, 'task', 'imap.example', 993, 'tls', 'support', 'INBOX', 'smtp.example', 465, 'tls',
                    'support@firma.example', 1000000, 1, now(), ?, now(), ?)
                """, mailbox, company, project, agent, agent);
        // The mail that opened the ticket, an answer that joined it later and a mail still waiting in the review queue.
        message(db, opening, company, mailbox, project, 1, "ticket", ticket, T0);
        message(db, answer, company, mailbox, project, 2, "ticket", ticket, T0.plusSeconds(3600));
        message(db, waiting, company, mailbox, project, 3, "review", null, T0.plusSeconds(7200));

        migrate(url, null);

        for (UUID id : new UUID[] { opening, answer, waiting }) {
            Map<String, Object> row = db.queryForMap("select joined_at, seen_at, seen_by from helpdesk_message where id = ?", id);
            assertEquals(java.util.Arrays.asList(null, null, null), java.util.Arrays.asList(row.get("joined_at"), row.get("seen_at"),
                row.get("seen_by")), id.toString());
        }
        // The query of the list of new answers finds none of them.
        assertEquals(0L, db.queryForObject("select count(*) from helpdesk_message where company_id = ? and joined_at is not null and seen_at is null",
            Long.class, company));
        // A marking without a time of joining is refused by the database.
        assertThrows(DataIntegrityViolationException.class,
            () -> db.update("update helpdesk_message set seen_at = now(), seen_by = ? where id = ?", agent, answer));
    }

    private static void message(JdbcTemplate db, UUID id, UUID company, UUID mailbox, UUID project, long uid, String state, UUID ticket,
            Instant received) {
        db.update("""
                insert into helpdesk_message (id, company_id, mailbox_id, project_id, uid_validity, uid, received_at, size_bytes, state, ticket_id,
                    attachments, version)
                values (?, ?, ?, ?, 1, ?, ?, 10, ?, ?, cast('[]' as jsonb), 1)
                """, id, company, mailbox, project, uid, Timestamp.from(received), state, ticket);
    }

    private static void migrate(String url, String target) {
        var configuration = Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password());
        (target == null ? configuration : configuration.target(target)).load().migrate();
    }

    private static JdbcTemplate db(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
    }
}
