package taskmanager.files.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * One attachment as stored. A removed attachment keeps its row for the history; {@code purgedAt} marks deleted content.
 * {@code visibility} is {@link #INTERNAL} or {@link #CUSTOMER} (TASK-076): only the latter reaches the portal.
 */
public record Attachment(UUID id, UUID companyId, UUID ticketId, UUID projectId, String name, long size, String sha256,
        Instant uploadedAt, UUID uploadedBy, Instant removedAt, UUID removedBy, Instant purgedAt, String visibility) {
    public static final String INTERNAL = "internal";
    public static final String CUSTOMER = "customer";

    public boolean customer() {
        return CUSTOMER.equals(visibility);
    }

    public boolean removed() {
        return removedAt != null;
    }
}
