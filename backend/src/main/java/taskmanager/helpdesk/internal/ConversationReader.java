package taskmanager.helpdesk.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.helpdesk.TicketConversation;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.tickets.TicketAccess;

/**
 * The conversation of a ticket for the AI assistance (TASK-126). The internal view follows {@link TicketCustomerService#view}
 * and {@link PortalConversation#posts}; the view of the customer follows the audiences of TASK-079: mails of the ticket from
 * the customer, replies sent to it and posts written for it, as the portal shows them.
 */
@Service
class ConversationReader implements TicketConversation {
    private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::at).thenComparing(Entry::id);

    private final TicketCustomerService customers;
    private final PortalConversation portal;
    private final ReplyService replies;
    private final HelpdeskRepository repository;
    private final PortalRepository posts;
    private final TicketAccess tickets;

    ConversationReader(TicketCustomerService customers, PortalConversation portal, ReplyService replies, HelpdeskRepository repository,
            PortalRepository posts, TicketAccess tickets) {
        this.customers = customers;
        this.portal = portal;
        this.replies = replies;
        this.repository = repository;
        this.posts = posts;
        this.tickets = tickets;
    }

    @Override
    @Transactional
    public List<Entry> readable(AccessContext actor, UUID projectId, long number) {
        if (actor.external()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        TicketCustomerService.View view = customers.view(actor, projectId, number);
        if (!view.readable().contains(TicketAccess.MAIL_FIELD)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        view.mails().forEach(m -> entries.add(mail(m)));
        view.replies().stream().filter(r -> r.state().equals("sent")).forEach(r -> entries.add(reply(r)));
        portal.posts(actor, projectId, number).forEach(p -> entries.add(new Entry("post", p.id(), p.createdAt(), p.authorKind(), null, null, p.body())));
        entries.sort(ORDER);
        return List.copyOf(entries);
    }

    @Override
    @Transactional
    public CustomerView forAnswer(AccessContext actor, UUID projectId, long number, Channel channel) {
        if (actor.external()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        replies.requireReply(actor, projectId);
        UUID ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
        Optional<HelpdeskRepository.TicketCustomer> link = repository.ticketCustomer(ticket);
        UUID customer;
        Contact contact;
        if (channel == Channel.MAIL) {
            contact = replies.contact(actor, ticket);
            customer = contact.customerId();
        } else {
            customer = link.map(HelpdeskRepository.TicketCustomer::customerId)
                .orElseThrow(() -> ApiProblemException.of(409, "helpdesk.portal_no_customer"));
            contact = link.map(HelpdeskRepository.TicketCustomer::contactId).flatMap(id -> repository.contact(actor.companyId(), id))
                .filter(c -> c.customerId().equals(customer)).orElse(null);
        }
        String language = contact != null && "en".equals(contact.language()) ? "en" : "de";
        List<String> recipient = new ArrayList<>();
        if (channel == Channel.MAIL) {
            recipient.add(contact.name() == null ? "" : contact.name());
            recipient.addAll(contact.addresses());
        }
        List<Entry> entries = new ArrayList<>();
        repository.messagesOfTicket(ticket).stream().filter(m -> m.state().equals("ticket") && customer.equals(m.customerId()))
            .forEach(m -> entries.add(mail(m)));
        repository.repliesOfTicket(ticket).stream().filter(r -> r.state().equals("sent") && customer.equals(r.customerId()))
            .forEach(r -> entries.add(reply(r)));
        posts.postsFor(ticket, customer).forEach(p -> entries.add(new Entry("post", p.id(), p.createdAt(), p.authorKind(), null, null, p.body())));
        entries.sort(ORDER);
        return new CustomerView(ticket, customer, contact == null ? null : contact.id(), recipient, language, entries);
    }

    private static Entry mail(Message m) {
        return new Entry("mail", m.id(), m.receivedAt(), PortalRepository.Post.CUSTOMER, m.fromName(), m.subject(), m.body());
    }

    private static Entry reply(Reply r) {
        return new Entry("reply", r.id(), r.sentAt() == null ? r.createdAt() : r.sentAt(), PortalRepository.Post.SUPPORT, null, r.subject(),
            r.body());
    }
}
