package taskmanager.helpdesk.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A mail the intake took from a mailbox (TASK-057) and what became of it: a ticket, a place in the review queue, an
 * automatic message without ticket, or discarded. {@code raw} is only kept while the mail waits in the review queue.
 * {@code relatedTicketId} names the ticket of a reply this mail answers when the mail waits for a person (TASK-058).
 */
public record Message(UUID id, UUID companyId, UUID mailboxId, UUID projectId, long uidValidity, long uid, String messageId,
        Instant receivedAt, Instant sentAt, String fromAddress, String fromName, String to, String cc, String subject, String body,
        long sizeBytes, List<AttachmentNote> attachments, boolean hasRaw, String state, String reason, UUID ticketId, UUID contactId,
        UUID customerId, Instant processedAt, UUID processedBy, String discardReason, UUID relatedTicketId, long version) {

    public Message {
        attachments = List.copyOf(attachments);
    }

    /** A file of the mail: its name and size, and the attachment it became or why it was refused; both empty while it waits. */
    public record AttachmentNote(String name, long size, UUID attachmentId, String refusal) {
    }
}
