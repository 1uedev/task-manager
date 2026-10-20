package taskmanager.projects.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.IdentityVerification;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;

/**
 * Projects, their configuration and their members (TASK-026, DEC-065).
 *
 * <p>Who may do what: the company right {@code projects.manage} creates projects and manages every project of the company,
 * without being a member. Inside a project, {@code project.read} shows it and {@code project.manage} changes it; both are
 * actions of the permissions module that need the membership in the project and can be granted for the whole company or for
 * one project (delegation). A project the caller may not read does not exist for them (404); a project they may read but
 * not change answers 403.
 *
 * <p>Locks, in this order: rights, identity of the requester, project row ({@code FOR SHARE} to read, {@code FOR UPDATE} to
 * change), then the account of a new member. Every write of the project, its configuration or its members holds the
 * project row exclusively, so the version in the ETag and the membership stay stable for a check that holds it shared.
 * The decision between the company and the project path is a snapshot ({@code inspect}) in the same transaction; the
 * chosen path is then checked authoritatively with {@code require}.
 */
@Service
public class ProjectService {
    public static final String READ = "project.read";
    public static final String MANAGE = "project.manage";
    static final String RESOURCE = "project";
    private static final String KEY = "[A-Z][A-Z0-9]{1,9}";
    private static final Comparator<ProjectView> BY_KEY = Comparator.comparing(view -> view.project().key());

    private final ProjectRepository projects;
    private final ProjectAccess access;
    private final Permissions permissions;
    private final IdentityVerification identity;
    private final Clock clock;

    ProjectService(ProjectRepository projects, ProjectAccess access, Permissions permissions, IdentityVerification identity,
            Clock clock) {
        this.projects = projects;
        this.access = access;
        this.permissions = permissions;
        this.identity = identity;
        this.clock = clock;
    }

    /** A project with its current configuration, and whether the caller may change it. */
    public record ProjectView(Project project, ProjectConfiguration configuration, boolean manageable) {
    }

    private record Granted(Project project, boolean manageable) {
    }

    /** The projects the caller may read: all of the company with {@code projects.manage}, otherwise those of their memberships. */
    @Transactional
    public List<ProjectView> list(AccessContext actor) {
        if (companyManager(actor)) {
            requireCompany(actor);
            return projects.lockAll(actor.companyId()).stream().map(project -> view(project, true)).sorted(BY_KEY).toList();
        }
        List<ProjectView> visible = new ArrayList<>();
        for (UUID id : projects.memberships(actor.companyId(), actor.userId())) {
            var resource = access.resource(actor.companyId(), id, actor.userId(), RESOURCE, false);
            if (permissions.inspect(actor, resource, READ, Set.of()).allowed()) {
                boolean manageable = permissions.inspect(actor, resource, MANAGE, Set.of()).allowed();
                projects.lock(actor.companyId(), id, false).ifPresent(project -> visible.add(view(project, manageable)));
            }
        }
        visible.sort(BY_KEY);
        return visible;
    }

    @Transactional
    public ProjectView create(AccessContext actor, String rawKey, String rawName, String rawDescription) {
        requireCompany(actor);
        String key = rawKey == null ? null : rawKey.strip().toUpperCase(Locale.ROOT);
        String name = text(rawName);
        String description = text(rawDescription);
        List<String> violations = new ArrayList<>();
        if (key == null || !key.matches(KEY)) {
            violations.add("key.invalid");
        }
        validate(name, description, violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        if (projects.keyTaken(actor.companyId(), key)) {
            throw ApiProblemException.of(409, "project.key_taken");
        }
        Instant now = clock.instant();
        Project project = new Project(UUID.randomUUID(), actor.companyId(), key, name, description, 1, 1, now, now);
        try {
            projects.insert(project, actor.userId());
        } catch (DuplicateKeyException race) {
            throw ApiProblemException.of(409, "project.key_taken");
        }
        projects.insertConfiguration(project.id(), 1, ProjectConfiguration.initial(), now, actor.userId());
        projects.addMember(project.id(), actor.userId(), now, actor.userId());
        projects.audit(project.id(), actor.userId(), "project.created", key, now);
        return view(project, true);
    }

    @Transactional
    public ProjectView get(AccessContext actor, UUID id) {
        Granted granted = access(actor, id, false);
        return view(granted.project(), granted.manageable());
    }

    @Transactional
    public ProjectView update(AccessContext actor, UUID id, long expectedVersion, String rawName, String rawDescription) {
        changeable(actor, id, expectedVersion);
        String name = text(rawName);
        String description = text(rawDescription);
        List<String> violations = new ArrayList<>();
        validate(name, description, violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        projects.update(id, name, description, now, actor.userId());
        projects.audit(id, actor.userId(), "project.updated", null, now);
        return current(actor, id);
    }

    @Transactional
    public ProjectView changeConfiguration(AccessContext actor, UUID id, long expectedVersion, ProjectConfiguration configuration) {
        Project project = changeable(actor, id, expectedVersion);
        if (configuration == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("configuration.required"));
        }
        List<String> violations = configuration.violations();
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        long next = project.configurationVersion() + 1;
        projects.insertConfiguration(id, next, configuration.normalized(), now, actor.userId());
        projects.touch(id, now, actor.userId());
        projects.audit(id, actor.userId(), "project.configuration_changed", "version " + next, now);
        return current(actor, id);
    }

    @Transactional
    public ProjectConfiguration configuration(AccessContext actor, UUID id, long version) {
        access(actor, id, false);
        return projects.configuration(id, version).orElseThrow(() -> ApiProblemException.of(404, "project.configuration_not_found"));
    }

    /** The members and the version of the project they belong to. */
    public record Members(List<Member> members, long version) {
    }

    @Transactional
    public Members members(AccessContext actor, UUID id) {
        Granted granted = access(actor, id, false);
        return new Members(projects.members(id), granted.project().version());
    }

    @Transactional
    public Members addMember(AccessContext actor, UUID id, long expectedVersion, UUID accountId) {
        changeable(actor, id, expectedVersion);
        // After the project lock: the identity module keeps the account row shared until the commit.
        if (accountId == null || !identity.accountIsActive(accountId)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("accountId.unknown"));
        }
        Instant now = clock.instant();
        if (projects.addMember(id, accountId, now, actor.userId())) {
            projects.touch(id, now, actor.userId());
            projects.audit(id, actor.userId(), "project.member_added", accountId.toString(), now);
        }
        return new Members(projects.members(id), projects.lock(actor.companyId(), id, true).orElseThrow().version());
    }

    @Transactional
    public Members removeMember(AccessContext actor, UUID id, long expectedVersion, UUID accountId) {
        changeable(actor, id, expectedVersion);
        Instant now = clock.instant();
        if (accountId != null && projects.removeMember(id, accountId)) {
            projects.touch(id, now, actor.userId());
            projects.audit(id, actor.userId(), "project.member_removed", accountId.toString(), now);
        }
        return new Members(projects.members(id), projects.lock(actor.companyId(), id, true).orElseThrow().version());
    }

    // ------------------------------------------------------------------------------------------------ access

    private Project changeable(AccessContext actor, UUID id, long expectedVersion) {
        Project project = access(actor, id, true).project();
        if (project.version() != expectedVersion) {
            throw ApiProblemException.of(412, "project.version_conflict");
        }
        return project;
    }

    private Granted access(AccessContext actor, UUID id, boolean change) {
        if (companyManager(actor)) {
            requireCompany(actor);
            Project project = projects.lock(actor.companyId(), id, change).orElseThrow(ProjectService::notFound);
            return new Granted(project, true);
        }
        Supplier<ResourceContext> resource = access.resource(actor.companyId(), id, actor.userId(), RESOURCE, change);
        boolean read = permissions.inspect(actor, resource, READ, Set.of()).allowed();
        boolean manage = permissions.inspect(actor, resource, MANAGE, Set.of()).allowed();
        if (!read && !manage) {
            throw notFound();
        }
        if (change && !manage) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        require(actor, resource, change || !read ? MANAGE : READ);
        // The supplier holds the row since the check; this reads it again inside the same lock.
        Project project = projects.lock(actor.companyId(), id, change).orElseThrow(ProjectService::notFound);
        return new Granted(project, manage);
    }

    private boolean companyManager(AccessContext actor) {
        return permissions.inspect(actor, () -> ResourceContext.company(actor.companyId()), AdministrationRight.PROJECTS_MANAGE.key(), Set.of())
            .allowed();
    }

    private void requireCompany(AccessContext actor) {
        require(actor, () -> ResourceContext.company(actor.companyId()), AdministrationRight.PROJECTS_MANAGE.key());
    }

    private void require(AccessContext actor, Supplier<ResourceContext> resource, String action) {
        try {
            permissions.require(actor, resource, action, Set.of(), Set.of());
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
    }

    private ProjectView current(AccessContext actor, UUID id) {
        return view(projects.lock(actor.companyId(), id, true).orElseThrow(ProjectService::notFound), true);
    }

    private ProjectView view(Project project, boolean manageable) {
        ProjectConfiguration configuration = projects.configuration(project.id(), project.configurationVersion()).orElseThrow();
        return new ProjectView(project, configuration, manageable);
    }

    private static void validate(String name, String description, List<String> violations) {
        if (name == null || name.codePointCount(0, name.length()) > 80) {
            violations.add("name.invalid");
        }
        if (description != null && description.codePointCount(0, description.length()) > 2000) {
            violations.add("description.too_long");
        }
    }

    private static String text(String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }

    private static ApiProblemException notFound() {
        return ApiProblemException.of(404, "project.not_found");
    }
}
