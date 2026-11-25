package taskmanager.tickets.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import taskmanager.permissions.AccessContext;
import taskmanager.tickets.TicketReader;

/** The readable fields of a ticket for other modules (TASK-126), through the same checks as the ticket route. */
@Service
class TicketReaderAdapter implements TicketReader {
    private final TicketService tickets;

    TicketReaderAdapter(TicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public Readable read(AccessContext actor, UUID projectId, long number) {
        TicketService.TicketView view = tickets.get(actor, projectId, number);
        Ticket ticket = view.ticket();
        Map<String, String> values = new HashMap<>();
        ticket.allValues().forEach((field, value) -> {
            if (value != null && view.readable().contains(field)) {
                values.put(field, value);
            }
        });
        return new Readable(ticket.id(), ticket.projectId(), view.projectKey(), ticket.number(), ticket.version(), values,
            view.configuration());
    }
}
