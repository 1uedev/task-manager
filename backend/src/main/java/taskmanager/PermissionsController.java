package taskmanager;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.GroupNameTaken;
import taskmanager.permissions.LastAdministration;
import taskmanager.permissions.PermissionAdministration;
import taskmanager.permissions.PermissionAdministration.Effect;
import taskmanager.permissions.PermissionAdministration.GroupDetail;
import taskmanager.permissions.PermissionAdministration.Operation;
import taskmanager.permissions.PermissionContexts;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;

/**
 * The administration of groups, members, actions and field rules over HTTP (TASK-030, DEC-070). It lives at the application
 * entry point because a rule for one project needs the project adapter of the projects module, and the permissions module
 * must not depend on the projects module. Every endpoint needs the company right {@code permissions.manage}; the permissions
 * module decides that with its locks, here only the keys are checked against the catalog the product modules enforce.
 *
 * <p>A project administration (an internal account with {@code permissions.manage} for one project) reaches the rules of
 * that project only: it reads them through {@code /projects/{id}/groups} and changes them through the same endpoints as the
 * company administration, which then answer 204 because it may not read the whole group (TASK-043).
 *
 * <p>A change runs in one transaction. A rights change by someone else moves the rights version between building the
 * context of the request and taking the rights lock, which the permissions module refuses; such a change is tried once more
 * with a fresh context, which checks everything again.
 */
@RestController
@RequestMapping("/api/v1/permissions")
class PermissionsController {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");
    static final String TICKET = "ticket";
    /** Company wide only: no project can narrow them. */
    static final List<String> COMPANY_ACTIONS = List.of("accounts.manage", "appearance.manage", "permissions.audit.read",
        "permissions.manage", "projects.manage", "session_duration.manage");
    /** For one project, or for every project the account is a member of when granted without a project. */
    static final List<String> PROJECT_ACTIONS = List.of("project.manage", "project.read", "tag.create", "tags.manage",
        "ticket.create", "ticket.edit", "ticket.read");
    static final Map<String, List<String>> FIELDS = Map.of(TICKET, List.of("assignee", "description", "status", "tags", "title", "type"));
    private static final String MANAGE = "permissions.manage";
    private static final int NAME_MAX = 80;

    private final PermissionAdministration administration;
    private final Permissions permissions;
    private final PermissionContexts contexts;
    private final AccessContexts identity;
    private final ProjectAccess projects;
    private final TransactionTemplate transaction;

    PermissionsController(PermissionAdministration administration, Permissions permissions, PermissionContexts contexts,
            AccessContexts identity, ProjectAccess projects, PlatformTransactionManager transactions) {
        this.administration = administration;
        this.permissions = permissions;
        this.contexts = contexts;
        this.identity = identity;
        this.projects = projects;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    record ResourceFields(String resourceType, List<String> fields) {
    }

    record Catalog(List<String> companyActions, List<String> projectActions, List<ResourceFields> fields) {
    }

    record GroupBody(UUID id, String name) {
    }

    record ActionBody(String action, UUID projectId) {
    }

    record FieldBody(String resourceType, String field, UUID projectId, String operation, String effect) {
    }

    record GroupDetailBody(UUID id, String name, List<UUID> members, List<ActionBody> actions, List<FieldBody> fields) {
        static GroupDetailBody of(GroupDetail detail) {
            return new GroupDetailBody(detail.group().id(), detail.group().name(), detail.members(),
                detail.actions().stream().map(a -> new ActionBody(a.action(), a.projectId())).toList(),
                detail.fields().stream().map(f -> new FieldBody(f.resourceType(), f.field(), f.projectId(), lower(f.operation()),
                    lower(f.effect()))).toList());
        }
    }

    record CreateGroupRequest(String name) {
    }

    record ActionRequest(String action, UUID projectId, Boolean enabled) {
    }

    record FieldRequest(String resourceType, String field, UUID projectId, String operation, String effect) {
    }

    record AuditBody(UUID id, UUID actorId, Instant occurredAt, String operation, UUID groupId, UUID subjectId, UUID projectId,
            String action, String resourceType, String field, String effect, String previousEffect, boolean previousRecorded,
            long rightsVersion) {
        static AuditBody of(PermissionAdministration.Audit audit) {
            return new AuditBody(audit.id(), audit.actorId(), audit.occurredAt(), audit.operation(), audit.groupId(),
                audit.subjectId(), audit.projectId(), audit.action(), audit.resourceType(), audit.field(), lower(audit.effect()),
                lower(audit.previousEffect()), audit.previousRecorded(), audit.rightsVersion());
        }
    }

    record ProjectGroupBody(UUID id, String name, List<ActionBody> actions, List<FieldBody> fields) {
    }

    /** What a project administration may hand out in the project, and every group with its rules there. */
    record ProjectRights(List<String> actions, List<ResourceFields> fields, List<ProjectGroupBody> groups) {
    }

    @GetMapping("/catalog")
    Catalog catalog() {
        return read(actor -> {
            if (!manages(actor)) {
                throw new PermissionDenied();
            }
            return new Catalog(COMPANY_ACTIONS, PROJECT_ACTIONS,
                FIELDS.entrySet().stream().map(e -> new ResourceFields(e.getKey(), e.getValue())).toList());
        });
    }

    @GetMapping("/groups")
    List<GroupBody> groups() {
        return read(actor -> administration.groups(actor).stream().map(g -> new GroupBody(g.id(), g.name())).toList());
    }

    @PostMapping("/groups")
    ResponseEntity<GroupDetailBody> createGroup(@RequestBody(required = false) CreateGroupRequest body) {
        String name = body == null || body.name() == null ? "" : body.name().strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_MAX) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(change(actor -> changed(actor, administration.createGroup(actor, name).id())));
    }

    @GetMapping("/groups/{id}")
    GroupDetailBody group(@PathVariable UUID id) {
        return read(actor -> detail(actor, id));
    }

    @PutMapping("/groups/{id}/members/{accountId}")
    ResponseEntity<GroupDetailBody> addMember(@PathVariable UUID id, @PathVariable UUID accountId) {
        return member(id, accountId, true);
    }

    @DeleteMapping("/groups/{id}/members/{accountId}")
    ResponseEntity<GroupDetailBody> removeMember(@PathVariable UUID id, @PathVariable UUID accountId) {
        return member(id, accountId, false);
    }

    @PutMapping("/groups/{id}/actions")
    ResponseEntity<GroupDetailBody> setAction(@PathVariable UUID id, @RequestBody(required = false) ActionRequest body) {
        List<String> violations = new ArrayList<>();
        String action = body == null ? null : body.action();
        UUID project = body == null ? null : body.projectId();
        if (action == null || !(COMPANY_ACTIONS.contains(action) || PROJECT_ACTIONS.contains(action))) {
            violations.add("action.unknown");
        } else if (project != null && !PROJECT_ACTIONS.contains(action)) {
            violations.add("action.company_only");
        }
        if (body == null || body.enabled() == null) {
            violations.add("enabled.required");
        }
        invalid(violations);
        return answer(change(actor -> {
            administration.setAction(actor, id, scope(actor, project, "project"), action, body.enabled());
            return changed(actor, id);
        }));
    }

    @PutMapping("/groups/{id}/fields")
    ResponseEntity<GroupDetailBody> setField(@PathVariable UUID id, @RequestBody(required = false) FieldRequest body) {
        List<String> violations = new ArrayList<>();
        String type = body == null ? null : body.resourceType();
        if (type == null || !FIELDS.containsKey(type)) {
            violations.add("resourceType.unknown");
        } else if (body.field() == null || !FIELDS.get(type).contains(body.field())) {
            violations.add("field.unknown");
        }
        Operation operation = parse(Operation.class, body == null ? null : body.operation());
        if (operation == null) {
            violations.add("operation.invalid");
        }
        Effect effect = parse(Effect.class, body == null ? null : body.effect());
        if (effect == null) {
            violations.add("effect.invalid");
        }
        invalid(violations);
        return answer(change(actor -> {
            administration.setField(actor, id, scope(actor, body.projectId(), type), body.field(), operation, effect);
            return changed(actor, id);
        }));
    }

    @GetMapping("/audit")
    List<AuditBody> audit(@RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) Long before) {
        List<String> violations = new ArrayList<>();
        if (limit < 1 || limit > 100) {
            violations.add("limit.out_of_range");
        }
        if (before != null && before < 1) {
            violations.add("before.out_of_range");
        }
        invalid(violations);
        return read(actor -> administration.audit(actor, before, limit).stream().map(AuditBody::of).toList());
    }

    /**
     * The rules of one project for its administration. Offered are the project actions the caller holds there (the company
     * administration: all of them) and the fields the caller may read and write there, because a delegate can hand out
     * nothing else.
     */
    @GetMapping("/projects/{projectId}/groups")
    ProjectRights projectGroups(@PathVariable UUID projectId) {
        return read(actor -> {
            Supplier<ResourceContext> project = scope(actor, projectId, "project");
            var groups = administration.projectGroups(actor, project).stream()
                .map(group -> new ProjectGroupBody(group.group().id(), group.group().name(),
                    group.actions().stream().map(a -> new ActionBody(a.action(), a.projectId())).toList(),
                    group.fields().stream().map(f -> new FieldBody(f.resourceType(), f.field(), f.projectId(), lower(f.operation()),
                        lower(f.effect()))).toList()))
                .toList();
            boolean company = manages(actor);
            List<String> actions = PROJECT_ACTIONS.stream()
                .filter(action -> company || permissions.inspect(actor, project, action, Set.of()).allowed()).toList();
            List<ResourceFields> fields = FIELDS.entrySet().stream().map(entry -> {
                if (company) {
                    return new ResourceFields(entry.getKey(), entry.getValue());
                }
                var decision = permissions.inspect(actor, scope(actor, projectId, entry.getKey()), MANAGE, Set.copyOf(entry.getValue()));
                return new ResourceFields(entry.getKey(), entry.getValue().stream().filter(decision.writableFields()::contains).toList());
            }).toList();
            return new ProjectRights(actions, fields, groups);
        });
    }

    private ResponseEntity<GroupDetailBody> member(UUID group, UUID account, boolean enabled) {
        return answer(change(actor -> {
            administration.setMember(actor, group, account, enabled);
            return changed(actor, group);
        }));
    }

    /** 204 when the change took the right to read the group away from the actor. */
    private static ResponseEntity<GroupDetailBody> answer(GroupDetailBody body) {
        return body == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(body);
    }

    /**
     * The group as the change left it, read in the same transaction. The change moved the rights version, which the context
     * of the request no longer carries; the version is read again under the exclusive rights lock the change still holds, so
     * no other change can come between. {@code null} when the change took the right of the actor away, for example by
     * leaving the administration group while another administration remains; the change still commits.
     */
    private GroupDetailBody changed(AccessContext actor, UUID group) {
        AccessContext after = new AccessContext(actor.companyId(), actor.userId(), actor.sessionId(),
            permissions.currentVersion(actor.companyId()), actor.passwordChangeRequired(), actor.external(), actor.organizationId());
        return manages(after) ? detail(after, group) : null;
    }

    /** Asked without throwing, so a refusal does not mark the surrounding transaction for rollback. */
    private boolean manages(AccessContext actor) {
        return permissions.inspect(actor, () -> ResourceContext.company(actor.companyId()), MANAGE, Set.of()).allowed();
    }

    private GroupDetailBody detail(AccessContext actor, UUID group) {
        return administration.group(actor, group).map(GroupDetailBody::of)
            .orElseThrow(() -> ApiProblemException.of(404, "permissions.group_not_found"));
    }

    /**
     * The company, or one project of it. A project the company does not have is refused before the permissions module runs;
     * otherwise the project adapter locks the project, as for every other rights check of a project. A project the caller
     * cannot see is answered like a missing one, as the projects API does (finding F2 of the TASK-043 review), so these routes
     * do not confirm that an unknown identifier exists.
     */
    private Supplier<ResourceContext> scope(AccessContext actor, UUID project, String resourceType) {
        if (project == null) {
            return () -> new ResourceContext(actor.companyId(), null, null, false, resourceType);
        }
        if (projects.key(project).isEmpty() || !visible(actor, project)) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        return projects.resource(actor.companyId(), project, actor.userId(), resourceType, false);
    }

    /** The visibility rule of the projects module: a company administration or a holder of a right in the project. */
    private boolean visible(AccessContext actor, UUID project) {
        if (manages(actor) || permissions.inspect(actor, () -> ResourceContext.company(actor.companyId()),
            AdministrationRight.PROJECTS_MANAGE.key(), Set.of()).allowed()) {
            return true;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), project, actor.userId(), "project", false);
        return permissions.inspect(actor, resource, "project.read", Set.of()).allowed()
            || permissions.inspect(actor, resource, "project.manage", Set.of()).allowed();
    }

    private <T> T read(Function<AccessContext, T> work) {
        AccessContext actor = actor();
        try {
            return transaction.execute(status -> work.apply(actor));
        } catch (PermissionDenied denied) {
            throw forbidden();
        } catch (RuntimeException failure) {
            if (retryable(failure)) {
                throw busy();
            }
            throw failure;
        }
    }

    private <T> T change(Function<AccessContext, T> work) {
        AccessContext actor = actor();
        for (int attempt = 1; ; attempt++) {
            AccessContext current = actor;
            try {
                return transaction.execute(status -> work.apply(current));
            } catch (LastAdministration last) {
                throw ApiProblemException.of(409, "permissions.last_administrator");
            } catch (GroupNameTaken taken) {
                throw ApiProblemException.of(409, "permissions.group_name_taken");
            } catch (PermissionDenied denied) {
                AccessContext fresh = attempt == 1 ? contexts.current().orElse(null) : null;
                if (fresh == null || fresh.rightsVersion() == actor.rightsVersion()) {
                    throw forbidden();
                }
                actor = fresh;
            } catch (RuntimeException failure) {
                if (retryable(failure)) {
                    throw busy();
                }
                throw failure;
            }
        }
    }

    /** The permissions context of a complete login; a login that still has to change its password is refused. */
    private AccessContext actor() {
        if (identity.require().level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(PermissionsController::forbidden);
    }

    private static void invalid(List<String> violations) {
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value) && value.equals(value.toLowerCase(java.util.Locale.ROOT))) {
                return constant;
            }
        }
        return null;
    }

    /**
     * A lock timeout (55P03), deadlock (40P01) or serialization failure (40001), wherever in the cause chain the driver puts
     * it; Spring does not translate every lock timeout into {@link PessimisticLockingFailureException}. Other SQL errors stay
     * internal errors.
     */
    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || (cause instanceof SQLException sql && RETRYABLE_STATES.contains(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    /** A lock was not granted in time (the rights lock waits at most five seconds); nothing changed, the call may be repeated. */
    private static ApiProblemException busy() {
        return ApiProblemException.retryAfter(503, "permissions.busy", 1);
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }
}
