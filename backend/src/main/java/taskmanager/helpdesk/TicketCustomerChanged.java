package taskmanager.helpdesk;

import java.time.Instant;
import java.util.UUID;

/**
 * Published in the transaction that set, changed or removed the customer of a ticket (TASK-068), after the change is
 * written. {@code customerId} is {@code null} when the customer was removed. {@code firstReceivedAt} is when the intake
 * stored the first mail of the ticket, or {@code null} for a ticket without mails; it is the first registration of a
 * request, which the service deadlines start from. Listeners run synchronously in that transaction.
 */
public record TicketCustomerChanged(UUID companyId, UUID projectId, UUID ticketId, UUID customerId, Instant firstReceivedAt,
        Instant occurredAt) {
}
