package taskmanager.tickets.internal;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketChanged;

/** The ticket port for other modules; see {@link TicketAccess}. */
@Service
class JdbcTicketAccess implements TicketAccess {
    private final TicketRepository tickets;
    private final ProjectAccess projects;

    JdbcTicketAccess(TicketRepository tickets, ProjectAccess projects) {
        this.tickets = tickets;
        this.projects = projects;
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
    public void recordAttachmentChange(UUID ticketId, Instant occurredAt, UUID actor, String removed, String added) {
        if ((removed == null) == (added == null)) {
            throw new IllegalArgumentException("exactly one of removed and added");
        }
        long version = tickets.lockShared(ticketId);
        tickets.change(ticketId, version, occurredAt, actor, ATTACHMENTS_FIELD, removed, added);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForAttachmentChange(UUID ticketId) {
        tickets.lockShared(ticketId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TicketChanged> searchSources(UUID companyId) {
        Map<UUID, ProjectConfiguration> configurations = new HashMap<>();
        return tickets.all(companyId).stream().map(ticket -> ticket.changed(configurations.computeIfAbsent(ticket.projectId(),
            project -> projects.currentConfigurationVersion(project).flatMap(version -> projects.configuration(project, version)).orElseThrow())))
            .toList();
    }

    private static String text(UUID tag) {
        return tag == null ? null : tag.toString();
    }
}
