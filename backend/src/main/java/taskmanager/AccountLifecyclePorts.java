package taskmanager;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.AccountDeactivationGuard;
import taskmanager.identity.FirstAdministratorHandler;
import taskmanager.permissions.CompanyAdministration;
import taskmanager.platform.CompanyContext;

/**
 * Binds the two ports of the identity module that concern the company administration to the permissions module
 * (TASK-039, DEC-063): the protection of the last administration when an account is deactivated, and the creation of
 * company and initial rights together with the first account.
 */
@Component
class AccountLifecyclePorts implements AccountDeactivationGuard, FirstAdministratorHandler {
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
        administration.initialize(company.create(), accountId);
    }
}
