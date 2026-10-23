package taskmanager.identity.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private static final long ADMINISTRATION_LOCK = 7_240_001L;
    private static final String COLUMNS =
        "id, username, display_name, password_hash, password_change_required, one_time_expires_at, one_time_used_at, active, created_at, updated_at, auth_kind, external_id";

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static AccountRecord map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AccountRecord(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("password_hash"),
            rs.getBoolean("password_change_required"),
            Db.instant(rs, "one_time_expires_at"),
            Db.instant(rs, "one_time_used_at"),
            rs.getBoolean("active"),
            Db.instant(rs, "created_at"),
            Db.instant(rs, "updated_at"),
            rs.getString("auth_kind"),
            rs.getString("external_id"));
    }

    public void insert(AccountRecord account) {
        jdbc.sql("insert into identity_account (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .params(account.id(), account.username(), account.displayName(), account.passwordHash(),
                account.passwordChangeRequired(), Db.ts(account.oneTimeExpiresAt()), Db.ts(account.oneTimeUsedAt()), account.active(),
                Db.ts(account.createdAt()), Db.ts(account.updatedAt()), account.authKind(), account.externalId())
            .update();
    }

    public Optional<AccountRecord> findByExternalId(String externalId) {
        return jdbc.sql("select " + COLUMNS + " from identity_account where external_id = ?")
            .param(externalId).query(AccountRepository::map).optional();
    }

    public Optional<AccountRecord> findByUsername(String normalizedUsername) {
        return jdbc.sql("select " + COLUMNS + " from identity_account where username = ?")
            .param(normalizedUsername).query(AccountRepository::map).optional();
    }

    public Optional<AccountRecord> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from identity_account where id = ?")
            .param(id).query(AccountRepository::map).optional();
    }

    /**
     * Serializes the administrative changes that lock an account other than the requester's, and the change of the session
     * duration, which writes the sessions of all accounts (TASK-041, DEC-066). The authoritative rights check holds the
     * requester's own account and session rows {@code FOR SHARE}; two such changes aimed at each other's accounts would
     * otherwise wait for each other. Take it before the rights check and before any identity lock; only the setting row of
     * the session duration comes earlier. Held until the end of the transaction.
     */
    public void serializeAdministration() {
        jdbc.sql("select pg_advisory_xact_lock(?)").param(ADMINISTRATION_LOCK).query().singleValue();
    }

    /** Locks the row until the end of the transaction, so concurrent changes of the same account are serialized. */
    public Optional<AccountRecord> findByIdForUpdate(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from identity_account where id = ? for update")
            .param(id).query(AccountRepository::map).optional();
    }

    public List<AccountRecord> findAll() {
        return jdbc.sql("select " + COLUMNS + " from identity_account order by username")
            .query(AccountRepository::map).list();
    }

    public long count() {
        return jdbc.sql("select count(*) from identity_account").query(Long.class).single();
    }

    /** Replaces the credential. A one time password is stored with its expiry; a chosen password without one. */
    public void updatePassword(UUID id, String passwordHash, Instant oneTimeExpiresAt, Instant now) {
        jdbc.sql("update identity_account set password_hash = ?, password_change_required = ?, one_time_expires_at = ?, one_time_used_at = null, updated_at = ? where id = ?")
            .params(passwordHash, oneTimeExpiresAt != null, Db.ts(oneTimeExpiresAt), Db.ts(now), id).update();
    }

    /** The first successful login with a one time password consumes it. The caller holds the row lock. */
    public void markOneTimeUsed(UUID id, Instant now) {
        jdbc.sql("update identity_account set one_time_used_at = ?, updated_at = ? where id = ?").params(Db.ts(now), Db.ts(now), id).update();
    }

    /** Turns a directory account into a local one; the caller sets a new credential right after. */
    public void convertToLocal(UUID id, Instant now) {
        jdbc.sql("update identity_account set auth_kind = 'local', external_id = null, updated_at = ? where id = ?").params(Db.ts(now), id).update();
    }

    public void setActive(UUID id, boolean active, Instant now) {
        jdbc.sql("update identity_account set active = ?, updated_at = ? where id = ?").params(active, Db.ts(now), id).update();
    }

    public void setDisplayName(UUID id, String displayName, Instant now) {
        jdbc.sql("update identity_account set display_name = ?, updated_at = ? where id = ?").params(displayName, Db.ts(now), id).update();
    }

    /** One entry of the audit trail. */
    public record AuditRecord(long id, Instant occurredAt, String actor, String event, UUID accountId, String detail) {
    }

    /** The audit trail, newest first, below the entry {@code before} if given. */
    public List<AuditRecord> audit(Long before, int limit) {
        return jdbc.sql("select id, occurred_at, actor, event, account_id, detail from identity_audit_event where id < ? order by id desc limit ?")
            .params(before == null ? Long.MAX_VALUE : before, limit)
            .query((rs, row) -> new AuditRecord(rs.getLong("id"), Db.instant(rs, "occurred_at"), rs.getString("actor"), rs.getString("event"),
                rs.getObject("account_id", UUID.class), rs.getString("detail")))
            .list();
    }

    public void audit(String actor, String event, UUID accountId, String detail, Instant now) {
        jdbc.sql("insert into identity_audit_event (occurred_at, actor, event, account_id, detail) values (?, ?, ?, ?, ?)")
            .params(Db.ts(now), actor, event, accountId, detail).update();
    }
}
