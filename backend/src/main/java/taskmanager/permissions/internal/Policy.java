package taskmanager.permissions.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.ResourceContext;

/** No database/entity dependencies. A missing allow always denies; field deny wins. */
final class Policy {
    record Action(UUID projectId, String key) {}
    record Field(UUID projectId, String type, String key, String operation, String effect) {}
    private final List<Action> actions;
    private final List<Field> fields;

    Policy(List<Action> actions, List<Field> fields) {
        this.actions = List.copyOf(actions);
        this.fields = List.copyOf(fields);
    }

    boolean allows(String action, UUID projectId) {
        return actions.stream().anyMatch(rule -> rule.key().equals(action) && matches(rule.projectId(), projectId));
    }

    boolean field(String type, String key, String operation, UUID projectId) {
        var matching = fields.stream().filter(rule -> matches(rule.projectId(), projectId)
            && rule.type().equals(type) && rule.key().equals(key) && rule.operation().equals(operation)).toList();
        return matching.stream().anyMatch(rule -> rule.effect().equals("ALLOW"))
            && matching.stream().noneMatch(rule -> rule.effect().equals("DENY"));
    }

    Decision decide(AccessContext actor, ResourceContext resource, String action, Set<String> requested) {
        if (!actor.companyId().equals(resource.companyId())
                || (resource.projectId() != null && !resource.projectMember())
                || (actor.external() && (resource.projectId() == null
                    || !actor.organizationId().equals(resource.organizationId())))
                || !allows(action, resource.projectId())) return Decision.denied();
        Set<String> read = new HashSet<>(), write = new HashSet<>();
        for (String key : requested) {
            if (field(resource.resourceType(), key, "READ", resource.projectId())) {
                read.add(key);
                if (field(resource.resourceType(), key, "WRITE", resource.projectId())) write.add(key);
            }
        }
        return new Decision(true, read, write);
    }

    private static boolean matches(UUID scope, UUID project) { return scope == null || scope.equals(project); }
}
