package taskmanager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.IdentityVerification;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.IdentityAccess;
import taskmanager.platform.CompanyContext;

/**
 * The one implementation of the permissions port {@link IdentityAccess} (TASK-039, DEC-063). It lives at the application
 * entry point because neither module may depend on the other in this direction: permissions defines the port, identity
 * offers locking checks, and only this class knows both. Every answer is {@code false} unless the company of the
 * installation exists and is the company of the request; every identity fact is checked in the caller's transaction
 * and stays locked there until its commit.
 */
@Component
class IdentityPermissionsAdapter implements IdentityAccess {
    private final CompanyContext company;
    private final IdentityVerification identity;

    IdentityPermissionsAdapter(CompanyContext company, IdentityVerification identity) {
        this.company = company;
        this.identity = identity;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isCurrent(AccessContext context) {
        return currentUntil(context).isPresent();
    }

    /** The end of the session period, which the permissions module checks again right before the commit (TASK-043). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Instant> currentUntil(AccessContext context) {
        // No portal accounts exist yet, so a context that claims to be external or to belong to an organization is not ours.
        if (context == null || !ofThisCompany(context.companyId())
                || context.passwordChangeRequired() || context.external() || context.organizationId() != null) {
            return Optional.empty();
        }
        return identity.currentFullSessionEnd(context.userId(), context.sessionId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean userExists(UUID companyId, UUID userId) {
        return ofThisCompany(companyId) && identity.accountExists(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean userActive(UUID companyId, UUID userId) {
        // Every account is an internal one until portal accounts exist.
        return ofThisCompany(companyId) && identity.accountIsActive(userId);
    }

    private boolean ofThisCompany(UUID companyId) {
        return companyId != null && company.find().filter(companyId::equals).isPresent();
    }
}
