package taskmanager.ai;

import java.util.UUID;
import taskmanager.permissions.AccessContext;

/**
 * Starts an AI function for a person (TASK-125). The call needs no transaction of its own; inside one, the execution begins
 * after the commit. It answers at once with the identifier of the execution, which the person then reads and can cancel
 * through {@code /api/v1/ai/runs/{id}}. Refusals are problems: 403 {@code ai.use_forbidden} without {@code ai.use} in the
 * project, 409 {@code ai.unavailable} with the reason when the use case has no usable assignment, 429 for a reached daily
 * limit, 503 {@code ai.busy} when all places are taken, 400 {@code ai.context_too_large} when the required sources do not fit.
 */
public interface AiRuns {
    UUID start(AccessContext actor, AiRequest request);
}
