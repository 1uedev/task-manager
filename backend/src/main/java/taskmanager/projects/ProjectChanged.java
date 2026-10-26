package taskmanager.projects;

import java.util.UUID;

/**
 * Published inside the transaction that created a project or changed its name or description, after the change is written
 * (TASK-029). Listeners run synchronously in that transaction.
 */
public record ProjectChanged(UUID projectId, UUID companyId, String key, String name, String description) {
}
