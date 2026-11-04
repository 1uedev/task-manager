package taskmanager.tags.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.tags.TicketTagChanged;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketOrganizations;
import taskmanager.tickets.TicketTagFilter;

/**
 * Tags in the portal (TASK-077, DEC-108).
 *
 * <p>The portal shows only tags visible to customers: a tag with the visibility {@code customer} for every customer, or one
 * bound to the organization of the caller. Internal tags and tags of other organizations never appear, not in the tags of a
 * request, in suggestions, in the filter of the list or in an answer that names a tag; such a tag answers like an unknown one.
 *
 * <p>Rights: the organization of the resource is the customer of the request, as for every portal path (TASK-075). Reading
 * needs {@code ticket.read} with the read rule of the field {@code tags}; setting and removing need {@code ticket.edit} with
 * the write rule of that field, which the group "Portal" does not grant. A name the organization does not see yet creates a
 * tag only with {@code tag.create} in the context of the project; the tag goes to the catalog of the project, visible to
 * customers and bound to the organization, until the support releases it for every customer.
 *
 * <p>Locks, in the order of DEC-068: rights, identity, project row, the customer link of the request, the tag row
 * ({@code FOR SHARE}), the ticket row and the advisory lock of the object. A change of the visibility locks the tag
 * {@code FOR UPDATE} and therefore waits for an assignment of the portal or is seen by it; internal tags of the same request
 * are never read or changed by these paths.
 */
@Service
public class PortalTags implements TicketTagFilter {
    private static final String TAG_RESOURCE = "tag";
    private static final String TICKET_READ = "ticket.read";
    private static final String TICKET_EDIT = "ticket.edit";
    private static final Set<String> TAGS = Set.of(TagService.FIELD);

    private final TagRepository tags;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final Permissions permissions;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<TicketOrganizations> organizations;

    PortalTags(TagRepository tags, ProjectAccess projects, TicketAccess tickets, Permissions permissions, Clock clock,
            ApplicationEventPublisher events, ObjectProvider<TicketOrganizations> organizations) {
        this.tags = tags;
        this.projects = projects;
        this.tickets = tickets;
        this.permissions = permissions;
        this.clock = clock;
        this.events = events;
        this.organizations = organizations;
    }

    /** The visible tags of a request and whether the caller may set and remove them. */
    public record RequestTags(List<Tag> items, boolean writable) {
    }

    /** Suggestions visible to the organization and whether the caller may create a tag for it. */
    public record Suggestions(List<Tag> items, boolean create) {
    }

    @Transactional
    public RequestTags ticketTags(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = ticketResource(actor, projectId, number);
        Decision read = permissions.inspect(actor, resource, TICKET_READ, TAGS);
        if (!read.allowed()) {
            throw ticketNotFound();
        }
        if (!read.readableFields().contains(TagService.FIELD)) {
            throw forbidden();
        }
        require(actor, resource, TICKET_READ, TAGS, Set.of());
        UUID ticket = ticket(actor, projectId, number);
        List<Tag> visible = tags.ticketTags(ticket).stream().filter(tag -> tag.visibleTo(actor.organizationId())).toList();
        Decision edit = permissions.inspect(actor, resource, TICKET_EDIT, TAGS);
        return new RequestTags(visible, edit.allowed() && edit.writableFields().contains(TagService.FIELD));
    }

    @Transactional
    public Suggestions suggestions(AccessContext actor, UUID projectId, String search, int limit) {
        Supplier<ResourceContext> resource = projectResource(actor, projectId, TicketAccess.RESOURCE_TYPE);
        Decision read = permissions.inspect(actor, resource, TICKET_READ, TAGS);
        if (!read.allowed() || !read.readableFields().contains(TagService.FIELD)) {
            throw projectNotFound();
        }
        require(actor, resource, TICKET_READ, TAGS, Set.of());
        List<Tag> found = tags.portalSuggestions(actor.companyId(), projectId, actor.organizationId(), TagName.searchKey(search), limit);
        boolean create = permissions.inspect(actor, projectResource(actor, projectId, TAG_RESOURCE), TagService.CREATE, Set.of()).allowed();
        return new Suggestions(found, create);
    }

    /**
     * Sets a tag at a request of the organization: a visible tag by its identifier, or by name the visible tag of that name
     * (of the organization first, then of the project, then of the company), created for the organization when none exists.
     * An existing assignment stays as it is. Returns the tag.
     */
    @Transactional
    public Tag assign(AccessContext actor, UUID projectId, long number, UUID tagId, String rawName) {
        UUID ticket = changeableTicket(actor, projectId, number);
        Tag tag;
        if (tagId != null) {
            tag = tags.lockForAssignment(actor.companyId(), tagId, projectId).filter(found -> found.visibleTo(actor.organizationId()))
                .orElseThrow(PortalTags::tagNotFound);
        } else {
            tag = byName(actor, projectId, rawName);
        }
        assignable(tag);
        tickets.lockForTagChange(ticket);
        tags.lockObject(TicketAccess.RESOURCE_TYPE, ticket);
        if (!tags.ticketHas(ticket, tag.id()) && tags.ticketCount(ticket) >= TagService.MAX_PER_OBJECT) {
            throw ApiProblemException.of(409, "tag.limit_reached");
        }
        Instant now = clock.instant();
        if (tags.assignTicket(ticket, tag.id(), projectId, now, actor.userId())) {
            tags.audit(actor.companyId(), tag.id(), "tag.assigned", TicketAccess.RESOURCE_TYPE, ticket, null, actor.userId(), now);
            tickets.recordTagChange(ticket, now, actor.userId(), null, tag.id());
            events.publishEvent(new TicketTagChanged(ticket, tag.id(), true));
        }
        return tag;
    }

    /** Removes a visible tag from a request of the organization; an internal or foreign tag answers like an unknown one. */
    @Transactional
    public void unassign(AccessContext actor, UUID projectId, long number, UUID tagId) {
        UUID ticket = changeableTicket(actor, projectId, number);
        // A merged tag no longer exists for the portal (TASK-079, H2), like for setting it.
        Tag tag = tags.lockForAssignment(actor.companyId(), tagId, projectId).filter(found -> found.visibleTo(actor.organizationId()) && !found.merged())
            .orElseThrow(PortalTags::tagNotFound);
        tickets.lockForTagChange(ticket);
        if (tags.unassignTicket(ticket, tag.id())) {
            Instant now = clock.instant();
            tags.audit(actor.companyId(), tag.id(), "tag.unassigned", TicketAccess.RESOURCE_TYPE, ticket, null, actor.userId(), now);
            tickets.recordTagChange(ticket, now, actor.userId(), tag.id(), null);
            events.publishEvent(new TicketTagChanged(ticket, tag.id(), false));
        }
    }

    @Override
    @Transactional
    public Set<UUID> ticketsWith(UUID companyId, UUID projectId, UUID organizationId, UUID tagId) {
        Optional<Tag> tag = tags.find(companyId, tagId)
            .filter(found -> !found.merged() && found.visibleTo(organizationId)
                && (found.projectId() == null || found.projectId().equals(projectId)));
        return tag.isEmpty() ? Set.of() : new HashSet<>(tags.ticketsWith(projectId, tagId));
    }

    private Tag byName(AccessContext actor, UUID projectId, String rawName) {
        String name = TagName.clean(rawName);
        if (name == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
        }
        String normalized = TagName.normalized(name);
        Optional<Tag> existing = tags.lockVisibleByName(actor.companyId(), projectId, actor.organizationId(), normalized);
        if (existing.isPresent()) {
            return existing.get();
        }
        require(actor, projectResource(actor, projectId, TAG_RESOURCE), TagService.CREATE, Set.of(), Set.of());
        Instant now = clock.instant();
        Tag tag = new Tag(UUID.randomUUID(), actor.companyId(), projectId, name, normalized, null, null, false, null, 1, now, actor.userId(),
            now, actor.userId(), Tag.CUSTOMER, actor.organizationId());
        if (!tags.insert(tag)) {
            // Created concurrently by the same organization: the insert waited for that commit, so the tag is visible now.
            return tags.lockByName(actor.companyId(), projectId, actor.organizationId(), normalized).orElseThrow(PortalTags::tagNotFound);
        }
        tags.audit(actor.companyId(), tag.id(), "tag.created", "project", projectId, tag.name(), actor.userId(), now);
        events.publishEvent(TagService.changed(tag));
        return tag;
    }

    private static void assignable(Tag tag) {
        if (tag.merged()) {
            throw tagNotFound();
        }
        if (tag.archived()) {
            throw ApiProblemException.of(409, "tag.archived");
        }
    }

    /** Checks {@code ticket.edit} with the write rule for the tags at a request of the organization and returns the ticket. */
    private UUID changeableTicket(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = ticketResource(actor, projectId, number);
        Decision read = permissions.inspect(actor, resource, TICKET_READ, TAGS);
        if (!read.allowed()) {
            throw ticketNotFound();
        }
        Decision edit = permissions.inspect(actor, resource, TICKET_EDIT, TAGS);
        if (!edit.allowed() || !edit.writableFields().contains(TagService.FIELD)) {
            throw forbidden();
        }
        require(actor, resource, TICKET_EDIT, Set.of(), TAGS);
        return ticket(actor, projectId, number);
    }

    /** The ticket context of a request: its organization is the customer when that is the organization of the caller. */
    private Supplier<ResourceContext> ticketResource(AccessContext actor, UUID projectId, long number) {
        portalOnly(actor);
        TicketOrganizations ports = organizations.getIfUnique();
        if (ports == null) {
            throw ticketNotFound();
        }
        Supplier<ResourceContext> project = projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
        return () -> {
            ResourceContext base = project.get();
            if (!base.projectMember()) {
                return base;
            }
            UUID organization = tickets.find(actor.companyId(), projectId, number)
                .flatMap(ticket -> ports.lockOrganization(ticket, actor.organizationId())).orElse(null);
            return new ResourceContext(base.companyId(), base.projectId(), organization, base.projectMember(), base.resourceType());
        };
    }

    /** A context of the project for the organization of the caller, for suggestions and the creation of a tag. */
    private Supplier<ResourceContext> projectResource(AccessContext actor, UUID projectId, String resourceType) {
        portalOnly(actor);
        Supplier<ResourceContext> project = projects.resource(actor.companyId(), projectId, actor.userId(), resourceType, false);
        return () -> {
            ResourceContext base = project.get();
            return new ResourceContext(base.companyId(), base.projectId(), actor.organizationId(), base.projectMember(), base.resourceType());
        };
    }

    private UUID ticket(AccessContext actor, UUID projectId, long number) {
        return tickets.find(actor.companyId(), projectId, number).orElseThrow(PortalTags::ticketNotFound);
    }

    private void require(AccessContext actor, Supplier<ResourceContext> resource, String action, Set<String> reads, Set<String> writes) {
        try {
            permissions.require(actor, resource, action, reads, writes);
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
    }

    private static void portalOnly(AccessContext actor) {
        if (!actor.external()) {
            throw projectNotFound();
        }
    }

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }

    private static ApiProblemException ticketNotFound() {
        return ApiProblemException.of(404, "ticket.not_found");
    }

    private static ApiProblemException tagNotFound() {
        return ApiProblemException.of(404, "tag.not_found");
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
