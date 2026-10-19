package taskmanager;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.PermissionContexts;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * The one implementation of the platform port {@link AdministrationRights} (TASK-040, DEC-064). It asks the permissions
 * module for the company wide action of each right, with the context of the current request: company, account and session
 * come from the server, and the permissions module checks the context again with locks. A request without a usable context,
 * a stale context and a missing right are the same answer: 403 {@code auth.forbidden}, which tells nothing about which of them it was.
 */
@Component
class AdministrationRightsAdapter implements AdministrationRights {
    private final PermissionContexts contexts;
    private final Permissions permissions;

    AdministrationRightsAdapter(PermissionContexts contexts, Permissions permissions) {
        this.contexts = contexts;
        this.permissions = permissions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void require(AdministrationRight right) {
        var context = contexts.current().orElseThrow(AdministrationRightsAdapter::forbidden);
        try {
            permissions.require(context, () -> ResourceContext.company(context.companyId()), right.key(), Set.of(), Set.of());
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
    }

    /** One transaction for the whole snapshot, so the rights version and the identity facts do not change between the rights. */
    @Override
    @Transactional
    public Set<AdministrationRight> held() {
        var context = contexts.current();
        var held = EnumSet.noneOf(AdministrationRight.class);
        if (context.isEmpty()) {
            return held;
        }
        for (AdministrationRight right : AdministrationRight.values()) {
            var decision = permissions.inspect(context.get(), () -> ResourceContext.company(context.get().companyId()), right.key(), Set.of());
            if (decision.allowed()) {
                held.add(right);
            }
        }
        return held;
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
