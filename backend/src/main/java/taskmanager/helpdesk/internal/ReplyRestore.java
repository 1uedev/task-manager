package taskmanager.helpdesk.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.helpdesk.HelpdeskRestore;

/** Holds the replies that were open at the backup point (TASK-131, rule 10). */
@Component
class ReplyRestore implements HelpdeskRestore {
    static final String CODE = "helpdesk.restored";

    private final JdbcClient jdbc;
    private final HelpdeskRepository repository;

    ReplyRestore(JdbcClient jdbc, HelpdeskRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    private record Held(UUID id, UUID companyId) {
    }

    @Override
    @Transactional
    public int holdOpenReplies(Instant at) {
        var held = jdbc.sql("""
                update helpdesk_reply set state = 'uncertain', error_code = ?, updated_at = ?, version = version + 1
                where state in ('pending', 'sending')
                returning id, company_id
                """).params(CODE, Timestamp.from(at))
            .query((rs, row) -> new Held(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class))).list();
        for (Held reply : held) {
            repository.audit(reply.companyId(), "reply", reply.id(), "reply.held_after_restore", null, null, at);
        }
        return held.size();
    }
}
