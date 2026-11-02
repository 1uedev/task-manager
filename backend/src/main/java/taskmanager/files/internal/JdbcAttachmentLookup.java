package taskmanager.files.internal;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.files.AttachmentLookup;

/** {@link AttachmentLookup} on the table of the files module. */
@Component
class JdbcAttachmentLookup implements AttachmentLookup {
    private final JdbcClient jdbc;

    JdbcAttachmentLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Summary> find(UUID ticketId, Collection<UUID> ids, boolean lock) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select id, name, size_bytes, uploaded_at, uploaded_by, visibility, removed_at from file_attachment "
                + "where ticket_id = ? and id = any (?) order by uploaded_at, id" + (lock ? " for share" : ""))
            .params(ticketId, ids.toArray(UUID[]::new))
            .query((rs, row) -> {
                Timestamp uploaded = rs.getTimestamp("uploaded_at");
                return new Summary(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("size_bytes"), uploaded.toInstant(),
                    rs.getObject("uploaded_by", UUID.class), Attachment.CUSTOMER.equals(rs.getString("visibility")),
                    rs.getTimestamp("removed_at") != null);
            }).list();
    }
}
