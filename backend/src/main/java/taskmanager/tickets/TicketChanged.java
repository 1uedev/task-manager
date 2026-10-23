package taskmanager.tickets;

import java.util.List;
import java.util.UUID;

/**
 * Published inside the transaction that created or changed a ticket, after the change is written (TASK-029). Listeners run
 * synchronously in that transaction, so a projection they keep commits or rolls back with the ticket. It carries all five
 * fields and the custom values; a listener must not show any of them without the read rules of the reader.
 */
public record TicketChanged(UUID ticketId, UUID companyId, UUID projectId, long number, String title, String description,
        String type, String status, UUID assignee, List<CustomValue> custom) {

    /** The value of one custom field in its canonical text form (TASK-044); {@code type} is the kind of the field. */
    public record CustomValue(String key, String type, String value) {
    }

    public TicketChanged {
        custom = custom == null ? List.of() : List.copyOf(custom);
    }

    /** A ticket without custom values. */
    public TicketChanged(UUID ticketId, UUID companyId, UUID projectId, long number, String title, String description,
            String type, String status, UUID assignee) {
        this(ticketId, companyId, projectId, number, title, description, type, status, assignee, List.of());
    }
}
