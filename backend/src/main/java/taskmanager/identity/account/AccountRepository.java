package taskmanager.identity.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private static final String COLUMNS =
        "id, username, display_name, password_hash, password_change_required, one_time_expires_at, active, administrator, created_at, updated_at";

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
            rs.getBoolean("active"),
            rs.getBoolean("administrator"),
            Db.instant(rs, "created_at"),
            Db.instant(rs, "updated_at"));
    }

    public void insert(AccountRecord account) {
        jdbc.sql("insert into identity_account (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .params(account.id(), account.username(), account.displayName(), account.passwordHash(),
                account.passwordChangeRequired(), Db.ts(account.oneTimeExpiresAt()), account.active(),
                account.administrator(), Db.ts(account.createdAt()), Db.ts(account.updatedAt()))
            .update();
    }

    public Optional<AccountRecord> findByUsername(String normalizedUsername) {
        return jdbc.sql("select " + COLUMNS + " from identity_account where username = ?")
            .param(normalizedUsername).query(AccountRepository::map).optional();
    }

    public Optional<AccountRecord> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from identity_account where id = ?")
            .param(id).query(AccountRepository::map).optional();
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

    public long countActiveAdministrators() {
        return jdbc.sql("select count(*) from identity_account where administrator and active").query(Long.class).single();
    }

    /** Replaces the credential. A one time password is stored with its expiry; a chosen password without one. */
    public void updatePassword(UUID id, String passwordHash, Instant oneTimeExpiresAt, Instant now) {
        jdbc.sql("update identity_account set password_hash = ?, password_change_required = ?, one_time_expires_at = ?, updated_at = ? where id = ?")
            .params(passwordHash, oneTimeExpiresAt != null, Db.ts(oneTimeExpiresAt), Db.ts(now), id).update();
    }

    public void setActive(UUID id, boolean active, Instant now) {
        jdbc.sql("update identity_account set active = ?, updated_at = ? where id = ?").params(active, Db.ts(now), id).update();
    }

    public void audit(String actor, String event, UUID accountId, String detail, Instant now) {
        jdbc.sql("insert into identity_audit_event (occurred_at, actor, event, account_id, detail) values (?, ?, ?, ?, ?)")
            .params(Db.ts(now), actor, event, accountId, detail).update();
    }
}
