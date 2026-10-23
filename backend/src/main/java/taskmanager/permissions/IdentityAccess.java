package taskmanager.permissions;

import java.time.Instant;
import java.util.Optional;
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

    /**
     * The same locking check as {@link #isCurrent}, together with the moment until which its answer holds: the end of the
     * session period. Empty whenever {@link #isCurrent} would be false. The permissions module compares the end with the
     * clock again right before the commit of a protected change, because the locks keep every fact stable but not the time
     * (TASK-043). The default knows no period and only asks {@link #isCurrent}.
     */
    default Optional<Instant> currentUntil(AccessContext context) {
        return isCurrent(context) ? Optional.of(Instant.MAX) : Optional.empty();
    }

    /** Keep the checked account stable through commit too; absent implementation denies. */
    default boolean userExists(UUID companyId, UUID userId) { return false; }

    /**
     * True only for an existing, active, internal account of the company, whichever session it has. Same locking
     * rule as {@link #userExists}: the state stays stable through the caller's commit; absent implementation denies.
     * A known but deactivated account is not active.
     */
    default boolean userActive(UUID companyId, UUID userId) { return false; }

    /**
     * True only for an active internal account of the company that signs in with a password of this application and not
     * through a directory (TASK-047). Same locking rule as {@link #userActive}; absent implementation denies.
     */
    default boolean userActiveLocal(UUID companyId, UUID userId) { return false; }
}
