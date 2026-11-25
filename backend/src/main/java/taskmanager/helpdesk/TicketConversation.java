package taskmanager.helpdesk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import taskmanager.permissions.AccessContext;

/**
 * The conversation of a ticket with the customer for modules that pass it on, such as the AI assistance (TASK-126): mails of
 * the customer, sent replies and posts of the portal, oldest first. Each method checks like the routes of the helpdesk and
 * answers with the same problems.
 */
public interface TicketConversation {
    /** Where an answer of the support goes: a reply by mail or an answer in the portal. */
    enum Channel {
        MAIL, PORTAL
    }

    /**
     * One entry: {@code kind} is {@code mail}, {@code reply} or {@code post}; {@code author} is {@code customer} or
     * {@code support}; {@code name} is the sender name of a mail; {@code subject} is empty for posts.
     */
    record Entry(String kind, UUID id, Instant at, String author, String name, String subject, String body) {
    }

    /**
     * What the customer already saw, for an answer of the support: only the entries written for the customer of the ticket and
     * the language of its contact ({@code de} or {@code en}, German without contact).
     */
    record CustomerView(UUID ticketId, UUID customerId, String language, List<Entry> entries) {
        public CustomerView {
            entries = List.copyOf(entries);
        }
    }

    /**
     * Everything of the conversation the caller may read in the internal ticket view: with {@code ticket.read} and the read rule
     * of {@code mail} every mail, every sent reply and every post of the portal; without the read rule of {@code mail} nothing.
     * Without {@code ticket.read} the project does not exist (404).
     */
    List<Entry> readable(AccessContext actor, UUID projectId, long number);

    /**
     * The conversation as the customer of the ticket saw it, with the rights of an answer of the support ({@code helpdesk.reply},
     * {@code ticket.read} with the read rules of {@code mail} and {@code contact}). A mail needs a contact with an address
     * (409 {@code helpdesk.reply_no_contact}), the portal a customer (409 {@code helpdesk.portal_no_customer}).
     */
    CustomerView forAnswer(AccessContext actor, UUID projectId, long number, Channel channel);
}
