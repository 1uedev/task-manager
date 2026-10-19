package taskmanager.permissions;

import java.util.UUID;

/**
 * Composition-root port to TASK-024, not an implementation in the identity module (no reverse
 * module dependency). Check ALL identity properties (including company, user, session,
 * password-change state and external/organization status) after the permission lock is acquired.
 * Join the caller's transaction and hold conflicting locks on the checked identity/session rows
 * until its commit; revocation/changes must take incompatible locks. A plain snapshot SELECT or
 * REQUIRES_NEW is insufficient. Lock order: permissions, identity, resource, business object.
 * An absent or ambiguous adapter denies access. Production must not register a test double.
 */
@FunctionalInterface
public interface IdentityAccess {
    boolean isCurrent(AccessContext context);

    /** Keep the checked account stable through commit too; absent implementation denies. */
    default boolean userExists(UUID companyId, UUID userId) { return false; }

    /**
     * True only for an existing, active, internal account of the company, whichever session it has. Same locking
     * rule as {@link #userExists}: the state stays stable through the caller's commit; absent implementation denies.
     * A known but deactivated account is not active.
     */
    default boolean userActive(UUID companyId, UUID userId) { return false; }
}
