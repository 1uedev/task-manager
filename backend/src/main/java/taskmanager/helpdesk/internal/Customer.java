package taskmanager.helpdesk.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A customer organization of the company and the projects it is released for (TASK-057). */
public record Customer(UUID id, UUID companyId, String name, List<UUID> projects, long version, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy) {
    public Customer {
        projects = List.copyOf(projects);
    }
}
