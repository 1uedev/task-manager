package taskmanager.permissions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The groups of the company for other modules (TASK-070), such as the escalation stages of the SLA rules that name a group
 * as recipient. Only names and memberships; what a group may do stays with {@link Permissions}.
 */
public interface GroupDirectory {
    record Summary(UUID id, String name) {
    }

    /** The groups of the company, by name. */
    List<Summary> groups(UUID companyId);

    /** The groups the account is a member of. */
    Set<UUID> groupsOf(UUID companyId, UUID accountId);
}
