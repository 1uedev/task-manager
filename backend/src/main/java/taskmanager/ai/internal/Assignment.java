package taskmanager.ai.internal;

import java.time.Instant;
import java.util.UUID;
import taskmanager.ai.AiUseCase;

/** Which model serves a use case for the company (TASK-125, rule 4); {@code version} 0 means nothing is stored yet. */
public record Assignment(AiUseCase useCase, UUID modelId, boolean enabled, long version, Instant updatedAt, UUID updatedBy) {
}
