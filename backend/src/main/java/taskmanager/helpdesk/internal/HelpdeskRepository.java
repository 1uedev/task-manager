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
        + "uid_validity, last_uid, polled_at, succeeded_at, error_code, error_at, notice, notice_at, version, created_at, created_by, "
        + "updated_at, updated_by";
    private static final String MESSAGE_COLUMNS = "id, company_id, mailbox_id, project_id, uid_validity, uid, message_id, received_at, "
        + "sent_at, from_address, from_name, to_addresses, cc_addresses, subject, body, size_bytes, attachments::text as attachments, "
        + "raw is not null as has_raw, state, reason, ticket_id, contact_id, customer_id, processed_at, processed_by, discard_reason, version";

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
     * Writes the settings. A change of server, account or folder starts the folder over: the intake forgets where it stood,
     * and the origin of the mails it has already taken still keeps them from arriving twice.
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
            jdbc.sql("update helpdesk_mailbox set uid_validity = null, last_uid = 0 where id = ?").param(m.id()).update();
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

    void position(UUID id, long uidValidity, long lastUid) {
        jdbc.sql("update helpdesk_mailbox set uid_validity = ?, last_uid = greatest(last_uid, ?) where id = ?")
            .params(uidValidity, lastUid, id).update();
    }

    /** A new UIDVALIDITY: the intake starts the folder over and leaves a notice. */
    void restartFolder(UUID id, long uidValidity, String notice, Instant now) {
        jdbc.sql("update helpdesk_mailbox set uid_validity = ?, last_uid = 0, notice = ?, notice_at = ? where id = ?")
            .params(uidValidity, notice, ts(now), id).update();
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
            rs.getLong("last_uid"), instant(rs, "polled_at"), instant(rs, "succeeded_at"), rs.getString("error_code"),
            instant(rs, "error_at"), rs.getString("notice"), instant(rs, "notice_at"), rs.getLong("version"), instant(rs, "created_at"),
            rs.getObject("created_by", UUID.class), instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class));
    }

    // ------------------------------------------------------------------------------------------------ messages

    /** Whether a mail of this origin is already there. */
    boolean knownOrigin(UUID mailboxId, long uidValidity, long uid) {
        return jdbc.sql("select count(*) from helpdesk_message where mailbox_id = ? and uid_validity = ? and uid = ?")
            .params(mailboxId, uidValidity, uid).query(Long.class).single() > 0;
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
    boolean insertMessage(Message m, byte[] raw) {
        return jdbc.sql("""
                insert into helpdesk_message (id, company_id, mailbox_id, project_id, uid_validity, uid, message_id, received_at,
                    sent_at, from_address, from_name, to_addresses, cc_addresses, subject, body, size_bytes, attachments, raw, state,
                    reason, ticket_id, contact_id, customer_id, processed_at, processed_by, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (mailbox_id, uid_validity, uid) do nothing
                """).params(m.id(), m.companyId(), m.mailboxId(), m.projectId(), m.uidValidity(), m.uid(), m.messageId(),
                ts(m.receivedAt()), ts(m.sentAt()), m.fromAddress(), m.fromName(), m.to(), m.cc(), m.subject(), m.body(), m.sizeBytes(),
                JSON.writeValueAsString(m.attachments()), raw, m.state(), m.reason(), m.ticketId(), m.contactId(), m.customerId(),
                ts(m.processedAt()), m.processedBy(), m.version()).update() == 1;
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

    private static Message message(ResultSet rs, int row) throws SQLException {
        List<Message.AttachmentNote> attachments = JSON.readValue(rs.getString("attachments"), new TypeReference<List<Message.AttachmentNote>>() { });
        return new Message(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("mailbox_id", UUID.class),
            rs.getObject("project_id", UUID.class), rs.getLong("uid_validity"), rs.getLong("uid"), rs.getString("message_id"),
            instant(rs, "received_at"), instant(rs, "sent_at"), rs.getString("from_address"), rs.getString("from_name"),
            rs.getString("to_addresses"), rs.getString("cc_addresses"), rs.getString("subject"), rs.getString("body"),
            rs.getLong("size_bytes"), attachments, rs.getBoolean("has_raw"), rs.getString("state"), rs.getString("reason"),
            rs.getObject("ticket_id", UUID.class), rs.getObject("contact_id", UUID.class), rs.getObject("customer_id", UUID.class),
            instant(rs, "processed_at"), rs.getObject("processed_by", UUID.class), rs.getString("discard_reason"), rs.getLong("version"));
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
