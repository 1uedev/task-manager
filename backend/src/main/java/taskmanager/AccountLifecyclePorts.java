package taskmanager;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.AccountDeactivationGuard;
import taskmanager.identity.EmergencyResetPolicy;
import taskmanager.identity.FirstAdministratorHandler;
import taskmanager.permissions.CompanyAdministration;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;

/**
 * Binds the ports of the identity module that concern the company administration to the permissions module
 * (TASK-039, DEC-063, TASK-040, DEC-064): the protection of the last administration when an account is deactivated, the
 * creation of company and initial rights together with the first account, and the emergency command for a locked out
 * administration. The initial rights are the right to manage rights and every {@link AdministrationRight}.
 */
@Component
class AccountLifecyclePorts implements AccountDeactivationGuard, FirstAdministratorHandler, EmergencyResetPolicy {
    private static final Set<String> INITIAL_RIGHTS = Arrays.stream(AdministrationRight.values())
        .map(AdministrationRight::key).collect(Collectors.toUnmodifiableSet());

    private final CompanyContext company;
    private final CompanyAdministration administration;

    AccountLifecyclePorts(CompanyContext company, CompanyAdministration administration) {
        this.company = company;
        this.administration = administration;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean mayDeactivate(UUID accountId) {
        // Without a company there is no company administration to protect (an installation that was not bootstrapped).
        return company.find().map(id -> administration.remainsWithout(id, accountId)).orElse(true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void firstAdministratorCreated(UUID accountId) {
        administration.initialize(company.create(), accountId, INITIAL_RIGHTS);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean mayReset(UUID accountId) {
        return company.find().map(id -> administration.isAdministrator(id, accountId)).orElse(false);
    }
}
