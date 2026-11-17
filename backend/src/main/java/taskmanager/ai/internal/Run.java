package taskmanager.ai.internal;

import java.time.Instant;
import java.util.UUID;
import taskmanager.ai.AiUseCase;

/** The record of one execution without content (TASK-125, rule 13). */
public record Run(UUID id, UUID companyId, UUID accountId, String kind, AiUseCase useCase, String language, UUID projectId,
        String objectKind, UUID objectId, Long objectVersion, String skillVersion, UUID providerId, UUID modelId, String modelKey,
        String dataPath, String state, String code, boolean truncated, Integer inputTokens, Integer outputTokens, Instant startedAt,
        Instant finishedAt, boolean cancelRequested) {

    public boolean open() {
        return "queued".equals(state) || "running".equals(state);
    }
}
