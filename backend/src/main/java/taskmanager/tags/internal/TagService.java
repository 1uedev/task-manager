package taskmanager.tags.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
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
import taskmanager.tags.ProjectTagChanged;
import taskmanager.tags.TagChanged;
import taskmanager.tags.TagMerged;
import taskmanager.tags.TicketTagChanged;
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
 *
 * <p>Catalog care (TASK-072, DEC-102): {@code tags.manage} also sets the description and the color and merges a tag into
 * another of the same catalog. Merging moves every assignment of the source to the target, drops duplicates, frees the name
 * of the source and keeps the source as a pointer to the target, without entries in the ticket history and without
 * changing ticket versions. Both tags are locked {@code FOR UPDATE}, the one with the smaller identifier first, so two merges
 * of the same pair cannot deadlock and an assignment that waits for the source sees it merged. A ticket or project holds at
 * most {@value #MAX_PER_OBJECT} tags; an advisory lock per object, taken after the locks above, serializes the check.
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
    /** The most tags a ticket or a project holds (rule 12 of TASK-072). */
    public static final int MAX_PER_OBJECT = 30;
    /** The most assignments one merge moves in its transaction (rule 9 of TASK-072). */
    public static final int MERGE_MAX = 20_000;
    public static final int SUGGESTIONS_MAX = 20;
    public static final int LOOKUP_MAX = 100;

    private final TagRepository tags;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final Permissions permissions;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    TagService(TagRepository tags, ProjectAccess projects, TicketAccess tickets, Permissions permissions, Clock clock,
            ApplicationEventPublisher events) {
        this.tags = tags;
        this.projects = projects;
        this.tickets = tickets;
        this.permissions = permissions;
        this.clock = clock;
        this.events = events;
    }

    /** The catalog a tag is taken from when it is named instead of identified. */
    public enum Catalog { COMPANY, PROJECT }

    /** An existing tag by identifier, or a tag of a catalog by name, created there when it does not exist yet. */
    public record Target(UUID tagId, String name, Catalog catalog) {
    }

    /**
     * A change of a tag; {@code null} keeps the name or the archive state. Description and color change only when they are
     * given, and a given {@code null} removes them.
     */
    public record Change(String name, Boolean archived, boolean descriptionGiven, String description, boolean colorGiven, String color) {
        public Change(String name, Boolean archived) {
            this(name, archived, false, null, false, null);
        }
    }

    /** How many tickets and projects that the caller may read carry a tag. */
    public record Usage(long tickets, long projects) {
    }

    /**
     * A tag as the caller sees it: whether they may maintain its catalog, the tag a merged tag points to, and, when asked
     * for, its usage.
     */
    public record TagView(Tag tag, boolean manageable, Tag mergedInto, Usage usage) {
    }

    /** The preview of a merge: both tags and the usage of the source among the tickets and projects the caller may read. */
    public record MergePreview(TagView source, TagView target, Usage usage) {
    }

    /** Suggestions for an assignment and whether the caller may create a tag in the company or the project catalog. */
    public record Suggestions(List<TagView> items, boolean createCompany, boolean createProject) {
    }

    // ------------------------------------------------------------------------------------------------ company catalog

    @Transactional
    public List<TagView> companyCatalog(AccessContext actor, String search, boolean includeArchived, String after, int limit, boolean usage) {
        internal(actor);
        return views(actor, tags.catalog(actor.companyId(), null, TagName.searchKey(search), includeArchived, TagName.searchKey(after), limit), usage);
    }

    @Transactional
    public TagView companyTag(AccessContext actor, UUID id) {
        internal(actor);
        return view(actor, tags.find(actor.companyId(), id).filter(Tag::inCompanyCatalog).orElseThrow(TagService::tagNotFound));
    }

    @Transactional
    public TagView createCompanyTag(AccessContext actor, String name) {
        requireAny(actor, company(actor), CREATE, MANAGE);
        return view(actor, create(actor, null, name));
    }

    @Transactional
    public TagView changeCompanyTag(AccessContext actor, UUID id, long expectedVersion, Change change) {
        requireAny(actor, company(actor), MANAGE);
        return view(actor, change(actor, null, id, expectedVersion, change));
    }

    @Transactional
    public MergePreview previewCompanyMerge(AccessContext actor, UUID source, UUID target) {
        requireAny(actor, company(actor), MANAGE);
        return preview(actor, null, source, target);
    }

    @Transactional
    public TagView mergeCompanyTag(AccessContext actor, UUID source, long expectedVersion, UUID target) {
        requireAny(actor, company(actor), MANAGE);
        return view(actor, merge(actor, null, source, expectedVersion, target));
    }

    /** Suggestions from the company catalog, for the tags of projects. */
    @Transactional
    public Suggestions companySuggestions(AccessContext actor, String search, int limit) {
        internal(actor);
        List<Tag> found = tags.suggestions(actor.companyId(), null, TagName.searchKey(search), limit);
        return new Suggestions(views(actor, found, false), allowedAny(actor, company(actor), CREATE, MANAGE), false);
    }

    // ------------------------------------------------------------------------------------------------ project catalog

    @Transactional
    public List<TagView> projectCatalog(AccessContext actor, UUID projectId, String search, boolean includeArchived, String after, int limit,
            boolean usage) {
        catalogVisible(actor, projectId);
        return views(actor, tags.catalog(actor.companyId(), projectId, TagName.searchKey(search), includeArchived, TagName.searchKey(after), limit), usage);
    }

    @Transactional
    public TagView projectTag(AccessContext actor, UUID projectId, UUID id) {
        catalogVisible(actor, projectId);
        return view(actor, tags.find(actor.companyId(), id).filter(tag -> projectId.equals(tag.projectId())).orElseThrow(TagService::tagNotFound));
    }

    @Transactional
    public TagView createProjectTag(AccessContext actor, UUID projectId, String name) {
        Supplier<ResourceContext> resource = catalogVisible(actor, projectId);
        requireAny(actor, resource, CREATE, MANAGE);
        return view(actor, create(actor, projectId, name));
    }

    @Transactional
    public TagView changeProjectTag(AccessContext actor, UUID projectId, UUID id, long expectedVersion, Change change) {
        Supplier<ResourceContext> resource = catalogVisible(actor, projectId);
        requireAny(actor, resource, MANAGE);
        return view(actor, change(actor, projectId, id, expectedVersion, change));
    }

    @Transactional
    public MergePreview previewProjectMerge(AccessContext actor, UUID projectId, UUID source, UUID target) {
        Supplier<ResourceContext> resource = catalogVisible(actor, projectId);
        requireAny(actor, resource, MANAGE);
        return preview(actor, projectId, source, target);
    }

    @Transactional
    public TagView mergeProjectTag(AccessContext actor, UUID projectId, UUID source, long expectedVersion, UUID target) {
        Supplier<ResourceContext> resource = catalogVisible(actor, projectId);
        requireAny(actor, resource, MANAGE);
        return view(actor, merge(actor, projectId, source, expectedVersion, target));
    }

    /** Suggestions from the company catalog and the catalog of the project, for the tags of its tickets. */
    @Transactional
    public Suggestions projectSuggestions(AccessContext actor, UUID projectId, String search, int limit) {
        Supplier<ResourceContext> resource = catalogVisible(actor, projectId);
        List<Tag> found = tags.suggestions(actor.companyId(), projectId, TagName.searchKey(search), limit);
        return new Suggestions(views(actor, found, false), allowedAny(actor, company(actor), CREATE, MANAGE), allowedAny(actor, resource, CREATE, MANAGE));
    }

    /**
     * The tags among {@code ids} in the company catalog and the catalog of the project, archived and merged ones included,
     * so a history can name tags that left the catalog. Identifiers of other catalogs are left out without a hint.
     */
    @Transactional
    public List<TagView> lookup(AccessContext actor, UUID projectId, Collection<UUID> ids) {
        catalogVisible(actor, projectId);
        return views(actor, tags.lookup(actor.companyId(), projectId, new LinkedHashSet<>(ids)), false);
    }

    // ------------------------------------------------------------------------------------------------ project tags

    @Transactional
    public List<TagView> projectTags(AccessContext actor, UUID projectId) {
        projectAccess(actor, projectId, false);
        return views(actor, tags.projectTags(projectId), false);
    }

    /** Assigns a tag of the company catalog to the project; an existing assignment stays as it is. */
    @Transactional
    public void assignProject(AccessContext actor, UUID projectId, Target target) {
        projectAccess(actor, projectId, true);
        Tag tag = resolve(actor, projectId, new Target(target.tagId(), target.name(), Catalog.COMPANY), true);
        tags.lockObject(PROJECT_RESOURCE, projectId);
        if (!tags.projectHas(projectId, tag.id()) && tags.projectCount(projectId) >= MAX_PER_OBJECT) {
            throw ApiProblemException.of(409, "tag.limit_reached");
        }
        if (tags.assignProject(projectId, tag.id(), clock.instant(), actor.userId())) {
            tags.audit(actor.companyId(), tag.id(), "tag.assigned", PROJECT_RESOURCE, projectId, null, actor.userId(), clock.instant());
            events.publishEvent(new ProjectTagChanged(projectId, tag.id(), true));
        }
    }

    @Transactional
    public void unassignProject(AccessContext actor, UUID projectId, UUID tagId) {
        projectAccess(actor, projectId, true);
        if (tags.unassignProject(projectId, tagId)) {
            tags.audit(actor.companyId(), tagId, "tag.unassigned", PROJECT_RESOURCE, projectId, null, actor.userId(), clock.instant());
            events.publishEvent(new ProjectTagChanged(projectId, tagId, false));
        }
    }

    // ------------------------------------------------------------------------------------------------ ticket tags

    @Transactional
    public List<TagView> ticketTags(AccessContext actor, UUID projectId, long number) {
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
        return views(actor, tags.ticketTags(ticket(actor, projectId, number)), false);
    }

    /** Assigns a tag of the company catalog or of the catalog of the project; an existing assignment stays as it is. */
    @Transactional
    public void assignTicket(AccessContext actor, UUID projectId, long number, Target target) {
        UUID ticket = changeableTicket(actor, projectId, number);
        Tag tag = resolve(actor, projectId, target, false);
        // Ticket after tag, as DEC-068 orders the locks; the time is read only once the ticket row is held (TASK-043).
        tickets.lockForTagChange(ticket);
        tags.lockObject(TicketAccess.RESOURCE_TYPE, ticket);
        if (!tags.ticketHas(ticket, tag.id()) && tags.ticketCount(ticket) >= MAX_PER_OBJECT) {
            throw ApiProblemException.of(409, "tag.limit_reached");
        }
        Instant now = clock.instant();
        if (tags.assignTicket(ticket, tag.id(), projectId, now, actor.userId())) {
            tags.audit(actor.companyId(), tag.id(), "tag.assigned", "ticket", ticket, null, actor.userId(), now);
            tickets.recordTagChange(ticket, now, actor.userId(), null, tag.id());
            events.publishEvent(new TicketTagChanged(ticket, tag.id(), true));
        }
    }

    @Transactional
    public void unassignTicket(AccessContext actor, UUID projectId, long number, UUID tagId) {
        UUID ticket = changeableTicket(actor, projectId, number);
        if (tags.unassignTicket(ticket, tagId)) {
            tickets.lockForTagChange(ticket);
            Instant now = clock.instant();
            tags.audit(actor.companyId(), tagId, "tag.unassigned", "ticket", ticket, null, actor.userId(), now);
            tickets.recordTagChange(ticket, now, actor.userId(), tagId, null);
            events.publishEvent(new TicketTagChanged(ticket, tagId, false));
        }
    }

    // ------------------------------------------------------------------------------------------------ catalog changes

    private Tag create(AccessContext actor, UUID projectId, String rawName) {
        Tag tag = newTag(actor, projectId, rawName);
        if (!tags.insert(tag)) {
            throw ApiProblemException.of(409, "tag.name_taken");
        }
        tags.audit(actor.companyId(), tag.id(), "tag.created", catalogTarget(projectId), projectId, tag.name(), actor.userId(), tag.createdAt());
        events.publishEvent(changed(tag));
        return tag;
    }

    private Tag newTag(AccessContext actor, UUID projectId, String rawName) {
        String name = TagName.clean(rawName);
        if (name == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
        }
        Instant now = clock.instant();
        return new Tag(UUID.randomUUID(), actor.companyId(), projectId, name, TagName.normalized(name), null, null, false, null, 1, now,
            actor.userId(), now, actor.userId());
    }

    private Tag change(AccessContext actor, UUID projectId, UUID id, long expectedVersion, Change change) {
        String description = null;
        if (change.descriptionGiven()) {
            try {
                description = TagLook.description(change.description());
            } catch (IllegalArgumentException invalid) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("description.invalid"));
            }
        }
        if (change.colorGiven() && !TagLook.color(change.color())) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("color.invalid"));
        }
        Tag tag = tags.lockForChange(actor.companyId(), id, projectId).orElseThrow(TagService::tagNotFound);
        if (tag.version() != expectedVersion) {
            throw ApiProblemException.of(412, "tag.version_conflict");
        }
        if (tag.merged()) {
            throw ApiProblemException.of(409, "tag.merged");
        }
        String name = tag.name();
        if (change.name() != null) {
            name = TagName.clean(change.name());
            if (name == null) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
            }
        }
        boolean archived = change.archived() == null ? tag.archived() : change.archived();
        String newDescription = change.descriptionGiven() ? description : tag.description();
        String color = change.colorGiven() ? change.color() : tag.color();
        boolean renamed = !name.equals(tag.name());
        boolean described = !Objects.equals(newDescription, tag.description());
        boolean colored = !Objects.equals(color, tag.color());
        if (!renamed && archived == tag.archived() && !described && !colored) {
            return tag;
        }
        String normalized = TagName.normalized(name);
        if (!normalized.equals(tag.normalized()) && tags.nameTaken(actor.companyId(), projectId, normalized, id)) {
            throw ApiProblemException.of(409, "tag.name_taken");
        }
        Instant now = clock.instant();
        Tag changed = new Tag(id, tag.companyId(), projectId, name, normalized, newDescription, color, archived, null, tag.version() + 1,
            tag.createdAt(), tag.createdBy(), now, actor.userId());
        try {
            tags.update(changed);
        } catch (DuplicateKeyException race) {
            throw ApiProblemException.of(409, "tag.name_taken");
        }
        if (renamed) {
            tags.audit(actor.companyId(), id, "tag.renamed", catalogTarget(projectId), projectId, tag.name(), actor.userId(), now);
        }
        if (described) {
            tags.audit(actor.companyId(), id, "tag.described", catalogTarget(projectId), projectId, null, actor.userId(), now);
        }
        if (colored) {
            tags.audit(actor.companyId(), id, "tag.colored", catalogTarget(projectId), projectId, color, actor.userId(), now);
        }
        if (archived != tag.archived()) {
            tags.audit(actor.companyId(), id, archived ? "tag.archived" : "tag.restored", catalogTarget(projectId), projectId, null, actor.userId(), now);
        }
        events.publishEvent(changed(changed));
        return changed;
    }

    // ------------------------------------------------------------------------------------------------ merging

    /** Both tags of one catalog, locked exclusively, the smaller identifier first; 404 when either is not in the catalog. */
    private Tag[] lockPair(AccessContext actor, UUID projectId, UUID source, UUID target) {
        if (source.equals(target)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("into.invalid"));
        }
        boolean sourceFirst = source.compareTo(target) < 0;
        Tag first = tags.lockForChange(actor.companyId(), sourceFirst ? source : target, projectId).orElseThrow(TagService::tagNotFound);
        Tag second = tags.lockForChange(actor.companyId(), sourceFirst ? target : source, projectId).orElseThrow(TagService::tagNotFound);
        return sourceFirst ? new Tag[] {first, second} : new Tag[] {second, first};
    }

    private static void mergeable(Tag source, Tag target) {
        if (source.merged() || target.merged()) {
            throw ApiProblemException.of(409, "tag.merged");
        }
        if (target.archived()) {
            throw ApiProblemException.of(409, "tag.archived");
        }
    }

    private MergePreview preview(AccessContext actor, UUID projectId, UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("into.invalid"));
        }
        Tag source = tags.find(actor.companyId(), sourceId).filter(tag -> Objects.equals(tag.projectId(), projectId)).orElseThrow(TagService::tagNotFound);
        Tag target = tags.find(actor.companyId(), targetId).filter(tag -> Objects.equals(tag.projectId(), projectId)).orElseThrow(TagService::tagNotFound);
        mergeable(source, target);
        List<TagView> both = views(actor, List.of(source, target), true);
        return new MergePreview(both.get(0), both.get(1), both.get(0).usage());
    }

    private Tag merge(AccessContext actor, UUID projectId, UUID sourceId, long expectedVersion, UUID targetId) {
        Tag[] pair = lockPair(actor, projectId, sourceId, targetId);
        Tag source = pair[0];
        Tag target = pair[1];
        // A source that a concurrent merge took already answers as merged, not as an old version.
        mergeable(source, target);
        if (source.version() != expectedVersion) {
            throw ApiProblemException.of(412, "tag.version_conflict");
        }
        if (tags.assignments(sourceId) > MERGE_MAX) {
            throw ApiProblemException.of(409, "tag.merge_too_large");
        }
        Instant now = clock.instant();
        tags.moveAssignments(sourceId, targetId);
        tags.markMerged(sourceId, targetId, source.version() + 1, now, actor.userId());
        tags.audit(actor.companyId(), sourceId, "tag.merged", catalogTarget(projectId), projectId, targetId.toString(), actor.userId(), now);
        tags.audit(actor.companyId(), targetId, "tag.merged_in", catalogTarget(projectId), projectId, sourceId.toString(), actor.userId(), now);
        events.publishEvent(new TagMerged(actor.companyId(), sourceId, targetId));
        return target;
    }

    // ------------------------------------------------------------------------------------------------ views

    private TagView view(AccessContext actor, Tag tag) {
        return views(actor, List.of(tag), false).get(0);
    }

    /**
     * The tags as the caller sees them. Whether a catalog may be maintained is checked once per catalog; the usage counts
     * only tickets of projects where the caller reads tickets with the field {@code tags}, and projects the caller may read.
     */
    private List<TagView> views(AccessContext actor, List<Tag> found, boolean usage) {
        Map<UUID, Boolean> manageable = new HashMap<>();
        Map<UUID, Tag> targets = new HashMap<>();
        for (Tag tag : found) {
            UUID catalog = tag.projectId();
            if (!manageable.containsKey(catalog)) {
                Supplier<ResourceContext> resource = catalog == null ? company(actor)
                    : projects.resource(actor.companyId(), catalog, actor.userId(), TAG_RESOURCE, false);
                manageable.put(catalog, !actor.external() && permissions.inspect(actor, resource, MANAGE, Set.of()).allowed());
            }
            if (tag.merged() && !targets.containsKey(tag.mergedInto())) {
                tags.find(actor.companyId(), tag.mergedInto()).ifPresent(target -> targets.put(target.id(), target));
            }
        }
        Map<UUID, Long> tickets = Map.of();
        Map<UUID, Long> projectCounts = Map.of();
        if (usage && !found.isEmpty()) {
            List<UUID> ids = found.stream().map(Tag::id).toList();
            tickets = tags.ticketUsage(ids, ticketReadableProjects(actor));
            List<UUID> companyTags = found.stream().filter(Tag::inCompanyCatalog).map(Tag::id).toList();
            projectCounts = tags.projectUsage(companyTags, readableProjects(actor));
        }
        List<TagView> views = new ArrayList<>();
        for (Tag tag : found) {
            Usage counted = usage ? new Usage(tickets.getOrDefault(tag.id(), 0L), projectCounts.getOrDefault(tag.id(), 0L)) : null;
            views.add(new TagView(tag, manageable.get(tag.projectId()), tag.merged() ? targets.get(tag.mergedInto()) : null, counted));
        }
        return views;
    }

    /** The member projects where the caller reads tickets together with their field {@code tags}. */
    private List<UUID> ticketReadableProjects(AccessContext actor) {
        List<UUID> readable = new ArrayList<>();
        for (UUID project : projects.memberships(actor.companyId(), actor.userId())) {
            Decision read = permissions.inspect(actor, ticketResource(actor, project), TICKET_READ, Set.of(FIELD));
            if (read.allowed() && read.readableFields().contains(FIELD)) {
                readable.add(project);
            }
        }
        return readable;
    }

    /** The projects the caller may read: {@code null} for all with {@code projects.manage}, else the member projects. */
    private List<UUID> readableProjects(AccessContext actor) {
        if (permissions.inspect(actor, company(actor), AdministrationRight.PROJECTS_MANAGE.key(), Set.of()).allowed()) {
            return null;
        }
        List<UUID> readable = new ArrayList<>();
        for (UUID project : projects.memberships(actor.companyId(), actor.userId())) {
            Supplier<ResourceContext> resource = projects.resource(actor.companyId(), project, actor.userId(), PROJECT_RESOURCE, false);
            if (firstAllowed(actor, resource, PROJECT_READERS).isPresent()) {
                readable.add(project);
            }
        }
        return readable;
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
        events.publishEvent(changed(tag));
        return tag;
    }

    /**
     * The audit target of a catalog event: the project of a project catalog, nothing for the company catalog (hint H4 of the
     * TASK-028 review). Assignments name the ticket or project they change.
     */
    private static String catalogTarget(UUID projectId) {
        return projectId == null ? null : PROJECT_RESOURCE;
    }

    static TagChanged changed(Tag tag) {
        return new TagChanged(tag.id(), tag.companyId(), tag.projectId(), tag.name());
    }

    private static Tag assignable(Tag tag) {
        if (tag.merged()) {
            throw ApiProblemException.of(409, "tag.merged");
        }
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

    private boolean allowedAny(AccessContext actor, Supplier<ResourceContext> resource, String... actions) {
        return !actor.external() && firstAllowed(actor, resource, List.of(actions)).isPresent();
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
