package taskmanager.requirements.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketRequirementReaders;
import taskmanager.wiki.WikiPages;

/**
 * Links of requirements with tickets and wiki pages of the same project (TASK-097, DEC-127) after the rules Holger confirmed
 * on 10 November 2026:
 * <ol>
 *   <li>A requirement links each ticket of its project at most once. The link names a revision, the current one when it is
 *       made; a newer revision shows the hint "Neuere Revision n vorhanden" until someone switches the link explicitly.</li>
 *   <li>Linking, switching and unlinking a ticket need {@code requirement.edit} and {@code ticket.read}. A link is visible only
 *       to a person who may read both sides; an unreadable counterpart is neither shown nor counted. A link changes neither the
 *       status nor the version of the ticket, nor those of the requirement, and is written into both histories.</li>
 *   <li>The requirement shows its readable tickets with their status and counts those whose status has the category
 *       {@code done}. A done ticket never approves the requirement or declares it fulfilled.</li>
 *   <li>A wiki page of the project is shown with its published title in the requested language for readers with
 *       {@code wiki.read}, otherwise as not available without a title. A page link names no revision.</li>
 *   <li>The ticket shows its readable linked requirements with the linked revision and the hint on a newer one.</li>
 * </ol>
 *
 * <p>Locks, in this order: rights of the requirement and of the ticket (held until the commit), the project row of the project
 * adapter, the requirement row ({@code FOR UPDATE}, so two changes of the links of one requirement run one after the other), the
 * ticket row ({@code FOR SHARE}, so its history entry carries a time after a field change it waited for).
 */
@Service
public class RequirementLinks implements TicketRequirementReaders {
    static final String TICKET_READ = "ticket.read";
    /** At most this many tickets and, separately, wiki pages per requirement, so a view stays small. */
    public static final int LINKS_MAX = 200;

    private final RequirementRepository repository;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final TicketAccess tickets;
    private final WikiPages wiki;
    private final Clock clock;

    RequirementLinks(RequirementRepository repository, ProjectAccess projects, Permissions permissions, TicketAccess tickets, WikiPages wiki,
            Clock clock) {
        this.repository = repository;
        this.projects = projects;
        this.permissions = permissions;
        this.tickets = tickets;
        this.wiki = wiki;
        this.clock = clock;
    }

    /**
     * A linked ticket as the caller may see it: title and status only with their read rules, {@code done} only with a readable
     * status. {@code newerRevision} is the current revision of the requirement when the link names an older one.
     */
    public record TicketLinkView(UUID ticketId, long number, String key, String title, String status, String statusName, boolean done,
            int revision, Integer newerRevision, Instant linkedAt, UUID linkedBy) {
    }

    /** A linked wiki page; {@code title} is {@code null} when the page is not available to the caller. */
    public record PageLinkView(UUID pageId, String title, Instant linkedAt, UUID linkedBy) {
    }

    /** "n tickets, of them m done", over the tickets the caller may read. */
    public record Summary(int tickets, int done) {
    }

    /** The links of one requirement; {@code edit} tells whether the caller may change them. */
    public record Links(UUID requirementId, int revision, boolean edit, boolean ticketsReadable, List<TicketLinkView> tickets, Summary summary,
            List<PageLinkView> pages) {
    }

    /** A requirement linked to a ticket as the caller may see it; the title only with its read rule. */
    public record TicketRequirementView(UUID requirementId, long number, String key, String title, String status, int revision,
            Integer newerRevision, Instant linkedAt, UUID linkedBy) {
    }

    /** The field "Anforderungen" of a ticket: empty without {@code requirement.read}. */
    public record TicketRequirements(UUID ticketId, boolean read, boolean edit, List<TicketRequirementView> requirements) {
    }

    // ------------------------------------------------------------------------------------------------ reading

    @Override
    @Transactional
    public boolean mayRead(AccessContext actor, UUID projectId) {
        return !actor.external() && permissions.inspect(actor, resource(actor, projectId, RequirementService.RESOURCE_TYPE),
            RequirementService.READ, Set.of()).allowed();
    }

    @Transactional
    public Links links(AccessContext actor, UUID id, String language) {
        RequirementRepository.Requirement requirement = visible(actor, id);
        return view(actor, requirement, language);
    }

    /** The linked requirements of a ticket the caller may read, by number in its project. */
    @Transactional
    public TicketRequirements ofTicket(AccessContext actor, UUID projectId, long number) {
        Decision ticketRead = ticketRead(actor, projectId);
        if (!ticketRead.allowed()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        UUID ticketId = tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
        Supplier<ResourceContext> resource = resource(actor, projectId, RequirementService.RESOURCE_TYPE);
        Decision read = permissions.inspect(actor, resource, RequirementService.READ, RequirementService.FIELDS);
        if (actor.external() || !read.allowed()) {
            return new TicketRequirements(ticketId, false, false, List.of());
        }
        boolean edit = permissions.inspect(actor, resource, RequirementService.EDIT, Set.of()).allowed();
        boolean titles = read.readableFields().contains("title");
        List<TicketRequirementView> result = new ArrayList<>();
        for (RequirementRepository.TicketLink link : repository.linksOfTicket(actor.companyId(), ticketId)) {
            repository.find(actor.companyId(), link.requirementId()).filter(r -> r.projectId().equals(projectId)).ifPresent(r -> {
                String title = titles ? repository.revision(r.id(), r.currentRevision()).map(RequirementRepository.Revision::title).orElse(null) : null;
                result.add(new TicketRequirementView(r.id(), r.number(), RequirementService.KEY_PREFIX + r.number(), title, r.status(), link.revision(),
                    link.revision() < r.currentRevision() ? r.currentRevision() : null, link.linkedAt(), link.linkedBy()));
            });
        }
        result.sort(Comparator.comparingLong(TicketRequirementView::number));
        return new TicketRequirements(ticketId, true, edit, List.copyOf(result));
    }

    // ------------------------------------------------------------------------------------------------ changes

    /** Links the ticket with this number to the current revision of the requirement. */
    @Transactional
    public Links linkTicket(AccessContext actor, UUID id, Long number, String language) {
        if (number == null || number < 1) {
            throw invalid("number.invalid");
        }
        RequirementRepository.Requirement requirement = changing(actor, id, true, true);
        UUID ticketId = tickets.find(actor.companyId(), requirement.projectId(), number).orElseThrow(() -> invalid("ticket.unknown"));
        if (repository.ticketLink(requirement.id(), ticketId).isPresent()) {
            throw ApiProblemException.of(409, "requirement.already_linked");
        }
        if (repository.ticketLinks(requirement.id()).size() >= LINKS_MAX) {
            throw ApiProblemException.of(409, "requirement.links_too_many");
        }
        tickets.lockForRequirementChange(ticketId);
        Instant now = clock.instant();
        int revision = requirement.currentRevision();
        repository.insert(actor.companyId(), new RequirementRepository.TicketLink(requirement.id(), ticketId, revision, now, actor.userId()));
        repository.event(actor.companyId(), requirement.id(), revision, "ticket_linked", "tickets", null, null, number + ":" + revision, null,
            actor.userId(), now);
        tickets.recordRequirementChange(ticketId, now, actor.userId(), null, label(requirement, revision));
        return view(actor, requirement, language);
    }

    /** Binds the link to the current revision, which has to be the {@code revision} the caller saw. */
    @Transactional
    public Links switchTicket(AccessContext actor, UUID id, long number, Integer revision, String language) {
        if (revision == null) {
            throw invalid("revision.required");
        }
        RequirementRepository.Requirement requirement = changing(actor, id, true, true);
        UUID ticketId = linkedTicket(actor, requirement, number);
        RequirementRepository.TicketLink link = repository.ticketLink(requirement.id(), ticketId).orElseThrow(RequirementLinks::linkNotFound);
        if (revision != requirement.currentRevision()) {
            throw ApiProblemException.of(412, "requirement.version_conflict");
        }
        if (link.revision() == revision) {
            return view(actor, requirement, language);
        }
        tickets.lockForRequirementChange(ticketId);
        Instant now = clock.instant();
        repository.switchRevision(requirement.id(), ticketId, revision);
        repository.event(actor.companyId(), requirement.id(), revision, "ticket_switched", "tickets", null, number + ":" + link.revision(),
            number + ":" + revision, null, actor.userId(), now);
        tickets.recordRequirementChange(ticketId, now, actor.userId(), label(requirement, link.revision()), label(requirement, revision));
        return view(actor, requirement, language);
    }

    @Transactional
    public Links unlinkTicket(AccessContext actor, UUID id, long number, String language) {
        RequirementRepository.Requirement requirement = changing(actor, id, false, true);
        UUID ticketId = linkedTicket(actor, requirement, number);
        RequirementRepository.TicketLink link = repository.ticketLink(requirement.id(), ticketId).orElseThrow(RequirementLinks::linkNotFound);
        tickets.lockForRequirementChange(ticketId);
        Instant now = clock.instant();
        repository.deleteTicketLink(requirement.id(), ticketId);
        repository.event(actor.companyId(), requirement.id(), requirement.currentRevision(), "ticket_unlinked", "tickets", null,
            number + ":" + link.revision(), null, null, actor.userId(), now);
        tickets.recordRequirementChange(ticketId, now, actor.userId(), label(requirement, link.revision()), null);
        return view(actor, requirement, language);
    }

    /** Links a wiki page of the project whose published title the caller may read. */
    @Transactional
    public Links linkPage(AccessContext actor, UUID id, UUID pageId, String language) {
        if (pageId == null) {
            throw invalid("pageId.invalid");
        }
        RequirementRepository.Requirement requirement = changing(actor, id, true, false);
        if (!wiki.publishedTitles(actor, requirement.projectId(), List.of(pageId), language).containsKey(pageId)) {
            throw invalid("page.unknown");
        }
        if (repository.pageLinked(requirement.id(), pageId)) {
            throw ApiProblemException.of(409, "requirement.already_linked");
        }
        if (repository.pageLinks(requirement.id()).size() >= LINKS_MAX) {
            throw ApiProblemException.of(409, "requirement.links_too_many");
        }
        Instant now = clock.instant();
        repository.insert(actor.companyId(), new RequirementRepository.PageLink(requirement.id(), pageId, now, actor.userId()));
        repository.event(actor.companyId(), requirement.id(), requirement.currentRevision(), "page_linked", "pages", null, null, pageId.toString(),
            null, actor.userId(), now);
        return view(actor, requirement, language);
    }

    /** Removes the link of a page; it needs no right on the page, so a link of an archived or hidden page can be removed. */
    @Transactional
    public Links unlinkPage(AccessContext actor, UUID id, UUID pageId, String language) {
        RequirementRepository.Requirement requirement = changing(actor, id, false, false);
        if (!repository.pageLinked(requirement.id(), pageId)) {
            throw linkNotFound();
        }
        Instant now = clock.instant();
        repository.deletePageLink(requirement.id(), pageId);
        repository.event(actor.companyId(), requirement.id(), requirement.currentRevision(), "page_unlinked", "pages", null, pageId.toString(), null,
            null, actor.userId(), now);
        return view(actor, requirement, language);
    }

    // ------------------------------------------------------------------------------------------------ for the history

    /** What the history of a requirement may show of its links: ticket events need {@code ticket.read}, page titles {@code wiki.read}. */
    record HistoryLinks(boolean tickets, Map<UUID, String> pageTitles) {
    }

    HistoryLinks historyLinks(AccessContext actor, UUID projectId, List<RequirementRepository.Event> events, String language) {
        Set<UUID> pages = new HashSet<>();
        for (RequirementRepository.Event event : events) {
            if ("pages".equals(event.field())) {
                Optional.ofNullable(event.toValue() != null ? event.toValue() : event.fromValue()).map(RequirementLinks::uuid).ifPresent(pages::add);
            }
        }
        boolean ticketsReadable = ticketRead(actor, projectId).allowed();
        return new HistoryLinks(ticketsReadable, Map.copyOf(wiki.publishedTitles(actor, projectId, pages, language)));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private Links view(AccessContext actor, RequirementRepository.Requirement requirement, String language) {
        UUID projectId = requirement.projectId();
        Decision ticketRead = ticketRead(actor, projectId);
        boolean edit = !"discarded".equals(requirement.status()) && permissions.inspect(actor, resource(actor, projectId,
            RequirementService.RESOURCE_TYPE), RequirementService.EDIT, Set.of()).allowed();
        List<TicketLinkView> ticketViews = new ArrayList<>();
        int done = 0;
        if (ticketRead.allowed()) {
            Set<String> readable = ticketRead.readableFields();
            String projectKey = projects.key(projectId).orElse("");
            ProjectConfiguration configuration = projects.currentConfigurationVersion(projectId).flatMap(v -> projects.configuration(projectId, v))
                .orElse(null);
            for (RequirementRepository.TicketLink link : repository.ticketLinks(requirement.id())) {
                Optional<TicketAccess.State> state = tickets.state(link.ticketId()).filter(s -> s.projectId().equals(projectId));
                if (state.isEmpty()) {
                    continue;
                }
                String status = readable.contains("status") ? state.get().status() : null;
                ProjectConfiguration.Status known = status == null || configuration == null ? null
                    : configuration.statuses().stream().filter(s -> s.key().equals(status)).findFirst().orElse(null);
                boolean isDone = known != null && ProjectConfiguration.CATEGORY_DONE.equals(known.category());
                if (isDone) {
                    done++;
                }
                ticketViews.add(new TicketLinkView(link.ticketId(), state.get().number(), projectKey + "-" + state.get().number(),
                    readable.contains("title") ? tickets.title(link.ticketId()).orElse(null) : null, status, known == null ? null : known.name(),
                    isDone, link.revision(), link.revision() < requirement.currentRevision() ? requirement.currentRevision() : null, link.linkedAt(),
                    link.linkedBy()));
            }
            ticketViews.sort(Comparator.comparingLong(TicketLinkView::number));
        }
        List<RequirementRepository.PageLink> pageLinks = repository.pageLinks(requirement.id());
        Map<UUID, String> titles = wiki.publishedTitles(actor, projectId, pageLinks.stream().map(RequirementRepository.PageLink::pageId).toList(),
            language);
        List<PageLinkView> pageViews = pageLinks.stream()
            .map(link -> new PageLinkView(link.pageId(), titles.get(link.pageId()), link.linkedAt(), link.linkedBy())).toList();
        return new Links(requirement.id(), requirement.currentRevision(), edit, ticketRead.allowed(), List.copyOf(ticketViews),
            new Summary(ticketViews.size(), done), pageViews);
    }

    /**
     * The requirement after {@code requirement.edit} was required and its row locked. {@code linking}: a discarded requirement
     * gets no new link and no switch; removing a link stays possible. {@code ticket}: the change concerns a ticket, which needs
     * {@code ticket.read} (rule 2); without it no ticket of the project exists for the caller.
     */
    private RequirementRepository.Requirement changing(AccessContext actor, UUID id, boolean linking, boolean ticket) {
        RequirementRepository.Requirement found = visible(actor, id);
        Supplier<ResourceContext> resource = resource(actor, found.projectId(), RequirementService.RESOURCE_TYPE);
        if (!permissions.inspect(actor, resource, RequirementService.EDIT, Set.of()).allowed()) {
            throw forbidden();
        }
        try {
            permissions.require(actor, resource, RequirementService.EDIT, Set.of(), Set.of());
            if (ticket) {
                permissions.require(actor, resource(actor, found.projectId(), TicketAccess.RESOURCE_TYPE), TICKET_READ, Set.of(), Set.of());
            }
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
        RequirementRepository.Requirement requirement = repository.lock(actor.companyId(), id).orElseThrow(RequirementLinks::notFound);
        if (linking && "discarded".equals(requirement.status())) {
            throw ApiProblemException.of(409, "requirement.discarded");
        }
        return requirement;
    }

    private UUID linkedTicket(AccessContext actor, RequirementRepository.Requirement requirement, long number) {
        return tickets.find(actor.companyId(), requirement.projectId(), number).orElseThrow(RequirementLinks::linkNotFound);
    }

    private RequirementRepository.Requirement visible(AccessContext actor, UUID id) {
        RequirementRepository.Requirement requirement = repository.find(actor.companyId(), id).orElseThrow(RequirementLinks::notFound);
        if (!mayRead(actor, requirement.projectId())) {
            throw notFound();
        }
        return requirement;
    }

    private Decision ticketRead(AccessContext actor, UUID projectId) {
        if (actor.external()) {
            return Decision.denied();
        }
        return permissions.inspect(actor, resource(actor, projectId, TicketAccess.RESOURCE_TYPE), TICKET_READ, Set.of("title", "status"));
    }

    private Supplier<ResourceContext> resource(AccessContext actor, UUID projectId, String type) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), type, false);
    }

    private static String label(RequirementRepository.Requirement requirement, int revision) {
        return RequirementService.KEY_PREFIX + requirement.number() + ":" + revision;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static ApiProblemException invalid(String violation) {
        return ApiProblemException.withViolations(400, "request.invalid", List.of(violation));
    }

    private static ApiProblemException notFound() {
        return ApiProblemException.of(404, "requirement.not_found");
    }

    private static ApiProblemException linkNotFound() {
        return ApiProblemException.of(404, "requirement.link_not_found");
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
