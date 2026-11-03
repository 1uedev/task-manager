package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Migrations V0041 to V0051 (TASK-079, DEC-109; TASK-080, DEC-110; TASK-083, DEC-113; TASK-084, DEC-114): existing posts, sent mail answers and
 * attachments visible to the customer get the organization they were written for, as far as their history tells it for
 * certain, and none otherwise (Holger's choice "Beheben, unklare Altdaten verbergen"), without a time window (Holger's choice
 * "Ja, so beheben"). An upgrade from V0040, an installation that already applied V0041 and V0042 and one that already applied
 * V0043 to V0045 end with the same audience; what the application stored itself well after V0043 stays, whatever the time
 * zone of the sessions. Synthetic rows only: the history is written as it ends up in the tables, so a change "held" for 70
 * seconds is a change whose recorded time lies 70 seconds before the answer; no transaction is held here. The order of locks
 * and times in the running application is covered by the API tests of TASK-080 and argued in backend/README.md.
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

    @Test
    void anInstallationThatAppliedTheWindowIsCorrectedAndKeepsWhatTheApplicationStored() throws Exception {
        String url = TestDatabase.createEmptyDatabase();
        migrate(url, "0040");
        Scenario rows = new Scenario(db(url));
        migrate(url, "0042");
        // An answer of TASK-079 to a contact created a moment before: the application stored its organization, V0043 took it
        // away because of the window (finding F2 of docs/REVIEW_TASK-080_PR-241.md).
        Instant recently = Instant.now().minusSeconds(30);
        UUID kim = rows.contact("Kim", rows.nordwerk, recently.minusSeconds(10));
        UUID beforeWindow = rows.reply(kim, recently, rows.nordwerk);
        rows.db.update("update helpdesk_reply set ticket_id = ? where id = ?", rows.loose, beforeWindow);
        migrate(url, "0045");
        // The window of V0043 and V0044 (finding F1): a change held longer than a minute counted as seen, a creation next to an
        // answer as unclear.
        assertEquals(rows.suedtechnik, rows.customer(rows.replyHeld));
        assertEquals(rows.suedtechnik, organization(rows.db, "file_attachment", rows.switchedHeld));
        assertNull(rows.customer(rows.replyFreshContact));
        assertNull(rows.customer(beforeWindow));
        // Answers the application of TASK-080 writes keep what it stored, also when the contact had moved before, once they lie
        // behind the boundary of V0051 (TASK-084); before it they are computed again by the rule.
        UUID early = rows.reply(rows.contact, Instant.now().plusSeconds(1), rows.suedtechnik);
        UUID stored = rows.reply(rows.contact, Instant.now().plus(Duration.ofHours(30)), rows.suedtechnik);
        UUID storedWithout = rows.reply(rows.contact, Instant.now().plus(Duration.ofHours(31)), null);
        migrate(url, null);
        rows.assertAudience();
        assertEquals(rows.nordwerk, rows.customer(beforeWindow), "an answer of TASK-079 is computed again");
        assertNull(rows.customer(early), "an answer within the boundary is computed again: the contact had moved before");
        assertEquals(rows.suedtechnik, rows.customer(stored), "an answer stored by the application since V0043 stays");
        assertNull(rows.customer(storedWithout), "also when the application stored none");
    }

    /**
     * V0051 (TASK-084, finding F1 of docs/REVIEW_TASK-083_PR-245.md): the time V0043 was installed is stored without time zone.
     * Whatever zone the session had then and has now, an old answer is computed again and an answer stored well after the
     * upgrade keeps its organization. Each pair runs V0043 in the first zone and the later migrations in the second.
     */
    @Test
    void theBoundaryHoldsWhateverTheTimeZoneOfTheSessions() throws Exception {
        String[][] pairs = { { "UTC", "Europe/Berlin" }, { "Europe/Berlin", "UTC" }, { "Etc/GMT+12", "Pacific/Kiritimati" },
            { "Pacific/Kiritimati", "Etc/GMT+12" } };
        for (String[] zones : pairs) {
            String url = TestDatabase.createEmptyDatabase();
            migrate(url, "0040", zones[0]);
            Scenario rows = new Scenario(db(url));
            migrate(url, "0042", zones[0]);
            Instant now = Instant.now();
            // Ten minutes before the upgrade: an answer to Lea after she moved, which V0043 gives Südtechnik, and one to a contact
            // created just before, which V0043 hides.
            UUID unclear = rows.reply(rows.contact, now.minus(Duration.ofMinutes(10)), rows.suedtechnik);
            UUID kim = rows.contact("Kim", rows.nordwerk, now.minus(Duration.ofMinutes(11)));
            UUID safe = rows.reply(kim, now.minus(Duration.ofMinutes(10)).plusSeconds(20), rows.nordwerk);
            rows.db.update("update helpdesk_reply set ticket_id = ? where id in (?, ?)", rows.loose, unclear, safe);
            migrate(url, "0043", zones[0]);
            assertEquals(rows.suedtechnik, rows.customer(unclear), String.join(" to ", zones));
            UUID wellAfter = rows.reply(rows.contact, Instant.now().plus(Duration.ofHours(27)), rows.suedtechnik);
            UUID wellAfterWithout = rows.reply(kim, Instant.now().plus(Duration.ofHours(28)), null);
            rows.db.update("update helpdesk_reply set ticket_id = ? where id in (?, ?)", rows.loose, wellAfter, wellAfterWithout);
            migrate(url, null, zones[1]);
            String pair = String.join(" to ", zones);
            assertNull(rows.customer(unclear), pair + ": an old answer after the move is unclear");
            assertEquals(rows.nordwerk, rows.customer(safe), pair + ": an old safe answer is restored");
            assertEquals(rows.suedtechnik, rows.customer(wellAfter), pair + ": an answer stored well after the upgrade stays");
            assertNull(rows.customer(wellAfterWithout), pair + ": also when the application stored none");
            rows.assertAudience();
        }
    }

    /** One ticket that belonged to Nordwerk and moved to Südtechnik at {@code T0 + 2h}, together with its contact Lea. */
    private static final class Scenario {
        final JdbcTemplate db;
        final UUID company = UUID.randomUUID(), project = UUID.randomUUID(), ticket = UUID.randomUUID(), loose = UUID.randomUUID();
        final UUID nordwerk = UUID.randomUUID(), suedtechnik = UUID.randomUUID(), agent = UUID.randomUUID(), contact = UUID.randomUUID();
        final UUID mailbox = UUID.randomUUID(), lea = UUID.randomUUID();
        final UUID customerPost, supportBefore, supportAfter, supportAtChange, loosePost;
        final UUID max = UUID.randomUUID();
        final UUID replyBefore, replyAfter, replyNearChange, replyHeld, replyFreshContact, replyRenamed;
        final UUID upload, mailed, switchedBefore, switchedAfter, switchedNearChange, switchedHeld, neverSwitched, released, internal;

        Scenario(JdbcTemplate db) {
            this.db = db;
            for (UUID customer : new UUID[] { nordwerk, suedtechnik }) {
                db.update("insert into helpdesk_customer (id, company_id, name, version, created_at, created_by, updated_at, updated_by) "
                    + "values (?, ?, ?, 1, now(), ?, now(), ?)", customer, company, "Kunde " + customer, agent, agent);
            }
            insertContact(contact, "Lea", suedtechnik);
            contactEvent(contact, "contact.created", nordwerk, T0);
            contactEvent(contact, "contact.changed", suedtechnik, at(120));
            // Max of Nordwerk is created at T0 + 59 min and only renamed at T0 + 100 min.
            insertContact(max, "Max", nordwerk);
            contactEvent(max, "contact.created", nordwerk, at(59));
            contactEvent(max, "contact.changed", nordwerk, at(100));
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
            // The move of Lea took its time at T0 + 2h but was held for 70 seconds; the answer still read Nordwerk (F1).
            replyHeld = reply(at(120).plusSeconds(70));
            replyFreshContact = reply(max, at(59).plusSeconds(20), null);
            replyRenamed = reply(max, at(100).plusSeconds(10), null);

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
            switchedHeld = file(agent, "customer");
            fileEvent(switchedHeld, "attachment.visibility", "customer", at(120).plusSeconds(70));
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
            assertNull(customer(replyAfter), "a mail answer after the contact moved is unclear");
            assertNull(customer(replyNearChange), "so is one shortly after the move");
            assertNull(customer(replyHeld), "and one after a move that was held longer than a minute");
            assertEquals(nordwerk, customer(replyFreshContact), "a mail answer to a contact created just before: its organization");
            assertEquals(nordwerk, customer(replyRenamed), "a change of the name keeps the organization");
            assertEquals(nordwerk, organization(db, "file_attachment", upload), "an upload of a portal account: its organization");
            assertEquals(nordwerk, organization(db, "file_attachment", mailed), "a file of a mail: the organization of the mail");
            assertEquals(nordwerk, organization(db, "file_attachment", switchedBefore), "switched before the change: the customer then");
            assertNull(organization(db, "file_attachment", switchedAfter), "switched after the customer changed is unclear");
            assertEquals(nordwerk, organization(db, "file_attachment", switchedNearChange), "switched before the change: the customer then");
            assertNull(organization(db, "file_attachment", switchedHeld), "switched after a change that was held is unclear");
            assertNull(organization(db, "file_attachment", neverSwitched), "no source, no portal until a release");
            assertEquals(suedtechnik, organization(db, "file_attachment", released), "an explicit release names its organization");
            assertNull(organization(db, "file_attachment", internal), "an internal file has none");
            // V0045 (TASK-080, F2): the answers of the support take positions in the order of their times; a mark covers what was
            // before its time.
            assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L), List.of(replyPosition(replyFreshContact), position(supportBefore),
                replyPosition(replyBefore), replyPosition(replyRenamed), position(supportAtChange), replyPosition(replyNearChange),
                replyPosition(replyHeld), position(supportAfter), replyPosition(replyAfter)));
            assertNull(position(customerPost), "a post of the customer has none");
            assertEquals(1L, position(loosePost));
            assertEquals(9L, db.queryForObject("select position from helpdesk_portal_position where ticket_id = ?", Long.class, ticket));
            assertEquals(7L, db.queryForObject("select seen_position from helpdesk_portal_seen where ticket_id = ?", Long.class, ticket));
        }

        private Long position(UUID post) {
            return db.queryForObject("select position from helpdesk_portal_post where id = ?", Long.class, post);
        }

        private Long replyPosition(UUID reply) {
            return db.queryForObject("select portal_position from helpdesk_reply where id = ?", Long.class, reply);
        }

        UUID customer(UUID reply) {
            return db.queryForObject("select customer_id from helpdesk_reply where id = ?", UUID.class, reply);
        }

        private void customerChange(UUID from, UUID to, Instant when) {
            db.update("insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value) "
                + "values (?, 1, ?, ?, 'customer', ?, ?)", ticket, Timestamp.from(when), agent, reference(from), reference(to));
        }

        UUID contact(String name, UUID customer, Instant created) {
            UUID id = UUID.randomUUID();
            insertContact(id, name, customer);
            contactEvent(id, "contact.created", customer, created);
            return id;
        }

        private void insertContact(UUID id, String name, UUID customer) {
            db.update("insert into helpdesk_contact (id, company_id, customer_id, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (?, ?, ?, ?, 1, now(), ?, now(), ?)", id, company, customer, name, agent, agent);
        }

        private void contactEvent(UUID target, String operation, UUID customer, Instant when) {
            db.update("insert into helpdesk_audit_event (company_id, target_type, target_id, operation, detail, actor_account_id, occurred_at) "
                + "values (?, 'contact', ?, ?, ?, ?, ?)", company, target, operation,
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
            return reply(contact, when, null);
        }

        /** A sent mail answer; {@code customer} is what the application stored since V0041 and is ignored before. */
        UUID reply(UUID to, Instant when, UUID customer) {
            UUID id = UUID.randomUUID();
            boolean stored = db.queryForObject("select count(*) from information_schema.columns where table_name = 'helpdesk_reply' "
                + "and column_name = 'customer_id'", Integer.class) > 0;
            db.update("""
                    insert into helpdesk_reply (id, company_id, project_id, ticket_id, mailbox_id, contact_id, from_address, to_address, subject, body,
                        message_id, state, job_id, sends, sent_at, created_at, created_by, updated_at, updated_by, version)
                    values (?, ?, ?, ?, ?, ?, 'support@firma.example', 'lea@nordwerk.example', 'Antwort', 'Text', ?, 'sent', ?, 1, ?, ?, ?, ?, ?, 1)
                    """, id, company, project, ticket, mailbox, to, "<" + id + "@firma.example>", UUID.randomUUID(),
                    Timestamp.from(when), Timestamp.from(when), agent, Timestamp.from(when), agent);
            if (stored) {
                db.update("update helpdesk_reply set customer_id = ? where id = ?", customer, id);
            }
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
        migrate(url, target, null);
    }

    /** Migrates with the session in {@code zone}, which Flyway uses for the time it records in its history. */
    private static void migrate(String url, String target, String zone) {
        var configuration = Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password());
        if (zone != null) {
            configuration = configuration.initSql("set time zone '" + zone + "'");
        }
        (target == null ? configuration : configuration.target(target)).load().migrate();
    }

    private static JdbcTemplate db(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
    }

    private static UUID organization(JdbcTemplate db, String table, UUID id) {
        return db.queryForObject("select organization_id from " + table + " where id = ?", UUID.class, id);
    }
}
