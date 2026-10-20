package taskmanager.tickets;

import java.util.Optional;
import java.util.UUID;

/**
 * What other modules (tags, later search) use of a ticket (TASK-028). They never read the tables of this module. Tickets
 * are never deleted, so an identifier once found stays valid.
 */
public interface TicketAccess {
    /** The identifier of the ticket with this number in the project of the company. */
    Optional<UUID> find(UUID companyId, UUID projectId, long number);

    /** The resource type of tickets in the permissions module, for field rules such as the field {@code tags}. */
    String RESOURCE_TYPE = "ticket";
}
