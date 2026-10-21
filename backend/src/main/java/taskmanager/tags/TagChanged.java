package taskmanager.tags;

import java.util.UUID;

/**
 * Published inside the transaction that created, renamed, archived or restored a tag (TASK-029); {@code projectId} is
 * {@code null} for the catalog of the company. Listeners run synchronously in that transaction.
 */
public record TagChanged(UUID tagId, UUID companyId, UUID projectId, String name) {
}
