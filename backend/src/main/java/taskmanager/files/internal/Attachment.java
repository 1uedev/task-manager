package taskmanager.files.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * One attachment as stored. A removed attachment keeps its row for the history; {@code purgedAt} marks deleted content.
 * {@code visibility} is {@link #INTERNAL} or {@link #CUSTOMER} (TASK-076): only the latter reaches the portal, and only the portal of
 * {@code organizationId}, the organization it was made visible for (TASK-079, DEC-109); null reaches no portal.
 */
public record Attachment(UUID id, UUID companyId, UUID ticketId, UUID projectId, String name, long size, String sha256,
        Instant uploadedAt, UUID uploadedBy, Instant removedAt, UUID removedBy, Instant purgedAt, String visibility,
        UUID organizationId) {
    public static final String INTERNAL = "internal";
    public static final String CUSTOMER = "customer";

    public boolean customer() {
        return CUSTOMER.equals(visibility);
    }

    /** Whether the portal of the organization shows the attachment. */
    public boolean visibleTo(UUID organization) {
        return customer() && !removed() && organization != null && organization.equals(organizationId);
    }

    public boolean removed() {
        return removedAt != null;
    }
}
