package taskmanager.identity.session;

import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.account.AccountRepository;
import taskmanager.identity.account.Db;
import taskmanager.platform.ApiProblemException;

/**
 * The setting for the fixed session period in hours. A new installation starts with 24 hours
 * (migration). The value is always read from the database, never cached. A shortened value is written into
 * the sessions that already exist when it is set and takes effect at their next request; it is never undone for
 * them. An extended value only applies to later logins.
 */
@Service
public class SessionDuration {
    public static final int MIN_HOURS = 1;
    public static final int MAX_HOURS = 720;
    static final String SETTING = "session_duration_hours";

    private final JdbcClient jdbc;
    private final AccountRepository accounts;
    private final Clock clock;

    SessionDuration(JdbcClient jdbc, AccountRepository accounts, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.clock = clock;
    }

    public int hours() {
        return Integer.parseInt(jdbc.sql("select value from identity_setting where name = ?").param(SETTING).query(String.class).single());
    }

    /** Reads the setting with a shared row lock; use it in the transaction that inserts a session. */
    public int hoursForShare() {
        return Integer.parseInt(jdbc.sql("select value from identity_setting where name = ? for share").param(SETTING).query(String.class).single());
    }

    @Transactional
    public void change(String actor, Integer hours) {
        change(actor, hours, () -> {
        });
    }

    /**
     * Changes the setting. The setting row is locked first, as in a login and in a password change, which read it before they
     * lock the account; the administrative serialization follows, because the change writes the sessions of every account
     * (TASK-041); {@code authorize} then runs in the same transaction and takes the rights and identity locks of the
     * requester behind it. Whatever it throws ends the change, and nothing is written before it returned.
     */
    @Transactional
    public void change(String actor, Integer hours, Runnable authorize) {
        jdbc.sql("select value from identity_setting where name = ? for update").param(SETTING).query(String.class).single();
        accounts.serializeAdministration();
        authorize.run();
        if (hours == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("hours.required"));
        }
        if (hours < MIN_HOURS || hours > MAX_HOURS) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("hours.out_of_range"));
        }
        var now = clock.instant();
        jdbc.sql("update identity_setting set value = ?, updated_at = ? where name = ?").params(Integer.toString(hours), Db.ts(now), SETTING).update();
        // A shortened period is final for the sessions that exist now: a later extension must not revive them.
        jdbc.sql("""
                update identity_session set expires_at = started_at + make_interval(hours => ?)
                where revoked_at is null and expires_at > started_at + make_interval(hours => ?)
                """).params(hours, hours).update();
        accounts.audit(actor, "settings.session_duration_changed", null, hours + "h", now);
    }
}
