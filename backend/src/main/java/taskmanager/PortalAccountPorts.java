package taskmanager;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.helpdesk.CustomerDirectory;
import taskmanager.identity.IdentityVerification;
import taskmanager.permissions.CompanyActions;
import taskmanager.platform.AdministrationRight;
import taskmanager.projects.MembershipAdmission;

/**
 * The ports of the portal accounts that need knowledge of several modules (TASK-075, DEC-105): which actions are company rights
 * that a portal account never holds, and which projects a portal account may join. Both live at the application entry point,
 * because no module may know the others in this direction.
 */
@Component
class PortalAccountPorts implements CompanyActions, MembershipAdmission {
    /** Every administration right and the reading of the audit trail of the rights. */
    private static final Set<String> COMPANY_ACTIONS = Stream.concat(
            Arrays.stream(AdministrationRight.values()).map(AdministrationRight::key), Stream.of("permissions.audit.read"))
        .collect(Collectors.toUnmodifiableSet());

    private final IdentityVerification identity;
    private final CustomerDirectory customers;

    PortalAccountPorts(IdentityVerification identity, CustomerDirectory customers) {
        this.identity = identity;
        this.customers = customers;
    }

    @Override
    public boolean isCompanyAction(String actionKey) {
        return COMPANY_ACTIONS.contains(actionKey);
    }

    /** A portal account joins only a project that the helpdesk released for its organization; any other account may. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean mayJoin(UUID companyId, UUID projectId, UUID accountId) {
        var organization = identity.portalOrganization(accountId);
        if (organization.isEmpty()) {
            return true;
        }
        return customers.customer(companyId, organization.get()).map(customer -> customer.projects().contains(projectId)).orElse(false);
    }
}
