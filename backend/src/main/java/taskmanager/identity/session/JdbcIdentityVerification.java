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
        if (accountId == null || sessionId == null) {
            return false;
        }
        AccountState account = lockAccount(accountId).orElse(null);
        if (account == null || !account.active() || account.passwordChangeRequired()) {
            return false;
        }
        Instant now = clock.instant();
        return jdbc.sql("""
                select s.started_at, s.expires_at, s.restricted,
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
                return now.isBefore(stored.isBefore(current) ? stored : current);
            })
            .optional().orElse(false);
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
