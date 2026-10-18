package taskmanager.identity.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.identity.account.AccountRecord;
import taskmanager.identity.account.AccountRepository;
import taskmanager.identity.account.Db;
import taskmanager.identity.account.PasswordHasher;
import taskmanager.identity.account.PasswordPolicy;
import taskmanager.identity.account.UsernameRules;
import taskmanager.identity.config.IdentityProperties;
import taskmanager.platform.ApiProblemException;

/**
 * Login, logout, the forced password change and the lookup of sessions.
 *
 * <p>The session period is fixed: it starts with the first successful login and its end is stored once.
 * Activity, polling, rotation of the session token and application restarts never move it. A password change
 * issues a new token but inherits the start and end of the period. A shortened administrator setting applies
 * to existing sessions at their next request (the effective end is the earlier of the stored end and start
 * plus the current duration); an extended setting only applies to later logins.
 */
@Service
public class SessionService {
    private static final int MAX_NAME_LENGTH = 256;

    private final JdbcClient jdbc;
    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final SessionDuration duration;
    private final Clock clock;
    private final IdentityProperties properties;
    private final TransactionTemplate transaction;

    SessionService(JdbcClient jdbc, AccountRepository accounts, PasswordHasher hasher, LoginThrottle throttle,
            SessionDuration duration, Clock clock, IdentityProperties properties, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.hasher = hasher;
        this.throttle = throttle;
        this.duration = duration;
        this.clock = clock;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** A session as it is presented to the client. */
    public record SessionView(UUID accountId, String username, String displayName, AuthenticationLevel level,
            boolean administrator, Instant startedAt, Instant expiresAt, Instant restrictedUntil) {
    }

    /** A freshly issued session together with the two secrets that exist only in the cookies. */
    public record Issued(String sessionToken, String csrfToken, SessionView view) {
    }

    /** A valid session found for a request. {@code csrfHash} is checked by the web layer. */
    public record Resolved(UUID sessionId, SessionView view, byte[] csrfHash) {
    }

    public Issued login(String rawUsername, String password, String sourceAddress) {
        Instant now = clock.instant();
        String attempted = rawUsername == null ? "" : rawUsername;
        String userSubject = LoginThrottle.userSubject(attempted);
        String sourceSubject = LoginThrottle.sourceSubject(sourceAddress);
        throttle.requireNotBlocked(List.of(userSubject, sourceSubject), now);

        String normalized = attempted.length() > MAX_NAME_LENGTH ? "" : UsernameRules.normalize(attempted);
        Optional<AccountRecord> found = UsernameRules.isValid(normalized) ? accounts.findByUsername(normalized) : Optional.empty();
        String secret = password == null ? "" : password;
        boolean valid = found.filter(AccountRecord::active).filter(a -> hasher.matches(secret, a.passwordHash())).isPresent();
        if (!valid) {
            if (found.isEmpty() || !found.get().active()) {
                hasher.burn(secret);
            }
            throttle.recordFailure(userSubject, sourceSubject, now);
            throw ApiProblemException.of(401, "auth.invalid_credentials");
        }
        AccountRecord account = found.get();
        if (account.passwordChangeRequired() && !now.isBefore(account.oneTimeExpiresAt())) {
            throw ApiProblemException.of(401, "auth.one_time_password_expired");
        }
        throttle.recordSuccess(userSubject);
        return transaction.execute(status -> startSession(account, now));
    }

    private Issued startSession(AccountRecord account, Instant now) {
        purgeOld(now);
        Instant expires = now.plus(Duration.ofHours(duration.hours()));
        Instant restrictedUntil = account.passwordChangeRequired() ? now.plus(properties.restrictedSessionValidity()) : null;
        return insertSession(account, now, expires, restrictedUntil, now);
    }

    private Issued insertSession(AccountRecord account, Instant startedAt, Instant expiresAt, Instant restrictedUntil, Instant now) {
        String token = Tokens.random();
        String csrf = Tokens.random();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into identity_session (id, account_id, token_hash, csrf_hash, started_at, expires_at, restricted, restricted_expires_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(id, account.id(), Tokens.hash(token), Tokens.hash(csrf), Db.ts(startedAt), Db.ts(expiresAt),
                restrictedUntil != null, Db.ts(restrictedUntil), Db.ts(now))
            .update();
        AuthenticationLevel level = restrictedUntil != null ? AuthenticationLevel.PASSWORD_CHANGE_REQUIRED : AuthenticationLevel.FULL;
        return new Issued(token, csrf, new SessionView(account.id(), account.username(), account.displayName(), level,
            account.administrator(), startedAt, expiresAt, restrictedUntil));
    }

    /** Finds the session behind a token, or nothing when it is unknown, revoked, expired or its account is deactivated. */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolve(String token) {
        Instant now = clock.instant();
        return jdbc.sql("""
                select s.id, s.account_id, s.csrf_hash, s.started_at, s.expires_at, s.restricted, s.restricted_expires_at,
                       a.username, a.display_name, a.administrator, a.active,
                       (select value from identity_setting where name = 'session_duration_hours') as duration_hours
                from identity_session s join identity_account a on a.id = s.account_id
                where s.token_hash = ? and s.revoked_at is null
                """)
            .param(Tokens.hash(token))
            .query((rs, row) -> {
                Instant started = Db.instant(rs, "started_at");
                Instant stored = Db.instant(rs, "expires_at");
                Instant current = started.plus(Duration.ofHours(Long.parseLong(rs.getString("duration_hours"))));
                Instant effective = stored.isBefore(current) ? stored : current;
                Instant restrictedUntil = Db.instant(rs, "restricted_expires_at");
                boolean restricted = rs.getBoolean("restricted");
                boolean alive = rs.getBoolean("active") && now.isBefore(effective) && (!restricted || now.isBefore(restrictedUntil));
                if (!alive) {
                    return null;
                }
                AuthenticationLevel level = restricted ? AuthenticationLevel.PASSWORD_CHANGE_REQUIRED : AuthenticationLevel.FULL;
                SessionView view = new SessionView(rs.getObject("account_id", UUID.class), rs.getString("username"),
                    rs.getString("display_name"), level, rs.getBoolean("administrator"), started, effective, restrictedUntil);
                return new Resolved(rs.getObject("id", UUID.class), view, rs.getBytes("csrf_hash"));
            })
            .optional();
    }

    @Transactional
    public void logout(UUID sessionId) {
        jdbc.sql("update identity_session set revoked_at = ?, revoke_reason = 'logout' where id = ? and revoked_at is null")
            .params(Db.ts(clock.instant()), sessionId).update();
    }

    /**
     * Replaces the password. With the one time password as current password this completes the forced change.
     * The check of the current password, the credential change, the end of all sessions of the account and the
     * new session happen in one transaction, so a one time password cannot be used twice.
     */
    public Issued changePassword(UUID sessionId, String current, String next, String sourceAddress) {
        Instant now = clock.instant();
        String sessionOwner = jdbc.sql("select a.username from identity_session s join identity_account a on a.id = s.account_id where s.id = ?")
            .param(sessionId).query(String.class).optional().orElseThrow(() -> ApiProblemException.of(401, "auth.session_expired"));
        String userSubject = LoginThrottle.userSubject(sessionOwner);
        String sourceSubject = LoginThrottle.sourceSubject(sourceAddress);
        throttle.requireNotBlocked(List.of(userSubject, sourceSubject), now);
        try {
            String currentSecret = current == null ? "" : current;
            String nextSecret = next == null ? "" : next;
            Issued issued = transaction.execute(status -> replacePassword(sessionId, currentSecret, nextSecret, now));
            throttle.recordSuccess(userSubject);
            return issued;
        } catch (ApiProblemException e) {
            if ("password.current_incorrect".equals(e.code())) {
                throttle.recordFailure(userSubject, sourceSubject, now);
            }
            throw e;
        }
    }

    private Issued replacePassword(UUID sessionId, String current, String next, Instant now) {
        UUID accountId = jdbc.sql("select account_id from identity_session where id = ?").param(sessionId).query(UUID.class).single();
        AccountRecord account = accounts.findByIdForUpdate(accountId).orElseThrow();
        // The session may have ended while the account row was locked by a concurrent change.
        Integer open = jdbc.sql("select count(*) from identity_session where id = ? and revoked_at is null").param(sessionId).query(Integer.class).single();
        if (open == 0 || !account.active()) {
            throw ApiProblemException.of(401, "auth.session_expired");
        }
        if (!hasher.matches(current, account.passwordHash())) {
            throw ApiProblemException.of(400, "password.current_incorrect");
        }
        if (account.passwordChangeRequired() && !now.isBefore(account.oneTimeExpiresAt())) {
            throw ApiProblemException.of(401, "auth.one_time_password_expired");
        }
        List<String> violations = PasswordPolicy.violations(next, account.username());
        if (violations.isEmpty() && next.equals(current)) {
            violations = List.of("password.unchanged");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "password.policy_violation", violations);
        }
        Instant started = jdbc.sql("select started_at from identity_session where id = ?").param(sessionId)
            .query((rs, row) -> Db.instant(rs, "started_at")).single();
        Instant expires = jdbc.sql("select expires_at from identity_session where id = ?").param(sessionId)
            .query((rs, row) -> Db.instant(rs, "expires_at")).single();
        accounts.updatePassword(accountId, hasher.hash(next), null, now);
        jdbc.sql("update identity_session set revoked_at = ?, revoke_reason = 'password.changed' where account_id = ? and revoked_at is null")
            .params(Db.ts(now), accountId).update();
        accounts.audit(account.username(), "password.changed", accountId, account.passwordChangeRequired() ? "forced" : "chosen", now);
        AccountRecord updated = accounts.findById(accountId).orElseThrow();
        // Same start and end as the session it replaces: the change must not extend the fixed period.
        Instant effectiveEnd = expires;
        return insertSession(updated, started, effectiveEnd, null, now);
    }

    private void purgeOld(Instant now) {
        jdbc.sql("delete from identity_session where expires_at < ?").param(Db.ts(now.minus(Duration.ofDays(7)))).update();
    }
}
