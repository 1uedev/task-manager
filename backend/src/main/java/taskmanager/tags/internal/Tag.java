package taskmanager.tags.internal;

import java.time.Instant;
import java.util.UUID;

/** One tag as stored; {@code projectId} is {@code null} for the catalog of the company. */
public record Tag(UUID id, UUID companyId, UUID projectId, String name, String normalized, boolean archived, long version,
        Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {

    public boolean inCompanyCatalog() {
        return projectId == null;
    }
}
