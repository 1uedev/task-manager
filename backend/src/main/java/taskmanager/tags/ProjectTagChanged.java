package taskmanager.tags;

import java.util.UUID;

/**
 * Published inside the transaction that actually assigned a tag to a project or removed it (TASK-029); a repeated call
 * publishes nothing. Listeners run synchronously in that transaction.
 */
public record ProjectTagChanged(UUID projectId, UUID tagId, boolean assigned) {
}
