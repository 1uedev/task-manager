package taskmanager.permissions;

/**
 * Which action keys are company rights (TASK-075, DEC-105): rights of the administration of the company that a portal account
 * may never hold, granted without a project. Implemented at the application entry point, which knows the catalog of the
 * administration rights. Without exactly one implementation every action without a project counts as a company right, so a
 * portal account joins no group that holds one.
 */
@FunctionalInterface
public interface CompanyActions {
    boolean isCompanyAction(String actionKey);
}
