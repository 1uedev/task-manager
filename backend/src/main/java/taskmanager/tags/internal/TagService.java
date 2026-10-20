package taskmanager.tags.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.tickets.TicketAccess;

/**
 * Tag catalogs and tag assignments (TASK-028, DEC-068).
 *
 * <p>Catalogs: the catalog of the company holds shared terms and the tags of projects; every complete internal login reads
 * it. Each project has its own catalog, which only its members read: with one of {@code project.read},
 * {@code project.manage}, {@code ticket.read}, {@code tag.create} or {@code tags.manage} in the context of the project.
 * {@code tag.create} adds a tag to a catalog and {@code tags.manage} also renames, archives and restores it; both are checked
 * on the company for its catalog and in the context of the project for the project catalog, where they need the membership.
 * Tags are archived instead of deleted, so assignments and later references keep their meaning.
 *
 * <p>Assignments: a ticket takes tags of the company catalog and of the catalog of its own project. Reading them needs
 * {@code ticket.read} with a read rule for the field {@code tags} of the resource type {@code ticket}; changing them needs
 * {@code ticket.edit} with a write rule for that field. A project takes tags of the company catalog; reading follows the
 * project rights ({@code projects.manage}, {@code project.read}, {@code project.manage}), changing needs
 * {@code projects.manage} or {@code project.manage}. An archived tag stays assigned but is not assigned again. A tag
 * assignment does not change the version of the ticket or of the project. An actual assignment or removal at a ticket
 * is recorded in the history of the ticket through {@link TicketAccess#recordTagChange} in the same transaction
 * (TASK-042); a repeated assignment or the removal of a tag that is not assigned records nothing.
 *
 * <p>Locks, in this order: rights, identity of the requester, project row ({@code FOR SHARE}, held by the project adapter
 * until the commit), then the tag row ({@code FOR SHARE} to assign it, {@code FOR UPDATE} to change it). Archiving thus
 * waits for an assignment that already holds the tag, and an assignment that waits sees the archived tag. A change at a
 * ticket finally locks the ticket row ({@code FOR SHARE}) to record it in the history.
 */
@Service
public class TagService {
    public static final String CREATE = "tag.create";
    public static final String MANAGE = AdministrationRight.TAGS_MANAGE.key();
    static final String FIELD = TicketAccess.TAGS_FIELD;
    private static final String TAG_RESOURCE = "tag";
    private static final String PROJECT_RESOURCE = "project";
    private static final List<String> CATALOG_READERS = List.of("project.read", "project.manage", "ticket.read", CREATE, MANAGE);
    private static final List<String> PROJECT_READERS = List.of("project.read", "project.manage");
    private static final String TICKET_READ = "ticket.read";
    private static final String TICKET_EDIT = "ticket.edit";

    private final TagRepository tags;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final Permissions permissions;
    private final Clock clock;

    TagService(TagRepository tags, ProjectAccess projects, TicketAccess tickets, Permissions permissions, Clock clock) {
        this.tags = tags;
        this.projects = projects;
        this.tickets = tickets;
        this.permissions = permissions;
        this.clock = clock;
    }

    /** The catalog a tag is taken from when it is named instead of identified. */
    public enum Catalog { COMPANY, PROJECT }

    /** An existing tag by identifier, or a tag of a catalog by name, created there when it does not exist yet. */
    public record Target(UUID tagId, String name, Catalog catalog) {
    }

    /** A change of a tag; {@code null} keeps the name or the archive state. */
    public record Change(String name, Boolean archived) {
    }

    // ------------------------------------------------------------------------------------------------ company catalog

    @Transactional
    public List<Tag> companyCatalog(AccessContext actor, String search, boolean includeArchived, String after, int limit) {
        internal(actor);
        return tags.catalog(actor.companyId(), null, TagName.searchKey(search), includeArchived, TagName.searchKey(after), limit);
    }

    @Transactional
    public Tag companyTag(AccessContext actor, UUID id) {
        internal(actor);
        return tags.find(actor.companyId(), id).filter(Tag::inCompanyCatalog).orElseThrow(TagService::tagNotFound);
    }

    @Transactional
    public Tag createCompanyTag(AccessContext actor, String name) {
        requireAny(actor, company(actor), CREATE, MANAGE);
        return create(actor, null, name);
    }

    @Transactional
    public Tag changeCompanyTag(AccessContext actor, UUID id, long expectedVersion, Change change) {
        requireAny(actor, company(actor), MANAGE);
        return change(actor, null, id, expectedVersion, change);
    }

    // ------------------------------------------------------------------------------------------------ project catalog

    @Transactional
    public List<Tag> projectCatalog(AccessContext actor, UUID projectId, String search, boolean includeArchived, String after, int limit) {
        catalogVisible(actor, projectId);
        return tags.catalog(actor.companyId(), projectId, TagName.searchKey(search), includeArchived, TagName.searchKey(after), limit);
    }

    @Transactional
    public Tag projectTag(AccessContext actor, UUID projectId, UUID id) {
        catalogVisible(actor, projectId);
        return tags.find(actor.companyId(), id).filter(tag -> projectId.equals(tag.projectId())).orElseThrow(TagService::tagNotFound);
    }

    @Transactional
    public Tag createProjectTag(AccessContext actor, UUID projectId, String name) {
        Supplier<ResourceContext> resource = catalogVisible(actor, projectId);
        requireAny(actor, resource, CREATE, MANAGE);
        return create(actor, projectId, name);
    }

    @Transactional
    public Tag changeProjectTag(AccessContext actor, UUID projectId, UUID id, long expectedVersion, Change change) {
        Supplier<ResourceContext> resource = catalogVisible(actor, projectId);
        requireAny(actor, resource, MANAGE);
        return change(actor, projectId, id, expectedVersion, change);
    }

    // ------------------------------------------------------------------------------------------------ project tags

    @Transactional
    public List<Tag> projectTags(AccessContext actor, UUID projectId) {
        projectAccess(actor, projectId, false);
        return tags.projectTags(projectId);
    }

    /** Assigns a tag of the company catalog to the project; an existing assignment stays as it is. */
    @Transactional
    public void assignProject(AccessContext actor, UUID projectId, Target target) {
        projectAccess(actor, projectId, true);
        Tag tag = resolve(actor, projectId, new Target(target.tagId(), target.name(), Catalog.COMPANY), true);
        if (tags.assignProject(projectId, tag.id(), clock.instant(), actor.userId())) {
            tags.audit(actor.companyId(), tag.id(), "tag.assigned", PROJECT_RESOURCE, projectId, null, actor.userId(), clock.instant());
        }
    }

    @Transactional
    public void unassignProject(AccessContext actor, UUID projectId, UUID tagId) {
        projectAccess(actor, projectId, true);
        if (tags.unassignProject(projectId, tagId)) {
            tags.audit(actor.companyId(), tagId, "tag.unassigned", PROJECT_RESOURCE, projectId, null, actor.userId(), clock.instant());
        }
    }

    // ------------------------------------------------------------------------------------------------ ticket tags

    @Transactional
    public List<Tag> ticketTags(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = ticketResource(actor, projectId);
        Decision read = permissions.inspect(actor, resource, TICKET_READ, Set.of(FIELD));
        if (!read.allowed()) {
            throw projectNotFound();
        }
        if (!read.readableFields().contains(FIELD)) {
            throw forbidden();
        }
        try {
            permissions.require(actor, resource, TICKET_READ, Set.of(FIELD), Set.of());
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
        return tags.ticketTags(ticket(actor, projectId, number));
    }

    /** Assigns a tag of the company catalog or of the catalog of the project; an existing assignment stays as it is. */
    @Transactional
    public void assignTicket(AccessContext actor, UUID projectId, long number, Target target) {
        UUID ticket = changeableTicket(actor, projectId, number);
        Tag tag = resolve(actor, projectId, target, false);
        Instant now = clock.instant();
        if (tags.assignTicket(ticket, tag.id(), projectId, now, actor.userId())) {
            tags.audit(actor.companyId(), tag.id(), "tag.assigned", "ticket", ticket, null, actor.userId(), now);
            tickets.recordTagChange(ticket, now, actor.userId(), null, tag.id());
        }
    }

    @Transactional
    public void unassignTicket(AccessContext actor, UUID projectId, long number, UUID tagId) {
        UUID ticket = changeableTicket(actor, projectId, number);
        if (tags.unassignTicket(ticket, tagId)) {
            Instant now = clock.instant();
            tags.audit(actor.companyId(), tagId, "tag.unassigned", "ticket", ticket, null, actor.userId(), now);
            tickets.recordTagChange(ticket, now, actor.userId(), tagId, null);
        }
    }

    // ------------------------------------------------------------------------------------------------ catalog changes

    private Tag create(AccessContext actor, UUID projectId, String rawName) {
        Tag tag = newTag(actor, projectId, rawName);
        if (!tags.insert(tag)) {
            throw ApiProblemException.of(409, "tag.name_taken");
        }
        tags.audit(actor.companyId(), tag.id(), "tag.created", catalogTarget(projectId), projectId, tag.name(), actor.userId(), tag.createdAt());
        return tag;
    }

    private Tag newTag(AccessContext actor, UUID projectId, String rawName) {
        String name = TagName.clean(rawName);
        if (name == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
        }
        Instant now = clock.instant();
        return new Tag(UUID.randomUUID(), actor.companyId(), projectId, name, TagName.normalized(name), false, 1, now,
            actor.userId(), now, actor.userId());
    }

    private Tag change(AccessContext actor, UUID projectId, UUID id, long expectedVersion, Change change) {
        Tag tag = tags.lockForChange(actor.companyId(), id, projectId).orElseThrow(TagService::tagNotFound);
        if (tag.version() != expectedVersion) {
            throw ApiProblemException.of(412, "tag.version_conflict");
        }
        String name = tag.name();
        if (change.name() != null) {
            name = TagName.clean(change.name());
            if (name == null) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
            }
        }
        boolean archived = change.archived() == null ? tag.archived() : change.archived();
        boolean renamed = !name.equals(tag.name());
        if (!renamed && archived == tag.archived()) {
            return tag;
        }
        String normalized = TagName.normalized(name);
        if (!normalized.equals(tag.normalized()) && tags.nameTaken(actor.companyId(), projectId, normalized, id)) {
            throw ApiProblemException.of(409, "tag.name_taken");
        }
        Instant now = clock.instant();
        Tag changed = new Tag(id, tag.companyId(), projectId, name, normalized, archived, tag.version() + 1, tag.createdAt(),
            tag.createdBy(), now, actor.userId());
        try {
            tags.update(changed);
        } catch (DuplicateKeyException race) {
            throw ApiProblemException.of(409, "tag.name_taken");
        }
        if (renamed) {
            tags.audit(actor.companyId(), id, "tag.renamed", catalogTarget(projectId), projectId, tag.name(), actor.userId(), now);
        }
        if (archived != tag.archived()) {
            tags.audit(actor.companyId(), id, archived ? "tag.archived" : "tag.restored", catalogTarget(projectId), projectId, null, actor.userId(), now);
        }
        return changed;
    }

    /**
     * The tag to assign, locked shared. A named tag that its catalog does not hold yet is created there, which needs
     * {@code tag.create} or {@code tags.manage} for that catalog.
     */
    private Tag resolve(AccessContext actor, UUID projectId, Target target, boolean companyOnly) {
        if (target.tagId() != null) {
            Tag tag = tags.lockForAssignment(actor.companyId(), target.tagId(), companyOnly ? null : projectId)
                .orElseThrow(TagService::tagNotFound);
            return assignable(tag);
        }
        UUID catalog = target.catalog() == Catalog.PROJECT ? projectId : null;
        if (catalog != null) {
            // Only who may see the catalog of the project learns whether it holds a name (TAGS_SEARCH.md).
            Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), TAG_RESOURCE, false);
            String action = firstAllowed(actor, resource, CATALOG_READERS).orElseThrow(TagService::forbidden);
            require(actor, resource, action, Set.of());
        }
        String name = TagName.clean(target.name());
        if (name == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
        }
        Optional<Tag> existing = tags.lockByName(actor.companyId(), catalog, TagName.normalized(name));
        if (existing.isPresent()) {
            return assignable(existing.get());
        }
        Supplier<ResourceContext> resource = catalog == null ? company(actor)
            : projects.resource(actor.companyId(), projectId, actor.userId(), TAG_RESOURCE, false);
        requireAny(actor, resource, CREATE, MANAGE);
        Tag tag = newTag(actor, catalog, name);
        if (!tags.insert(tag)) {
            // Created concurrently: the insert waited for that commit, so the tag is visible now.
            return assignable(tags.lockByName(actor.companyId(), catalog, tag.normalized()).orElseThrow(TagService::tagNotFound));
        }
        tags.audit(actor.companyId(), tag.id(), "tag.created", catalogTarget(catalog), catalog, tag.name(), actor.userId(), tag.createdAt());
        return tag;
    }

    /**
     * The audit target of a catalog event: the project of a project catalog, nothing for the company catalog (hint H4 of the
     * TASK-028 review). Assignments name the ticket or project they change.
     */
    private static String catalogTarget(UUID projectId) {
        return projectId == null ? null : PROJECT_RESOURCE;
    }

    private static Tag assignable(Tag tag) {
        if (tag.archived()) {
            throw ApiProblemException.of(409, "tag.archived");
        }
        return tag;
    }

    // ------------------------------------------------------------------------------------------------ access

    /** Portal accounts get no catalog until the portal decides what they see (DEC-049). */
    private static void internal(AccessContext actor) {
        if (actor.external()) {
            throw forbidden();
        }
    }

    private static Supplier<ResourceContext> company(AccessContext actor) {
        return () -> ResourceContext.company(actor.companyId());
    }

    /** The project context for the catalog of the project; a caller who may not see it gets 404. */
    private Supplier<ResourceContext> catalogVisible(AccessContext actor, UUID projectId) {
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), TAG_RESOURCE, false);
        String action = firstAllowed(actor, resource, CATALOG_READERS).orElseThrow(TagService::projectNotFound);
        require(actor, resource, action, Set.of());
        return resource;
    }

    /**
     * Checks the project rights for its tags. {@code projects.manage} covers every project of the company without membership;
     * otherwise {@code project.read} or {@code project.manage} shows the project and {@code project.manage} changes it.
     */
    private void projectAccess(AccessContext actor, UUID projectId, boolean change) {
        String companyRight = AdministrationRight.PROJECTS_MANAGE.key();
        if (permissions.inspect(actor, company(actor), companyRight, Set.of()).allowed()) {
            require(actor, company(actor), companyRight, Set.of());
            projects.key(projectId).orElseThrow(TagService::projectNotFound);
            return;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), PROJECT_RESOURCE, false);
        String action = firstAllowed(actor, resource, PROJECT_READERS).orElseThrow(TagService::projectNotFound);
        if (change && !permissions.inspect(actor, resource, "project.manage", Set.of()).allowed()) {
            throw forbidden();
        }
        require(actor, resource, change ? "project.manage" : action, Set.of());
    }

    private Supplier<ResourceContext> ticketResource(AccessContext actor, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
    }

    /** Checks {@code ticket.edit} with the write rule for the tags and returns the ticket. */
    private UUID changeableTicket(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = ticketResource(actor, projectId);
        boolean read = permissions.inspect(actor, resource, TICKET_READ, Set.of()).allowed();
        boolean edit = permissions.inspect(actor, resource, TICKET_EDIT, Set.of()).allowed();
        if (!read && !edit) {
            throw projectNotFound();
        }
        try {
            permissions.require(actor, resource, TICKET_EDIT, Set.of(), Set.of(FIELD));
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
        return ticket(actor, projectId, number);
    }

    private UUID ticket(AccessContext actor, UUID projectId, long number) {
        return tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
    }

    private Optional<String> firstAllowed(AccessContext actor, Supplier<ResourceContext> resource, List<String> actions) {
        return actions.stream().filter(action -> permissions.inspect(actor, resource, action, Set.of()).allowed()).findFirst();
    }

    /** Requires the first of the actions the caller holds; without any of them 403. */
    private void requireAny(AccessContext actor, Supplier<ResourceContext> resource, String... actions) {
        internal(actor);
        String action = firstAllowed(actor, resource, List.of(actions)).orElseThrow(TagService::forbidden);
        require(actor, resource, action, Set.of());
    }

    private void require(AccessContext actor, Supplier<ResourceContext> resource, String action, Set<String> writeFields) {
        try {
            permissions.require(actor, resource, action, Set.of(), writeFields);
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
    }

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }

    private static ApiProblemException tagNotFound() {
        return ApiProblemException.of(404, "tag.not_found");
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
