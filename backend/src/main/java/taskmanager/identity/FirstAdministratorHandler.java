package taskmanager.identity;

import java.util.UUID;

/**
 * Port that the application entry point implements for the container command {@code bootstrap-admin} (TASK-039).
 * It is called in the transaction that creates the first account, so account, company and initial rights appear
 * together or not at all. Exactly one implementation must be present; without one, the command fails and creates nothing.
 */
public interface FirstAdministratorHandler {
    /** The first account exists in the caller's transaction. Failing here rolls the account back. */
    void firstAdministratorCreated(UUID accountId);
}
