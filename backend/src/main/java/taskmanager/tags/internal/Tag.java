package taskmanager.tags.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * One tag as stored; {@code projectId} is {@code null} for the catalog of the company. {@code description} and {@code color}
 * are optional (TASK-072); a merged tag names the tag that took its assignments in {@code mergedInto}.
 */
public record Tag(UUID id, UUID companyId, UUID projectId, String name, String normalized, String description, String color,
        boolean archived, UUID mergedInto, long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {

    public boolean inCompanyCatalog() {
        return projectId == null;
    }

    public boolean merged() {
        return mergedInto != null;
    }
}
