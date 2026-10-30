package taskmanager.helpdesk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The customer organizations for other modules (TASK-068), such as the SLA rules that name a customer. Names and the
 * projects a customer is released for, nothing about contacts or mails.
 */
public interface CustomerDirectory {
    record Summary(UUID id, String name, List<UUID> projects) {
    }

    List<Summary> customers(UUID companyId);

    Optional<Summary> customer(UUID companyId, UUID customerId);

    /** The customer of the ticket, if it has one. */
    Optional<UUID> customerOf(UUID ticketId);
}
