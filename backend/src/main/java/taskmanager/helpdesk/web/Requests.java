package taskmanager.helpdesk.web;

import java.sql.SQLException;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import taskmanager.helpdesk.internal.HelpdeskAccess;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;

/**
 * What the endpoints of the helpdesk share (TASK-057): the caller with a complete login, the version of If-Match and the
 * answer 503 {@code helpdesk.busy} with {@code Retry-After} for a lock that could not be taken in time or a deadlock, after
 * which the whole request may be repeated.
 */
@Component
class Requests {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");

    private final AccessContexts identity;
    private final PermissionContexts contexts;
    private final HelpdeskAccess rights;

    Requests(AccessContexts identity, PermissionContexts contexts, HelpdeskAccess rights) {
        this.identity = identity;
        this.contexts = contexts;
        this.rights = rights;
    }

    /** The account of a request with a complete login that holds {@code helpdesk.manage} right now; the service decides. */
    taskmanager.identity.AccessContext administrator() {
        var context = identity.require();
        if (context.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        if (!rights.holdsManage()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return context;
    }

    /** The permissions context of a request with a complete login. */
    AccessContext actor() {
        var request = identity.require();
        if (request.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "helpdesk.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "helpdesk.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "helpdesk.version_conflict");
        }
    }

    static String etag(long version) {
        return "\"" + version + "\"";
    }

    static <T> T retrying(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "helpdesk.busy", 1);
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
