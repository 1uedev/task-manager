package taskmanager.projects.internal;

import java.time.Instant;
import java.util.UUID;
import taskmanager.projects.ProjectChanged;

/** A project row. {@code version} moves with every change of the project, its configuration or its members. */
public record Project(UUID id, UUID companyId, String key, String name, String description, long configurationVersion,
        long version, Instant createdAt, Instant updatedAt) {

    /** The event other modules derive their data from (TASK-029). */
    ProjectChanged changed() {
        return new ProjectChanged(id, companyId, key, name, description);
    }
}
