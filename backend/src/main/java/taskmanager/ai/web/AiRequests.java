package taskmanager.ai.web;

import java.sql.SQLException;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import taskmanager.ai.internal.ConfigService;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;

/**
 * What the endpoints of the AI foundation share (TASK-125): the internal person with a complete login (portal accounts never
 * use AI, DEC-035), the version of If-Match and the answer 503 {@code ai.db_busy} with {@code Retry-After} for a lock that
 * could not be taken in time or a deadlock, after which the whole request may be repeated.
 */
@Component
class AiRequests {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");

    private final AccessContexts identity;
    private final PermissionContexts contexts;
    private final ConfigService config;

    AiRequests(AccessContexts identity, PermissionContexts contexts, ConfigService config) {
        this.identity = identity;
        this.contexts = contexts;
        this.config = config;
    }

    /** The internal account of a request with a complete login. */
    taskmanager.identity.AccessContext person() {
        var context = identity.require();
        if (context.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        if (context.portal()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return context;
    }

    /** The account of a request that holds {@code ai.manage} right now; the service decides in its transaction. */
    taskmanager.identity.AccessContext administrator() {
        var context = person();
        if (!config.holdsManage()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return context;
    }

    /** The permissions context of an internal request with a complete login. */
    AccessContext actor() {
        person();
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "ai.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
    }

    /** An If-Match tag as it was given, without quotes; 428 without one. */
    static String expectedTag(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "ai.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        return value.substring(1, value.length() - 1);
    }

    static String etag(String tag) {
        return "\"" + tag + "\"";
    }

    static String etag(long version) {
        return "\"" + version + "\"";
    }

    static <T> T retrying(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "ai.db_busy", 1);
            }
            throw failure;
        }
    }

    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || (cause instanceof SQLException sql && RETRYABLE_STATES.contains(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
}
