package taskmanager.permissions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Mutations join an existing transaction and serialize against protected business writes. A change that leaves everything as
 * it was (the same member added again, an absent rule removed) writes nothing: no new rights version, no audit entry.
 * Do not call a mutation in a transaction that already ran {@link Permissions#inspect} or {@link Permissions#require}: the
 * shared rights lock taken there cannot be upgraded to the exclusive one without risking a deadlock.
 */
public interface PermissionAdministration {
    enum Operation { READ, WRITE }
    enum Effect { ALLOW, DENY, REMOVE }
    record Group(UUID id, String name) {}
    record Audit(UUID id, UUID actorId, Instant occurredAt, String operation, UUID groupId,
        UUID subjectId, UUID projectId, String action, String resourceType, String field,
        String effect, String previousEffect, boolean previousRecorded, long rightsVersion) {}
    record ActionRule(UUID projectId, String action) {}
    record FieldRule(UUID projectId, String resourceType, String field, Operation operation, Effect effect) {}
    /** A group with everything it grants; members, actions and fields are sorted, company scope (no project) first. */
    record GroupDetail(Group group, List<UUID> members, List<ActionRule> actions, List<FieldRule> fields) {}
    /** A group as a project administration sees it: only the rules of that one project, no members (TASK-043). */
    record ProjectGroup(Group group, List<ActionRule> actions, List<FieldRule> fields) {}

    /** Refused with {@link GroupNameTaken} if the company has a group of that name, compared without regard to case. */
    Group createGroup(AccessContext actor, String name);
    void setMember(AccessContext actor, UUID groupId, UUID userId, boolean enabled);
    void setAction(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String action, boolean enabled);
    void setField(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String field, Operation operation, Effect effect);
    List<Group> groups(AccessContext actor);
    /** Company administration only, like {@link #groups}; empty for a group the company does not have (TASK-030). */
    Optional<GroupDetail> group(AccessContext actor, UUID groupId);
    /**
     * Every group of the company with its rules in the project, for whoever may administer rights there: the company
     * administration or an internal account with {@code permissions.manage} for that project (TASK-043).
     */
    List<ProjectGroup> projectGroups(AccessContext actor, Supplier<ResourceContext> project);
    /**
     * Newest first, at most {@code limit} (1 to 100) entries; with {@code before}, only entries with a lower rights version,
     * so the lowest version of one page fetches the next. Needs the company right {@code permissions.audit.read}.
     */
    List<Audit> audit(AccessContext actor, Long before, int limit);
}
