package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.identity.session.SessionService.Resolved;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * What the administrative endpoints need to ask for a right (TASK-040, DEC-064). The services behind them do not decide
 * who may call them, because the container commands and the tests call them as well; the endpoints ask here instead, in the
 * transaction of the change, so the rights lock and the identity locks of the requester are held until its commit.
 * Without exactly one {@link AdministrationRights} implementation every request is refused.
 */
@Component
class AdministrationRequests {
    private final ObjectProvider<AdministrationRights> rights;
    private final TransactionTemplate transaction;

    AdministrationRequests(ObjectProvider<AdministrationRights> rights, PlatformTransactionManager transactions) {
        this.rights = rights;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** The session of the request. The authentication filter has already refused every request without a complete login. */
    static Resolved actor(HttpServletRequest request) {
        Resolved resolved = (Resolved) request.getAttribute(AuthenticationFilter.RESOLVED);
        if (resolved == null) {
            throw new IllegalStateException("The authentication filter did not run");
        }
        return resolved;
    }

    /** Runs the action in one transaction whose first step is the check of the right. */
    <T> T run(AdministrationRight right, Supplier<T> action) {
        return transaction.execute(status -> {
            require(right);
            return action.get();
        });
    }

    /** The authoritative check; call it inside the transaction of the change. */
    void require(AdministrationRight right) {
        var port = rights.getIfUnique();
        if (port == null) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        port.require(right);
    }

    /**
     * A snapshot check for requests that would otherwise do work or take locks before the authoritative check. It refuses
     * only what is clearly not allowed and never replaces {@link #require}.
     */
    void early(AdministrationRight right) {
        var port = rights.getIfUnique();
        if (port == null || !port.held().contains(right)) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
    }
}
