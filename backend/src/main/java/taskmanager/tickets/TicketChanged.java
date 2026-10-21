package taskmanager.tickets;

import java.util.UUID;

/**
 * Published inside the transaction that created or changed a ticket, after the change is written (TASK-029). Listeners run
 * synchronously in that transaction, so a projection they keep commits or rolls back with the ticket. It carries all five
 * fields; a listener must not show any of them without the read rules of the reader.
 */
public record TicketChanged(UUID ticketId, UUID companyId, UUID projectId, long number, String title, String description,
        String type, String status, UUID assignee) {
}
