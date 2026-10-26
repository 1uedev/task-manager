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
}
