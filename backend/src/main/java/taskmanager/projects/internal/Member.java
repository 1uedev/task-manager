package taskmanager.projects.internal;

import java.time.Instant;
import java.util.UUID;

public record Member(UUID accountId, Instant addedAt, UUID addedBy) {
}
