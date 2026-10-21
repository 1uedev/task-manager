package taskmanager.tags;

import java.util.UUID;

/**
 * Published inside the transaction that actually assigned a tag to a ticket or removed it (TASK-029); a repeated call
 * publishes nothing. Listeners run synchronously in that transaction.
 */
public record TicketTagChanged(UUID ticketId, UUID tagId, boolean assigned) {
}
