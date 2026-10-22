package taskmanager.permissions;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public interface Permissions {
    /** Internal context assembly only. Unknown companies fail closed; not an HTTP endpoint. */
    long currentVersion(UUID companyId);

    /** Read snapshot only. Read/project/serialize content within this transaction when consistency
     * with revocation matters; never use this result as a later write capability. A refusal is a denied decision, but a
     * lock timeout or deadlock still ends in a database error, like {@link #require}. */
    Decision inspect(AccessContext actor, Supplier<ResourceContext> resource, String action, Set<String> fields);

    /** Existing READ COMMITTED transaction required; hold the shared rights lock until its commit.
     * IdentityAccess and the resource supplier must also hold their documented locks until commit.
     * Invoke before business reads/writes. One DataSource/transaction manager; no REQUIRES_NEW.
     * The check waits at most five seconds for each lock; the lock timeout of the transaction is restored afterwards.
     * Under load it can therefore end with a lock timeout or deadlock error instead of PermissionDenied; that error aborts
     * the transaction, and the caller answers it as retryable and repeats the whole transaction, never only this call.
     * Right before the commit the end of the checked session is compared with the clock again; a session that ended in
     * between makes the commit throw PermissionDenied and roll back (TASK-043). */
    void require(AccessContext actor, Supplier<ResourceContext> resource, String action,
        Set<String> readFields, Set<String> writeFields);
}
