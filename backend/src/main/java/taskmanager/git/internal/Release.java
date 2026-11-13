package taskmanager.git.internal;

import java.time.Instant;
import java.util.UUID;

/** A repository released for one project, by the stable identifier of its provider (TASK-111). */
public record Release(UUID id, UUID companyId, UUID projectId, UUID providerId, String externalId, Instant releasedAt, UUID releasedBy) {
}
