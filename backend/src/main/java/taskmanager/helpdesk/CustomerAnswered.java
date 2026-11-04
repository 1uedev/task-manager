package taskmanager.helpdesk;

import java.time.Instant;
import java.util.UUID;

/**
 * Published in the transaction that added an answer of the customer to an existing ticket (TASK-068), by the intake or from
 * the review queue. {@code receivedAt} is when the intake stored the mail. Listeners run synchronously in that transaction.
 */
public record CustomerAnswered(UUID companyId, UUID projectId, UUID ticketId, Instant receivedAt) {
}
