package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.jobs.Jobs;
import taskmanager.jobs.NewJob;
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
 * Replies to the contact of a ticket (TASK-058, DEC-091). Sending needs the project action {@code helpdesk.reply} and
 * {@code ticket.read} with the read rules of the fields {@code mail} and {@code contact}; whoever may not read the mails or
 * the contact of a ticket cannot write to the customer either. The recipient is an address of the contact of the ticket, the
 * sender a mailbox of the project with SMTP settings and an address.
 *
 * <p>Confirming a reply writes it with its send job in one transaction, together with an entry of the field {@code mail} in
 * the history; the job sends it later (outbox). The rights count at the confirmation; the job checks again that the person
 * who confirmed the send last is an active member of the project and that mailbox and recipient still fit, but not the rights in the groups, because a
 * job has no session to check them with. A failed reply is sent again only by a person, an uncertain one only when the person
 * confirms the risk of a second delivery.
 *
 * <p>Locks, in this order: rights, identity, project row ({@code FOR SHARE}), ticket row ({@code FOR SHARE}), the reply.
 */
@Service
public class ReplyService {
    static final String REPLY = "helpdesk.reply";
    static final String JOB_KIND = "helpdesk.reply";
    static final int MAX_ATTEMPTS = 5;
    private static final String READ = "ticket.read";
    private static final Set<String> FIELDS = Set.of(TicketAccess.MAIL_FIELD, TicketAccess.CONTACT_FIELD);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int SUBJECT_MAX = 998;
    private static final int BODY_MAX = 100_000;
    private static final int REFERENCES_MAX = 20;
    private static final int OPEN_LIMIT = 100;

    private final HelpdeskRepository repository;
    private final TicketAccess tickets;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final Jobs jobs;
    private final Clock clock;

    ReplyService(HelpdeskRepository repository, TicketAccess tickets, ProjectAccess projects, Permissions permissions, Jobs jobs, Clock clock) {
        this.repository = repository;
        this.tickets = tickets;
        this.projects = projects;
        this.permissions = permissions;
        this.jobs = jobs;
        this.clock = clock;
    }

    /** A mailbox that can send: enabled, in the project, with SMTP server and address. */
    public record Sender(UUID id, String name, String address) {
    }

    /** What the form of a reply offers: the senders, the addresses of the contact and the subject to start with. */
    public record Compose(List<Sender> mailboxes, UUID contactId, String contactName, List<String> recipients, String subject,
            UUID suggestedMailbox) {
    }

    /** An open reply with where it belongs, for the list of open sends. */
    public record Open(Reply reply, long number) {
    }

    /** Whether the caller may send replies in the project; a snapshot for the interface, never an authorization. */
    boolean mayReply(AccessContext actor, UUID projectId) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = permissions.inspect(actor, resource, READ, FIELDS);
        return read.allowed() && read.readableFields().containsAll(FIELDS)
            && permissions.inspect(actor, resource, REPLY, Set.of()).allowed();
    }

    @Transactional
    public Compose compose(AccessContext actor, UUID projectId, long number) {
        requireReply(actor, projectId);
        UUID ticket = ticket(actor, projectId, number);
        Contact contact = contact(actor, ticket);
        List<Sender> senders = senders(actor.companyId(), projectId);
        List<Message> mails = repository.messagesOfTicket(ticket);
        String subject = mails.isEmpty() ? null : mails.get(mails.size() - 1).subject();
        UUID suggested = mails.isEmpty() ? null : mails.get(mails.size() - 1).mailboxId();
        if (suggested != null && senders.stream().noneMatch(s -> s.id().equals(mails.get(mails.size() - 1).mailboxId()))) {
            suggested = null;
        }
        if (suggested == null && !senders.isEmpty()) {
            suggested = senders.get(0).id();
        }
        return new Compose(senders, contact.id(), contact.name(), contact.addresses(), answerSubject(subject), suggested);
    }

    /** Confirms a reply: it is written with its send job and an entry in the history of the ticket. */
    @Transactional
    public Reply create(AccessContext actor, UUID projectId, long number, UUID mailboxId, String rawTo, String rawSubject, String rawBody) {
        requireReply(actor, projectId);
        UUID ticket = ticket(actor, projectId, number);
        Contact contact = contact(actor, ticket);
        List<String> violations = new ArrayList<>();
        Mailbox mailbox = mailboxId == null ? null : repository.mailbox(actor.companyId(), mailboxId).filter(m -> sends(m, projectId)).orElse(null);
        if (mailbox == null) {
            violations.add("mailboxId.unavailable");
        }
        String to = rawTo == null || rawTo.isBlank() ? contact.addresses().get(0) : DirectoryService.normalizedAddress(rawTo);
        if (!recipient(contact, to) || mailbox != null && to.equals(mailbox.address())) {
            violations.add("to.not_of_contact");
        }
        String subject = ParsedMail.clean(rawSubject);
        if (subject == null || subject.contains("\n") || subject.codePointCount(0, subject.length()) > SUBJECT_MAX) {
            violations.add("subject.invalid");
        }
        String body = ParsedMail.clean(rawBody);
        if (body == null || body.length() > BODY_MAX) {
            violations.add("body.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        tickets.lockForHelpdeskChange(ticket);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        List<String> thread = thread(ticket);
        UUID job = enqueue(actor.companyId(), id, 1, actor.userId());
        Reply reply = new Reply(id, actor.companyId(), projectId, ticket, mailbox.id(), contact.id(), mailbox.address(), to, subject, body,
            "<" + id + "@" + ParsedMail.domain(mailbox.address()) + ">", thread.isEmpty() ? null : thread.get(thread.size() - 1),
            thread.isEmpty() ? null : String.join(" ", thread), "pending", null, job, 1, null, null, now, actor.userId(), now, actor.userId(), 1);
        repository.insertReply(reply);
        tickets.recordHelpdeskChange(ticket, now, actor.userId(), TicketAccess.MAIL_FIELD, null,
            JSON.writeValueAsString(new ReplyReference(id.toString(), to, subject)));
        repository.audit(actor.companyId(), "reply", id, "reply.confirmed", JSON.writeValueAsString(new Detail(ticket, mailbox.id(), to)),
            actor.userId(), now);
        return reply;
    }

    /**
     * Sends a failed or uncertain reply again with a new job. An uncertain one may already have reached the customer; it is
     * sent again only when {@code duplicateAccepted}.
     */
    @Transactional
    public Reply sendAgain(AccessContext actor, UUID projectId, long number, UUID replyId, long expectedVersion, boolean duplicateAccepted) {
        Reply reply = lockForChange(actor, projectId, number, replyId, expectedVersion);
        if (!reply.state().equals("failed") && !reply.state().equals("uncertain")) {
            throw ApiProblemException.of(409, "helpdesk.reply_not_failed");
        }
        if (reply.state().equals("uncertain") && !duplicateAccepted) {
            throw ApiProblemException.of(409, "helpdesk.reply_uncertain");
        }
        Mailbox mailbox = repository.mailbox(actor.companyId(), reply.mailboxId()).filter(m -> sends(m, projectId)).orElse(null);
        Contact contact = contact(actor, reply.ticketId());
        if (mailbox == null || !mailbox.address().equals(reply.fromAddress())) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("mailboxId.unavailable"));
        }
        if (!contact.id().equals(reply.contactId()) || !recipient(contact, reply.toAddress())) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("to.not_of_contact"));
        }
        Instant now = clock.instant();
        UUID job = enqueue(actor.companyId(), reply.id(), reply.sends() + 1, actor.userId());
        repository.replyAgain(reply.id(), job, now, actor.userId());
        repository.audit(actor.companyId(), "reply", reply.id(), "reply.sent_again",
            JSON.writeValueAsString(new Again(reply.state(), reply.errorCode())), actor.userId(), now);
        return repository.reply(actor.companyId(), reply.id()).orElseThrow();
    }

    /** A person who learned that an uncertain reply arrived records it as sent; nothing is sent. */
    @Transactional
    public Reply settle(AccessContext actor, UUID projectId, long number, UUID replyId, long expectedVersion) {
        Reply reply = lockForChange(actor, projectId, number, replyId, expectedVersion);
        if (!reply.state().equals("uncertain")) {
            throw ApiProblemException.of(409, "helpdesk.reply_not_uncertain");
        }
        Instant now = clock.instant();
        repository.replyState(reply.id(), "sent", null, null, reply.attemptedAt() == null ? now : reply.attemptedAt(), now, actor.userId());
        repository.audit(actor.companyId(), "reply", reply.id(), "reply.settled", null, actor.userId(), now);
        return repository.reply(actor.companyId(), reply.id()).orElseThrow();
    }

    /** The replies not known to be sent in the projects where the caller may send replies, newest first. */
    @Transactional
    public List<Open> open(AccessContext actor) {
        List<UUID> allowed = projects.memberships(actor.companyId(), actor.userId()).stream().sorted()
            .filter(project -> mayReply(actor, project)).toList();
        List<Open> result = new ArrayList<>();
        for (Reply reply : repository.openReplies(actor.companyId(), allowed, OPEN_LIMIT)) {
            tickets.locate(reply.ticketId()).ifPresent(location -> result.add(new Open(reply, location.number())));
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------ rules

    /** The mailboxes of the project that can send, by name. */
    List<Sender> senders(UUID companyId, UUID projectId) {
        return repository.mailboxes(companyId).stream().filter(m -> sends(m, projectId))
            .map(m -> new Sender(m.id(), m.name(), m.address())).toList();
    }

    /** Whether the mailbox sends for the project: enabled, with SMTP server and a plain address (TASK-062, X-F3). */
    static boolean sends(Mailbox mailbox, UUID projectId) {
        return mailbox.enabled() && mailbox.projectId().equals(projectId) && mailbox.smtpHost() != null
            && mailbox.address() != null && mailbox.address().equals(DirectoryService.normalizedAddress(mailbox.address()));
    }

    /** Whether the address is one of the contact and plain enough to send to; addresses stored before TASK-062 may not be. */
    static boolean recipient(Contact contact, String address) {
        return address != null && contact.addresses().contains(address) && address.equals(DirectoryService.normalizedAddress(address));
    }

    /** "Re: " before the subject of the last mail, unless it starts with it already. */
    static String answerSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        String line = subject.replace('\n', ' ').strip();
        return line.regionMatches(true, 0, "re:", 0, 3) || line.regionMatches(true, 0, "aw:", 0, 3) ? line : ParsedMail.cut("Re: " + line, SUBJECT_MAX);
    }

    /** The Message-IDs of the mails and replies of the ticket, oldest first, at most the last twenty. */
    private List<String> thread(UUID ticket) {
        record Entry(Instant at, String id) {
        }
        List<Entry> entries = new ArrayList<>();
        repository.messagesOfTicket(ticket).forEach(m -> {
            // Only identifiers of printable ASCII go into a header of the reply (TASK-062, C-F3).
            if (m.messageId() != null && SmtpMailSender.MESSAGE_ID.matcher(m.messageId()).matches()) {
                entries.add(new Entry(m.sentAt() == null ? m.receivedAt() : m.sentAt(), m.messageId()));
            }
        });
        repository.repliesOfTicket(ticket).forEach(r -> entries.add(new Entry(r.createdAt(), r.messageId())));
        entries.sort(Comparator.comparing(Entry::at));
        List<String> ids = entries.stream().map(Entry::id).distinct().toList();
        return ids.subList(Math.max(0, ids.size() - REFERENCES_MAX), ids.size());
    }

    /** The job of one send; it carries the person who confirmed it, whose membership the job checks (TASK-062, C-F2). */
    private UUID enqueue(UUID companyId, UUID replyId, int send, UUID confirmedBy) {
        return jobs.enqueue(NewJob.of(companyId, JOB_KIND).withPayload(Map.of("reply", replyId.toString(), "by", confirmedBy.toString()))
            .withDedupKey("reply:" + replyId + ":" + send).withMaxAttempts(MAX_ATTEMPTS));
    }

    private Contact contact(AccessContext actor, UUID ticket) {
        Optional<Contact> contact = repository.ticketCustomer(ticket).map(HelpdeskRepository.TicketCustomer::contactId)
            .flatMap(id -> repository.contact(actor.companyId(), id));
        if (contact.isEmpty() || contact.get().addresses().isEmpty()) {
            throw ApiProblemException.of(409, "helpdesk.reply_no_contact");
        }
        return contact.get();
    }

    private Reply lockForChange(AccessContext actor, UUID projectId, long number, UUID replyId, long expectedVersion) {
        requireReply(actor, projectId);
        UUID ticket = ticket(actor, projectId, number);
        Reply reply = repository.lockReply(replyId).filter(r -> r.companyId().equals(actor.companyId()) && r.ticketId().equals(ticket))
            .orElseThrow(() -> ApiProblemException.of(404, "helpdesk.reply_not_found"));
        if (reply.version() != expectedVersion) {
            throw ApiProblemException.of(412, "helpdesk.version_conflict");
        }
        // A reply whose job ended without an outcome is settled first, so that it can be sent again or recorded (TASK-062).
        return settleOrphan(reply, clock.instant());
    }

    /**
     * Settles a reply that waits or is being sent although its job will not run it again (TASK-062, C-F1 and X-F2): the last
     * attempt ended without a recorded outcome, failed otherwise, or the administration cancelled the job. One that was handed
     * to the mail server may have arrived and becomes uncertain ({@code helpdesk.send_interrupted}); one that was not becomes
     * failed ({@code helpdesk.job_ended}). Either way a person can send it again or record it. The reply must be locked; a
     * reply whose job still waits or runs is left alone, and once ended a job only runs again through a retry of the
     * administration, which then finds the reply no longer pending and sends nothing.
     */
    Reply settleOrphan(Reply reply, Instant now) {
        boolean sending = reply.state().equals("sending");
        if (!sending && !reply.state().equals("pending") || jobs.active(reply.jobId())) {
            return reply;
        }
        repository.replyState(reply.id(), sending ? "uncertain" : "failed", sending ? "helpdesk.send_interrupted" : "helpdesk.job_ended",
            null, null, now, reply.updatedBy());
        return repository.reply(reply.companyId(), reply.id()).orElseThrow();
    }

    /**
     * Without {@code ticket.read} the project does not exist for the caller (404); without {@code helpdesk.reply} or the read
     * rules of {@code mail} and {@code contact} the reply is refused (403).
     */
    private void requireReply(AccessContext actor, UUID projectId) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        if (!permissions.inspect(actor, resource, READ, Set.of()).allowed()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        try {
            permissions.require(actor, resource, READ, FIELDS, Set.of());
            permissions.require(actor, resource, REPLY, Set.of(), Set.of());
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
    }

    private Supplier<ResourceContext> resource(AccessContext actor, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
    }

    private UUID ticket(AccessContext actor, UUID projectId, long number) {
        return tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
    }

    /** How the history keeps a reply: its identifier, recipient and subject. */
    private record ReplyReference(String reply, String to, String subject) {
    }

    private record Detail(UUID ticketId, UUID mailboxId, String to) {
    }

    private record Again(String from, String errorCode) {
    }
}
