package taskmanager.tickets;

import java.util.UUID;
import taskmanager.permissions.AccessContext;

/**
 * Who sees the links of requirements in the history of a ticket (TASK-097, DEC-127). The requirements module owns the links
 * and implements this port; a link is visible only to a person who may read both sides, so the ticket module shows the entries
 * of the field {@link TicketAccess#REQUIREMENTS_FIELD} only when the answer is {@code true}, after its own check of
 * {@code ticket.read}.
 */
public interface TicketRequirementReaders {
    /** Whether the caller may read the requirements of the project. */
    boolean mayRead(AccessContext actor, UUID projectId);
}
