package taskmanager.ai.internal;

import java.time.Instant;
import java.util.UUID;

/** A model of one provider access (TASK-125, rule 3): the identifier the provider knows and its limits in tokens. */
public record AiModel(UUID id, UUID companyId, UUID providerId, String name, String modelKey, int contextTokens, int outputTokens,
        boolean enabled, long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy, Instant removedAt) {

    public boolean removed() {
        return removedAt != null;
    }
}
