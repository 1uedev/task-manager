package taskmanager.files.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.files.AttachmentIntake;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketOrganizations;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

/**
 * Attachments of tickets (TASK-051, DEC-080).
 *
 * <p>Rights: the attachments are the ticket field {@code attachments} of the resource type {@code ticket}. Listing and
 * downloading need {@code ticket.read} with a read rule for the field, adding and removing need {@code ticket.edit} with a
 * write rule for it. Whoever may not read the ticket gets 404 {@code project.not_found} like for the tags, whoever may
 * read the ticket but not the field gets 403. The identifier of an attachment is no proof of anything: every download
 * checks the rights again and the attachment must belong to the ticket named in the path.
 *
 * <p>Order: an upload is read to the staging area outside of any transaction, checked, and put in its place in the file
 * store under the identifier of the new attachment. Only then one transaction checks the rights with their locks, inserts
 * the row and records the change in the history. A crash in between leaves content without a row, which the clean up
 * removes after a day; a row without content cannot arise. Removing an attachment hides it at once and records the
 * removal; the content is deleted by the clean up after the retention period, the row stays for the history.
 *
 * <p>Locks, in this order: rights, identity of the requester, project row ({@code FOR SHARE}, held by the project
 * adapter until the commit), the ticket row ({@code FOR SHARE}, to record the change in the history), the attachment row.
 */
@Service
public class AttachmentService {
    static final String FIELD = TicketAccess.ATTACHMENTS_FIELD;
    private static final String TICKET_READ = "ticket.read";
    private static final String TICKET_EDIT = "ticket.edit";
    /** The project action with which a portal account answers a request and adds files to it (TASK-076). */
    static final String PORTAL_ANSWER = "portal.answer";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachments;
    private final FileStorage storage;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final Permissions permissions;
    private final Clock clock;
    private final UploadRules rules;
    private final TransactionTemplate transaction;
    private final ObjectProvider<TicketOrganizations> organizations;

    AttachmentService(AttachmentRepository attachments, FileStorage storage, ProjectAccess projects, TicketAccess tickets,
            Permissions permissions, Clock clock, FilesProperties properties, PlatformTransactionManager transactions,
            ObjectProvider<TicketOrganizations> organizations) {
        this.organizations = organizations;
        this.attachments = attachments;
        this.storage = storage;
        this.projects = projects;
        this.tickets = tickets;
        this.permissions = permissions;
        this.clock = clock;
        this.rules = new UploadRules(properties.maxBytes(), properties.blockedExtensions());
        this.transaction = new TransactionTemplate(transactions);
    }

    /** The content of an attachment to send: the attachment and the open stream, which the caller closes. */
    public record Download(Attachment attachment, InputStream content) {
    }

    public long maxBytes() {
        return rules.maxBytes();
    }

    // ------------------------------------------------------------------------------------------------ reading

    @Transactional
    public Listing list(AccessContext actor, UUID projectId, long number) {
        UUID ticket = readableTicket(actor, projectId, number);
        return new Listing(attachments.visible(ticket), currentCustomer(ticket));
    }

    /** The attachments of a ticket and its current customer, so the list can show who sees a file now (TASK-079). */
    public record Listing(List<Attachment> items, UUID customer) {
    }

    @Transactional
    public Download download(AccessContext actor, UUID projectId, long number, UUID id) {
        UUID ticket = readableTicket(actor, projectId, number);
        Attachment attachment = attachments.find(ticket, id).filter(found -> !found.removed()).orElseThrow(AttachmentService::notFound);
        try {
            return new Download(attachment, storage.open(attachment.id()));
        } catch (NoSuchFileException missing) {
            LOG.warn("The content of attachment {} is missing from the file store", attachment.id());
            throw notFound();
        } catch (IOException unavailable) {
            throw storageUnavailable("open the content of attachment " + attachment.id(), unavailable);
        }
    }

    // ------------------------------------------------------------------------------------------------ adding

    /** Checks that the caller may add attachments to the ticket before a body is read; it holds nothing after it returns. */
    @Transactional
    public void mayAdd(AccessContext actor, UUID projectId, long number) {
        changeableTicket(actor, projectId, number);
    }

    /** Checks the name and reads the upload into the file store, then adds the attachment in one transaction. */
    public Attachment upload(AccessContext actor, UUID projectId, long number, String rawName, long declaredLength, InputStream body) {
        return store(rawName, declaredLength, body, projectId, (id, name, size, sha256) -> {
            UUID ticket = changeableTicket(actor, projectId, number);
            return add(actor, projectId, ticket, id, name, size, sha256, Attachment.INTERNAL, null);
        });
    }

    /**
     * A file a portal account adds to a request of its organization (TASK-076): {@code portal.answer} with the write rule for
     * the attachments, checked like an upload of the staff; the attachment is visible to the customer from the start.
     */
    public Attachment portalUpload(AccessContext actor, UUID projectId, long number, String rawName, long declaredLength, InputStream body) {
        return store(rawName, declaredLength, body, projectId, (id, name, size, sha256) -> {
            UUID ticket = portalTicket(actor, projectId, number, PORTAL_ANSWER, Set.of(), Set.of(FIELD));
            return add(actor, projectId, ticket, id, name, size, sha256, Attachment.CUSTOMER, actor.organizationId());
        });
    }

    /** Checks before a body is read that a portal account may add files to the request; it holds nothing after it returns. */
    @Transactional
    public void portalMayAdd(AccessContext actor, UUID projectId, long number) {
        portalTicket(actor, projectId, number, PORTAL_ANSWER, Set.of(), Set.of(FIELD));
    }

    private interface Adder {
        Attachment add(UUID id, String name, long size, String sha256);
    }

    private Attachment store(String rawName, long declaredLength, InputStream body, UUID projectId, Adder adder) {
        String name = UploadRules.clean(rawName);
        rules.checkName(name);
        if (declaredLength > rules.maxBytes()) {
            throw ApiProblemException.of(413, "attachment.too_large");
        }
        FileStorage.Staged staged;
        try {
            staged = storage.stage(body, rules.maxBytes());
        } catch (FileStorage.UploadInterrupted interrupted) {
            // The client broke the transfer off; nothing is wrong with the store and nothing is left in it.
            LOG.info("An upload to a ticket of project {} ended before its body was read: {}", projectId, interrupted.getCause().toString());
            throw ApiProblemException.of(400, "attachment.incomplete");
        } catch (IOException failure) {
            throw storageUnavailable("write an upload to the staging area", failure);
        }
        UUID id = UUID.randomUUID();
        try {
            if (staged.size() > rules.maxBytes()) {
                throw ApiProblemException.of(413, "attachment.too_large");
            }
            if (staged.size() == 0) {
                throw ApiProblemException.of(400, "attachment.empty");
            }
            UploadRules.checkHead(staged.head());
            try {
                storage.store(staged, id);
            } catch (IOException failure) {
                throw storageUnavailable("put the content of attachment " + id + " in its place", failure);
            }
        } catch (RuntimeException refused) {
            storage.discard(staged);
            throw refused;
        }
        try {
            return transaction.execute(status -> adder.add(id, name, staged.size(), staged.sha256()));
        } catch (ApiProblemException | PermissionDenied refused) {
            // Definitely not committed: the content has no row and can go at once. Any other failure may have committed.
            deleteQuietly(id);
            throw refused;
        }
    }

    /** Runs inside the transaction of {@link #upload}. */
    private Attachment add(AccessContext actor, UUID projectId, UUID ticket, UUID id, String name, long size, String sha256, String visibility,
            UUID organization) {
        // The ticket row is held before the time is read, so a change that waited for another one carries a later time.
        tickets.lockForAttachmentChange(ticket);
        Instant now = clock.instant();
        Attachment attachment = new Attachment(id, actor.companyId(), ticket, projectId, name, size, sha256, now, actor.userId(), null, null, null,
            visibility, organization);
        attachments.insert(attachment);
        attachments.audit(actor.companyId(), id, ticket, "attachment.added", describe(attachment), actor.userId(), now);
        tickets.recordAttachmentChange(ticket, now, actor.userId(), null, describe(attachment));
        return attachment;
    }

    // ------------------------------------------------------------------------------------------------ mail intake

    /**
     * Adds a file of a mail to a ticket in the transaction of the caller (TASK-057); see {@link taskmanager.files.AttachmentIntake}.
     * Runs the checks of an upload and reports a refusal instead of throwing it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AttachmentIntake.Outcome attachFromMail(UUID companyId, UUID projectId, UUID ticketId, String rawName, byte[] content,
            UUID actor, UUID organization) {
        String name;
        try {
            name = UploadRules.clean(rawName);
        } catch (ApiProblemException invalid) {
            return new AttachmentIntake.Outcome(null, rawName, "attachment.name_invalid");
        }
        try {
            rules.checkName(name);
        } catch (ApiProblemException blocked) {
            return new AttachmentIntake.Outcome(null, name, blocked.code());
        }
        if (content.length == 0) {
            return new AttachmentIntake.Outcome(null, name, "attachment.empty");
        }
        if (content.length > rules.maxBytes()) {
            return new AttachmentIntake.Outcome(null, name, "attachment.too_large");
        }
        try {
            UploadRules.checkHead(content);
        } catch (ApiProblemException blocked) {
            return new AttachmentIntake.Outcome(null, name, blocked.code());
        }
        UUID id = UUID.randomUUID();
        FileStorage.Staged staged;
        try {
            staged = storage.stage(new ByteArrayInputStream(content), rules.maxBytes());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        try {
            storage.store(staged, id);
        } catch (IOException failure) {
            storage.discard(staged);
            LOG.warn("The file store could not put the content of attachment {} from a mail in its place: {}", id, failure.toString());
            throw new UncheckedIOException(failure);
        }
        tickets.lockForAttachmentChange(ticketId);
        Instant now = clock.instant();
        // Files of a mail of the customer are visible to the customer: they sent them (TASK-076); to the customer of the mail
        // only (TASK-079).
        Attachment attachment = new Attachment(id, companyId, ticketId, projectId, name, staged.size(), staged.sha256(), now, actor, null, null, null,
            Attachment.CUSTOMER, organization);
        attachments.insert(attachment);
        attachments.audit(companyId, id, ticketId, "attachment.added", describe(attachment), actor, now);
        tickets.recordAttachmentChange(ticketId, now, actor, null, describe(attachment));
        return new AttachmentIntake.Outcome(id, name, null);
    }

    // ------------------------------------------------------------------------------------------------ removing

    /** Hides the attachment and records the removal; removing a removed attachment again changes nothing. */
    @Transactional
    public void remove(AccessContext actor, UUID projectId, long number, UUID id) {
        UUID ticket = changeableTicket(actor, projectId, number);
        tickets.lockForAttachmentChange(ticket);
        Attachment attachment = attachments.lock(ticket, id).orElseThrow(AttachmentService::notFound);
        if (attachment.removed()) {
            return;
        }
        Instant now = clock.instant();
        attachments.markRemoved(id, now, actor.userId());
        attachments.audit(actor.companyId(), id, ticket, "attachment.removed", describe(attachment), actor.userId(), now);
        tickets.recordAttachmentChange(ticket, now, actor.userId(), describe(attachment), null);
    }

    // ------------------------------------------------------------------------------------------------ visibility (TASK-076)

    /**
     * Makes an attachment internal or visible to the customer; needs what removing needs ({@code ticket.edit} with the write
     * rule for the attachments). Setting the visibility it already has changes nothing and writes no audit entry.
     *
     * <p>Visible to the customer means visible to the current customer of the ticket (TASK-079, DEC-109): the attachment keeps
     * that organization when the customer of the ticket changes later. Setting {@code customer} again on an attachment of a
     * former customer is the deliberate release for the current one; it is audited as {@code attachment.audience}. Without a
     * customer the attachment reaches no portal until it is released again.
     */
    @Transactional
    public Attachment setVisibility(AccessContext actor, UUID projectId, long number, UUID id, String visibility) {
        if (!Attachment.INTERNAL.equals(visibility) && !Attachment.CUSTOMER.equals(visibility)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("visibility.invalid"));
        }
        UUID ticket = changeableTicket(actor, projectId, number);
        // The link row of the customer before the ticket row, in the order of the portal (TASK-079).
        UUID audience = Attachment.CUSTOMER.equals(visibility) ? currentCustomer(ticket) : null;
        tickets.lockForAttachmentChange(ticket);
        Attachment attachment = attachments.lock(ticket, id).filter(found -> !found.removed()).orElseThrow(AttachmentService::notFound);
        if (attachment.visibility().equals(visibility) && java.util.Objects.equals(attachment.organizationId(), audience)) {
            return attachment;
        }
        Instant now = clock.instant();
        attachments.setVisibility(id, visibility, audience);
        if (!attachment.visibility().equals(visibility)) {
            attachments.audit(actor.companyId(), id, ticket, "attachment.visibility", visibility, actor.userId(), now);
        }
        if (audience != null) {
            attachments.audit(actor.companyId(), id, ticket, "attachment.audience", audience.toString(), actor.userId(), now);
        }
        return attachments.find(ticket, id).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ portal (TASK-076)

    /** The attachments of a request a portal account may see: only those visible to the customer. */
    @Transactional
    public List<Attachment> portalList(AccessContext actor, UUID projectId, long number) {
        UUID ticket = portalTicket(actor, projectId, number, TICKET_READ, Set.of(FIELD), Set.of());
        return attachments.visible(ticket).stream().filter(a -> a.visibleTo(actor.organizationId())).toList();
    }

    /** One attachment for a portal account; an internal one answers 404 like an unknown one. */
    @Transactional
    public Download portalDownload(AccessContext actor, UUID projectId, long number, UUID id) {
        UUID ticket = portalTicket(actor, projectId, number, TICKET_READ, Set.of(FIELD), Set.of());
        Attachment attachment = attachments.find(ticket, id).filter(found -> found.visibleTo(actor.organizationId()))
            .orElseThrow(AttachmentService::notFound);
        try {
            return new Download(attachment, storage.open(attachment.id()));
        } catch (NoSuchFileException missing) {
            LOG.warn("The content of attachment {} is missing from the file store", attachment.id());
            throw notFound();
        } catch (IOException unavailable) {
            throw storageUnavailable("open the content of attachment " + attachment.id(), unavailable);
        }
    }

    /** The current customer of the ticket, held {@code FOR SHARE}; null without one or without the helpdesk. */
    private UUID currentCustomer(UUID ticket) {
        TicketOrganizations ports = organizations.getIfUnique();
        return ports == null ? null : ports.lockCustomer(ticket).orElse(null);
    }

    /**
     * Checks a request of the organization of a portal account (TASK-076): the organization of the resource is the customer
     * of the ticket, so a request of another organization or without customer answers 404 like an unknown one, as does a
     * request the account may not read at all. The action and the field rules then decide; a refusal there answers 403.
     */
    private UUID portalTicket(AccessContext actor, UUID projectId, long number, String action, Set<String> reads, Set<String> writes) {
        if (!actor.external()) {
            throw projectNotFound();
        }
        TicketOrganizations ports = organizations.getIfUnique();
        if (ports == null) {
            throw ApiProblemException.of(404, "ticket.not_found");
        }
        Supplier<ResourceContext> project = ticketResource(actor, projectId);
        Supplier<ResourceContext> resource = () -> {
            ResourceContext base = project.get();
            if (!base.projectMember()) {
                return base;
            }
            UUID organization = tickets.find(actor.companyId(), projectId, number).flatMap(ticket -> ports.lockOrganization(ticket, actor.organizationId())).orElse(null);
            return new ResourceContext(base.companyId(), base.projectId(), organization, base.projectMember(), base.resourceType());
        };
        Decision read = permissions.inspect(actor, resource, TICKET_READ, reads);
        if (!read.allowed() || !read.readableFields().containsAll(reads)) {
            throw ApiProblemException.of(404, "ticket.not_found");
        }
        try {
            permissions.require(actor, resource, action, reads, writes);
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
        return ticket(actor, projectId, number);
    }

    // ------------------------------------------------------------------------------------------------ rights

    /** Checks {@code ticket.read} with the read rule for the attachments and returns the ticket. */
    private UUID readableTicket(AccessContext actor, UUID projectId, long number) {
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
        return ticket(actor, projectId, number);
    }

    /** Checks {@code ticket.edit} with the write rule for the attachments and returns the ticket. */
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

    private Supplier<ResourceContext> ticketResource(AccessContext actor, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
    }

    private UUID ticket(AccessContext actor, UUID projectId, long number) {
        return tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private void deleteQuietly(UUID id) {
        try {
            storage.delete(id);
        } catch (IOException ignored) {
            // The clean up removes content without a row.
        }
    }

    /** The attachment as the text the history and the audit trail keep: identifier, name, size and checksum. */
    static String describe(Attachment attachment) {
        return JSON.writeValueAsString(new Description(attachment.id().toString(), attachment.name(), attachment.size(), attachment.sha256()));
    }

    private record Description(String id, String name, long size, String sha256) {
    }

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }

    private static ApiProblemException notFound() {
        return ApiProblemException.of(404, "attachment.not_found");
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }

    /**
     * The file store failed; the cause goes to the log, where the correlation identifier of the request ties it to the 503
     * the client sees (review F7 of TASK-051). The answer itself names no path.
     */
    private static ApiProblemException storageUnavailable(String step, IOException failure) {
        LOG.warn("The file store could not {}: {}", step, failure.toString());
        return ApiProblemException.retryAfter(503, "attachment.storage_unavailable", 5);
    }
}
