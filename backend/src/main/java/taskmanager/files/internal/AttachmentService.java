package taskmanager.files.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.tickets.TicketAccess;
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
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AttachmentRepository attachments;
    private final FileStorage storage;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final Permissions permissions;
    private final Clock clock;
    private final UploadRules rules;
    private final TransactionTemplate transaction;

    AttachmentService(AttachmentRepository attachments, FileStorage storage, ProjectAccess projects, TicketAccess tickets,
            Permissions permissions, Clock clock, FilesProperties properties, PlatformTransactionManager transactions) {
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
    public List<Attachment> list(AccessContext actor, UUID projectId, long number) {
        UUID ticket = readableTicket(actor, projectId, number);
        return attachments.visible(ticket);
    }

    @Transactional
    public Download download(AccessContext actor, UUID projectId, long number, UUID id) {
        UUID ticket = readableTicket(actor, projectId, number);
        Attachment attachment = attachments.find(ticket, id).filter(found -> !found.removed()).orElseThrow(AttachmentService::notFound);
        try {
            return new Download(attachment, storage.open(attachment.id()));
        } catch (NoSuchFileException missing) {
            throw notFound();
        } catch (IOException unavailable) {
            throw storageUnavailable();
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
        String name = UploadRules.clean(rawName);
        rules.checkName(name);
        if (declaredLength > rules.maxBytes()) {
            throw ApiProblemException.of(413, "attachment.too_large");
        }
        FileStorage.Staged staged;
        try {
            staged = storage.stage(body, rules.maxBytes());
        } catch (IOException failure) {
            throw storageUnavailable();
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
                throw storageUnavailable();
            }
        } catch (RuntimeException refused) {
            storage.discard(staged);
            throw refused;
        }
        try {
            return transaction.execute(status -> add(actor, projectId, number, id, name, staged.size(), staged.sha256()));
        } catch (ApiProblemException | PermissionDenied refused) {
            // Definitely not committed: the content has no row and can go at once. Any other failure may have committed.
            deleteQuietly(id);
            throw refused;
        }
    }

    /** Runs inside the transaction of {@link #upload}. */
    private Attachment add(AccessContext actor, UUID projectId, long number, UUID id, String name, long size, String sha256) {
        UUID ticket = changeableTicket(actor, projectId, number);
        // The ticket row is held before the time is read, so a change that waited for another one carries a later time.
        tickets.lockForAttachmentChange(ticket);
        Instant now = clock.instant();
        Attachment attachment = new Attachment(id, actor.companyId(), ticket, projectId, name, size, sha256, now, actor.userId(), null, null, null);
        attachments.insert(attachment);
        attachments.audit(actor.companyId(), id, ticket, "attachment.added", describe(attachment), actor.userId(), now);
        tickets.recordAttachmentChange(ticket, now, actor.userId(), null, describe(attachment));
        return attachment;
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

    private static ApiProblemException storageUnavailable() {
        return ApiProblemException.retryAfter(503, "attachment.storage_unavailable", 5);
    }
}
