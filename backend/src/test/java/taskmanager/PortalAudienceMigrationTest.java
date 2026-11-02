package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Migrations V0041 to V0045 (TASK-079, DEC-109; TASK-080, DEC-110): existing posts, sent mail answers and attachments visible to
 * the customer get the organization they were written for, as far as their history tells it for certain, and none otherwise
 * (Holger's choice "Beheben, unklare Altdaten verbergen"). Both an upgrade from V0040 and an installation that already applied
 * V0041 and V0042 end with the same audience. Synthetic rows only.
 */
class PortalAudienceMigrationTest {
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void anUpgradeTakesTheAudienceFromTheHistory() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        migrate(url, "0040");
        Scenario rows = new Scenario(db(url));
        migrate(url, null);
        rows.assertAudience();
    }

    @Test
    void anInstallationThatAppliedTheFirstFillIsCorrected() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        migrate(url, "0040");
        Scenario rows = new Scenario(db(url));
        migrate(url, "0042");
        // The first fill of V0041 and V0042 took the customer of today (finding F1 of docs/REVIEW_TASK-079_PR-239.md).
        assertEquals(rows.suedtechnik, organization(rows.db, "helpdesk_portal_post", rows.supportBefore));
        assertEquals(rows.suedtechnik, organization(rows.db, "file_attachment", rows.switchedBefore));
        assertEquals(rows.suedtechnik, rows.db.queryForObject("select customer_id from helpdesk_reply where id = ?", UUID.class, rows.replyBefore));
        migrate(url, null);
        rows.assertAudience();
    }

    /** One ticket that belonged to Nordwerk and moved to Südtechnik at {@code T0 + 2h}, together with its contact Lea. */
    private static final class Scenario {
        final JdbcTemplate db;
        final UUID company = UUID.randomUUID(), project = UUID.randomUUID(), ticket = UUID.randomUUID(), loose = UUID.randomUUID();
        final UUID nordwerk = UUID.randomUUID(), suedtechnik = UUID.randomUUID(), agent = UUID.randomUUID(), contact = UUID.randomUUID();
        final UUID mailbox = UUID.randomUUID(), lea = UUID.randomUUID();
        final UUID customerPost, supportBefore, supportAfter, supportAtChange, loosePost;
        final UUID replyBefore, replyAfter, replyNearChange;
        final UUID upload, mailed, switchedBefore, switchedAfter, switchedNearChange, neverSwitched, released, internal;

        Scenario(JdbcTemplate db) {
            this.db = db;
            for (UUID customer : new UUID[] { nordwerk, suedtechnik }) {
                db.update("insert into helpdesk_customer (id, company_id, name, version, created_at, created_by, updated_at, updated_by) "
                    + "values (?, ?, ?, 1, now(), ?, now(), ?)", customer, company, "Kunde " + customer, agent, agent);
            }
            db.update("insert into helpdesk_contact (id, company_id, customer_id, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (?, ?, ?, 'Lea', 2, now(), ?, now(), ?)", contact, company, suedtechnik, agent, agent);
            contactEvent("contact.created", nordwerk, T0);
            contactEvent("contact.changed", suedtechnik, at(120));
            db.update("insert into identity_account (id, username, password_hash, password_change_required, created_at, updated_at, kind, organization_id) "
                + "values (?, 'lea.migration', 'x', false, now(), now(), 'portal', ?)", lea, nordwerk);
            for (UUID id : new UUID[] { ticket, loose }) {
                db.update("insert into ticket (id, company_id, project_id, number, title, type, status, configuration_version, version, created_at, "
                    + "created_by, updated_at, updated_by) values (?, ?, ?, ?, 'Anfrage', 'task', 'new', 1, 1, ?, ?, ?, ?)",
                    id, company, project, id.equals(ticket) ? 1 : 2, Timestamp.from(T0), agent, Timestamp.from(T0), agent);
            }
            // The ticket belongs to Südtechnik now; Nordwerk was its customer until T0 + 2h.
            db.update("insert into helpdesk_ticket_customer (ticket_id, company_id, project_id, customer_id) values (?, ?, ?, ?)",
                ticket, company, project, suedtechnik);
            customerChange(null, nordwerk, T0);
            customerChange(nordwerk, suedtechnik, at(120));
            db.update("""
                    insert into helpdesk_mailbox (id, company_id, name, enabled, project_id, ticket_type, imap_host, imap_port, imap_security,
                        username, folder, smtp_host, smtp_port, smtp_security, address, max_bytes, version, created_at, created_by, updated_at, updated_by)
                    values (?, ?, 'Postfach', false, ?, 'task', 'imap.example', 993, 'tls', 'support', 'INBOX', 'smtp.example', 465, 'tls',
                        'support@firma.example', 1000000, 1, now(), ?, now(), ?)
                    """, mailbox, company, project, agent, agent);

            customerPost = post(ticket, "customer", lea, at(60));
            supportBefore = post(ticket, "support", agent, at(60));
            supportAfter = post(ticket, "support", agent, at(180));
            supportAtChange = post(ticket, "support", agent, at(120));
            loosePost = post(loose, "support", agent, at(60));
            replyBefore = reply(at(60));
            replyAfter = reply(at(180));
            replyNearChange = reply(at(120).plusSeconds(20));

            upload = file(lea, "customer");
            mailed = file(agent, "customer");
            db.update("""
                    insert into helpdesk_message (id, company_id, mailbox_id, project_id, uid_validity, uid, received_at, size_bytes, state, ticket_id,
                        customer_id, attachments, version)
                    values (?, ?, ?, ?, 1, 1, now(), 10, 'ticket', ?, ?, cast(? as jsonb), 1)
                    """, UUID.randomUUID(), company, mailbox, project, ticket, nordwerk,
                    "[{\"name\":\"a.pdf\",\"size\":3,\"attachmentId\":\"" + mailed + "\",\"refusal\":null}]");
            switchedBefore = file(agent, "customer");
            fileEvent(switchedBefore, "attachment.visibility", "customer", at(60));
            switchedAfter = file(agent, "customer");
            fileEvent(switchedAfter, "attachment.visibility", "internal", at(30));
            fileEvent(switchedAfter, "attachment.visibility", "customer", at(180));
            switchedNearChange = file(agent, "customer");
            fileEvent(switchedNearChange, "attachment.visibility", "customer", at(120).minusSeconds(30));
            neverSwitched = file(agent, "customer");
            released = file(agent, "customer");
            fileEvent(released, "attachment.visibility", "customer", at(60));
            fileEvent(released, "attachment.audience", suedtechnik.toString(), at(200));
            internal = file(agent, "internal");
            db.update("insert into helpdesk_portal_seen (account_id, ticket_id, seen_at) values (?, ?, ?)", lea, ticket, Timestamp.from(at(150)));
        }

        void assertAudience() {
            assertEquals(nordwerk, organization(db, "helpdesk_portal_post", customerPost), "a post of a portal account: its organization");
            assertEquals(nordwerk, organization(db, "helpdesk_portal_post", supportBefore), "an answer before the change: the customer then");
            assertEquals(suedtechnik, organization(db, "helpdesk_portal_post", supportAfter), "an answer after the change: the customer then");
            assertNull(organization(db, "helpdesk_portal_post", supportAtChange), "an answer at the time of the change is unclear");
            assertNull(organization(db, "helpdesk_portal_post", loosePost), "no customer, no portal");
            assertEquals(nordwerk, customer(replyBefore), "a mail answer before the contact moved: the organization then");
            assertEquals(suedtechnik, customer(replyAfter), "a mail answer after the contact moved: the organization then");
            assertNull(customer(replyNearChange), "a mail answer within a minute of the move is unclear");
            assertEquals(nordwerk, organization(db, "file_attachment", upload), "an upload of a portal account: its organization");
            assertEquals(nordwerk, organization(db, "file_attachment", mailed), "a file of a mail: the organization of the mail");
            assertEquals(nordwerk, organization(db, "file_attachment", switchedBefore), "switched before the change: the customer then");
            assertEquals(suedtechnik, organization(db, "file_attachment", switchedAfter), "the newest switch decides");
            assertNull(organization(db, "file_attachment", switchedNearChange), "switched within a minute of the change is unclear");
            assertNull(organization(db, "file_attachment", neverSwitched), "no source, no portal until a release");
            assertEquals(suedtechnik, organization(db, "file_attachment", released), "an explicit release names its organization");
            assertNull(organization(db, "file_attachment", internal), "an internal file has none");
            // V0045 (TASK-080, F2): the answers of the support take positions in the order of their times; a mark covers what was
            // before its time.
            assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), List.of(position(supportBefore), replyPosition(replyBefore), position(supportAtChange),
                replyPosition(replyNearChange), position(supportAfter), replyPosition(replyAfter)));
            assertNull(position(customerPost), "a post of the customer has none");
            assertEquals(1L, position(loosePost));
            assertEquals(6L, db.queryForObject("select position from helpdesk_portal_position where ticket_id = ?", Long.class, ticket));
            assertEquals(4L, db.queryForObject("select seen_position from helpdesk_portal_seen where ticket_id = ?", Long.class, ticket));
        }

        private Long position(UUID post) {
            return db.queryForObject("select position from helpdesk_portal_post where id = ?", Long.class, post);
        }

        private Long replyPosition(UUID reply) {
            return db.queryForObject("select portal_position from helpdesk_reply where id = ?", Long.class, reply);
        }

        private UUID customer(UUID reply) {
            return db.queryForObject("select customer_id from helpdesk_reply where id = ?", UUID.class, reply);
        }

        private void customerChange(UUID from, UUID to, Instant when) {
            db.update("insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value) "
                + "values (?, 1, ?, ?, 'customer', ?, ?)", ticket, Timestamp.from(when), agent, reference(from), reference(to));
        }

        private void contactEvent(String operation, UUID customer, Instant when) {
            db.update("insert into helpdesk_audit_event (company_id, target_type, target_id, operation, detail, actor_account_id, occurred_at) "
                + "values (?, 'contact', ?, ?, ?, ?, ?)", company, contact, operation,
                "{\"name\":\"Lea\",\"customerId\":\"" + customer + "\",\"addresses\":[\"lea@nordwerk.example\"]}", agent, Timestamp.from(when));
        }

        private void fileEvent(UUID file, String operation, String detail, Instant when) {
            db.update("insert into file_audit_event (company_id, attachment_id, ticket_id, operation, detail, actor_account_id, occurred_at) "
                + "values (?, ?, ?, ?, ?, ?, ?)", company, file, ticket, operation, detail, agent, Timestamp.from(when));
        }

        private UUID post(UUID on, String kind, UUID author, Instant when) {
            UUID id = UUID.randomUUID();
            db.update("insert into helpdesk_portal_post (id, company_id, project_id, ticket_id, author_kind, author_id, body, created_at) "
                + "values (?, ?, ?, ?, ?, ?, 'Text', ?)", id, company, project, on, kind, author, Timestamp.from(when));
            return id;
        }

        private UUID reply(Instant when) {
            UUID id = UUID.randomUUID();
            db.update("""
                    insert into helpdesk_reply (id, company_id, project_id, ticket_id, mailbox_id, contact_id, from_address, to_address, subject, body,
                        message_id, state, job_id, sends, sent_at, created_at, created_by, updated_at, updated_by, version)
                    values (?, ?, ?, ?, ?, ?, 'support@firma.example', 'lea@nordwerk.example', 'Antwort', 'Text', ?, 'sent', ?, 1, ?, ?, ?, ?, ?, 1)
                    """, id, company, project, ticket, mailbox, contact, "<" + id + "@firma.example>", UUID.randomUUID(),
                    Timestamp.from(when), Timestamp.from(when), agent, Timestamp.from(when), agent);
            return id;
        }

        private UUID file(UUID by, String visibility) {
            UUID id = UUID.randomUUID();
            db.update("insert into file_attachment (id, company_id, ticket_id, project_id, name, size_bytes, sha256, uploaded_at, uploaded_by, visibility) "
                + "values (?, ?, ?, ?, 'datei.pdf', 3, ?, ?, ?, ?)", id, company, ticket, project, "0".repeat(64), Timestamp.from(T0), by, visibility);
            return id;
        }
    }

    private static Instant at(long minutes) {
        return T0.plusSeconds(minutes * 60);
    }

    private static String reference(UUID id) {
        return id == null ? null : "{\"id\":\"" + id + "\",\"name\":\"Kunde\"}";
    }

    private static void migrate(String url, String target) {
        var configuration = Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password());
        (target == null ? configuration : configuration.target(target)).load().migrate();
    }

    private static JdbcTemplate db(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
    }

    private static UUID organization(JdbcTemplate db, String table, UUID id) {
        return db.queryForObject("select organization_id from " + table + " where id = ?", UUID.class, id);
    }
}
