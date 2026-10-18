package taskmanager.permissions;

import java.time.Instant;
import java.util.List;
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

    Group createGroup(AccessContext actor, String name);
    void setMember(AccessContext actor, UUID groupId, UUID userId, boolean enabled);
    void setAction(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String action, boolean enabled);
    void setField(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String field, Operation operation, Effect effect);
    List<Group> groups(AccessContext actor);
    List<Audit> audit(AccessContext actor, int limit);
}
