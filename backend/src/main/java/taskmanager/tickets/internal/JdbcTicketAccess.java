package taskmanager.tickets.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketChanged;

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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTagChange(UUID ticketId, Instant occurredAt, UUID actor, UUID removed, UUID added) {
        if ((removed == null) == (added == null)) {
            throw new IllegalArgumentException("exactly one of removed and added");
        }
        long version = tickets.lockShared(ticketId);
        tickets.change(ticketId, version, occurredAt, actor, TAGS_FIELD, text(removed), text(added));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForTagChange(UUID ticketId) {
        tickets.lockShared(ticketId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TicketChanged> searchSources(UUID companyId) {
        return tickets.all(companyId).stream().map(Ticket::changed).toList();
    }

    private static String text(UUID tag) {
        return tag == null ? null : tag.toString();
    }
}
