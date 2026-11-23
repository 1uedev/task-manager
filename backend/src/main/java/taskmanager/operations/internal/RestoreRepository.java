package taskmanager.operations.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The marker of a restore that was not released yet, and the record of the command (TASK-131). */
@Repository
class RestoreRepository {
    /** A restore that waits for {@code after-restore}: the name of its backup point and when it was restored. */
    record Pending(String point, Instant restoredAt) {
    }

    private final JdbcClient jdbc;

    RestoreRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    Optional<Pending> pending() {
        return jdbc.sql("select point, restored_at from operations_restore_pending order by point limit 1")
            .query((rs, row) -> new Pending(rs.getString("point"), rs.getTimestamp("restored_at").toInstant())).optional();
    }

    /**
     * Locks the marker for the command until its transaction ends (TASK-135, finding F7). A marker a second command holds
     * is skipped, so that command learns at once that another one runs instead of waiting for the lock.
     */
    @Transactional
    Optional<Pending> lockPending() {
        return jdbc.sql("select point, restored_at from operations_restore_pending order by point limit 1 for update skip locked")
            .query((rs, row) -> new Pending(rs.getString("point"), rs.getTimestamp("restored_at").toInstant())).optional();
    }

    @Transactional(readOnly = true)
    boolean anyPending() {
        return jdbc.sql("select exists (select 1 from operations_restore_pending)").query(Boolean.class).single();
    }

    @Transactional
    void release(String point) {
        jdbc.sql("delete from operations_restore_pending where point = ?").param(point).update();
    }

    @Transactional
    void audit(String operation, String point, String operator, String detail, Instant at) {
        jdbc.sql("""
                insert into operations_audit_event (operation, point, operator, detail, occurred_at)
                values (?, ?, ?, cast(? as jsonb), ?)
                """).params(operation, point, operator, detail, Timestamp.from(at)).update();
    }
}
