package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.internal.Contact;
import taskmanager.helpdesk.internal.Customer;
import taskmanager.helpdesk.internal.Message;
import taskmanager.helpdesk.internal.Reply;
import taskmanager.helpdesk.internal.ReplyService;
import taskmanager.helpdesk.internal.TicketCustomerService;
import taskmanager.permissions.AccessContext;

/**
 * Customer, contact and mails of a ticket (TASK-057): the ticket fields {@code customer}, {@code contact} and {@code mail}.
 * Each part is in the answer only with its read rule; {@code readable} and {@code writable} name the fields, so an
 * interface offers exactly what the server allows. Replies to the contact (TASK-058) need the project action
 * {@code helpdesk.reply} and the read rules of {@code mail} and {@code contact}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tickets/{number}/helpdesk")
class TicketCustomerController {
    private final TicketCustomerService service;
    private final ReplyService replies;
    private final Requests requests;

    TicketCustomerController(TicketCustomerService service, ReplyService replies, Requests requests) {
        this.service = service;
        this.replies = replies;
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

    /** A reply as the ticket shows it (TASK-058); {@code sending} is internal and shown as {@code pending}. */
    record ReplyBody(UUID id, UUID mailboxId, String fromAddress, String toAddress, String subject, String body, String state,
            String errorCode, int sends, Instant createdAt, UUID createdBy, Instant attemptedAt, Instant sentAt, long version) {
        static ReplyBody of(Reply r) {
            return new ReplyBody(r.id(), r.mailboxId(), r.fromAddress(), r.toAddress(), r.subject(), r.body(),
                r.state().equals("sending") ? "pending" : r.state(), r.errorCode(), r.sends(), r.createdAt(), r.createdBy(), r.attemptedAt(),
                r.sentAt(), r.version());
        }
    }

    record ViewBody(List<String> readable, List<String> writable, NamedBody customer, ContactBody contact, List<MailBody> mails,
            List<ReplyBody> replies, boolean replyAllowed) {
        static ViewBody of(TicketCustomerService.View view) {
            boolean mails = view.readable().contains("mail");
            return new ViewBody(view.readable().stream().sorted().toList(), view.writable().stream().sorted().toList(),
                view.customer() == null ? null : new NamedBody(view.customer().id(), view.customer().name()),
                view.contact() == null ? null : ContactBody.of(view.contact()),
                mails ? view.mails().stream().map(MailBody::of).toList() : null,
                mails ? view.replies().stream().map(ReplyBody::of).toList() : null, view.replyAllowed());
        }
    }

    record SenderBody(UUID id, String name, String address) {
    }

    record ComposeBody(List<SenderBody> mailboxes, UUID contactId, String contactName, List<String> recipients, String subject,
            UUID suggestedMailbox) {
    }

    record ReplyRequest(UUID mailboxId, String to, String subject, String body) {
    }

    record AgainRequest(Boolean duplicateAccepted) {
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

    /** What a reply to the contact can use: the sending mailboxes, the addresses of the contact and a subject (TASK-058). */
    @GetMapping("/reply")
    ComposeBody compose(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        ReplyService.Compose compose = Requests.retrying(() -> replies.compose(actor, projectId, number));
        return new ComposeBody(compose.mailboxes().stream().map(m -> new SenderBody(m.id(), m.name(), m.address())).toList(),
            compose.contactId(), compose.contactName(), compose.recipients(), compose.subject(), compose.suggestedMailbox());
    }

    /** Confirms a reply; it is sent in the background (TASK-058). */
    @PostMapping("/replies")
    ResponseEntity<ReplyBody> reply(@PathVariable UUID projectId, @PathVariable long number, @RequestBody(required = false) ReplyRequest body) {
        AccessContext actor = requests.actor();
        ReplyRequest request = body == null ? new ReplyRequest(null, null, null, null) : body;
        Reply reply = Requests.retrying(() -> replies.create(actor, projectId, number, request.mailboxId(), request.to(), request.subject(),
            request.body()));
        return ResponseEntity.status(201).eTag(Requests.etag(reply.version())).body(ReplyBody.of(reply));
    }

    /** Sends a failed or uncertain reply again; an uncertain one only with {@code duplicateAccepted}. */
    @PostMapping("/replies/{replyId}/send")
    ResponseEntity<ReplyBody> sendAgain(@PathVariable UUID projectId, @PathVariable long number, @PathVariable UUID replyId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, @RequestBody(required = false) AgainRequest body) {
        AccessContext actor = requests.actor();
        long version = Requests.expectedVersion(ifMatch);
        boolean duplicate = body != null && Boolean.TRUE.equals(body.duplicateAccepted());
        Reply reply = Requests.retrying(() -> replies.sendAgain(actor, projectId, number, replyId, version, duplicate));
        return ResponseEntity.ok().eTag(Requests.etag(reply.version())).body(ReplyBody.of(reply));
    }

    /** Records an uncertain reply as sent, once a person knows that it arrived. */
    @PostMapping("/replies/{replyId}/settle")
    ResponseEntity<ReplyBody> settle(@PathVariable UUID projectId, @PathVariable long number, @PathVariable UUID replyId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        AccessContext actor = requests.actor();
        long version = Requests.expectedVersion(ifMatch);
        Reply reply = Requests.retrying(() -> replies.settle(actor, projectId, number, replyId, version));
        return ResponseEntity.ok().eTag(Requests.etag(reply.version())).body(ReplyBody.of(reply));
    }
}
