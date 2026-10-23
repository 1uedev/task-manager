package taskmanager.tickets.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.projects.TicketUsage;

/**
 * What the projects module asks of the tickets when it publishes a configuration (TASK-044, DEC-073). The caller holds the
 * project row exclusively, so no ticket is created and no ticket changes its type or status meanwhile; the ticket rows are
 * locked in the order of their identifiers, like every other path that locks several of them.
 */
@Service
class TicketUsageAdapter implements TicketUsage {
    private final TicketRepository tickets;
    private final ProjectAccess projects;
    private final ApplicationEventPublisher events;

    TicketUsageAdapter(TicketRepository tickets, ProjectAccess projects, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.projects = projects;
        this.events = events;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Use> usage(UUID projectId) {
        return tickets.usage(projectId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long migrate(UUID projectId, List<Move> moves, UUID actor, Instant now) {
        long version = projects.currentConfigurationVersion(projectId).orElseThrow(() -> ApiProblemException.of(404, "project.not_found"));
        ProjectConfiguration configuration = projects.configuration(projectId, version)
            .orElseThrow(() -> ApiProblemException.of(404, "project.not_found"));
        long moved = 0;
        for (Move move : moves) {
            for (Ticket ticket : tickets.lockUsing(projectId, move.fromType(), move.fromStatus())) {
                Ticket changed = new Ticket(ticket.id(), ticket.companyId(), projectId, ticket.number(), ticket.title(),
                    ticket.description(), move.toType(), move.toStatus(), ticket.assignee(), ticket.custom(), ticket.configurationVersion(),
                    ticket.version() + 1, ticket.createdAt(), ticket.createdBy(), now, actor);
                tickets.update(changed);
                if (!ticket.type().equals(changed.type())) {
                    tickets.change(ticket.id(), changed.version(), now, actor, "type", ticket.type(), changed.type());
                }
                if (!ticket.status().equals(changed.status())) {
                    tickets.change(ticket.id(), changed.version(), now, actor, "status", ticket.status(), changed.status());
                }
                events.publishEvent(changed.changed(configuration));
                moved++;
            }
        }
        return moved;
    }
}
