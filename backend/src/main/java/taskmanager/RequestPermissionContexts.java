package taskmanager;

import java.util.Optional;
import org.springframework.stereotype.Component;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.platform.CompanyContext;

/**
 * Builds the permissions context of the current request (TASK-039, DEC-063) from the identity of the request, the company
 * of the installation and the current rights version. The company never comes from the client. The context is only a
 * claim: {@code IdentityPermissionsAdapter} checks it again, with locks, whenever permissions are enforced.
 */
@Component
class RequestPermissionContexts implements PermissionContexts {
    private final AccessContexts identity;
    private final CompanyContext company;
    private final Permissions permissions;

    RequestPermissionContexts(AccessContexts identity, CompanyContext company, Permissions permissions) {
        this.identity = identity;
        this.company = company;
        this.permissions = permissions;
    }

    @Override
    public Optional<AccessContext> current() {
        var request = identity.current();
        var companyId = company.find();
        // A login that is not complete (forced password change) never yields a context, as the port promises.
        if (request.isEmpty() || request.get().level() != AuthenticationLevel.FULL || companyId.isEmpty()) {
            return Optional.empty();
        }
        try {
            long version = permissions.currentVersion(companyId.get());
            var who = request.get();
            // No portal accounts yet: never external, without organization.
            return Optional.of(new AccessContext(companyId.get(), who.accountId(), who.sessionId(), version,
                false, false, null));
        } catch (PermissionDenied unknownCompany) {
            return Optional.empty();
        }
    }
}
