package taskmanager.files.internal;

import java.time.Instant;
import java.util.UUID;

/** One attachment as stored. A removed attachment keeps its row for the history; {@code purgedAt} marks deleted content. */
public record Attachment(UUID id, UUID companyId, UUID ticketId, UUID projectId, String name, long size, String sha256,
        Instant uploadedAt, UUID uploadedBy, Instant removedAt, UUID removedBy, Instant purgedAt) {

    public boolean removed() {
        return removedAt != null;
    }
}
