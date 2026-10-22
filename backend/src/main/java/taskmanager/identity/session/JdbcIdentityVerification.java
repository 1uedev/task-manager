package taskmanager.identity.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.IdentityVerification;
import taskmanager.identity.account.Db;

/**
 * Account row first, session row second, both {@code FOR SHARE}: the same order as the revocation paths, which lock the
 * account before they touch its sessions, so a check and a revocation cannot wait for each other. The conditions that
 * decide are in the WHERE clause of the locking statement and are checked again on the row that is returned, so a
 * revocation that committed while this check waited for the lock is seen (READ COMMITTED re-evaluates the row).
 * The time of the check is the moment after the locks are held; a session that ends later, while the caller works on, is not
 * stopped by any lock, because the end of a period is a point in time and not a write. That is why the end is handed out
 * too: the caller compares it with the clock again before its commit (TASK-043).
 * The setting row is deliberately not locked: a shortening writes the lowered end into the session rows, which the
 * shared session lock covers, and locking the setting row after the session row would invert the order of that path.
 */
@Service
class JdbcIdentityVerification implements IdentityVerification {
    private record AccountState(boolean active, boolean passwordChangeRequired) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcIdentityVerification(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isCurrentFullSession(UUID accountId, UUID sessionId) {
        return currentFullSessionEnd(accountId, sessionId).isPresent();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Instant> currentFullSessionEnd(UUID accountId, UUID sessionId) {
        if (accountId == null || sessionId == null) {
            return Optional.empty();
        }
        AccountState account = lockAccount(accountId).orElse(null);
        if (account == null || !account.active() || account.passwordChangeRequired()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                select s.started_at, s.expires_at,
                       (select value from identity_setting where name = 'session_duration_hours') as duration_hours
                from identity_session s
                where s.id = ? and s.account_id = ? and s.revoked_at is null and not s.restricted
                for share of s
                """)
            .params(sessionId, accountId)
            .query((rs, row) -> {
                Instant started = Db.instant(rs, "started_at");
                Instant stored = Db.instant(rs, "expires_at");
                Instant current = started.plus(Duration.ofHours(Long.parseLong(rs.getString("duration_hours"))));
                return stored.isBefore(current) ? stored : current;
            })
            .optional()
            // The clock is read only now, after the lock wait: the lock keeps the row stable, not the time, and a
            // wait for an uncommitted shortening can pass the end of the session.
            .filter(end -> clock.instant().isBefore(end));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean accountExists(UUID accountId) {
        return accountId != null && lockAccount(accountId).isPresent();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean accountIsActive(UUID accountId) {
        return accountId != null && lockAccount(accountId).map(AccountState::active).orElse(false);
    }

    private Optional<AccountState> lockAccount(UUID accountId) {
        return jdbc.sql("select active, password_change_required from identity_account where id = ? for share")
            .param(accountId)
            .query((rs, row) -> new AccountState(rs.getBoolean("active"), rs.getBoolean("password_change_required")))
            .optional();
    }
}
