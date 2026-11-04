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

    /** A contact of a customer with its name (TASK-075). */
    record ContactSummary(UUID id, String name) {
    }

    /** The contacts of the customer, by name (TASK-075). */
    List<ContactSummary> contacts(UUID companyId, UUID customerId);

    List<Summary> customers(UUID companyId);

    Optional<Summary> customer(UUID companyId, UUID customerId);

    /** The customer of the ticket, if it has one. */
    Optional<UUID> customerOf(UUID ticketId);

    /**
     * The customer of the contact of the company, empty for an unknown contact (TASK-075). The contact row stays locked
     * {@code FOR SHARE} until the commit of the caller, so it keeps its customer meanwhile.
     */
    Optional<UUID> lockCustomerOfContact(UUID companyId, UUID contactId);
}
