package taskmanager.tickets;

import java.util.Set;
import java.util.UUID;

/**
 * The tag filter of the portal list (TASK-077, DEC-108). The tags module owns the tags and implements this port; the ticket
 * module asks it only for an external caller after its own rights check, and only narrows the tickets of the organization of
 * the caller with the answer.
 */
public interface TicketTagFilter {
    /**
     * The tickets of the project that carry the tag, when the portal shows the tag to the organization; an empty set for an
     * unknown tag, an internal one, one of another organization or of another catalog, so the answer gives no hint about it.
     */
    Set<UUID> ticketsWith(UUID companyId, UUID projectId, UUID organizationId, UUID tagId);
}
