package taskmanager.identity.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.AccountDeactivationGuard;
import taskmanager.identity.EmergencyResetPolicy;
import taskmanager.identity.FirstAdministratorHandler;
import taskmanager.identity.config.IdentityProperties;
import taskmanager.identity.session.LoginThrottle;
import taskmanager.platform.ApiProblemException;

/**
 * Administrative account life cycle: creation with a one time password, deactivation, reactivation and
 * password reset. Every credential change and every deactivation ends the sessions of the account in the
 * same transaction. The one time password is returned exactly once and stored only as a hash.
 *
 * <p>The methods do not decide who may call them. The web layer asks for the right to manage accounts in the transaction
 * of the call (TASK-040); the container commands act with the access to the server.
 */
@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final OneTimePasswords oneTimePasswords;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdentityProperties properties;
    private final ObjectProvider<AccountDeactivationGuard> deactivationGuard;
    private final ObjectProvider<FirstAdministratorHandler> firstAdministrator;
    private final ObjectProvider<EmergencyResetPolicy> emergencyReset;
    private final LoginThrottle throttle;

    AccountService(AccountRepository accounts, PasswordHasher hasher, OneTimePasswords oneTimePasswords,
            JdbcClient jdbc, Clock clock, IdentityProperties properties,
            ObjectProvider<AccountDeactivationGuard> deactivationGuard, ObjectProvider<FirstAdministratorHandler> firstAdministrator,
            ObjectProvider<EmergencyResetPolicy> emergencyReset, LoginThrottle throttle) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.oneTimePasswords = oneTimePasswords;
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
        this.deactivationGuard = deactivationGuard;
        this.firstAdministrator = firstAdministrator;
        this.emergencyReset = emergencyReset;
        this.throttle = throttle;
    }

    /** An account together with the one time password that was just issued. The password is shown only once. */
    public record Issued(AccountRecord account, String oneTimePassword, Instant oneTimeExpiresAt) {
    }

    public List<AccountRecord> list() {
        return accounts.findAll();
    }

    /** The end of the running waiting time after failed logins for each account that has one (TASK-045). */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, Instant> loginDelays(List<AccountRecord> list) {
        java.util.Map<String, Instant> byName = throttle.delays(list.stream().map(AccountRecord::username).toList(), clock.instant());
        java.util.Map<UUID, Instant> delays = new java.util.HashMap<>();
        list.forEach(account -> {
            Instant until = byName.get(account.username());
            if (until != null) {
                delays.put(account.id(), until);
            }
        });
        return delays;
    }

    /** The audit trail of the account administration, newest first (TASK-045). */
    @Transactional(readOnly = true)
    public List<AccountRepository.AuditRecord> audit(Long before, int limit) {
        return accounts.audit(before, limit);
    }

    /**
     * Changes the display name; an empty one removes it. {@code authorize} runs after the serialization and before the account
     * is locked, like for the other changes of an account (TASK-045). A change that keeps the name writes nothing.
     */
    @Transactional
    public AccountRecord rename(String actor, UUID id, String rawDisplayName, Runnable authorize) {
        String name = rawDisplayName == null || rawDisplayName.isBlank() ? null : rawDisplayName.trim();
        if (name != null && (name.codePointCount(0, name.length()) > 100 || name.chars().anyMatch(Character::isISOControl))) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of(name.chars().anyMatch(Character::isISOControl)
                ? "displayName.invalid" : "displayName.too_long"));
        }
        accounts.serializeAdministration();
        authorize.run();
        AccountRecord account = lock(id);
        requireLocal(account);
        if (!java.util.Objects.equals(account.displayName(), name)) {
            Instant now = clock.instant();
            accounts.setDisplayName(id, name, now);
            accounts.audit(actor, "account.renamed", id, null, now);
        }
        return accounts.findById(id).orElseThrow();
    }

    /**
     * Ends the waiting time after failed logins of the account and forgets its failures, so the next attempt counts from zero.
     * The counters of source addresses stay, because one account must not be able to reset them (TASK-045).
     */
    @Transactional
    public AccountRecord clearLoginDelay(String actor, UUID id, Runnable authorize) {
        accounts.serializeAdministration();
        authorize.run();
        AccountRecord account = lock(id);
        if (throttle.forget(account.username())) {
            accounts.audit(actor, "account.login_delay_cleared", id, null, clock.instant());
        }
        return account;
    }

    @Transactional
    public Issued create(String actor, String rawUsername, String displayName) {
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
        AccountRecord account = new AccountRecord(UUID.randomUUID(), username, name, hasher.hash(oneTime), true, expires, null,
            true, now, now, AccountRecord.LOCAL, null);
        if (accounts.findByUsername(username).isPresent()) {
            throw ApiProblemException.of(409, "account.username_taken");
        }
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException race) {
            throw ApiProblemException.of(409, "account.username_taken");
        }
        accounts.audit(actor, "account.created", account.id(), null, now);
        return new Issued(account, oneTime, expires);
    }

    @Transactional
    public AccountRecord deactivate(String actor, UUID id) {
        return deactivate(actor, id, () -> {
        });
    }

    /**
     * Deactivates the account. {@code authorize} runs in the transaction of the deactivation right after the guard and
     * before the account is locked: the guard takes the exclusive rights lock, so the caller's own check must come after it
     * (a shared rights lock taken earlier could not be upgraded without a deadlock). Whatever it throws ends the deactivation.
     */
    @Transactional
    public AccountRecord deactivate(String actor, UUID id, Runnable authorize) {
        // First of all, so two administrators cannot deactivate, reset or reactivate each other at the same time.
        accounts.serializeAdministration();
        // Before any identity lock: other modules order their own locks (rights) ahead of the identity locks.
        if (!exactlyOne(deactivationGuard).mayDeactivate(id)) {
            throw ApiProblemException.of(409, "account.last_administrator");
        }
        authorize.run();
        AccountRecord account = lock(id);
        Instant now = clock.instant();
        if (account.active()) {
            accounts.setActive(id, false, now);
            revokeSessions(id, "account.deactivated", now);
            accounts.audit(actor, "account.deactivated", id, null, now);
        }
        return accounts.findById(id).orElseThrow();
    }

    @Transactional
    public AccountRecord reactivate(String actor, UUID id) {
        return reactivate(actor, id, () -> {
        });
    }

    /** Reactivates the account. {@code authorize} runs after the serialization and before the account is locked. */
    @Transactional
    public AccountRecord reactivate(String actor, UUID id, Runnable authorize) {
        accounts.serializeAdministration();
        authorize.run();
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
        return resetPassword(actor, id, () -> {
        });
    }

    /** Administrative reset; {@code authorize} runs after the serialization and before the account is locked. */
    @Transactional
    public Issued resetPassword(String actor, UUID id, Runnable authorize) {
        accounts.serializeAdministration();
        authorize.run();
        AccountRecord account = lock(id);
        requireLocal(account);
        return issueNewOneTimePassword(actor, account, "password.reset");
    }

    /**
     * Creates the very first account. Allowed only while no account exists at all, so the command cannot be
     * used to add administrators to a running installation. The application entry point hooks company and initial
     * rights into the same transaction through {@link FirstAdministratorHandler}.
     */
    @Transactional
    public Issued bootstrapAdministrator(String rawUsername) {
        jdbc.sql("lock table identity_account in exclusive mode").update();
        if (accounts.count() > 0) {
            throw ApiProblemException.of(409, "bootstrap.accounts_exist");
        }
        Issued issued = create("cli", rawUsername, null);
        // Same transaction: the company and the initial rights appear with the account or not at all.
        exactlyOne(firstAdministrator).firstAdministratorCreated(issued.account().id());
        return issued;
    }

    /** A missing or ambiguous port is a wiring error, not a client error: nothing is changed. */
    private static <T> T exactlyOne(ObjectProvider<T> provider) {
        var candidates = provider.stream().limit(2).toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException("Exactly one implementation of the port is required");
        }
        return candidates.getFirst();
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
        requireLocal(account);
        if (!exactlyOne(emergencyReset).mayReset(account.id())) {
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

    /** The password and the display name of an account from the directory belong to the directory (TASK-046). */
    private static void requireLocal(AccountRecord account) {
        if (account.fromDirectory()) {
            throw ApiProblemException.of(409, "account.directory_managed");
        }
    }

    private AccountRecord lock(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> ApiProblemException.of(404, "account.not_found"));
    }

    private void revokeSessions(UUID accountId, String reason, Instant now) {
        jdbc.sql("update identity_session set revoked_at = ?, revoke_reason = ? where account_id = ? and revoked_at is null")
            .params(Db.ts(now), reason, accountId).update();
    }
}
