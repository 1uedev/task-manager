package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.files.AttachmentLookup;
import taskmanager.helpdesk.CustomerAnswered;
import taskmanager.helpdesk.ReplySent;
import taskmanager.helpdesk.TicketCustomerChanged;
import taskmanager.identity.IdentityVerification;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketOrganizations;
import tools.jackson.databind.json.JsonMapper;

/**
 * The conversation of the customer portal (TASK-076, DEC-106).
 *
 * <p>A portal account creates a request with title, description and one of the ticket types the project offers in the portal;
 * customer and contact come from its account. It answers a request of its organization with a post of up to 100 000 characters
 * and the files it uploaded to that request before; a post counts as an answer of the customer like a mail (event
 * {@link CustomerAnswered}). An internal account with {@code helpdesk.reply} answers through the portal; such an answer counts as
 * the first response like a sent mail (event {@link ReplySent}).
 *
 * <p>The portal shows per request the conversation: the mails of the organization that became part of the ticket, the sent mail
 * answers, and the posts in both directions. The internal history, the review queue, failed or uncertain mails and every other
 * internal data stay out. Attachments appear only when the caller may read the field {@code attachments} and the attachment is
 * visible to the customer.
 *
 * <p>Rights of a portal account are checked with the organization of the request as the organization of the resource, so a
 * request of another organization answers 404 like an unknown one. Locks, in this order: rights, identity, project row
 * ({@code FOR SHARE}), link row of the customer ({@code FOR SHARE}), ticket row ({@code FOR SHARE}), the posts of the ticket
 * (advisory lock), attachment rows ({@code FOR SHARE}).
 */
@Service
public class PortalConversation {
    static final String ANSWER = "portal.answer";
    private static final String READ = "ticket.read";
    private static final String CREATE = "ticket.create";
    private static final Set<String> SUPPORT_READS = Set.of(TicketAccess.MAIL_FIELD);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int TITLE_MAX = 200;
    private static final int DESCRIPTION_MAX = 20_000;
    private static final int BODY_MAX = 100_000;
    private static final int FILES_MAX = 20;

    private final PortalRepository posts;
    private final HelpdeskRepository repository;
    private final ReplyService replies;
    private final TicketAccess tickets;
    private final TicketOrganizations organizations;
    private final AttachmentLookup attachments;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final IdentityVerification identity;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PortalConversation(PortalRepository posts, HelpdeskRepository repository, ReplyService replies, TicketAccess tickets,
            TicketOrganizations organizations, AttachmentLookup attachments, ProjectAccess projects, Permissions permissions,
            IdentityVerification identity, ApplicationEventPublisher events, Clock clock) {
        this.posts = posts;
        this.repository = repository;
        this.replies = replies;
        this.tickets = tickets;
        this.organizations = organizations;
        this.attachments = attachments;
        this.projects = projects;
        this.permissions = permissions;
        this.identity = identity;
        this.events = events;
        this.clock = clock;
    }

    /** A new request of the portal. */
    public record Created(UUID ticketId, long number) {
    }

    /** An entry of the conversation: {@code mail}, {@code reply} (sent mail answer) or {@code post}. */
    public record Entry(String kind, UUID id, Instant at, String author, boolean own, String subject, String body,
            List<AttachmentLookup.Summary> attachments) {
    }

    /** A post as the internal ticket view shows it, with its author. */
    public record PostView(UUID id, String authorKind, UUID authorId, String body, Instant createdAt, List<AttachmentLookup.Summary> attachments) {
    }

    /** A request of the portal list with its last entry, the last answer of the support and when the caller last opened it. */
    public record Activity(long number, Instant lastActivityAt, Instant lastSupportAt, Instant seenAt) {
        public boolean unread() {
            return lastSupportAt != null && (seenAt == null || lastSupportAt.isAfter(seenAt));
        }
    }

    // ------------------------------------------------------------------------------------------------ portal

    /**
     * Creates a request: {@code ticket.create} with the write rules of title (and description when given) in the organization of
     * the caller. The type must be one the project offers in the portal; customer is the organization of the account, contact
     * its contact while the contact still belongs to the organization. The service deadlines start now.
     */
    @Transactional
    public Created create(AccessContext actor, UUID projectId, String type, String rawTitle, String rawDescription) {
        portalOnly(actor);
        String title = ParsedMail.clean(rawTitle);
        String description = ParsedMail.clean(rawDescription);
        Supplier<ResourceContext> project = projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
        Supplier<ResourceContext> resource = () -> withOrganization(project.get(), actor.organizationId());
        if (!permissions.inspect(actor, resource, READ, Set.of()).allowed()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        Set<String> writes = description == null ? Set.of("title") : Set.of("title", "description");
        try {
            permissions.require(actor, resource, CREATE, Set.of(), writes);
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        ProjectConfiguration configuration = projects.currentConfigurationVersion(projectId).flatMap(v -> projects.configuration(projectId, v))
            .orElseThrow(() -> ApiProblemException.of(404, "project.not_found"));
        List<String> violations = new ArrayList<>();
        if (type == null || configuration.portalTypes().stream().noneMatch(t -> t.key().equals(type))) {
            violations.add("type.not_offered");
        }
        if (title == null || title.contains("\n") || title.codePointCount(0, title.length()) > TITLE_MAX) {
            violations.add("title.invalid");
        }
        if (description != null && description.length() > DESCRIPTION_MAX) {
            violations.add("description.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Customer customer = repository.customer(actor.companyId(), actor.organizationId())
            .orElseThrow(() -> ApiProblemException.of(404, "project.not_found"));
        Contact contact = identity.portalContact(actor.userId()).flatMap(id -> repository.shareContact(actor.companyId(), id))
            .filter(c -> c.customerId().equals(customer.id())).orElse(null);
        Instant now = clock.instant();
        TicketAccess.Created created = tickets.createForHelpdesk(actor.companyId(), projectId, type, title, description, actor.userId())
            .orElseThrow(() -> ApiProblemException.of(404, "project.not_found"));
        repository.setTicketCustomer(actor.companyId(), projectId, created.id(), customer.id(), contact == null ? null : contact.id());
        tickets.recordHelpdeskChange(created.id(), now, actor.userId(), TicketAccess.CUSTOMER_FIELD, null, TicketOpener.reference(customer.id(), customer.name()));
        if (contact != null) {
            tickets.recordHelpdeskChange(created.id(), now, actor.userId(), TicketAccess.CONTACT_FIELD, null, TicketOpener.reference(contact.id(), contact.name()));
        }
        events.publishEvent(new TicketCustomerChanged(actor.companyId(), projectId, created.id(), customer.id(), null, now));
        repository.audit(actor.companyId(), "ticket", created.id(), "portal.created", null, actor.userId(), now);
        return new Created(created.id(), created.number());
    }

    /**
     * A post of the customer: {@code portal.answer} on a request of the organization. The files are attachments of this request
     * that the caller uploaded through the portal and no other post carries yet.
     */
    @Transactional
    public Entry post(AccessContext actor, UUID projectId, long number, String rawBody, List<UUID> attachmentIds) {
        Portal portal = portalTicket(actor, projectId, number, ANSWER);
        String body = ParsedMail.clean(rawBody);
        Set<UUID> files = attachmentIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(attachmentIds);
        List<String> violations = new ArrayList<>();
        if (body == null || body.length() > BODY_MAX) {
            violations.add("body.invalid");
        }
        if (files.size() > FILES_MAX || files.contains(null)) {
            violations.add("attachmentIds.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        tickets.lockForHelpdeskChange(portal.ticketId());
        posts.lockPosts(portal.ticketId());
        List<AttachmentLookup.Summary> carried = List.of();
        if (!files.isEmpty()) {
            if (!portal.attachmentsReadable()) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("attachmentIds.invalid"));
            }
            carried = attachments.find(portal.ticketId(), files, true);
            Set<UUID> linked = posts.linked(files);
            boolean usable = carried.size() == files.size() && carried.stream().allMatch(a -> a.customerVisible() && !a.removed()
                && actor.userId().equals(a.uploadedBy()) && !linked.contains(a.id()));
            if (!usable) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("attachmentIds.invalid"));
            }
        }
        Instant now = clock.instant();
        PortalRepository.Post post = new PortalRepository.Post(UUID.randomUUID(), actor.companyId(), projectId, portal.ticketId(),
            PortalRepository.Post.CUSTOMER, actor.userId(), body, now);
        posts.insert(post);
        carried.forEach(file -> posts.link(post.id(), file.id()));
        events.publishEvent(new CustomerAnswered(actor.companyId(), projectId, portal.ticketId(), now));
        repository.audit(actor.companyId(), "ticket", portal.ticketId(), "portal.posted",
            JSON.writeValueAsString(new PostDetail(post.id(), carried.size())), actor.userId(), now);
        return new Entry("post", post.id(), now, PortalRepository.Post.CUSTOMER, true, null, body, carried);
    }

    /** The conversation of a request of the organization, oldest first; {@code ticket.read} is enough. */
    @Transactional
    public List<Entry> timeline(AccessContext actor, UUID projectId, long number) {
        Portal portal = portalTicket(actor, projectId, number, null);
        List<Entry> entries = new ArrayList<>();
        List<UUID> files = new ArrayList<>();
        List<Message> mails = repository.messagesOfTicket(portal.ticketId()).stream()
            .filter(m -> m.state().equals("ticket") && actor.organizationId().equals(m.customerId())).toList();
        mails.forEach(m -> m.attachments().stream().map(Message.AttachmentNote::attachmentId).filter(java.util.Objects::nonNull).forEach(files::add));
        List<PortalRepository.Post> own = posts.posts(portal.ticketId());
        Map<UUID, List<UUID>> carried = posts.attachmentsOf(own.stream().map(PortalRepository.Post::id).toList());
        carried.values().forEach(files::addAll);
        Map<UUID, AttachmentLookup.Summary> visible = portal.attachmentsReadable() && !files.isEmpty()
            ? attachments.find(portal.ticketId(), files, false).stream().filter(a -> a.customerVisible() && !a.removed())
                .collect(Collectors.toMap(AttachmentLookup.Summary::id, Function.identity()))
            : Map.of();
        for (Message mail : mails) {
            entries.add(new Entry("mail", mail.id(), mail.receivedAt(), PortalRepository.Post.CUSTOMER, false, mail.subject(), mail.body(),
                pick(visible, mail.attachments().stream().map(Message.AttachmentNote::attachmentId).toList())));
        }
        for (Reply reply : repository.repliesOfTicket(portal.ticketId())) {
            if (reply.state().equals("sent")) {
                entries.add(new Entry("reply", reply.id(), reply.sentAt() == null ? reply.createdAt() : reply.sentAt(),
                    PortalRepository.Post.SUPPORT, false, reply.subject(), reply.body(), List.of()));
            }
        }
        for (PortalRepository.Post post : own) {
            boolean customer = post.authorKind().equals(PortalRepository.Post.CUSTOMER);
            entries.add(new Entry("post", post.id(), post.createdAt(), post.authorKind(), customer && post.authorId().equals(actor.userId()),
                null, post.body(), pick(visible, carried.getOrDefault(post.id(), List.of()))));
        }
        entries.sort(Comparator.comparing(Entry::at).thenComparing(Entry::id));
        return entries;
    }

    /** Records that the caller opened the request now, for the marker of new answers. */
    @Transactional
    public void seen(AccessContext actor, UUID projectId, long number) {
        Portal portal = portalTicket(actor, projectId, number, null);
        posts.seen(actor.userId(), portal.ticketId(), clock.instant());
    }

    /** The requests of the organization in the project with their last entry and the marker of new answers of the support. */
    @Transactional
    public List<Activity> activity(AccessContext actor, UUID projectId) {
        portalOnly(actor);
        Supplier<ResourceContext> project = projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
        Supplier<ResourceContext> resource = () -> withOrganization(project.get(), actor.organizationId());
        if (!permissions.inspect(actor, resource, READ, Set.of()).allowed()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        List<UUID> own = organizations.lockTicketsOf(projectId, actor.organizationId());
        List<Activity> result = new ArrayList<>();
        for (PortalRepository.Activity entry : posts.activity(actor.userId(), actor.organizationId(), own)) {
            tickets.locate(entry.ticketId()).ifPresent(location ->
                result.add(new Activity(location.number(), entry.lastActivityAt(), entry.lastSupportAt(), entry.seenAt())));
        }
        result.sort(Comparator.comparingLong(Activity::number).reversed());
        return result;
    }

    // ------------------------------------------------------------------------------------------------ support

    /**
     * An answer of the support through the portal: the rights of a mail answer ({@code helpdesk.reply}, {@code ticket.read} with
     * the read rules of {@code mail} and {@code contact}), and the request needs a customer whose portal can show it.
     */
    @Transactional
    public PostView answer(AccessContext actor, UUID projectId, long number, String rawBody) {
        internalOnly(actor);
        replies.requireReply(actor, projectId);
        UUID ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
        String body = ParsedMail.clean(rawBody);
        if (body == null || body.length() > BODY_MAX) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("body.invalid"));
        }
        // The order of a change of the customer (TASK-062): the lock of the customer link first, then the ticket row.
        repository.lockTicketCustomer(ticket);
        tickets.lockForHelpdeskChange(ticket);
        if (repository.ticketCustomer(ticket).map(HelpdeskRepository.TicketCustomer::customerId).isEmpty()) {
            throw ApiProblemException.of(409, "helpdesk.portal_no_customer");
        }
        Instant now = clock.instant();
        PortalRepository.Post post = new PortalRepository.Post(UUID.randomUUID(), actor.companyId(), projectId, ticket,
            PortalRepository.Post.SUPPORT, actor.userId(), body, now);
        posts.insert(post);
        events.publishEvent(new ReplySent(actor.companyId(), projectId, ticket, post.id(), now));
        repository.audit(actor.companyId(), "ticket", ticket, "portal.answered", JSON.writeValueAsString(new PostDetail(post.id(), 0)),
            actor.userId(), now);
        return new PostView(post.id(), post.authorKind(), post.authorId(), body, now, List.of());
    }

    /**
     * The posts of a request for the internal ticket view, oldest first: {@code ticket.read} with the read rule of {@code mail},
     * as for the mails of the ticket. Attachments appear with the read rule of {@code attachments}.
     */
    @Transactional
    public List<PostView> posts(AccessContext actor, UUID projectId, long number) {
        internalOnly(actor);
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
        Decision read = permissions.inspect(actor, resource, READ, Set.of(TicketAccess.MAIL_FIELD, TicketAccess.ATTACHMENTS_FIELD));
        if (!read.allowed()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        if (!read.readableFields().containsAll(SUPPORT_READS)) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        UUID ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
        List<PortalRepository.Post> own = posts.posts(ticket);
        Map<UUID, List<UUID>> carried = posts.attachmentsOf(own.stream().map(PortalRepository.Post::id).toList());
        List<UUID> files = carried.values().stream().flatMap(List::stream).toList();
        Map<UUID, AttachmentLookup.Summary> known = read.readableFields().contains(TicketAccess.ATTACHMENTS_FIELD) && !files.isEmpty()
            ? attachments.find(ticket, files, false).stream().filter(a -> !a.removed())
                .collect(Collectors.toMap(AttachmentLookup.Summary::id, Function.identity()))
            : Map.of();
        return own.stream().map(p -> new PostView(p.id(), p.authorKind(), p.authorId(), p.body(), p.createdAt(),
            pick(known, carried.getOrDefault(p.id(), List.of())))).toList();
    }

    // ------------------------------------------------------------------------------------------------ rules

    private record Portal(UUID ticketId, boolean attachmentsReadable) {
    }

    /**
     * Checks {@code ticket.read} of a portal account on a request, with the organization of the request as the organization of
     * the resource; a refusal answers 404 like an unknown request. With an {@code action} it is required too; a refusal of it
     * answers 403.
     */
    private Portal portalTicket(AccessContext actor, UUID projectId, long number, String action) {
        portalOnly(actor);
        Supplier<ResourceContext> project = projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
        Supplier<ResourceContext> resource = () -> {
            ResourceContext base = project.get();
            if (!base.projectMember()) {
                return base;
            }
            UUID organization = tickets.find(actor.companyId(), projectId, number).flatMap(organizations::lockOrganization).orElse(null);
            return withOrganization(base, organization);
        };
        Decision read = permissions.inspect(actor, resource, READ, Set.of(TicketAccess.ATTACHMENTS_FIELD));
        if (!read.allowed()) {
            throw ApiProblemException.of(404, "ticket.not_found");
        }
        if (action != null) {
            try {
                permissions.require(actor, resource, action, Set.of(), Set.of());
            } catch (PermissionDenied denied) {
                throw ApiProblemException.of(403, "auth.forbidden");
            }
        }
        UUID ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
        return new Portal(ticket, read.readableFields().contains(TicketAccess.ATTACHMENTS_FIELD));
    }

    private static List<AttachmentLookup.Summary> pick(Map<UUID, AttachmentLookup.Summary> known, List<UUID> ids) {
        return ids.stream().filter(java.util.Objects::nonNull).map(known::get).filter(java.util.Objects::nonNull).toList();
    }

    private static ResourceContext withOrganization(ResourceContext base, UUID organization) {
        return new ResourceContext(base.companyId(), base.projectId(), organization, base.projectMember(), base.resourceType());
    }

    private static void portalOnly(AccessContext actor) {
        if (!actor.external()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
    }

    private static void internalOnly(AccessContext actor) {
        if (actor.external()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
    }

    private record PostDetail(UUID post, int attachments) {
    }
}
