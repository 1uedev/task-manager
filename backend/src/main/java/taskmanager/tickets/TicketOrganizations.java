package taskmanager.tickets;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The customer organization of tickets, for the sight of portal accounts (TASK-075, DEC-105). The helpdesk owns the customer of
 * a ticket and implements this port; the ticket module asks it only for an external caller, because the permissions module
 * compares the organization of the resource with the organization of such a caller. Both methods join the transaction of the
 * caller and hold the link rows they read {@code FOR SHARE} until its commit, so a change of the customer waits for it; they
 * run after the project lock of the permissions check.
 */
public interface TicketOrganizations {
    /**
     * The organization of the ticket when it is the given one, empty for a ticket of another organization or without customer.
     * Only a matching link row is locked, so a held row of another organization never delays the answer (TASK-078, F1).
     */
    Optional<UUID> lockOrganization(UUID ticketId, UUID organizationId);

    /**
     * The current customer of the ticket, empty without one (TASK-079, DEC-109): the organization a file made visible to the
     * customer is written for. Holds the link row {@code FOR SHARE} like the other methods.
     */
    Optional<UUID> lockCustomer(UUID ticketId);

    /** The tickets of the organization in the project. */
    List<UUID> lockTicketsOf(UUID projectId, UUID organizationId);
}
