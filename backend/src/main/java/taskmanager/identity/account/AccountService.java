package taskmanager.identity.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.config.IdentityProperties;
import taskmanager.platform.ApiProblemException;

/**
 * Administrative account life cycle: creation with a one time password, deactivation, reactivation and
 * password reset. Every credential change and every deactivation ends the sessions of the account in the
 * same transaction. The one time password is returned exactly once and stored only as a hash.
 */
@Service
public class AccountService {
    private static final long ADMINISTRATOR_LOCK = 7_240_001L;

    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final OneTimePasswords oneTimePasswords;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdentityProperties properties;

    AccountService(AccountRepository accounts, PasswordHasher hasher, OneTimePasswords oneTimePasswords,
            JdbcClient jdbc, Clock clock, IdentityProperties properties) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.oneTimePasswords = oneTimePasswords;
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    /** An account together with the one time password that was just issued. The password is shown only once. */
    public record Issued(AccountRecord account, String oneTimePassword, Instant oneTimeExpiresAt) {
    }

    public List<AccountRecord> list() {
        return accounts.findAll();
    }

    @Transactional
    public Issued create(String actor, String rawUsername, String displayName, boolean administrator) {
        String username = UsernameRules.normalize(rawUsername);
        if (!UsernameRules.isValid(username)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("username.invalid"));
        }
        String name = displayName == null || displayName.isBlank() ? null : displayName.trim();
        if (name != null && name.codePointCount(0, name.length()) > 100) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("displayName.too_long"));
        }
        Instant now = clock.instant();
        String oneTime = oneTimePasswords.next();
        Instant expires = now.plus(properties.oneTimePasswordValidity());
        AccountRecord account = new AccountRecord(UUID.randomUUID(), username, name, hasher.hash(oneTime), true, expires,
            true, administrator, now, now);
        if (accounts.findByUsername(username).isPresent()) {
            throw ApiProblemException.of(409, "account.username_taken");
        }
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException race) {
            throw ApiProblemException.of(409, "account.username_taken");
        }
        accounts.audit(actor, "account.created", account.id(), administrator ? "administrator" : "user", now);
        return new Issued(account, oneTime, expires);
    }

    @Transactional
    public AccountRecord deactivate(String actor, UUID id) {
        // Serializes deactivations, so two administrators cannot deactivate each other at the same time.
        jdbc.sql("select pg_advisory_xact_lock(?)").param(ADMINISTRATOR_LOCK).query().singleValue();
        AccountRecord account = lock(id);
        Instant now = clock.instant();
        if (account.active()) {
            if (account.administrator() && accounts.countActiveAdministrators() <= 1) {
                throw ApiProblemException.of(409, "account.last_administrator");
            }
            accounts.setActive(id, false, now);
            revokeSessions(id, "account.deactivated", now);
            accounts.audit(actor, "account.deactivated", id, null, now);
        }
        return accounts.findById(id).orElseThrow();
    }

    @Transactional
    public AccountRecord reactivate(String actor, UUID id) {
        AccountRecord account = lock(id);
        Instant now = clock.instant();
        if (!account.active()) {
            accounts.setActive(id, true, now);
            accounts.audit(actor, "account.reactivated", id, null, now);
        }
        return accounts.findById(id).orElseThrow();
    }

    /** Administrative reset: new one time password, forced change, all sessions of the account end. */
    @Transactional
    public Issued resetPassword(String actor, UUID id) {
        AccountRecord account = lock(id);
        return issueNewOneTimePassword(actor, account, "password.reset");
    }

    /**
     * Creates the very first account. Allowed only while no account exists at all, so the command cannot be
     * used to add administrators to a running installation.
     */
    @Transactional
    public Issued bootstrapAdministrator(String rawUsername) {
        jdbc.sql("lock table identity_account in exclusive mode").update();
        if (accounts.count() > 0) {
            throw ApiProblemException.of(409, "bootstrap.accounts_exist");
        }
        return create("cli", rawUsername, null, true);
    }

    /**
     * Emergency path for a locked out administrator, used with the container command. Needs access to the
     * server. Also reactivates the account, because regaining access is the purpose, and records that in the audit trail.
     */
    @Transactional
    public Issued emergencyAdministratorReset(String rawUsername) {
        String username = UsernameRules.normalize(rawUsername);
        AccountRecord account = accounts.findByUsername(username)
            .flatMap(found -> accounts.findByIdForUpdate(found.id()))
            .orElseThrow(() -> ApiProblemException.of(404, "account.not_found"));
        if (!account.administrator()) {
            throw ApiProblemException.of(409, "account.not_administrator");
        }
        Instant now = clock.instant();
        if (!account.active()) {
            accounts.setActive(account.id(), true, now);
            accounts.audit("cli", "account.reactivated", account.id(), "emergency", now);
        }
        return issueNewOneTimePassword("cli", account, "password.emergency_reset");
    }

    private Issued issueNewOneTimePassword(String actor, AccountRecord account, String event) {
        Instant now = clock.instant();
        String oneTime = oneTimePasswords.next();
        Instant expires = now.plus(properties.oneTimePasswordValidity());
        accounts.updatePassword(account.id(), hasher.hash(oneTime), expires, now);
        revokeSessions(account.id(), event, now);
        accounts.audit(actor, event, account.id(), null, now);
        return new Issued(accounts.findById(account.id()).orElseThrow(), oneTime, expires);
    }

    private AccountRecord lock(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> ApiProblemException.of(404, "account.not_found"));
    }

    private void revokeSessions(UUID accountId, String reason, Instant now) {
        jdbc.sql("update identity_session set revoked_at = ?, revoke_reason = ? where account_id = ? and revoked_at is null")
            .params(Db.ts(now), reason, accountId).update();
    }
}
