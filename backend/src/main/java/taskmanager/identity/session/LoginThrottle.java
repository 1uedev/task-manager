package taskmanager.identity.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.account.Db;
import taskmanager.identity.config.IdentityProperties;
import taskmanager.platform.ApiProblemException;

/**
 * Growing waiting time after repeated failures, per attempted login name and per source address, instead
 * of locking accounts (a lock would let anyone shut an administrator out on purpose). The counters are kept
 * under a hash of the attempted name, whether or not the account exists, so the behavior does not reveal
 * which names exist. Failures are recorded in their own transaction so they survive the rejected request.
 */
@Component
public class LoginThrottle {
    private static final Duration FORGET_AFTER = Duration.ofHours(1);
    private static final Duration PURGE_AFTER = Duration.ofDays(1);

    private final JdbcClient jdbc;
    private final IdentityProperties properties;

    LoginThrottle(JdbcClient jdbc, IdentityProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public static String userSubject(String attemptedName) {
        String bounded = attemptedName.trim().toLowerCase(java.util.Locale.ROOT);
        if (bounded.length() > 256) {
            bounded = bounded.substring(0, 256);
        }
        return "user:" + sha256Hex(bounded);
    }

    public static String sourceSubject(String address) {
        String bounded = address == null ? "unknown" : address;
        return "ip:" + (bounded.length() > 64 ? sha256Hex(bounded) : bounded);
    }

    /** Refuses the attempt while a waiting time is running. Does not count the refused attempt. */
    @Transactional(readOnly = true)
    public void requireNotBlocked(List<String> subjects, Instant now) {
        for (String subject : subjects) {
            Instant until = jdbc.sql("select blocked_until from identity_login_throttle where subject = ?")
                .param(subject).query((rs, row) -> Db.instant(rs, "blocked_until")).optional().orElse(null);
            if (until != null && until.isAfter(now)) {
                long seconds = Math.max(1, Duration.between(now, until).plusMillis(999).toSeconds());
                throw ApiProblemException.retryAfter(429, "auth.too_many_attempts", (int) Math.min(seconds, Integer.MAX_VALUE));
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String userSubject, String sourceSubject, Instant now) {
        count(userSubject, properties.userFailureThreshold(), now);
        count(sourceSubject, properties.sourceFailureThreshold(), now);
        jdbc.sql("delete from identity_login_throttle where last_failure_at < ?").param(Db.ts(now.minus(PURGE_AFTER))).update();
    }

    /** A correct login clears only the counter of that login name. The source counter must not be resettable by one's own valid account. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String userSubject) {
        jdbc.sql("delete from identity_login_throttle where subject = ?").param(userSubject).update();
    }

    private void count(String subject, int threshold, Instant now) {
        int failures = jdbc.sql("""
                insert into identity_login_throttle (subject, failures, last_failure_at, blocked_until) values (?, 1, ?, null)
                on conflict (subject) do update set
                    failures = case when identity_login_throttle.last_failure_at < ? then 1 else identity_login_throttle.failures + 1 end,
                    last_failure_at = excluded.last_failure_at
                returning failures
                """)
            .params(subject, Db.ts(now), Db.ts(now.minus(FORGET_AFTER))).query(Integer.class).single();
        Instant blockedUntil = null;
        if (failures >= threshold) {
            long factor = 1L << Math.min(failures - threshold, 20);
            Duration delay = properties.baseDelay().multipliedBy(factor);
            if (delay.compareTo(properties.maxDelay()) > 0) {
                delay = properties.maxDelay();
            }
            blockedUntil = now.plus(delay);
        }
        jdbc.sql("update identity_login_throttle set blocked_until = ? where subject = ?").params(Db.ts(blockedUntil), subject).update();
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
