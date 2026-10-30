package taskmanager.helpdesk.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.files.AttachmentIntake;
import taskmanager.helpdesk.CustomerAnswered;
import taskmanager.helpdesk.TicketCustomerChanged;
import taskmanager.tickets.TicketAccess;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a mail into a ticket (TASK-057), for the intake and for the review queue alike: the ticket with subject and text,
 * the files of the mail as attachments, customer and contact, and the entries of customer, contact and mail in the history
 * of the ticket. Runs in the transaction of the caller, which has decided that the work is allowed.
 */
@Component
class TicketOpener {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TicketAccess tickets;
    private final AttachmentIntake attachments;
    private final HelpdeskRepository repository;
    private final ApplicationEventPublisher events;

    TicketOpener(TicketAccess tickets, AttachmentIntake attachments, HelpdeskRepository repository, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.attachments = attachments;
        this.repository = repository;
        this.events = events;
    }

    public record Opened(UUID ticketId, long number, List<Message.AttachmentNote> attachments) {
    }

    /**
     * Creates the ticket and records the outcome with the mail, which leaves the review queue. Empty when the project or the
     * ticket type no longer exists; then nothing is written.
     *
     * @param parsed the mail as read, or {@code null} for a mail without content (too large or unreadable)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Opened> open(Message message, ParsedMail parsed, String type, Customer customer, Contact contact, UUID actor, Instant now) {
        String body = parsed == null ? message.body() : parsed.body();
        var created = tickets.createForHelpdesk(message.companyId(), message.projectId(), type, message.subject(), body, actor);
        if (created.isEmpty()) {
            return Optional.empty();
        }
        UUID ticket = created.get().id();
        List<Message.AttachmentNote> notes = new ArrayList<>();
        if (parsed != null) {
            for (ParsedMail.File file : parsed.files()) {
                AttachmentIntake.Outcome outcome = attachments.attach(message.companyId(), message.projectId(), ticket, file.name(),
                    file.content(), actor);
                notes.add(new Message.AttachmentNote(outcome.name(), file.content().length, outcome.attachmentId(), outcome.refusal()));
            }
        } else {
            message.attachments().forEach(note -> notes.add(new Message.AttachmentNote(note.name(), note.size(), null, "attachment.not_fetched")));
        }
        if (customer != null) {
            repository.setTicketCustomer(message.companyId(), message.projectId(), ticket, customer.id(), contact == null ? null : contact.id());
            tickets.recordHelpdeskChange(ticket, now, actor, TicketAccess.CUSTOMER_FIELD, null, reference(customer.id(), customer.name()));
            if (contact != null) {
                tickets.recordHelpdeskChange(ticket, now, actor, TicketAccess.CONTACT_FIELD, null, reference(contact.id(), contact.name()));
            }
        }
        tickets.recordHelpdeskChange(ticket, now, actor, TicketAccess.MAIL_FIELD, null,
            JSON.writeValueAsString(new MailReference(message.id().toString(), message.fromAddress(), message.subject())));
        repository.processed(message.id(), "ticket", ticket, contact == null ? null : contact.id(), customer == null ? null : customer.id(),
            notes, null, now, actor, message.version() + 1);
        if (customer != null) {
            // The deadlines start at the first registration of the mail, also when it waited in the review queue (TASK-068, S-12).
            events.publishEvent(new TicketCustomerChanged(message.companyId(), message.projectId(), ticket, customer.id(), message.receivedAt(), now));
        }
        return Optional.of(new Opened(ticket, created.get().number(), notes));
    }

    /**
     * Adds the mail to an existing ticket (TASK-058): an answer of the customer to a reply of the ticket. The files become
     * attachments of the ticket, the mail an entry of the field {@code mail} in its history; customer and contact of the ticket
     * stay as they are. The mail leaves the review queue with the ticket, its contact and customer.
     *
     * @param parsed the mail as read, or {@code null} for a mail without content (too large or unreadable)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Opened append(Message message, ParsedMail parsed, UUID ticket, Customer customer, Contact contact, UUID actor, Instant now) {
        tickets.lockForHelpdeskChange(ticket);
        List<Message.AttachmentNote> notes = new ArrayList<>();
        if (parsed != null) {
            for (ParsedMail.File file : parsed.files()) {
                AttachmentIntake.Outcome outcome = attachments.attach(message.companyId(), message.projectId(), ticket, file.name(),
                    file.content(), actor);
                notes.add(new Message.AttachmentNote(outcome.name(), file.content().length, outcome.attachmentId(), outcome.refusal()));
            }
        } else {
            message.attachments().forEach(note -> notes.add(new Message.AttachmentNote(note.name(), note.size(), null, "attachment.not_fetched")));
        }
        tickets.recordHelpdeskChange(ticket, now, actor, TicketAccess.MAIL_FIELD, null,
            JSON.writeValueAsString(new MailReference(message.id().toString(), message.fromAddress(), message.subject())));
        repository.processed(message.id(), "ticket", ticket, contact == null ? null : contact.id(), customer == null ? null : customer.id(),
            notes, null, now, actor, message.version() + 1);
        events.publishEvent(new CustomerAnswered(message.companyId(), message.projectId(), ticket, message.receivedAt()));
        long number = tickets.locate(ticket).map(TicketAccess.Location::number).orElse(0L);
        return new Opened(ticket, number, notes);
    }

    /** How the history keeps a customer or contact: identifier and the name it had then. */
    static String reference(UUID id, String name) {
        return JSON.writeValueAsString(new Reference(id.toString(), name));
    }

    private record Reference(String id, String name) {
    }

    private record MailReference(String id, String from, String subject) {
    }
}
