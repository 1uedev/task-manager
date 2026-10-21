package taskmanager.permissions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Mutations join an existing transaction and serialize against protected business writes. */
public interface PermissionAdministration {
    enum Operation { READ, WRITE }
    enum Effect { ALLOW, DENY, REMOVE }
    record Group(UUID id, String name) {}
    record Audit(UUID id, UUID actorId, Instant occurredAt, String operation, UUID groupId,
        UUID subjectId, UUID projectId, String action, String resourceType, String field,
        String effect, long rightsVersion) {}
    record ActionRule(UUID projectId, String action) {}
    record FieldRule(UUID projectId, String resourceType, String field, Operation operation, Effect effect) {}
    /** A group with everything it grants; members, actions and fields are sorted, company scope (no project) first. */
    record GroupDetail(Group group, List<UUID> members, List<ActionRule> actions, List<FieldRule> fields) {}

    Group createGroup(AccessContext actor, String name);
    void setMember(AccessContext actor, UUID groupId, UUID userId, boolean enabled);
    void setAction(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String action, boolean enabled);
    void setField(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String field, Operation operation, Effect effect);
    List<Group> groups(AccessContext actor);
    /** Company administration only, like {@link #groups}; empty for a group the company does not have (TASK-030). */
    Optional<GroupDetail> group(AccessContext actor, UUID groupId);
    List<Audit> audit(AccessContext actor, int limit);
}
