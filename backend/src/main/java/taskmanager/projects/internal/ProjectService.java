package taskmanager.projects.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
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
import taskmanager.projects.TicketUsage;

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
    private final ApplicationEventPublisher events;
    private final ObjectProvider<TicketUsage> tickets;

    ProjectService(ProjectRepository projects, ProjectAccess access, Permissions permissions, IdentityVerification identity,
            Clock clock, ApplicationEventPublisher events, ObjectProvider<TicketUsage> tickets) {
        this.projects = projects;
        this.access = access;
        this.permissions = permissions;
        this.identity = identity;
        this.clock = clock;
        this.events = events;
        this.tickets = tickets;
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
        events.publishEvent(project.changed());
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
        ProjectView view = current(actor, id);
        events.publishEvent(view.project().changed());
        return view;
    }

    /**
     * Publishes a configuration directly as the next version, without a draft. Statuses and ticket types that tickets still
     * use cannot go this way, as there is no mapping to move them; the publication of a draft takes one.
     */
    @Transactional
    public ProjectView changeConfiguration(AccessContext actor, UUID id, long expectedVersion, ProjectConfiguration configuration) {
        Project project = changeable(actor, id, expectedVersion);
        if (configuration == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("configuration.required"));
        }
        publish(actor, project, configuration, Map.of(), Map.of());
        return current(actor, id);
    }

    /** The draft of the configuration; the configuration of a project is for its administration only. */
    @Transactional
    public Optional<ConfigurationDraft> draft(AccessContext actor, UUID id) {
        access(actor, id, true);
        return projects.draft(id);
    }

    /**
     * Stores the draft. {@code expectedRevision} is {@code null} to create it and the revision read to change it; a draft
     * that started from an older published version than the current one is refused, since saving it would hide what was
     * published since.
     */
    @Transactional
    public ConfigurationDraft saveDraft(AccessContext actor, UUID id, Long expectedRevision, long baseVersion,
            ProjectConfiguration configuration) {
        Project project = access(actor, id, true).project();
        if (configuration == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("configuration.required"));
        }
        List<String> violations = configuration.violations();
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Optional<ConfigurationDraft> existing = projects.draft(id);
        if (existing.isPresent() ? expectedRevision == null || existing.get().revision() != expectedRevision : expectedRevision != null) {
            throw ApiProblemException.of(412, "project.draft_conflict");
        }
        if (baseVersion != project.configurationVersion()) {
            throw ApiProblemException.of(409, "project.draft_stale");
        }
        Instant now = clock.instant();
        long revision = existing.map(draft -> draft.revision() + 1).orElse(1L);
        projects.saveDraft(id, baseVersion, revision, configuration.normalized(), now, actor.userId());
        projects.audit(id, actor.userId(), "project.configuration_draft_saved", "revision " + revision, now);
        return projects.draft(id).orElseThrow();
    }

    @Transactional
    public void discardDraft(AccessContext actor, UUID id, long expectedRevision) {
        access(actor, id, true);
        ConfigurationDraft draft = projects.draft(id).orElseThrow(() -> ApiProblemException.of(404, "project.draft_not_found"));
        if (draft.revision() != expectedRevision) {
            throw ApiProblemException.of(412, "project.draft_conflict");
        }
        projects.deleteDraft(id);
        projects.audit(id, actor.userId(), "project.configuration_draft_discarded", "revision " + draft.revision(), clock.instant());
    }

    /**
     * Publishes the draft as the next version. {@code statusMapping} and {@code typeMapping} say where the tickets go that
     * use a status or ticket type the new version no longer has or no longer allows for their type; without them the
     * publication is refused and nothing changes (F-05 of DOMAIN_MODEL.md).
     */
    @Transactional
    public ProjectView publishDraft(AccessContext actor, UUID id, long expectedVersion, long draftRevision,
            Map<String, String> statusMapping, Map<String, String> typeMapping) {
        Project project = changeable(actor, id, expectedVersion);
        ConfigurationDraft draft = projects.draft(id).orElseThrow(() -> ApiProblemException.of(404, "project.draft_not_found"));
        if (draft.revision() != draftRevision) {
            throw ApiProblemException.of(412, "project.draft_conflict");
        }
        if (draft.baseVersion() != project.configurationVersion()) {
            throw ApiProblemException.of(409, "project.draft_stale");
        }
        publish(actor, project, draft.configuration(), statusMapping == null ? Map.of() : statusMapping,
            typeMapping == null ? Map.of() : typeMapping);
        projects.deleteDraft(id);
        return current(actor, id);
    }

    /** How many tickets use which type and status, for the mapping of a publication. */
    @Transactional
    public List<TicketUsage.Use> usage(AccessContext actor, UUID id) {
        access(actor, id, true);
        return usages(id);
    }

    private List<TicketUsage.Use> usages(UUID id) {
        TicketUsage usage = tickets.getIfAvailable();
        return usage == null ? List.of() : usage.usage(id);
    }

    private void publish(AccessContext actor, Project project, ProjectConfiguration configuration, Map<String, String> statusMapping,
            Map<String, String> typeMapping) {
        List<String> violations = configuration.violations();
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        ProjectConfiguration previous = projects.configuration(project.id(), project.configurationVersion()).orElseThrow();
        List<String> incompatible = configuration.incompatibilities(previous);
        if (!incompatible.isEmpty()) {
            throw ApiProblemException.withViolations(409, "project.configuration_incompatible", incompatible);
        }
        List<String> mapping = new ArrayList<>();
        statusMapping.forEach((from, to) -> {
            if (!previous.hasStatus(from)) {
                mapping.add("statusMapping.key_unknown");
            }
            if (to == null || !configuration.hasStatus(to)) {
                mapping.add("statusMapping.target_unknown");
            }
        });
        typeMapping.forEach((from, to) -> {
            if (!previous.hasTicketType(from)) {
                mapping.add("typeMapping.key_unknown");
            }
            if (to == null || !configuration.hasTicketType(to)) {
                mapping.add("typeMapping.target_unknown");
            }
        });
        if (!mapping.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", mapping.stream().distinct().toList());
        }
        List<TicketUsage.Move> moves = new ArrayList<>();
        Set<String> unmapped = new LinkedHashSet<>();
        for (TicketUsage.Use use : usages(project.id())) {
            // A type the new version no longer has needs a mapping; a status needs one when the ticket type does not know it.
            String type = use.type();
            if (!configuration.hasTicketType(type)) {
                String target = typeMapping.get(type);
                if (target == null) {
                    unmapped.add("type.in_use:" + type);
                    continue;
                }
                type = target;
            }
            String status = use.status();
            if (!configuration.statusKeysOf(type).contains(status)) {
                String target = statusMapping.get(status);
                if (target == null) {
                    unmapped.add("status.in_use:" + status);
                    continue;
                }
                if (!configuration.statusKeysOf(type).contains(target)) {
                    unmapped.add("status.mapping_invalid:" + status);
                    continue;
                }
                status = target;
            }
            if (!type.equals(use.type()) || !status.equals(use.status())) {
                moves.add(new TicketUsage.Move(use.type(), use.status(), type, status));
            }
        }
        if (!unmapped.isEmpty()) {
            throw ApiProblemException.withViolations(409, "project.configuration_in_use", List.copyOf(unmapped));
        }
        Instant now = clock.instant();
        long moved = moves.isEmpty() ? 0 : tickets.getObject().migrate(project.id(), moves, actor.userId(), now);
        long next = project.configurationVersion() + 1;
        projects.insertConfiguration(project.id(), next, configuration.normalized(), now, actor.userId());
        projects.touch(project.id(), now, actor.userId());
        projects.audit(project.id(), actor.userId(), "project.configuration_changed", "version " + next + (moved > 0 ? ", " + moved + " tickets moved" : ""), now);
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
