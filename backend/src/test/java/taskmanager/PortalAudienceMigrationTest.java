package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Migrations V0041 and V0042 (TASK-079, DEC-109): existing posts, sent mail answers and attachments visible to the customer get the
 * organization they were most likely written for. Synthetic rows only.
 */
class PortalAudienceMigrationTest {
    @Test
    void existingRowsKeepTheOrganizationTheyWereWrittenFor() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).target("0040").load().migrate();
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
        UUID company = UUID.randomUUID(), project = UUID.randomUUID(), ticket = UUID.randomUUID(), loose = UUID.randomUUID();
        UUID nordwerk = UUID.randomUUID(), suedtechnik = UUID.randomUUID(), agent = UUID.randomUUID();
        for (UUID customer : new UUID[] { nordwerk, suedtechnik }) {
            db.update("insert into helpdesk_customer (id, company_id, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (?, ?, ?, 1, now(), ?, now(), ?)", customer, company, "Kunde " + customer, agent, agent);
        }
        UUID contact = UUID.randomUUID();
        db.update("insert into helpdesk_contact (id, company_id, customer_id, name, version, created_at, created_by, updated_at, updated_by) "
            + "values (?, ?, ?, 'Lea', 1, now(), ?, now(), ?)", contact, company, nordwerk, agent, agent);
        UUID lea = UUID.randomUUID();
        db.update("insert into identity_account (id, username, password_hash, password_change_required, created_at, updated_at, kind, organization_id) "
            + "values (?, 'lea.migration', 'x', false, now(), now(), 'portal', ?)", lea, nordwerk);
        // The ticket belongs to Südtechnik now; Nordwerk was its customer before.
        db.update("insert into helpdesk_ticket_customer (ticket_id, company_id, project_id, customer_id) values (?, ?, ?, ?)",
            ticket, company, project, suedtechnik);
        UUID mailbox = UUID.randomUUID();
        db.update("""
                insert into helpdesk_mailbox (id, company_id, name, enabled, project_id, ticket_type, imap_host, imap_port, imap_security,
                    username, folder, smtp_host, smtp_port, smtp_security, address, max_bytes, version, created_at, created_by, updated_at, updated_by)
                values (?, ?, 'Postfach', false, ?, 'task', 'imap.example', 993, 'tls', 'support', 'INBOX', 'smtp.example', 465, 'tls',
                    'support@firma.example', 1000000, 1, now(), ?, now(), ?)
                """, mailbox, company, project, agent, agent);

        UUID customerPost = post(db, company, project, ticket, "customer", lea);
        UUID supportPost = post(db, company, project, ticket, "support", agent);
        UUID loosePost = post(db, company, project, loose, "support", agent);
        UUID reply = UUID.randomUUID();
        db.update("""
                insert into helpdesk_reply (id, company_id, project_id, ticket_id, mailbox_id, contact_id, from_address, to_address, subject, body,
                    message_id, state, job_id, sends, sent_at, created_at, created_by, updated_at, updated_by, version)
                values (?, ?, ?, ?, ?, ?, 'support@firma.example', 'lea@nordwerk.example', 'Antwort', 'Text', ?, 'sent', ?, 1, now(), now(), ?, now(), ?, 1)
                """, reply, company, project, ticket, mailbox, contact, "<" + reply + "@firma.example>", UUID.randomUUID(), agent, agent);
        UUID upload = file(db, company, project, ticket, lea, "customer");
        UUID mailed = file(db, company, project, ticket, agent, "customer");
        UUID released = file(db, company, project, ticket, agent, "customer");
        UUID internal = file(db, company, project, ticket, agent, "internal");
        db.update("""
                insert into helpdesk_message (id, company_id, mailbox_id, project_id, uid_validity, uid, received_at, size_bytes, state, ticket_id,
                    customer_id, attachments, version)
                values (?, ?, ?, ?, 1, 1, now(), 10, 'ticket', ?, ?, cast(? as jsonb), 1)
                """, UUID.randomUUID(), company, mailbox, project, ticket, nordwerk,
                "[{\"name\":\"a.pdf\",\"size\":3,\"attachmentId\":\"" + mailed + "\",\"refusal\":null}]");

        Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password()).load().migrate();

        assertEquals(nordwerk, organization(db, "helpdesk_portal_post", customerPost), "a post of a portal account: its organization");
        assertEquals(suedtechnik, organization(db, "helpdesk_portal_post", supportPost), "an answer of the support: the current customer");
        assertNull(organization(db, "helpdesk_portal_post", loosePost), "no customer, no portal");
        assertEquals(nordwerk, db.queryForObject("select customer_id from helpdesk_reply where id = ?", UUID.class, reply), "the contact's organization");
        assertEquals(nordwerk, organization(db, "file_attachment", upload), "an upload of a portal account: its organization");
        assertEquals(nordwerk, organization(db, "file_attachment", mailed), "a file of a mail: the organization of the mail");
        assertEquals(suedtechnik, organization(db, "file_attachment", released), "a released file: the current customer");
        assertNull(organization(db, "file_attachment", internal), "an internal file has none");
    }

    private static UUID post(JdbcTemplate db, UUID company, UUID project, UUID ticket, String kind, UUID author) {
        UUID id = UUID.randomUUID();
        db.update("insert into helpdesk_portal_post (id, company_id, project_id, ticket_id, author_kind, author_id, body, created_at) "
            + "values (?, ?, ?, ?, ?, ?, 'Text', now())", id, company, project, ticket, kind, author);
        return id;
    }

    private static UUID file(JdbcTemplate db, UUID company, UUID project, UUID ticket, UUID by, String visibility) {
        UUID id = UUID.randomUUID();
        db.update("insert into file_attachment (id, company_id, ticket_id, project_id, name, size_bytes, sha256, uploaded_at, uploaded_by, visibility) "
            + "values (?, ?, ?, ?, 'datei.pdf', 3, ?, now(), ?, ?)", id, company, ticket, project, "0".repeat(64), by, visibility);
        return id;
    }

    private static UUID organization(JdbcTemplate db, String table, UUID id) {
        return db.queryForObject("select organization_id from " + table + " where id = ?", UUID.class, id);
    }
}
