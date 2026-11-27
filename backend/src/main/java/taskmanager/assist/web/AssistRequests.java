package taskmanager.assist.web;

import java.sql.SQLException;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;

/**
 * What the endpoints of the AI assistance share (TASK-126): the internal caller with a complete login, portal accounts refused
 * with 403 {@code auth.forbidden} before any object is read (TASK-141), and the answer 503
 * {@code assist.busy} with {@code Retry-After} for a lock that could not be taken in time or a deadlock, after which the whole
 * request may be repeated.
 */
@Component
class AssistRequests {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");

    private final AccessContexts identity;
    private final PermissionContexts contexts;

    AssistRequests(AccessContexts identity, PermissionContexts contexts) {
        this.identity = identity;
        this.contexts = contexts;
    }

    /** The permissions context of a request of an internal account with a complete login. */
    AccessContext actor() {
        var request = identity.require();
        if (request.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        AccessContext actor = contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
        // Portal accounts never reach the AI assistance of the support, before any object is read (TASK-141, F4).
        if (actor.external()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return actor;
    }

    static <T> T retrying(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "assist.busy", 1);
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
