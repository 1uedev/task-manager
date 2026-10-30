package taskmanager.tickets.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final Set<String> HELPDESK_FIELDS = Set.of(CUSTOMER_FIELD, CONTACT_FIELD, MAIL_FIELD);
    private static final int TITLE_MAX = 200;
    private static final int DESCRIPTION_MAX = 20_000;

    private final TicketRepository tickets;
    private final ProjectAccess projects;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    JdbcTicketAccess(TicketRepository tickets, ProjectAccess projects, Clock clock, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.projects = projects;
        this.clock = clock;
        this.events = events;
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
    public Optional<Created> createForHelpdesk(UUID companyId, UUID projectId, String type, String title, String description,
            UUID actor) {
        if (!projects.lockForIntake(companyId, projectId)) {
            return Optional.empty();
        }
        long configurationVersion = projects.currentConfigurationVersion(projectId).orElse(0L);
        ProjectConfiguration configuration = projects.configuration(projectId, configurationVersion).orElse(null);
        if (configuration == null || type == null || !configuration.hasTicketType(type)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        long number = tickets.nextNumber(projectId);
        Ticket ticket = new Ticket(UUID.randomUUID(), companyId, projectId, number, cut(clean(title), TITLE_MAX, "(kein Betreff)"),
            cut(clean(description), DESCRIPTION_MAX, null), type, configuration.initialStatusOf(type), null, Map.of(),
            configurationVersion, 1, now, actor, now, actor);
        tickets.insert(ticket);
        ticket.allValues().forEach((field, value) -> {
            if (value != null) {
                tickets.change(ticket.id(), 1, now, actor, field, null, value);
            }
        });
        events.publishEvent(ticket.changed(configuration));
        return Optional.of(new Created(ticket.id(), number));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Location> locate(UUID ticketId) {
        return tickets.byId(ticketId).map(ticket -> new Location(ticket.companyId(), ticket.projectId(), ticket.number()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordHelpdeskChange(UUID ticketId, Instant occurredAt, UUID actor, String field, String removed, String added) {
        if (!HELPDESK_FIELDS.contains(field)) {
            throw new IllegalArgumentException("not a helpdesk field: " + field);
        }
        if (removed == null && added == null) {
            throw new IllegalArgumentException("nothing changed");
        }
        long version = tickets.lockShared(ticketId);
        tickets.change(ticketId, version, occurredAt, actor, field, removed, added);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForHelpdeskChange(UUID ticketId) {
        tickets.lockShared(ticketId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPriorityChange(UUID ticketId, Instant occurredAt, UUID actor, String removed, String added) {
        if (removed == null && added == null) {
            throw new IllegalArgumentException("nothing changed");
        }
        long version = tickets.lockShared(ticketId);
        tickets.change(ticketId, version, occurredAt, actor, PRIORITY_FIELD, removed, added);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForPriorityChange(UUID ticketId) {
        tickets.lockShared(ticketId);
    }

    @Override
    public Optional<State> state(UUID ticketId) {
        return tickets.byId(ticketId).map(ticket -> new State(ticket.companyId(), ticket.projectId(), ticket.number(), ticket.type(),
            ticket.status(), ticket.createdAt()));
    }

    /** Without the NUL character, which PostgreSQL cannot store, and without surrounding space; blank is {@code null}. */
    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("\u0000", "").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** At most {@code max} code points; a longer text ends in an ellipsis. */
    private static String cut(String text, int max, String fallback) {
        if (text == null) {
            return fallback;
        }
        if (text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max - 1)).stripTrailing() + "\u2026";
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
