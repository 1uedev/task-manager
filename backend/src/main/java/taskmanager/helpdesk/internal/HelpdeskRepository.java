package taskmanager.helpdesk.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The tables of the helpdesk module. Nobody else reads them. */
@Repository
class HelpdeskRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MAILBOX_COLUMNS = "id, company_id, name, enabled, project_id, ticket_type, imap_host, imap_port, "
        + "imap_security, username, password_cipher, folder, smtp_host, smtp_port, smtp_security, address, authserv_id, max_bytes, "
        + "uid_validity, last_uid, source, compare_until, polled_at, succeeded_at, error_code, error_at, notice, notice_at, version, "
        + "created_at, created_by, updated_at, updated_by";
    private static final String MESSAGE_COLUMNS = "id, company_id, mailbox_id, project_id, uid_validity, uid, message_id, received_at, "
        + "sent_at, from_address, from_name, to_addresses, cc_addresses, subject, body, size_bytes, attachments::text as attachments, "
        + "raw is not null as has_raw, state, reason, ticket_id, contact_id, customer_id, processed_at, processed_by, discard_reason, related_ticket_id, version";

    private final JdbcClient jdbc;

    HelpdeskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------ customers

    List<Customer> customers(UUID companyId) {
        return jdbc.sql("select * from helpdesk_customer where company_id = ? order by lower(name), id").param(companyId)
            .query((rs, row) -> customer(rs)).list();
    }

    Optional<Customer> customer(UUID companyId, UUID id) {
        return jdbc.sql("select * from helpdesk_customer where company_id = ? and id = ?").params(companyId, id)
            .query((rs, row) -> customer(rs)).optional();
    }

    Optional<Customer> lockCustomer(UUID companyId, UUID id) {
        return jdbc.sql("select * from helpdesk_customer where company_id = ? and id = ? for update").params(companyId, id)
            .query((rs, row) -> customer(rs)).optional();
    }

    boolean customerNameTaken(UUID companyId, String name, UUID except) {
        return jdbc.sql("select count(*) from helpdesk_customer where company_id = ? and lower(name) = lower(?) and id <> ?")
            .params(companyId, name, except == null ? new UUID(0, 0) : except).query(Long.class).single() > 0;
    }

    void insertCustomer(Customer customer) {
        jdbc.sql("""
                insert into helpdesk_customer (id, company_id, name, version, created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(customer.id(), customer.companyId(), customer.name(), customer.version(), ts(customer.createdAt()),
                customer.createdBy(), ts(customer.updatedAt()), customer.updatedBy()).update();
        replaceProjects(customer.id(), customer.projects());
    }

    void updateCustomer(Customer customer) {
        jdbc.sql("update helpdesk_customer set name = ?, version = ?, updated_at = ?, updated_by = ? where id = ?")
            .params(customer.name(), customer.version(), ts(customer.updatedAt()), customer.updatedBy(), customer.id()).update();
        replaceProjects(customer.id(), customer.projects());
    }

    private void replaceProjects(UUID customerId, List<UUID> projects) {
        jdbc.sql("delete from helpdesk_customer_project where customer_id = ?").param(customerId).update();
        for (UUID project : projects) {
            jdbc.sql("insert into helpdesk_customer_project (customer_id, project_id) values (?, ?)").params(customerId, project).update();
        }
    }

    private Customer customer(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<UUID> projects = jdbc.sql("select project_id from helpdesk_customer_project where customer_id = ? order by project_id")
            .param(id).query(UUID.class).list();
        return new Customer(id, rs.getObject("company_id", UUID.class), rs.getString("name"), projects, rs.getLong("version"),
            instant(rs, "created_at"), rs.getObject("created_by", UUID.class), instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class));
    }

    // ------------------------------------------------------------------------------------------------ contacts

    List<Contact> contacts(UUID companyId, UUID customerId) {
        return jdbc.sql("select * from helpdesk_contact where company_id = ? and (cast(? as uuid) is null or customer_id = ?) order by lower(name), id")
            .params(companyId, customerId, customerId).query((rs, row) -> contact(rs)).list();
    }

    Optional<Contact> contact(UUID companyId, UUID id) {
        return jdbc.sql("select * from helpdesk_contact where company_id = ? and id = ?").params(companyId, id)
            .query((rs, row) -> contact(rs)).optional();
    }

    Optional<Contact> lockContact(UUID companyId, UUID id) {
        return jdbc.sql("select * from helpdesk_contact where company_id = ? and id = ? for update").params(companyId, id)
            .query((rs, row) -> contact(rs)).optional();
    }

    /** The contact an address belongs to, if any. */
    Optional<Contact> contactByAddress(UUID companyId, String address) {
        return jdbc.sql("""
                select c.* from helpdesk_contact c join helpdesk_contact_address a on a.contact_id = c.id
                where a.company_id = ? and a.address = ?
                """).params(companyId, address).query((rs, row) -> contact(rs)).optional();
    }

    /** Whether one of the addresses belongs to a contact of another customer than {@code customerId}, in one query. */
    boolean otherCustomerAmong(UUID companyId, UUID customerId, Collection<String> addresses) {
        if (addresses.isEmpty()) {
            return false;
        }
        return jdbc.sql("""
                select exists (select 1 from helpdesk_contact_address a join helpdesk_contact c on c.id = a.contact_id
                where a.company_id = ? and a.address = any(?) and c.customer_id <> ?)
                """).params(companyId, addresses.toArray(String[]::new), customerId).query(Boolean.class).single();
    }

    /** The addresses of the list that belong to another contact than {@code except}. */
    List<String> addressesTaken(UUID companyId, Collection<String> addresses, UUID except) {
        List<String> taken = new ArrayList<>();
        for (String address : addresses) {
            if (jdbc.sql("select count(*) from helpdesk_contact_address where company_id = ? and address = ? and contact_id <> ?")
                    .params(companyId, address, except == null ? new UUID(0, 0) : except).query(Long.class).single() > 0) {
                taken.add(address);
            }
        }
        return taken;
    }

    void insertContact(Contact contact) {
        jdbc.sql("""
                insert into helpdesk_contact (id, company_id, customer_id, name, version, created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(contact.id(), contact.companyId(), contact.customerId(), contact.name(), contact.version(),
                ts(contact.createdAt()), contact.createdBy(), ts(contact.updatedAt()), contact.updatedBy()).update();
        replaceAddresses(contact);
    }

    void updateContact(Contact contact) {
        jdbc.sql("update helpdesk_contact set customer_id = ?, name = ?, version = ?, updated_at = ?, updated_by = ? where id = ?")
            .params(contact.customerId(), contact.name(), contact.version(), ts(contact.updatedAt()), contact.updatedBy(), contact.id())
            .update();
        replaceAddresses(contact);
    }

    private void replaceAddresses(Contact contact) {
        jdbc.sql("delete from helpdesk_contact_address where contact_id = ?").param(contact.id()).update();
        for (String address : contact.addresses()) {
            jdbc.sql("insert into helpdesk_contact_address (company_id, address, contact_id) values (?, ?, ?)")
                .params(contact.companyId(), address, contact.id()).update();
        }
    }

    private Contact contact(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<String> addresses = jdbc.sql("select address from helpdesk_contact_address where contact_id = ? order by address")
            .param(id).query(String.class).list();
        return new Contact(id, rs.getObject("company_id", UUID.class), rs.getObject("customer_id", UUID.class), rs.getString("name"),
            addresses, rs.getLong("version"), instant(rs, "created_at"), rs.getObject("created_by", UUID.class), instant(rs, "updated_at"),
            rs.getObject("updated_by", UUID.class));
    }

    // ------------------------------------------------------------------------------------------------ mailboxes

    List<Mailbox> mailboxes(UUID companyId) {
        return jdbc.sql("select " + MAILBOX_COLUMNS + " from helpdesk_mailbox where company_id = ? order by lower(name), id")
            .param(companyId).query(HelpdeskRepository::mailbox).list();
    }

    /** The active mailboxes of every company, for the fetch. */
    List<UUID> enabledMailboxes() {
        return jdbc.sql("select id from helpdesk_mailbox where enabled order by id").query(UUID.class).list();
    }

    Optional<Mailbox> mailbox(UUID companyId, UUID id) {
        return jdbc.sql("select " + MAILBOX_COLUMNS + " from helpdesk_mailbox where company_id = ? and id = ?").params(companyId, id)
            .query(HelpdeskRepository::mailbox).optional();
    }

    Optional<Mailbox> mailbox(UUID id) {
        return jdbc.sql("select " + MAILBOX_COLUMNS + " from helpdesk_mailbox where id = ?").param(id)
            .query(HelpdeskRepository::mailbox).optional();
    }

    Optional<Mailbox> lockMailbox(UUID companyId, UUID id) {
        return jdbc.sql("select " + MAILBOX_COLUMNS + " from helpdesk_mailbox where company_id = ? and id = ? for update")
            .params(companyId, id).query(HelpdeskRepository::mailbox).optional();
    }

    /** Locks the mailbox for one fetch: a second fetch of the same mailbox does not wait but skips it. */
    Optional<Mailbox> lockMailboxForFetch(UUID id) {
        return jdbc.sql("select " + MAILBOX_COLUMNS + " from helpdesk_mailbox where id = ? for update skip locked").param(id)
            .query(HelpdeskRepository::mailbox).optional();
    }

    boolean mailboxNameTaken(UUID companyId, String name, UUID except) {
        return jdbc.sql("select count(*) from helpdesk_mailbox where company_id = ? and lower(name) = lower(?) and id <> ?")
            .params(companyId, name, except == null ? new UUID(0, 0) : except).query(Long.class).single() > 0;
    }

    void insertMailbox(Mailbox m) {
        jdbc.sql("""
                insert into helpdesk_mailbox (id, company_id, name, enabled, project_id, ticket_type, imap_host, imap_port,
                    imap_security, username, password_cipher, folder, smtp_host, smtp_port, smtp_security, address, authserv_id,
                    max_bytes, last_uid, version, created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                """).params(m.id(), m.companyId(), m.name(), m.enabled(), m.projectId(), m.ticketType(), m.imapHost(), m.imapPort(),
                m.imapSecurity(), m.username(), m.passwordCipher(), m.folder(), m.smtpHost(), m.smtpPort(), m.smtpSecurity(), m.address(),
                m.authservId(), m.maxBytes(), m.version(), ts(m.createdAt()), m.createdBy(), ts(m.updatedAt()), m.updatedBy()).update();
    }

    /**
     * Writes the settings. A change of server, port, account or folder starts the folder over as a new source: the intake
     * forgets where it stood, and a mail of the new source is never mistaken for one of the old (TASK-059, F3).
     */
    void updateMailbox(Mailbox m, boolean restart) {
        jdbc.sql("""
                update helpdesk_mailbox set name = ?, enabled = ?, project_id = ?, ticket_type = ?, imap_host = ?, imap_port = ?,
                    imap_security = ?, username = ?, password_cipher = ?, folder = ?, smtp_host = ?, smtp_port = ?, smtp_security = ?,
                    address = ?, authserv_id = ?, max_bytes = ?, version = ?, updated_at = ?, updated_by = ?
                where id = ?
                """).params(m.name(), m.enabled(), m.projectId(), m.ticketType(), m.imapHost(), m.imapPort(), m.imapSecurity(),
                m.username(), m.passwordCipher(), m.folder(), m.smtpHost(), m.smtpPort(), m.smtpSecurity(), m.address(), m.authservId(),
                m.maxBytes(), m.version(), ts(m.updatedAt()), m.updatedBy(), m.id()).update();
        if (restart) {
            jdbc.sql("update helpdesk_mailbox set uid_validity = null, last_uid = 0, source = source + 1, compare_until = null where id = ?")
                .param(m.id()).update();
        }
    }

    void fetchStarted(UUID id, Instant now) {
        jdbc.sql("update helpdesk_mailbox set polled_at = ? where id = ?").params(ts(now), id).update();
    }

    void fetchSucceeded(UUID id, Instant now) {
        jdbc.sql("update helpdesk_mailbox set succeeded_at = ?, error_code = null, error_at = null where id = ?").params(ts(now), id).update();
    }

    void fetchFailed(UUID id, String code, Instant now) {
        jdbc.sql("update helpdesk_mailbox set error_code = ?, error_at = ? where id = ?").params(code, ts(now), id).update();
    }

    /**
     * Moves the position on, only while the fetch still reads the current source and UIDVALIDITY; a fetch that runs while the
     * settings change or another fetch starts the folder over writes nothing and learns so from the answer (TASK-060, R1). It
     * never changes the UIDVALIDITY. The comparison of Message-IDs ends once the position passed its bound.
     */
    boolean position(UUID id, int source, long uidValidity, long lastUid) {
        return jdbc.sql("""
                update helpdesk_mailbox set last_uid = greatest(last_uid, ?),
                    compare_until = case when compare_until <= ? then null else compare_until end
                where id = ? and source = ? and uid_validity = ?
                """).params(lastUid, lastUid, id, source, uidValidity).update() == 1;
    }

    /**
     * The first fetch of a folder: it starts at {@code start}, only while the source is the one the fetch read and nobody
     * started the folder before it (TASK-060, R1).
     */
    boolean startFolder(UUID id, int source, long uidValidity, long start) {
        return jdbc.sql("""
                update helpdesk_mailbox set uid_validity = ?, last_uid = ?, compare_until = null
                where id = ? and source = ? and uid_validity is null
                """).params(uidValidity, start, id, source).update() == 1;
    }

    /**
     * A new UIDVALIDITY: the intake starts the folder over as a new source, so that no fetch that still reads the old state
     * can move the position afterwards (TASK-060, R1). Only from the state the fetch read; a second fetch that saw the same
     * change finds the new source and writes nothing. It leaves a notice and compares Message-IDs up to {@code compareUntil}
     * across every batch, failure and restart until the position passes it (TASK-059, F2).
     */
    boolean restartFolder(UUID id, int source, long previousValidity, long uidValidity, long start, Long compareUntil, String notice,
            Instant now) {
        return jdbc.sql("""
                update helpdesk_mailbox set source = source + 1, uid_validity = ?, last_uid = ?, compare_until = ?, notice = ?, notice_at = ?
                where id = ? and source = ? and uid_validity = ?
                """).params(uidValidity, start, compareUntil, notice, ts(now), id, source, previousValidity).update() == 1;
    }

    /**
     * Locks the mailbox row and tells whether the fetch still reads its current source and UIDVALIDITY. Under this lock the
     * intake records a mail, compares Message-IDs and writes nothing for a stale fetch; a start over of the folder writes the
     * same row and waits for it (TASK-061, S1).
     */
    boolean lockCurrent(UUID id, int source, long uidValidity) {
        return jdbc.sql("select coalesce(source = ? and uid_validity = ?, false) from helpdesk_mailbox where id = ? for update")
            .params(source, uidValidity, id).query(Boolean.class).optional().orElse(false);
    }

    void clearNotice(UUID id) {
        jdbc.sql("update helpdesk_mailbox set notice = null, notice_at = null where id = ?").param(id).update();
    }

    private static Mailbox mailbox(ResultSet rs, int row) throws SQLException {
        return new Mailbox(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("name"),
            rs.getBoolean("enabled"), rs.getObject("project_id", UUID.class), rs.getString("ticket_type"), rs.getString("imap_host"),
            rs.getInt("imap_port"), rs.getString("imap_security"), rs.getString("username"), rs.getBytes("password_cipher"),
            rs.getString("folder"), rs.getString("smtp_host"), (Integer) rs.getObject("smtp_port"), rs.getString("smtp_security"),
            rs.getString("address"), rs.getString("authserv_id"), rs.getLong("max_bytes"), (Long) rs.getObject("uid_validity"),
            rs.getLong("last_uid"), rs.getInt("source"), (Long) rs.getObject("compare_until"), instant(rs, "polled_at"),
            instant(rs, "succeeded_at"), rs.getString("error_code"),
            instant(rs, "error_at"), rs.getString("notice"), instant(rs, "notice_at"), rs.getLong("version"), instant(rs, "created_at"),
            rs.getObject("created_by", UUID.class), instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class));
    }

    // ------------------------------------------------------------------------------------------------ messages

    /** Whether a mail of this origin is already there. */
    boolean knownOrigin(UUID mailboxId, int source, long uidValidity, long uid) {
        return jdbc.sql("select count(*) from helpdesk_message where mailbox_id = ? and source = ? and uid_validity = ? and uid = ?")
            .params(mailboxId, source, uidValidity, uid).query(Long.class).single() > 0;
    }

    /** Whether the mailbox already holds a mail with this Message-ID, for the comparison after a new UIDVALIDITY. */
    boolean knownMessageId(UUID mailboxId, String messageId) {
        return messageId != null && jdbc.sql("select count(*) from helpdesk_message where mailbox_id = ? and message_id = ?")
            .params(mailboxId, messageId).query(Long.class).single() > 0;
    }

    /**
     * Inserts the mail unless a mail of the same origin exists; a concurrent insert of the same origin waits for the other
     * and then inserts nothing. Returns whether the row is new.
     */
    boolean insertMessage(Message m, int source, byte[] raw) {
        return jdbc.sql("""
                insert into helpdesk_message (id, company_id, mailbox_id, project_id, source, uid_validity, uid, message_id, received_at,
                    sent_at, from_address, from_name, to_addresses, cc_addresses, subject, body, size_bytes, attachments, raw, state,
                    reason, ticket_id, contact_id, customer_id, processed_at, processed_by, related_ticket_id, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (mailbox_id, source, uid_validity, uid) do nothing
                """).params(m.id(), m.companyId(), m.mailboxId(), m.projectId(), source, m.uidValidity(), m.uid(), m.messageId(),
                ts(m.receivedAt()), ts(m.sentAt()), m.fromAddress(), m.fromName(), m.to(), m.cc(), m.subject(), m.body(), m.sizeBytes(),
                JSON.writeValueAsString(m.attachments()), raw, m.state(), m.reason(), m.ticketId(), m.contactId(), m.customerId(),
                ts(m.processedAt()), m.processedBy(), m.relatedTicketId(), m.version()).update() == 1;
    }

    Optional<Message> message(UUID companyId, UUID id) {
        return jdbc.sql("select " + MESSAGE_COLUMNS + " from helpdesk_message where company_id = ? and id = ?").params(companyId, id)
            .query(HelpdeskRepository::message).optional();
    }

    Optional<Message> lockMessage(UUID companyId, UUID id) {
        return jdbc.sql("select " + MESSAGE_COLUMNS + " from helpdesk_message where company_id = ? and id = ? for update")
            .params(companyId, id).query(HelpdeskRepository::message).optional();
    }

    Optional<byte[]> raw(UUID id) {
        return jdbc.sql("select raw from helpdesk_message where id = ? and raw is not null").param(id).query(byte[].class).optional();
    }

    /** The mails of the projects in the given states, newest first, below the time {@code before} if given. */
    List<Message> queue(UUID companyId, Collection<UUID> projects, Collection<String> states, Instant before, int limit) {
        if (projects.isEmpty() || states.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + MESSAGE_COLUMNS + " from helpdesk_message where company_id = ? and project_id = any(?) and state = any(?) "
                + "and received_at < ? order by received_at desc, id desc limit ?")
            .params(companyId, projects.toArray(UUID[]::new), states.toArray(String[]::new),
                ts(before == null ? Instant.parse("9999-12-31T00:00:00Z") : before), limit)
            .query(HelpdeskRepository::message).list();
    }

    long count(UUID companyId, Collection<UUID> projects, String state) {
        if (projects.isEmpty()) {
            return 0;
        }
        return jdbc.sql("select count(*) from helpdesk_message where company_id = ? and project_id = any(?) and state = ?")
            .params(companyId, projects.toArray(UUID[]::new), state).query(Long.class).single();
    }

    List<Message> messagesOfTicket(UUID ticketId) {
        return jdbc.sql("select " + MESSAGE_COLUMNS + " from helpdesk_message where ticket_id = ? order by received_at, id")
            .param(ticketId).query(HelpdeskRepository::message).list();
    }

    /** Records the outcome of the review queue; the raw mail is dropped. */
    void processed(UUID id, String state, UUID ticketId, UUID contactId, UUID customerId, List<Message.AttachmentNote> attachments,
            String discardReason, Instant now, UUID actor, long version) {
        jdbc.sql("""
                update helpdesk_message set state = ?, ticket_id = ?, contact_id = ?, customer_id = ?, attachments = cast(? as jsonb),
                    discard_reason = ?, processed_at = ?, processed_by = ?, version = ?, raw = null
                where id = ?
                """).params(state, ticketId, contactId, customerId, JSON.writeValueAsString(attachments), discardReason, ts(now), actor,
                version, id).update();
    }

    /** A mail that was meant to become a ticket stays in the review queue with this reason. */
    void reviewInstead(UUID id, String reason) {
        jdbc.sql("update helpdesk_message set state = 'review', reason = ? where id = ?").params(reason, id).update();
    }

    /** A mail that would have joined a ticket waits in the review queue with that ticket as its reference (TASK-062, X-F4). */
    void reviewWithTicket(UUID id, String reason, UUID relatedTicketId) {
        jdbc.sql("update helpdesk_message set state = 'review', reason = ?, related_ticket_id = ? where id = ?").params(reason, relatedTicketId, id)
            .update();
    }

    /**
     * Serializes the changes of customer and contact of one ticket with the answers that join it (TASK-062, X-F4), until the
     * transaction ends. A lock of its own, because the ticket row is only locked {@code FOR SHARE} by the helpdesk and the link
     * row may not exist yet.
     */
    void lockTicketCustomer(UUID ticketId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended('helpdesk.ticket_customer:' || ?::text, 0))").param(ticketId.toString())
            .query((rs, row) -> 1).list();
    }

    /** Locks the contact {@code FOR SHARE}, so that it keeps its customer until the transaction ends; then reads it. */
    Optional<Contact> shareContact(UUID companyId, UUID contactId) {
        if (jdbc.sql("select id from helpdesk_contact where id = ? and company_id = ? for share").params(contactId, companyId)
                .query(UUID.class).optional().isEmpty()) {
            return Optional.empty();
        }
        return contact(companyId, contactId);
    }

    private static Message message(ResultSet rs, int row) throws SQLException {
        List<Message.AttachmentNote> attachments = JSON.readValue(rs.getString("attachments"), new TypeReference<List<Message.AttachmentNote>>() { });
        return new Message(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("mailbox_id", UUID.class),
            rs.getObject("project_id", UUID.class), rs.getLong("uid_validity"), rs.getLong("uid"), rs.getString("message_id"),
            instant(rs, "received_at"), instant(rs, "sent_at"), rs.getString("from_address"), rs.getString("from_name"),
            rs.getString("to_addresses"), rs.getString("cc_addresses"), rs.getString("subject"), rs.getString("body"),
            rs.getLong("size_bytes"), attachments, rs.getBoolean("has_raw"), rs.getString("state"), rs.getString("reason"),
            rs.getObject("ticket_id", UUID.class), rs.getObject("contact_id", UUID.class), rs.getObject("customer_id", UUID.class),
            instant(rs, "processed_at"), rs.getObject("processed_by", UUID.class), rs.getString("discard_reason"),
            rs.getObject("related_ticket_id", UUID.class), rs.getLong("version"));
    }

    // ------------------------------------------------------------------------------------------------ replies (TASK-058)

    private static final String REPLY_COLUMNS = "id, company_id, project_id, ticket_id, mailbox_id, contact_id, from_address, to_address, "
        + "subject, body, message_id, in_reply_to, references_header, state, error_code, job_id, sends, attempted_at, sent_at, created_at, "
        + "created_by, updated_at, updated_by, version";

    void insertReply(Reply r) {
        jdbc.sql("""
                insert into helpdesk_reply (id, company_id, project_id, ticket_id, mailbox_id, contact_id, from_address, to_address, subject,
                    body, message_id, in_reply_to, references_header, state, error_code, job_id, sends, attempted_at, sent_at, created_at,
                    created_by, updated_at, updated_by, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(r.id(), r.companyId(), r.projectId(), r.ticketId(), r.mailboxId(), r.contactId(), r.fromAddress(), r.toAddress(),
                r.subject(), r.body(), r.messageId(), r.inReplyTo(), r.references(), r.state(), r.errorCode(), r.jobId(), r.sends(),
                ts(r.attemptedAt()), ts(r.sentAt()), ts(r.createdAt()), r.createdBy(), ts(r.updatedAt()), r.updatedBy(), r.version()).update();
    }

    Optional<Reply> reply(UUID companyId, UUID id) {
        return jdbc.sql("select " + REPLY_COLUMNS + " from helpdesk_reply where company_id = ? and id = ?").params(companyId, id)
            .query(HelpdeskRepository::reply).optional();
    }

    Optional<Reply> lockReply(UUID id) {
        return jdbc.sql("select " + REPLY_COLUMNS + " from helpdesk_reply where id = ? for update").param(id)
            .query(HelpdeskRepository::reply).optional();
    }

    List<Reply> repliesOfTicket(UUID ticketId) {
        return jdbc.sql("select " + REPLY_COLUMNS + " from helpdesk_reply where ticket_id = ? order by created_at, id").param(ticketId)
            .query(HelpdeskRepository::reply).list();
    }

    /** The replies of the projects that are not known to be sent, newest first. */
    List<Reply> openReplies(UUID companyId, Collection<UUID> projects, int limit) {
        if (projects.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + REPLY_COLUMNS + " from helpdesk_reply where company_id = ? and project_id = any(?) and state <> 'sent' "
                + "order by created_at desc, id desc limit ?")
            .params(companyId, projects.toArray(UUID[]::new), limit).query(HelpdeskRepository::reply).list();
    }

    /** The replies of all companies that wait or are being sent, longest unchanged first: candidates for {@code ReplyWatch}. */
    List<UUID> unsettledReplies(int limit) {
        return jdbc.sql("select id from helpdesk_reply where state in ('pending', 'sending') order by updated_at, id limit ?").param(limit)
            .query(UUID.class).list();
    }

    /** Writes the state of a reply and moves its version on. */
    void replyState(UUID id, String state, String errorCode, Instant attemptedAt, Instant sentAt, Instant now, UUID actor) {
        jdbc.sql("""
                update helpdesk_reply set state = ?, error_code = ?, attempted_at = coalesce(?, attempted_at), sent_at = ?, updated_at = ?,
                    updated_by = ?, version = version + 1
                where id = ?
                """).params(state, errorCode, ts(attemptedAt), ts(sentAt), ts(now), actor, id).update();
    }

    /** A person sends the reply again: a new job, the reply waits again. */
    void replyAgain(UUID id, UUID jobId, Instant now, UUID actor) {
        jdbc.sql("""
                update helpdesk_reply set state = 'pending', error_code = null, job_id = ?, sends = sends + 1, updated_at = ?, updated_by = ?,
                    version = version + 1
                where id = ?
                """).params(jobId, ts(now), actor, id).update();
    }

    /** Whether one of the replies of the company carries this Message-ID: the mail is our own. */
    boolean ownReply(UUID companyId, String messageId) {
        return messageId != null && jdbc.sql("select count(*) from helpdesk_reply where company_id = ? and message_id = ?")
            .params(companyId, messageId).query(Long.class).single() > 0;
    }

    /**
     * The ticket of the newest reply of the project that one of the Message-IDs names, from In-Reply-To or References of an
     * incoming mail.
     */
    Optional<UUID> repliedTicket(UUID companyId, UUID projectId, Collection<String> messageIds) {
        if (messageIds.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                select ticket_id from helpdesk_reply where company_id = ? and project_id = ? and message_id = any(?)
                order by created_at desc, id desc limit 1
                """).params(companyId, projectId, messageIds.toArray(String[]::new)).query(UUID.class).optional();
    }

    private static Reply reply(ResultSet rs, int row) throws SQLException {
        return new Reply(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("ticket_id", UUID.class), rs.getObject("mailbox_id", UUID.class), rs.getObject("contact_id", UUID.class),
            rs.getString("from_address"), rs.getString("to_address"), rs.getString("subject"), rs.getString("body"),
            rs.getString("message_id"), rs.getString("in_reply_to"), rs.getString("references_header"), rs.getString("state"),
            rs.getString("error_code"), rs.getObject("job_id", UUID.class), rs.getInt("sends"), instant(rs, "attempted_at"),
            instant(rs, "sent_at"), instant(rs, "created_at"), rs.getObject("created_by", UUID.class), instant(rs, "updated_at"),
            rs.getObject("updated_by", UUID.class), rs.getLong("version"));
    }

    // ------------------------------------------------------------------------------------------------ customer of a ticket

    record TicketCustomer(UUID customerId, UUID contactId) {
    }

    Optional<TicketCustomer> ticketCustomer(UUID ticketId) {
        return jdbc.sql("select customer_id, contact_id from helpdesk_ticket_customer where ticket_id = ?").param(ticketId)
            .query((rs, row) -> new TicketCustomer(rs.getObject("customer_id", UUID.class), rs.getObject("contact_id", UUID.class)))
            .optional();
    }

    void setTicketCustomer(UUID companyId, UUID projectId, UUID ticketId, UUID customerId, UUID contactId) {
        jdbc.sql("""
                insert into helpdesk_ticket_customer (ticket_id, company_id, project_id, customer_id, contact_id) values (?, ?, ?, ?, ?)
                on conflict (ticket_id) do update set customer_id = excluded.customer_id, contact_id = excluded.contact_id
                """).params(ticketId, companyId, projectId, customerId, contactId).update();
    }

    // ------------------------------------------------------------------------------------------------ audit

    void audit(UUID companyId, String targetType, UUID targetId, String operation, String detail, UUID actor, Instant now) {
        jdbc.sql("""
                insert into helpdesk_audit_event (company_id, target_type, target_id, operation, detail, actor_account_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """).params(companyId, targetType, targetId, operation, detail, actor, ts(now)).update();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
