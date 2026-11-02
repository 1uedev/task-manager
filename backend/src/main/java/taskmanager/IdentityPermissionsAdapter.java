package taskmanager;

import java.time.Instant;
import java.util.Objects;
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
        if (context == null || !ofThisCompany(context.companyId()) || context.passwordChangeRequired()) {
            return Optional.empty();
        }
        Optional<Instant> end = identity.currentFullSessionEnd(context.userId(), context.sessionId());
        if (end.isEmpty()) {
            return end;
        }
        // A portal account is external with the organization of its account (TASK-075); an internal one is neither. A context
        // that claims anything else is not ours. The organization of an account never changes, and its row is locked already.
        Optional<UUID> organization = identity.portalOrganization(context.userId());
        if (context.external() != organization.isPresent() || !Objects.equals(context.organizationId(), organization.orElse(null))) {
            return Optional.empty();
        }
        return end;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean userExists(UUID companyId, UUID userId) {
        return ofThisCompany(companyId) && identity.accountExists(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean userActive(UUID companyId, UUID userId) {
        // Only internal accounts (TASK-075): a portal account never counts for an administration, an assignment or a sending.
        return ofThisCompany(companyId) && identity.accountIsActiveInternal(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean userActiveLocal(UUID companyId, UUID userId) {
        return ofThisCompany(companyId) && identity.accountIsActiveLocal(userId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean userPortal(UUID companyId, UUID userId) {
        return ofThisCompany(companyId) && identity.accountIsPortal(userId);
    }

    private boolean ofThisCompany(UUID companyId) {
        return companyId != null && company.find().filter(companyId::equals).isPresent();
    }
}
