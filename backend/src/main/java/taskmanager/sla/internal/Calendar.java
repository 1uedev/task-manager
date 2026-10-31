package taskmanager.sla.internal;

import java.time.Instant;
import java.util.UUID;

/** A stored business hours calendar with its current version (TASK-068). */
public record Calendar(UUID id, UUID companyId, String name, WorkCalendar.Definition definition, long version, Instant createdAt,
        UUID createdBy, Instant updatedAt, UUID updatedBy) {
}
