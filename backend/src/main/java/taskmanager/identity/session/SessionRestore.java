package taskmanager.identity.session;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.IdentityRestore;

/** Ends the sessions after a restore and names the active accounts (TASK-131). */
@Component
class SessionRestore implements IdentityRestore {
    private final JdbcClient jdbc;

    SessionRestore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int revokeAllSessions(Instant at) {
        return jdbc.sql("update identity_session set revoked_at = ?, revoke_reason = 'restore' where revoked_at is null")
            .param(Timestamp.from(at)).update();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> activeLoginNames(Collection<UUID> accountIds) {
        Map<UUID, String> names = new TreeMap<>();
        if (accountIds.isEmpty()) {
            return names;
        }
        jdbc.sql("select id, username from identity_account where active and id = any (?)")
            .param(accountIds.toArray(UUID[]::new))
            .query((rs, row) -> names.put(rs.getObject("id", UUID.class), rs.getString("username"))).list();
        return names;
    }
}
