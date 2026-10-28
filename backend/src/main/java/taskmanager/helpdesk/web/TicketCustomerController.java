package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.internal.Contact;
import taskmanager.helpdesk.internal.Customer;
import taskmanager.helpdesk.internal.Message;
import taskmanager.helpdesk.internal.TicketCustomerService;
import taskmanager.permissions.AccessContext;

/**
 * Customer, contact and mails of a ticket (TASK-057): the ticket fields {@code customer}, {@code contact} and {@code mail}.
 * Each part is in the answer only with its read rule; {@code readable} and {@code writable} name the fields, so an
 * interface offers exactly what the server allows.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tickets/{number}/helpdesk")
class TicketCustomerController {
    private final TicketCustomerService service;
    private final Requests requests;

    TicketCustomerController(TicketCustomerService service, Requests requests) {
        this.service = service;
        this.requests = requests;
    }

    record NamedBody(UUID id, String name) {
    }

    record ContactBody(UUID id, String name, List<String> addresses) {
        static ContactBody of(Contact c) {
            return new ContactBody(c.id(), c.name(), c.addresses());
        }
    }

    record MailBody(UUID id, Instant receivedAt, Instant sentAt, String fromAddress, String fromName, String to, String cc, String subject,
            String body, List<Message.AttachmentNote> attachments) {
        static MailBody of(Message m) {
            return new MailBody(m.id(), m.receivedAt(), m.sentAt(), m.fromAddress(), m.fromName(), m.to(), m.cc(), m.subject(), m.body(),
                m.attachments());
        }
    }

    record ViewBody(List<String> readable, List<String> writable, NamedBody customer, ContactBody contact, List<MailBody> mails) {
        static ViewBody of(TicketCustomerService.View view) {
            return new ViewBody(view.readable().stream().sorted().toList(), view.writable().stream().sorted().toList(),
                view.customer() == null ? null : new NamedBody(view.customer().id(), view.customer().name()),
                view.contact() == null ? null : ContactBody.of(view.contact()),
                view.readable().contains("mail") ? view.mails().stream().map(MailBody::of).toList() : null);
        }
    }

    record OptionBody(UUID id, String name, List<ContactBody> contacts) {
        static OptionBody of(TicketCustomerService.Option option) {
            Customer c = option.customer();
            return new OptionBody(c.id(), c.name(), option.contacts().stream().map(ContactBody::of).toList());
        }
    }

    record CustomerRequest(UUID customerId, UUID contactId) {
    }

    @GetMapping
    ViewBody view(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        return ViewBody.of(Requests.retrying(() -> service.view(actor, projectId, number)));
    }

    @GetMapping("/options")
    List<OptionBody> options(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        return Requests.retrying(() -> service.options(actor, projectId, number)).stream().map(OptionBody::of).toList();
    }

    @PutMapping("/customer")
    ViewBody set(@PathVariable UUID projectId, @PathVariable long number, @RequestBody(required = false) CustomerRequest body) {
        AccessContext actor = requests.actor();
        CustomerRequest request = body == null ? new CustomerRequest(null, null) : body;
        return ViewBody.of(Requests.retrying(() -> service.set(actor, projectId, number, request.customerId(), request.contactId())));
    }
}
