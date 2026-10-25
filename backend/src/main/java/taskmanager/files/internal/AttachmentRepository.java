package taskmanager.files.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the files module. Nobody else reads them. */
@Repository
class AttachmentRepository {
    private static final String COLUMNS = "id, company_id, ticket_id, project_id, name, size_bytes, sha256, uploaded_at, uploaded_by, "
        + "removed_at, removed_by, purged_at";

    private final JdbcClient jdbc;

    AttachmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Attachment map(ResultSet rs, int row) throws SQLException {
        return new Attachment(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("ticket_id", UUID.class),
            rs.getObject("project_id", UUID.class), rs.getString("name"), rs.getLong("size_bytes"), rs.getString("sha256"),
            instant(rs, "uploaded_at"), rs.getObject("uploaded_by", UUID.class), instant(rs, "removed_at"),
            rs.getObject("removed_by", UUID.class), instant(rs, "purged_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    void insert(Attachment attachment) {
        jdbc.sql("insert into file_attachment (id, company_id, ticket_id, project_id, name, size_bytes, sha256, uploaded_at, uploaded_by) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .params(attachment.id(), attachment.companyId(), attachment.ticketId(), attachment.projectId(), attachment.name(),
                attachment.size(), attachment.sha256(), Timestamp.from(attachment.uploadedAt()), attachment.uploadedBy())
            .update();
    }

    /** The attachments of the ticket that are not removed, oldest first. */
    List<Attachment> visible(UUID ticketId) {
        return jdbc.sql("select " + COLUMNS + " from file_attachment where ticket_id = ? and removed_at is null order by uploaded_at, id")
            .param(ticketId).query(AttachmentRepository::map).list();
    }

    Optional<Attachment> find(UUID ticketId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from file_attachment where ticket_id = ? and id = ?")
            .params(ticketId, id).query(AttachmentRepository::map).optional();
    }

    /** Reads and locks the row until the end of the transaction. */
    Optional<Attachment> lock(UUID ticketId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from file_attachment where ticket_id = ? and id = ? for update")
            .params(ticketId, id).query(AttachmentRepository::map).optional();
    }

    void markRemoved(UUID id, Instant at, UUID actor) {
        jdbc.sql("update file_attachment set removed_at = ?, removed_by = ? where id = ? and removed_at is null")
            .params(Timestamp.from(at), actor, id).update();
    }

    /** The removed attachments whose content is still there and which were removed before the given time. */
    List<Attachment> toPurge(Instant removedBefore, int limit) {
        return jdbc.sql("select " + COLUMNS + " from file_attachment where removed_at < ? and purged_at is null order by removed_at, id limit ?")
            .params(Timestamp.from(removedBefore), limit).query(AttachmentRepository::map).list();
    }

    /** Locks one row for the purge and returns it if its content is still to be deleted. */
    Optional<Attachment> lockForPurge(UUID id, Instant removedBefore) {
        return jdbc.sql("select " + COLUMNS + " from file_attachment where id = ? and removed_at < ? and purged_at is null for update")
            .params(id, Timestamp.from(removedBefore)).query(AttachmentRepository::map).optional();
    }

    void markPurged(UUID id, Instant at) {
        jdbc.sql("update file_attachment set purged_at = ? where id = ?").params(Timestamp.from(at), id).update();
    }

    /** Which of the identifiers belong to an attachment row, removed or not. */
    Set<UUID> known(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("select id from file_attachment where id = any (?)")
            .param(ids.toArray(UUID[]::new)).query(UUID.class).list());
    }

    void audit(UUID companyId, UUID attachmentId, UUID ticketId, String operation, String detail, UUID actor, Instant at) {
        jdbc.sql("insert into file_audit_event (company_id, attachment_id, ticket_id, operation, detail, actor_account_id, occurred_at) "
                + "values (?, ?, ?, ?, ?, ?, ?)")
            .params(companyId, attachmentId, ticketId, operation, detail, actor, Timestamp.from(at)).update();
    }
}
