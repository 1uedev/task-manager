package taskmanager.sla;

import java.util.Optional;
import java.util.UUID;
import taskmanager.permissions.AccessContext;

/**
 * The priority of a ticket as a person may read it, for modules that pass it on, such as the AI assistance (TASK-126). Without
 * {@code ticket.read} the project does not exist for the caller (404); without the read rule of the field {@code priority} the
 * answer is empty.
 */
public interface TicketPriorities {
    /** {@code low}, {@code normal}, {@code high} or {@code critical}; empty without the read rule. */
    Optional<String> readable(AccessContext actor, UUID projectId, long number);
}
