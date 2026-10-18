package taskmanager.permissions;

import java.util.Objects;
import java.util.UUID;

/** Loaded by the owning module AFTER rights and identity locks. Contains no entity or field values.
 * The supplier joins the same transaction and locks project membership/organization/object scope
 * against changes until commit. Every corresponding writer must use conflicting locks.
 * A cached DTO or plain snapshot read does not implement this contract. */
public record ResourceContext(UUID companyId, UUID projectId, UUID organizationId,
        boolean projectMember, String resourceType) {
    public ResourceContext {
        Objects.requireNonNull(companyId);
        if (resourceType == null || !resourceType.matches("[a-z][a-z0-9_.]{0,119}"))
            throw new IllegalArgumentException("Invalid resource type");
    }

    public static ResourceContext company(UUID companyId) {
        return new ResourceContext(companyId, null, null, false, "permissions");
    }
}
