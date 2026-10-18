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
 * The administrator setting for the fixed session period in hours. A new installation starts with 24 hours
 * (migration). The value is always read from the database, never cached. A shortened value takes effect for
 * sessions that already exist at their next request, an extended value only for later logins.
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

    @Transactional
    public void change(String actor, Integer hours) {
        if (hours == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("hours.required"));
        }
        if (hours < MIN_HOURS || hours > MAX_HOURS) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("hours.out_of_range"));
        }
        var now = clock.instant();
        jdbc.sql("update identity_setting set value = ?, updated_at = ? where name = ?").params(Integer.toString(hours), Db.ts(now), SETTING).update();
        accounts.audit(actor, "settings.session_duration_changed", null, hours + "h", now);
    }
}
