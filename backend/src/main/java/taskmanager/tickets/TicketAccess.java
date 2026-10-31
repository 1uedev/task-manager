package taskmanager.tickets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules (tags, later search) use of a ticket (TASK-028). They never read the tables of this module. Tickets
 * are never deleted, so an identifier once found stays valid.
 */
public interface TicketAccess {
    /** The identifier of the ticket with this number in the project of the company. */
    Optional<UUID> find(UUID companyId, UUID projectId, long number);

    /**
     * Records in the history of the ticket that a tag was assigned ({@code removed} is {@code null}) or removed
     * ({@code added} is {@code null}) (TASK-042). The version of the ticket stays as it is; the entry carries the version
     * the ticket has. The ticket row is locked {@code FOR SHARE} until the commit, so no change of the fields interleaves.
     */
    void recordTagChange(UUID ticketId, Instant occurredAt, UUID actor, UUID removed, UUID added);

    /**
     * Locks the ticket row {@code FOR SHARE} until the commit, as {@link #recordTagChange} does, but before the caller takes
     * the time of its change (TASK-043): a change that waited for a field change then carries a time after it.
     */
    void lockForTagChange(UUID ticketId);

    /**
     * Records in the history of the ticket that an attachment was added ({@code removed} is {@code null}) or removed
     * ({@code added} is {@code null}) (TASK-051). The values are the description of the attachment as text. Like a tag
     * change it leaves the version of the ticket as it is and holds the ticket row {@code FOR SHARE} until the commit.
     */
    void recordAttachmentChange(UUID ticketId, Instant occurredAt, UUID actor, String removed, String added);

    /** Locks the ticket row {@code FOR SHARE} before the caller takes the time of an attachment change, as {@link #lockForTagChange}. */
    void lockForAttachmentChange(UUID ticketId);

    /**
     * Creates a ticket for the helpdesk (TASK-057): from a mail of a confirmed contact with the intake as actor, or from the
     * review queue with the person who processed it. The caller decided that the work is allowed; no right of the actor is
     * checked here. The project row is locked {@code FOR SHARE} until the commit. The ticket gets the initial status of its
     * type; required fields other than title and type are not enforced, as for a field that became required later.
     * Title and description are cut to their limits. Returns empty when the project or the type does not exist.
     */
    Optional<Created> createForHelpdesk(UUID companyId, UUID projectId, String type, String title, String description, UUID actor);

    /** A ticket the helpdesk created. */
    record Created(UUID id, long number) {
    }

    /** Where a ticket lives: its company, project and number, or empty for an unknown identifier. */
    Optional<Location> locate(UUID ticketId);

    record Location(UUID companyId, UUID projectId, long number) {
    }

    /**
     * Records a change of one of the helpdesk fields ({@link #CUSTOMER_FIELD}, {@link #CONTACT_FIELD}, {@link #MAIL_FIELD})
     * in the history, as {@link #recordTagChange} does: the version stays, the ticket row is held {@code FOR SHARE}.
     */
    void recordHelpdeskChange(UUID ticketId, Instant occurredAt, UUID actor, String field, String removed, String added);

    /** Locks the ticket row {@code FOR SHARE} before the caller takes the time of a helpdesk change. */
    void lockForHelpdeskChange(UUID ticketId);

    /**
     * Records a change of the field {@link #PRIORITY_FIELD} in the history (TASK-068), as {@link #recordTagChange} does: the
     * version stays, the ticket row is held {@code FOR SHARE}.
     */
    void recordPriorityChange(UUID ticketId, Instant occurredAt, UUID actor, String removed, String added);

    /** Locks the ticket row {@code FOR SHARE} before the caller takes the time of a priority change. */
    void lockForPriorityChange(UUID ticketId);

    /** What the deadlines of a ticket depend on (TASK-068): project, type, status and when the ticket was created. */
    record State(UUID companyId, UUID projectId, long number, String type, String status, Instant createdAt) {
    }

    /** The current state of the ticket, or empty for an unknown identifier. Needs no lock; read it in the transaction of the change. */
    Optional<State> state(UUID ticketId);

    /**
     * Every ticket of the company with its five fields, to rebuild a projection (TASK-029). Needs a transaction; the fields
     * must not be shown without the read rules of the reader.
     */
    List<TicketChanged> searchSources(UUID companyId);

    /** The resource type of tickets in the permissions module, for field rules such as the field {@code tags}. */
    String RESOURCE_TYPE = "ticket";

    /** The field of the ticket that holds its tags; its read rule decides who sees tag changes in the history. */
    String TAGS_FIELD = "tags";

    /** The field of the ticket that holds its attachments; its read rule decides who sees them and their changes in the history. */
    String ATTACHMENTS_FIELD = "attachments";

    /** The customer organization of the ticket (TASK-057). */
    String CUSTOMER_FIELD = "customer";

    /** The contact of the customer who asked (TASK-057). */
    String CONTACT_FIELD = "contact";

    /** The mails of the ticket (TASK-057); only read, they arrive through the mail intake. */
    String MAIL_FIELD = "mail";

    /** The priority of the ticket for service deadlines (TASK-068): low, normal, high or critical. */
    String PRIORITY_FIELD = "priority";

    /** The actor of what the mail intake does by itself, such as a ticket from a confirmed contact; no account has it. */
    UUID MAIL_INTAKE = new UUID(0, 0x4d41494cL);
}
