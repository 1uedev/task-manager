package taskmanager.time.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The effective state of a time entry; a cancelled entry counts in no sum. */
public record TimeEntry(UUID id, UUID companyId, UUID projectId, UUID ticketId, UUID personId, LocalDate date, int minutes,
        String description, Instant cancelledAt, UUID cancelledBy, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy,
        long version) {
    public boolean cancelled() {
        return cancelledAt != null;
    }
}
