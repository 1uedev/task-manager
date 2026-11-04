package taskmanager.tags.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * One tag as stored; {@code projectId} is {@code null} for the catalog of the company. {@code description} and {@code color}
 * are optional (TASK-072); a merged tag names the tag that took its assignments in {@code mergedInto}. {@code visibility} is
 * {@value #INTERNAL} or {@value #CUSTOMER}; {@code organizationId} names the only customer organization that sees a tag a
 * portal account created, {@code null} for every customer (TASK-077, DEC-108).
 */
public record Tag(UUID id, UUID companyId, UUID projectId, String name, String normalized, String description, String color,
        boolean archived, UUID mergedInto, long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy,
        String visibility, UUID organizationId) {
    public static final String INTERNAL = "internal";
    public static final String CUSTOMER = "customer";

    public boolean inCompanyCatalog() {
        return projectId == null;
    }

    public boolean merged() {
        return mergedInto != null;
    }

    /** Whether the portal shows the tag to the organization: visible to customers, for everyone or for this organization. */
    public boolean visibleTo(UUID organization) {
        return CUSTOMER.equals(visibility) && organization != null && (organizationId == null || organizationId.equals(organization));
    }
}
