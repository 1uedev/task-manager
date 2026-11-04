package taskmanager.projects;

import java.util.UUID;

/**
 * Whether a portal account may join a project (TASK-075, DEC-105): only a project that the helpdesk released for the
 * organization of the account. Implemented at the application entry point, which knows identity and helpdesk. The project
 * module asks it for portal accounts only; without exactly one implementation it refuses them. Joins the transaction of the
 * caller, after the project lock.
 */
@FunctionalInterface
public interface MembershipAdmission {
    boolean mayJoin(UUID companyId, UUID projectId, UUID accountId);
}
