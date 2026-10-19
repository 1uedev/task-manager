package taskmanager.identity;

import java.util.UUID;

/**
 * Locking checks of identity facts for the transaction of the caller (TASK-039). The permissions adapter at the
 * application entry point uses them; the identity module never calls the permissions module.
 *
 * <p>Every method needs a running transaction and keeps the checked rows stable until its commit: the rows are held
 * with a shared lock, and every revocation path of this module (logout, deactivation, password reset, password change,
 * shortening of the session period) updates them and therefore waits for that commit. A check that lost against a
 * committed revocation answers {@code false}. Lock order inside this module: account row, then session row.
 */
public interface IdentityVerification {
    /**
     * True only if the session belongs to the account, is open and inside its fixed period, has the full login level, and
     * the account is active and has no pending forced password change. Both rows stay locked until the commit.
     */
    boolean isCurrentFullSession(UUID accountId, UUID sessionId);

    /** True if an account with this identifier exists, active or not. The row stays locked until the commit. */
    boolean accountExists(UUID accountId);

    /** True if the account exists and is active. The row stays locked until the commit. */
    boolean accountIsActive(UUID accountId);
}
