package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import tools.jackson.databind.json.JsonMapper;

/**
 * The review queue (TASK-057, DEC-031): mails the intake did not turn into a ticket by itself, with their reason. Whoever
 * holds the project action {@code helpdesk.review} in the project of the mailbox sees them and decides: a ticket with or
 * without a contact of a customer released for the project, or discarding with a reason. A mail is processed once; a
 * second attempt with the old version answers 412, one after the processing 409.
 *
 * <p>Locks, in this order: rights, identity, project row ({@code FOR SHARE}), the mail, then what the ticket needs.
 */
@Service
public class ReviewService {
    public static final Set<String> STATES = Set.of("review", "automatic", "ticket", "discarded");
    private static final Logger LOG = LoggerFactory.getLogger(ReviewService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int DISCARD_MAX = 500;

    private final HelpdeskRepository repository;
    private final HelpdeskAccess access;
    private final ProjectAccess projects;
    private final TicketOpener opener;
    private final Clock clock;

    ReviewService(HelpdeskRepository repository, HelpdeskAccess access, ProjectAccess projects, TicketOpener opener, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.projects = projects;
        this.opener = opener;
        this.clock = clock;
    }

    /** A contact the person may assign, and whether its address is the sender of the mail. */
    public record Candidate(Contact contact, Customer customer, boolean sender) {
    }

    /** The mails waiting in the queue, by project, for the overview. */
    public record Summary(long review, long automatic) {
    }

    @Transactional
    public List<Message> list(AccessContext actor, String state, UUID projectId, Instant before, int limit) {
        if (state != null && !STATES.contains(state)) {
            throw ApiProblemException.of(400, "helpdesk.state_invalid");
        }
        List<UUID> reviewable = reviewable(actor, projectId);
        return repository.queue(actor.companyId(), reviewable, List.of(state == null ? "review" : state), before, limit);
    }

    @Transactional
    public Summary summary(AccessContext actor) {
        List<UUID> reviewable = reviewable(actor, null);
        return new Summary(repository.count(actor.companyId(), reviewable, "review"), repository.count(actor.companyId(), reviewable, "automatic"));
    }

    @Transactional
    public Message get(AccessContext actor, UUID id) {
        return readable(actor, id);
    }

    /** The contacts of the customers released for the project of the mail; the sender first if it is one of them. */
    @Transactional
    public List<Candidate> candidates(AccessContext actor, UUID id) {
        Message message = readable(actor, id);
        List<Candidate> result = new ArrayList<>();
        for (Customer customer : repository.customers(actor.companyId())) {
            if (!customer.projects().contains(message.projectId())) {
                continue;
            }
            for (Contact contact : repository.contacts(actor.companyId(), customer.id())) {
                boolean sender = message.fromAddress() != null && contact.addresses().contains(message.fromAddress());
                result.add(new Candidate(contact, customer, sender));
            }
        }
        result.sort((a, b) -> Boolean.compare(b.sender(), a.sender()));
        return result;
    }

    /**
     * Creates the ticket of the mail. {@code contactId} {@code null} creates it without customer; {@code type} {@code null}
     * takes the ticket type of the mailbox.
     */
    @Transactional
    public TicketOpener.Opened createTicket(AccessContext actor, UUID id, long expectedVersion, UUID contactId, String type) {
        Message message = lockForChange(actor, id, expectedVersion);
        Contact contact = null;
        Customer customer = null;
        if (contactId != null) {
            contact = repository.contact(actor.companyId(), contactId).orElse(null);
            customer = contact == null ? null : repository.customer(actor.companyId(), contact.customerId()).orElse(null);
            if (customer == null || !customer.projects().contains(message.projectId())) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("contactId.not_released"));
            }
        }
        String ticketType = type;
        if (ticketType == null) {
            ticketType = repository.mailbox(actor.companyId(), message.mailboxId()).map(Mailbox::ticketType).orElse(null);
        }
        ParsedMail parsed = null;
        Optional<byte[]> raw = repository.raw(message.id());
        if (raw.isPresent()) {
            try {
                parsed = ParsedMail.parse(raw.get());
            } catch (Exception unreadable) {
                LOG.info("Mail {} could not be read again: {}", message.id(), unreadable.toString());
            }
        }
        Instant now = clock.instant();
        TicketOpener.Opened opened = opener.open(message, parsed, ticketType, customer, contact, actor.userId(), now)
            .orElseThrow(() -> ApiProblemException.withViolations(400, "request.invalid", List.of("type.unknown")));
        repository.audit(actor.companyId(), "message", message.id(), "message.ticket_created",
            JSON.writeValueAsString(new Outcome(opened.ticketId(), contactId, null)), actor.userId(), now);
        return opened;
    }

    @Transactional
    public Message discard(AccessContext actor, UUID id, long expectedVersion, String rawReason) {
        String reason = ParsedMail.clean(rawReason);
        if (reason == null || reason.codePointCount(0, reason.length()) > DISCARD_MAX) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("reason.invalid"));
        }
        Message message = lockForChange(actor, id, expectedVersion);
        Instant now = clock.instant();
        repository.processed(message.id(), "discarded", null, null, null, message.attachments(), reason, now, actor.userId(), message.version() + 1);
        repository.audit(actor.companyId(), "message", message.id(), "message.discarded", JSON.writeValueAsString(new Outcome(null, null, reason)),
            actor.userId(), now);
        return repository.message(actor.companyId(), id).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ access

    /** The projects whose mails the caller sees, all of them or the one asked for. */
    private List<UUID> reviewable(AccessContext actor, UUID projectId) {
        List<UUID> candidates = projectId == null ? projects.memberships(actor.companyId(), actor.userId()) : List.of(projectId);
        return candidates.stream().sorted().filter(project -> access.mayReview(actor, project)).toList();
    }

    /** The mail, if the caller may see the mails of its project; otherwise it does not exist for the caller. */
    private Message readable(AccessContext actor, UUID id) {
        Message message = repository.message(actor.companyId(), id).orElseThrow(ReviewService::notFound);
        if (!access.mayReview(actor, message.projectId())) {
            throw notFound();
        }
        return message;
    }

    private Message lockForChange(AccessContext actor, UUID id, long expectedVersion) {
        Message unlocked = repository.message(actor.companyId(), id).orElseThrow(ReviewService::notFound);
        access.requireReview(actor, unlocked.projectId());
        Message message = repository.lockMessage(actor.companyId(), id).orElseThrow(ReviewService::notFound);
        if (!message.state().equals("review") && !message.state().equals("automatic")) {
            throw ApiProblemException.of(409, "helpdesk.message_processed");
        }
        if (message.version() != expectedVersion) {
            throw ApiProblemException.of(412, "helpdesk.version_conflict");
        }
        return message;
    }

    private record Outcome(UUID ticketId, UUID contactId, String reason) {
    }

    private static ApiProblemException notFound() {
        return ApiProblemException.of(404, "helpdesk.message_not_found");
    }
}
