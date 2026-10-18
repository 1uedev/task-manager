package taskmanager.permissions;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public interface Permissions {
    /** Internal context assembly only. Unknown companies fail closed; not an HTTP endpoint. */
    long currentVersion(UUID companyId);

    /** Read snapshot only. Read/project/serialize content within this transaction when consistency
     * with revocation matters; never use this result as a later write capability. */
    Decision inspect(AccessContext actor, Supplier<ResourceContext> resource, String action, Set<String> fields);

    /** Existing READ COMMITTED transaction required; hold the shared rights lock until its commit.
     * IdentityAccess and the resource supplier must also hold their documented locks until commit.
     * Invoke before business reads/writes. One DataSource/transaction manager; no REQUIRES_NEW. */
    void require(AccessContext actor, Supplier<ResourceContext> resource, String action,
        Set<String> readFields, Set<String> writeFields);
}
