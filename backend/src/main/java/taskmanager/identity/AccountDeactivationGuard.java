package taskmanager.identity;

import java.util.UUID;

/**
 * Port that the application entry point implements to protect what other modules require of the accounts (TASK-039).
 * The identity module calls it at the start of the deactivation transaction, before it locks the account, because the
 * lock order is: rights, then identity. Exactly one implementation must be present; without one, deactivation fails
 * with an internal error and nothing is deactivated.
 */
public interface AccountDeactivationGuard {
    /** True if the account may be deactivated. Joins the caller's transaction and may hold locks until its commit. */
    boolean mayDeactivate(UUID accountId);
}
