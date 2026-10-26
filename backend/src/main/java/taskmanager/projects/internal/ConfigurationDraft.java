package taskmanager.projects.internal;

import java.time.Instant;
import java.util.UUID;
import taskmanager.projects.ProjectConfiguration;

/** The draft of the configuration of a project (TASK-044): the published version it started from and its revision. */
public record ConfigurationDraft(UUID projectId, long baseVersion, long revision, ProjectConfiguration configuration, Instant updatedAt,
        UUID updatedBy) {
}
