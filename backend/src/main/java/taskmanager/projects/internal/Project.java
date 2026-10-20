package taskmanager.projects.internal;

import java.time.Instant;
import java.util.UUID;

/** A project row. {@code version} moves with every change of the project, its configuration or its members. */
public record Project(UUID id, UUID companyId, String key, String name, String description, long configurationVersion,
        long version, Instant createdAt, Instant updatedAt) {
}
