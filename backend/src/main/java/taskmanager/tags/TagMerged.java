package taskmanager.tags;

import java.util.UUID;

/**
 * Published inside the transaction that merged the tag {@code sourceId} into {@code targetId} of the same catalog (TASK-072):
 * every assignment of the source now belongs to the target, duplicates are gone, and the source is no longer in its catalog.
 * Listeners run synchronously in that transaction.
 */
public record TagMerged(UUID companyId, UUID sourceId, UUID targetId) {
}
