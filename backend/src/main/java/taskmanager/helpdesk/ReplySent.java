package taskmanager.helpdesk;

import java.time.Instant;
import java.util.UUID;

/**
 * Published in the transaction that recorded a reply to the contact of a ticket as sent (TASK-068): the mail server
 * accepted it at {@code sentAt}, or a person recorded an uncertain reply as arrived. Listeners run synchronously in that
 * transaction.
 */
public record ReplySent(UUID companyId, UUID projectId, UUID ticketId, UUID replyId, Instant sentAt) {
}
