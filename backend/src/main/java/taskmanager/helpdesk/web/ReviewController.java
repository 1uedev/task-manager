package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.internal.Message;
import taskmanager.helpdesk.internal.ReviewService;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.tickets.TicketAccess;

/**
 * The review queue of the helpdesk (TASK-057, DEC-031). Every endpoint needs a complete login; the mails of a project are
 * there only for the holders of {@code helpdesk.review} in it, for everyone else they do not exist (404). The list shows no
 * text, the single mail does. Changes need If-Match with the version of the mail.
 */
@RestController
@RequestMapping("/api/v1/helpdesk/inbox")
class ReviewController {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final ReviewService review;
    private final TicketAccess tickets;
    private final Requests requests;

    ReviewController(ReviewService review, TicketAccess tickets, Requests requests) {
        this.review = review;
        this.tickets = tickets;
        this.requests = requests;
    }

    record TicketReference(UUID projectId, long number) {
    }

    record MessageBody(UUID id, UUID mailboxId, UUID projectId, Instant receivedAt, Instant sentAt, String fromAddress, String fromName,
            String to, String cc, String subject, String body, long sizeBytes, List<Message.AttachmentNote> attachments, String state,
            String reason, TicketReference ticket, UUID contactId, UUID customerId, Instant processedAt, UUID processedBy,
            String discardReason, long version) {
        static MessageBody of(Message m, TicketReference ticket, boolean withBody) {
            return new MessageBody(m.id(), m.mailboxId(), m.projectId(), m.receivedAt(), m.sentAt(), m.fromAddress(), m.fromName(), m.to(),
                m.cc(), m.subject(), withBody ? m.body() : null, m.sizeBytes(), m.attachments(), m.state(), m.reason(), ticket, m.contactId(),
                m.customerId(), m.processedAt(), m.processedBy(), m.discardReason(), m.version());
        }
    }

    record SummaryBody(long review, long automatic) {
    }

    record CandidateBody(UUID contactId, String contactName, List<String> addresses, UUID customerId, String customerName, boolean sender) {
    }

    record TicketRequest(UUID contactId, String type) {
    }

    record CreatedBody(UUID ticketId, UUID projectId, long number, List<Message.AttachmentNote> attachments) {
    }

    record DiscardRequest(String reason) {
    }

    /** Newest first; the next page starts below the {@code receivedAt} of the last mail. */
    @GetMapping
    List<MessageBody> list(@RequestParam(required = false) String state, @RequestParam(required = false) UUID project,
            @RequestParam(required = false) Instant before, @RequestParam(required = false) Integer limit) {
        AccessContext actor = requests.actor();
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiProblemException.of(400, "helpdesk.limit_invalid");
        }
        return Requests.retrying(() -> review.list(actor, state, project, before, size)).stream()
            .map(message -> MessageBody.of(message, ticket(message), false)).toList();
    }

    @GetMapping("/summary")
    SummaryBody summary() {
        AccessContext actor = requests.actor();
        ReviewService.Summary summary = Requests.retrying(() -> review.summary(actor));
        return new SummaryBody(summary.review(), summary.automatic());
    }

    @GetMapping("/{id}")
    ResponseEntity<MessageBody> get(@PathVariable UUID id) {
        AccessContext actor = requests.actor();
        Message message = Requests.retrying(() -> review.get(actor, id));
        return ResponseEntity.ok().eTag(Requests.etag(message.version())).body(MessageBody.of(message, ticket(message), true));
    }

    @GetMapping("/{id}/candidates")
    List<CandidateBody> candidates(@PathVariable UUID id) {
        AccessContext actor = requests.actor();
        return Requests.retrying(() -> review.candidates(actor, id)).stream()
            .map(c -> new CandidateBody(c.contact().id(), c.contact().name(), c.contact().addresses(), c.customer().id(), c.customer().name(), c.sender()))
            .toList();
    }

    @PostMapping("/{id}/ticket")
    CreatedBody createTicket(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) TicketRequest body) {
        AccessContext actor = requests.actor();
        long version = Requests.expectedVersion(ifMatch);
        TicketRequest request = body == null ? new TicketRequest(null, null) : body;
        var opened = Requests.retrying(() -> review.createTicket(actor, id, version, request.contactId(), request.type()));
        var location = tickets.locate(opened.ticketId()).orElseThrow();
        return new CreatedBody(opened.ticketId(), location.projectId(), opened.number(), opened.attachments());
    }

    @PostMapping("/{id}/discard")
    ResponseEntity<MessageBody> discard(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) DiscardRequest body) {
        AccessContext actor = requests.actor();
        long version = Requests.expectedVersion(ifMatch);
        Message message = Requests.retrying(() -> review.discard(actor, id, version, body == null ? null : body.reason()));
        return ResponseEntity.ok().eTag(Requests.etag(message.version())).body(MessageBody.of(message, null, true));
    }

    private TicketReference ticket(Message message) {
        if (message.ticketId() == null) {
            return null;
        }
        return tickets.locate(message.ticketId()).map(l -> new TicketReference(l.projectId(), l.number())).orElse(null);
    }
}
