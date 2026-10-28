package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.internal.Contact;
import taskmanager.helpdesk.internal.Customer;
import taskmanager.helpdesk.internal.DirectoryService;
import taskmanager.helpdesk.internal.HelpdeskProperties;
import taskmanager.helpdesk.internal.Mailbox;
import taskmanager.helpdesk.internal.MailboxService;
import taskmanager.platform.ApiProblemException;

/**
 * Mailboxes, customer organizations and contacts of the helpdesk (TASK-057). Every endpoint needs the company right
 * {@code helpdesk.manage}; the endpoint refuses a request that clearly lacks it, the service decides in its transaction.
 * A mailbox password is write only: answers say whether one is stored, never what it is.
 */
@RestController
@RequestMapping("/api/v1/helpdesk")
class HelpdeskAdministrationController {
    private final DirectoryService directory;
    private final MailboxService mailboxes;
    private final HelpdeskProperties properties;
    private final Requests requests;

    HelpdeskAdministrationController(DirectoryService directory, MailboxService mailboxes, HelpdeskProperties properties, Requests requests) {
        this.directory = directory;
        this.mailboxes = mailboxes;
        this.properties = properties;
        this.requests = requests;
    }

    record StatusBody(boolean secretKey, long pollIntervalSeconds, long maxBytes) {
    }

    record CustomerBody(UUID id, String name, List<UUID> projects, long version, Instant createdAt, UUID createdBy, Instant updatedAt,
            UUID updatedBy) {
        static CustomerBody of(Customer c) {
            return new CustomerBody(c.id(), c.name(), c.projects(), c.version(), c.createdAt(), c.createdBy(), c.updatedAt(), c.updatedBy());
        }
    }

    record CustomerRequest(String name, List<UUID> projects) {
    }

    record ContactBody(UUID id, UUID customerId, String name, List<String> addresses, long version, Instant createdAt, UUID createdBy,
            Instant updatedAt, UUID updatedBy) {
        static ContactBody of(Contact c) {
            return new ContactBody(c.id(), c.customerId(), c.name(), c.addresses(), c.version(), c.createdAt(), c.createdBy(), c.updatedAt(),
                c.updatedBy());
        }
    }

    record ContactRequest(UUID customerId, String name, List<String> addresses) {
    }

    record MailboxStatusBody(Instant polledAt, Instant succeededAt, String errorCode, Instant errorAt, String notice, Instant noticeAt) {
    }

    record MailboxBody(UUID id, String name, boolean enabled, UUID projectId, String ticketType, String imapHost, int imapPort,
            String imapSecurity, String username, boolean passwordSet, String folder, String smtpHost, Integer smtpPort, String smtpSecurity,
            String address, String authservId, long maxBytes, MailboxStatusBody status, long version, Instant updatedAt, UUID updatedBy) {
        static MailboxBody of(Mailbox m) {
            return new MailboxBody(m.id(), m.name(), m.enabled(), m.projectId(), m.ticketType(), m.imapHost(), m.imapPort(), m.imapSecurity(),
                m.username(), m.passwordSet(), m.folder(), m.smtpHost(), m.smtpPort(), m.smtpSecurity(), m.address(), m.authservId(),
                m.maxBytes(), new MailboxStatusBody(m.polledAt(), m.succeededAt(), m.errorCode(), m.errorAt(), m.notice(), m.noticeAt()),
                m.version(), m.updatedAt(), m.updatedBy());
        }
    }

    record MailboxRequest(String name, Boolean enabled, UUID projectId, String ticketType, String imapHost, Integer imapPort,
            String imapSecurity, String username, String password, String folder, String smtpHost, Integer smtpPort, String smtpSecurity,
            String address, String authservId, Long maxBytes) {
        MailboxService.Settings settings() {
            return new MailboxService.Settings(name, enabled, projectId, ticketType, imapHost, imapPort, imapSecurity, username, password,
                folder, smtpHost, smtpPort, smtpSecurity, address, authservId, maxBytes);
        }
    }

    record TestBody(boolean ok, String code) {
    }

    @GetMapping("/status")
    StatusBody status() {
        requests.administrator();
        return new StatusBody(mailboxes.secretKeyAvailable(), properties.pollInterval().toSeconds(), properties.maxBytes());
    }

    // ------------------------------------------------------------------------------------------------ customers

    @GetMapping("/customers")
    List<CustomerBody> customers() {
        requests.administrator();
        return Requests.retrying(directory::customers).stream().map(CustomerBody::of).toList();
    }

    @PostMapping("/customers")
    ResponseEntity<CustomerBody> createCustomer(@RequestBody CustomerRequest body) {
        UUID actor = requests.administrator().accountId();
        Customer customer = Requests.retrying(() -> directory.createCustomer(actor, required(body).name(), body.projects()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(Requests.etag(customer.version())).body(CustomerBody.of(customer));
    }

    @PutMapping("/customers/{id}")
    ResponseEntity<CustomerBody> updateCustomer(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody CustomerRequest body) {
        UUID actor = requests.administrator().accountId();
        long version = Requests.expectedVersion(ifMatch);
        Customer customer = Requests.retrying(() -> directory.updateCustomer(actor, id, version, required(body).name(), body.projects()));
        return ResponseEntity.ok().eTag(Requests.etag(customer.version())).body(CustomerBody.of(customer));
    }

    // ------------------------------------------------------------------------------------------------ contacts

    @GetMapping("/contacts")
    List<ContactBody> contacts(@RequestParam(required = false) UUID customer) {
        requests.administrator();
        return Requests.retrying(() -> directory.contacts(customer)).stream().map(ContactBody::of).toList();
    }

    @PostMapping("/contacts")
    ResponseEntity<ContactBody> createContact(@RequestBody ContactRequest body) {
        UUID actor = requests.administrator().accountId();
        Contact contact = Requests.retrying(() -> directory.createContact(actor, required(body).customerId(), body.name(), body.addresses()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(Requests.etag(contact.version())).body(ContactBody.of(contact));
    }

    @PutMapping("/contacts/{id}")
    ResponseEntity<ContactBody> updateContact(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody ContactRequest body) {
        UUID actor = requests.administrator().accountId();
        long version = Requests.expectedVersion(ifMatch);
        Contact contact = Requests.retrying(() -> directory.updateContact(actor, id, version, required(body).customerId(), body.name(),
            body.addresses()));
        return ResponseEntity.ok().eTag(Requests.etag(contact.version())).body(ContactBody.of(contact));
    }

    // ------------------------------------------------------------------------------------------------ mailboxes

    @GetMapping("/mailboxes")
    List<MailboxBody> mailboxes() {
        requests.administrator();
        return Requests.retrying(mailboxes::list).stream().map(MailboxBody::of).toList();
    }

    @PostMapping("/mailboxes")
    ResponseEntity<MailboxBody> createMailbox(@RequestBody MailboxRequest body) {
        UUID actor = requests.administrator().accountId();
        Mailbox mailbox = Requests.retrying(() -> mailboxes.create(actor, required(body).settings()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(Requests.etag(mailbox.version())).body(MailboxBody.of(mailbox));
    }

    @PutMapping("/mailboxes/{id}")
    ResponseEntity<MailboxBody> updateMailbox(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody MailboxRequest body) {
        UUID actor = requests.administrator().accountId();
        long version = Requests.expectedVersion(ifMatch);
        Mailbox mailbox = Requests.retrying(() -> mailboxes.update(actor, id, version, required(body).settings()));
        return ResponseEntity.ok().eTag(Requests.etag(mailbox.version())).body(MailboxBody.of(mailbox));
    }

    /** Opens the folder with the stored settings; the answer names the first failure by its code. */
    @PostMapping("/mailboxes/{id}/test")
    TestBody test(@PathVariable UUID id) {
        requests.administrator();
        MailboxService.TestResult result = Requests.retrying(() -> mailboxes.test(id));
        return new TestBody(result.ok(), result.code());
    }

    private static <T> T required(T body) {
        if (body == null) {
            throw ApiProblemException.of(400, "request.invalid");
        }
        return body;
    }
}
