package taskmanager.sla.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import taskmanager.permissions.AccessContext;
import taskmanager.sla.TicketPriorities;

/** The readable priority of a ticket for other modules (TASK-126). */
@Service
class TicketPrioritiesAdapter implements TicketPriorities {
    private final TicketSla sla;

    TicketPrioritiesAdapter(TicketSla sla) {
        this.sla = sla;
    }

    @Override
    public Optional<String> readable(AccessContext actor, UUID projectId, long number) {
        return sla.readablePriority(actor, projectId, number);
    }
}
