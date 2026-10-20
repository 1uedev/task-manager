package taskmanager.tickets.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.tickets.TicketAccess;

/** The ticket port for other modules; see {@link TicketAccess}. */
@Service
class JdbcTicketAccess implements TicketAccess {
    private final TicketRepository tickets;

    JdbcTicketAccess(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> find(UUID companyId, UUID projectId, long number) {
        return tickets.find(companyId, projectId, number).map(Ticket::id);
    }
}
