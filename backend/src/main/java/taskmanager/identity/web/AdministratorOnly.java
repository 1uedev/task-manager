package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import taskmanager.identity.session.SessionService.Resolved;
import taskmanager.platform.ApiProblemException;

/** The administrative endpoints need the technical administrator marker. Group rights follow with TASK-025. */
final class AdministratorOnly {
    private AdministratorOnly() {
    }

    static Resolved require(HttpServletRequest request) {
        Resolved resolved = (Resolved) request.getAttribute(AuthenticationFilter.RESOLVED);
        if (resolved == null) {
            throw new IllegalStateException("The authentication filter did not run");
        }
        if (!resolved.view().administrator()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return resolved;
    }
}
