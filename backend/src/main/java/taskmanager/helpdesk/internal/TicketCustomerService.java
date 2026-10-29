package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
import taskmanager.tickets.TicketAccess;

/**
 * Customer, contact and mails of a ticket (TASK-057). They are the ticket fields {@code customer}, {@code contact} and
 * {@code mail} of the resource type {@code ticket}: reading needs {@code ticket.read} with the read rule of each field,
 * which is shown only with it; changing customer and contact needs {@code ticket.edit} with the write rules of the fields
 * that change. The mails arrive only through the intake. Without access to the ticket the project does not exist for the
 * caller (404), as for the other fields.
 */
@Service
public class TicketCustomerService {
    private static final String READ = "ticket.read";
    private static final String EDIT = "ticket.edit";
    private static final Set<String> FIELDS = Set.of(TicketAccess.CUSTOMER_FIELD, TicketAccess.CONTACT_FIELD, TicketAccess.MAIL_FIELD);

    private final HelpdeskRepository repository;
    private final TicketAccess tickets;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final ReplyService replies;
    private final Clock clock;

    TicketCustomerService(HelpdeskRepository repository, TicketAccess tickets, ProjectAccess projects, Permissions permissions,
            ReplyService replies, Clock clock) {
        this.repository = repository;
        this.replies = replies;
        this.tickets = tickets;
        this.projects = projects;
        this.permissions = permissions;
        this.clock = clock;
    }

    /**
     * What the caller sees: each part only with its read rule; {@code writable} names the fields the caller may change. The
     * replies of the ticket come with the mails (TASK-058); {@code replyAllowed} says whether the caller may send one.
     */
    public record View(Set<String> readable, Set<String> writable, Customer customer, Contact contact, List<Message> mails,
            List<Reply> replies, boolean replyAllowed) {
    }

    /** A customer released for the project with its contacts, for the choice in the ticket. */
    public record Option(Customer customer, List<Contact> contacts) {
    }

    @Transactional
    public View view(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = permissions.inspect(actor, resource, READ, FIELDS);
        if (!read.allowed()) {
            throw projectNotFound();
        }
        UUID ticket = ticket(actor, projectId, number);
        Set<String> readable = read.readableFields();
        Decision edit = permissions.inspect(actor, resource, EDIT, FIELDS);
        Set<String> writable = new HashSet<>(edit.allowed() ? edit.writableFields() : Set.of());
        writable.remove(TicketAccess.MAIL_FIELD);
        var link = repository.ticketCustomer(ticket);
        Customer customer = readable.contains(TicketAccess.CUSTOMER_FIELD)
            ? link.map(HelpdeskRepository.TicketCustomer::customerId).flatMap(id -> repository.customer(actor.companyId(), id)).orElse(null) : null;
        Contact contact = readable.contains(TicketAccess.CONTACT_FIELD)
            ? link.map(HelpdeskRepository.TicketCustomer::contactId).flatMap(id -> repository.contact(actor.companyId(), id)).orElse(null) : null;
        List<Message> mails = readable.contains(TicketAccess.MAIL_FIELD) ? repository.messagesOfTicket(ticket) : List.of();
        List<Reply> replies = readable.contains(TicketAccess.MAIL_FIELD) ? repository.repliesOfTicket(ticket) : List.of();
        return new View(Set.copyOf(readable), Set.copyOf(writable), customer, contact, mails, replies,
            contact != null && this.replies.mayReply(actor, projectId));
    }

    /**
     * The customers released for the project and their contacts. The customers need the read and write rules for the field
     * {@code customer}; the contacts are listed only with the read and write rules for {@code contact}, otherwise each
     * customer comes without contacts (TASK-059, F4).
     */
    @Transactional
    public List<Option> options(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        requireEdit(actor, resource, Set.of(TicketAccess.CUSTOMER_FIELD));
        Decision read = permissions.inspect(actor, resource, READ, FIELDS);
        if (!read.allowed() || !read.readableFields().contains(TicketAccess.CUSTOMER_FIELD)) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        Decision edit = permissions.inspect(actor, resource, EDIT, FIELDS);
        boolean contacts = read.readableFields().contains(TicketAccess.CONTACT_FIELD)
            && edit.allowed() && edit.writableFields().contains(TicketAccess.CONTACT_FIELD);
        ticket(actor, projectId, number);
        List<Option> options = new ArrayList<>();
        for (Customer customer : repository.customers(actor.companyId())) {
            if (customer.projects().contains(projectId)) {
                options.add(new Option(customer, contacts ? repository.contacts(actor.companyId(), customer.id()) : List.of()));
            }
        }
        return options;
    }

    /**
     * Sets or clears customer and contact. The customer must be released for the project and the contact must belong to it;
     * a contact without customer is refused. Each field that changes needs its write rule and gets an entry in the history.
     */
    @Transactional
    public View set(AccessContext actor, UUID projectId, long number, UUID customerId, UUID contactId) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        boolean read = permissions.inspect(actor, resource, READ, Set.of()).allowed();
        boolean edit = permissions.inspect(actor, resource, EDIT, Set.of()).allowed();
        if (!read && !edit) {
            throw projectNotFound();
        }
        UUID ticket = ticket(actor, projectId, number);
        var current = repository.ticketCustomer(ticket).orElse(new HelpdeskRepository.TicketCustomer(null, null));
        Set<String> changing = new HashSet<>();
        if (!Objects.equals(current.customerId(), customerId)) {
            changing.add(TicketAccess.CUSTOMER_FIELD);
        }
        if (!Objects.equals(current.contactId(), contactId)) {
            changing.add(TicketAccess.CONTACT_FIELD);
        }
        requireEdit(actor, resource, changing.isEmpty() ? Set.of(TicketAccess.CUSTOMER_FIELD) : changing);
        Customer customer = null;
        Contact contact = null;
        List<String> violations = new ArrayList<>();
        if (customerId != null) {
            customer = repository.customer(actor.companyId(), customerId).orElse(null);
            if (customer == null || (!customer.projects().contains(projectId) && changing.contains(TicketAccess.CUSTOMER_FIELD))) {
                violations.add("customerId.not_released");
            }
        }
        if (contactId != null) {
            contact = repository.contact(actor.companyId(), contactId).orElse(null);
            if (contact == null || customerId == null || !contact.customerId().equals(customerId)) {
                violations.add("contactId.not_of_customer");
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        if (!changing.isEmpty()) {
            tickets.lockForHelpdeskChange(ticket);
            Instant now = clock.instant();
            repository.setTicketCustomer(actor.companyId(), projectId, ticket, customerId, contactId);
            if (changing.contains(TicketAccess.CUSTOMER_FIELD)) {
                tickets.recordHelpdeskChange(ticket, now, actor.userId(), TicketAccess.CUSTOMER_FIELD,
                    reference(actor.companyId(), current.customerId(), true), customer == null ? null : TicketOpener.reference(customer.id(), customer.name()));
            }
            if (changing.contains(TicketAccess.CONTACT_FIELD)) {
                tickets.recordHelpdeskChange(ticket, now, actor.userId(), TicketAccess.CONTACT_FIELD,
                    reference(actor.companyId(), current.contactId(), false), contact == null ? null : TicketOpener.reference(contact.id(), contact.name()));
            }
        }
        return view(actor, projectId, number);
    }

    private String reference(UUID companyId, UUID id, boolean customer) {
        if (id == null) {
            return null;
        }
        String name = customer ? repository.customer(companyId, id).map(Customer::name).orElse("")
            : repository.contact(companyId, id).map(Contact::name).orElse("");
        return TicketOpener.reference(id, name);
    }

    private void requireEdit(AccessContext actor, Supplier<ResourceContext> resource, Set<String> fields) {
        boolean read = permissions.inspect(actor, resource, READ, Set.of()).allowed();
        boolean edit = permissions.inspect(actor, resource, EDIT, Set.of()).allowed();
        if (!read && !edit) {
            throw projectNotFound();
        }
        try {
            permissions.require(actor, resource, EDIT, Set.of(), fields);
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

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }
}
