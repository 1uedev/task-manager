package taskmanager.sla.web;

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
import taskmanager.sla.internal.SlaAdministration;

/**
 * What the endpoints of the SLA module share (TASK-068): the caller with a complete login, the version of If-Match and the
 * answer 503 {@code sla.busy} with {@code Retry-After} for a lock that could not be taken in time or a deadlock, after which
 * the whole request may be repeated.
 */
@Component
class SlaRequests {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");

    private final AccessContexts identity;
    private final PermissionContexts contexts;
    private final SlaAdministration administration;

    SlaRequests(AccessContexts identity, PermissionContexts contexts, SlaAdministration administration) {
        this.identity = identity;
        this.contexts = contexts;
        this.administration = administration;
    }

    /** The account of a request with a complete login that holds {@code sla.manage} right now; the service decides. */
    taskmanager.identity.AccessContext administrator() {
        var context = identity.require();
        if (context.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        if (!administration.holdsManage()) {
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
            throw ApiProblemException.of(428, "sla.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "sla.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "sla.version_conflict");
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
                throw ApiProblemException.retryAfter(503, "sla.busy", 1);
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
