package taskmanager.sla.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.helpdesk.CustomerAnswered;
import taskmanager.helpdesk.CustomerDirectory;
import taskmanager.helpdesk.ReplySent;
import taskmanager.helpdesk.TicketCustomerChanged;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.GroupDirectory;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketChanged;

/**
 * The deadlines of tickets (TASK-068). The clocks move in the transaction of the change that moves them, after the row of the
 * ticket in this module is locked {@code FOR UPDATE}, so the changes of one ticket run one after the other:
 * <ul>
 *   <li>the customer is set: the first cycle starts at the first registration, the first stored mail or else the creation of
 *       the ticket (rules 4 and 7); removing the customer cancels the open cycle;</li>
 *   <li>the status changes: its kind of the project configuration pauses, resumes, resolves or cancels (rules 9 to 11); a
 *       return from a resolved or cancelled status into an open one starts a new cycle (rule 12);</li>
 *   <li>a reply is sent: the first response is met (rule 8); an answer of the customer ends the pause;</li>
 *   <li>the priority changes: both clocks get the budgets of the new priority from the rule version of the cycle (rule 13).</li>
 * </ul>
 * Reading the deadlines needs {@code ticket.read} with the read rules of {@code customer} and {@code priority}, changing the
 * priority {@code ticket.edit} with the write rule of {@code priority}. Without access to the ticket the project does not
 * exist for the caller (404), as for the other fields.
 */
@Service
public class TicketSla {
    private static final Logger LOG = LoggerFactory.getLogger(TicketSla.class);
    private static final int ESCALATION_LIST_LIMIT = 200;
    private static final String READ = "ticket.read";
    private static final String EDIT = "ticket.edit";
    private static final Set<String> FIELDS = Set.of(TicketAccess.CUSTOMER_FIELD, TicketAccess.PRIORITY_FIELD);

    private final SlaRepository repository;
    private final TicketAccess tickets;
    private final ProjectAccess projects;
    private final CustomerDirectory customers;
    private final Permissions permissions;
    private final GroupDirectory groups;
    private final Clock clock;
    private final Map<String, WorkCalendar> calendars = new ConcurrentHashMap<>();

    TicketSla(SlaRepository repository, TicketAccess tickets, ProjectAccess projects, CustomerDirectory customers, Permissions permissions,
            GroupDirectory groups, Clock clock) {
        this.repository = repository;
        this.tickets = tickets;
        this.projects = projects;
        this.customers = customers;
        this.permissions = permissions;
        this.groups = groups;
        this.clock = clock;
    }

    /** What the caller sees of the deadlines of a ticket; {@code state} is {@code null} without the read rules. */
    public record View(Set<String> readable, Set<String> writable, String priority, String state, List<CycleView> cycles, Instant now) {
    }

    public record CycleView(SlaCycle cycle, String ruleName, String calendarName, String timeZone, long firstResponseRemaining,
            long resolutionRemaining, List<StageView> escalations) {
    }

    /** A reached escalation stage of a group of the caller (TASK-070, rule 2). */
    public record StageView(String kind, int percent, UUID groupId, String groupName, Instant thresholdAt, Instant reachedAt) {
    }

    /** An entry of the list "SLA Eskalationen": a reached stage of a group of the caller on a ticket the caller may read. */
    public record EscalationItem(UUID projectId, String projectKey, long number, int cycleNumber, String cycleOutcome, String kind, int percent,
            UUID groupId, String groupName, Instant thresholdAt, Instant reachedAt) {
    }

    /** No customer: no deadlines. A customer without a rule that matches: none assigned. Else the cycles. */
    public static final String NO_CUSTOMER = "no_customer";
    public static final String NO_RULE = "no_rule";
    public static final String ACTIVE = "active";

    // ------------------------------------------------------------------------------------------------ events

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TicketChanged changed) {
        SlaRepository.TicketRow row = repository.lockTicket(changed.ticketId(), changed.companyId(), changed.projectId());
        String kind = kindOf(changed.projectId(), changed.status());
        if (row.status() == null) {
            repository.ticketStatus(changed.ticketId(), changed.status(), kind);
            return;
        }
        if (row.status().equals(changed.status())) {
            return;
        }
        repository.ticketStatus(changed.ticketId(), changed.status(), kind);
        String previous = row.statusKind() == null ? ProjectConfiguration.SLA_OPEN : row.statusKind();
        Instant now = now();
        Optional<SlaCycle> open = repository.openCycle(changed.ticketId());
        if (open.isPresent()) {
            SlaCycle cycle = open.get();
            if (!previous.equals(kind)) {
                repository.updateCycle(cycle.statusChanged(previous, kind, now, calendar(cycle)));
            }
            return;
        }
        boolean reopened = (previous.equals(SlaCycle.RESOLVED) || previous.equals(SlaCycle.CANCELLED))
            && (kind.equals(SlaCycle.OPEN) || kind.equals(SlaCycle.WAITING));
        if (reopened && repository.cycleCount(changed.ticketId()) > 0) {
            customers.customerOf(changed.ticketId()).ifPresent(customer ->
                start(changed.companyId(), changed.projectId(), changed.ticketId(), customer, changed.type(), row.priority(), now, kind, now));
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TicketCustomerChanged changed) {
        TicketAccess.State state = tickets.state(changed.ticketId()).orElse(null);
        if (state == null) {
            return;
        }
        SlaRepository.TicketRow row = lockWithStatus(changed.ticketId(), state);
        Optional<SlaCycle> open = repository.openCycle(changed.ticketId());
        Instant now = now();
        if (changed.customerId() == null) {
            open.ifPresent(cycle -> repository.updateCycle(cycle.close(SlaCycle.CANCELLED, "customer_removed", now, calendar(cycle))));
            return;
        }
        if (open.isPresent()) {
            return;
        }
        String kind = row.statusKind() == null ? ProjectConfiguration.SLA_OPEN : row.statusKind();
        if (kind.equals(SlaCycle.CANCELLED)) {
            return;
        }
        // The first cycle starts at the first registration (rule 7); after a removed customer a new one starts now.
        Instant start = repository.cycleCount(changed.ticketId()) > 0 ? now
            : changed.firstReceivedAt() != null ? seconds(changed.firstReceivedAt()) : seconds(state.createdAt());
        start(changed.companyId(), changed.projectId(), changed.ticketId(), changed.customerId(), state.type(), row.priority(), start, kind, now);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(ReplySent sent) {
        repository.lockTicket(sent.ticketId(), sent.companyId(), sent.projectId());
        repository.openCycle(sent.ticketId()).ifPresent(cycle -> repository.updateCycle(cycle.replySent(seconds(sent.sentAt()), calendar(cycle))));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(CustomerAnswered answered) {
        repository.lockTicket(answered.ticketId(), answered.companyId(), answered.projectId());
        repository.openCycle(answered.ticketId())
            .ifPresent(cycle -> repository.updateCycle(cycle.customerAnswered(seconds(answered.receivedAt()), calendar(cycle))));
    }

    private SlaRepository.TicketRow lockWithStatus(UUID ticketId, TicketAccess.State state) {
        SlaRepository.TicketRow row = repository.lockTicket(ticketId, state.companyId(), state.projectId());
        if (row.status() == null) {
            String kind = kindOf(state.projectId(), state.status());
            repository.ticketStatus(ticketId, state.status(), kind);
            return new SlaRepository.TicketRow(ticketId, row.companyId(), row.projectId(), row.priority(), state.status(), kind);
        }
        return row;
    }

    private void start(UUID companyId, UUID projectId, UUID ticketId, UUID customerId, String type, String priority, Instant startedAt,
            String kind, Instant now) {
        List<Rule> rules = repository.matching(companyId, projectId, customerId, type);
        if (rules.isEmpty()) {
            return;
        }
        Rule rule = rules.getFirst();
        Calendar calendar = repository.calendar(companyId, rule.calendarId()).orElseThrow();
        SlaCycle cycle = SlaCycle.start(ticketId, repository.cycleCount(ticketId) + 1, rule.id(), rule.version(), calendar.id(), calendar.version(),
            priority, rule.budgets().seconds(priority), startedAt, kind, now, calendar(calendar.id(), calendar.version()));
        repository.insertCycle(cycle, companyId, projectId);
    }

    /** Clocks count whole seconds, so every instant they see is cut to the second. */
    private Instant now() {
        return seconds(clock.instant());
    }

    private static Instant seconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }

    private String kindOf(UUID projectId, String status) {
        return projects.currentConfigurationVersion(projectId).flatMap(version -> projects.configuration(projectId, version))
            .map(configuration -> configuration.slaKindOf(status)).orElse(ProjectConfiguration.SLA_OPEN);
    }

    private WorkCalendar calendar(SlaCycle cycle) {
        return calendar(cycle.calendarId(), cycle.calendarVersion());
    }

    /** A calendar version never changes, so its computed form is kept. */
    private WorkCalendar calendar(UUID calendarId, long version) {
        return calendars.computeIfAbsent(calendarId + "/" + version,
            key -> WorkCalendar.of(repository.calendarVersion(calendarId, version).orElseThrow()));
    }

    // ------------------------------------------------------------------------------------------------ the ticket

    @Transactional
    public View view(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = permissions.inspect(actor, resource, READ, FIELDS);
        if (!read.allowed()) {
            throw projectNotFound();
        }
        UUID ticket = ticket(actor, projectId, number);
        Set<String> readable = read.readableFields();
        Decision edit = permissions.inspect(actor, resource, EDIT, FIELDS);
        Set<String> writable = new HashSet<>(edit.allowed() ? edit.writableFields() : Set.of());
        writable.retainAll(Set.of(TicketAccess.PRIORITY_FIELD));
        String priority = repository.ticket(ticket).map(SlaRepository.TicketRow::priority).orElse(Rule.DEFAULT_PRIORITY);
        if (!readable.containsAll(FIELDS)) {
            return new View(Set.copyOf(readable), Set.copyOf(writable), readable.contains(TicketAccess.PRIORITY_FIELD) ? priority : null, null,
                List.of(), now());
        }
        Instant now = now();
        Set<UUID> mine = groups.groupsOf(actor.companyId(), actor.userId());
        Map<UUID, String> groupNames = mine.isEmpty() ? Map.of() : groupNames(actor.companyId());
        List<CycleView> cycles = repository.cycles(ticket).stream().map(cycle -> {
            WorkCalendar calendar = calendar(cycle);
            String ruleName = repository.rule(actor.companyId(), cycle.ruleId()).map(Rule::name).orElse("");
            String calendarName = repository.calendar(actor.companyId(), cycle.calendarId()).map(Calendar::name).orElse("");
            List<StageView> stages = mine.isEmpty() ? List.of() : repository.escalationsOf(cycle.id()).stream()
                .filter(e -> !e.skipped() && mine.contains(e.groupId()))
                .map(e -> new StageView(e.kind(), e.percent(), e.groupId(), groupNames.getOrDefault(e.groupId(), ""), e.thresholdAt(), e.reachedAt()))
                .toList();
            return new CycleView(cycle, ruleName, calendarName, calendar.zone().getId(), cycle.firstResponse().remainingAt(now, calendar),
                cycle.resolution().remainingAt(now, calendar), stages);
        }).toList();
        String state;
        if (customers.customerOf(ticket).isEmpty()) {
            state = NO_CUSTOMER;
        } else if (cycles.isEmpty()) {
            state = NO_RULE;
        } else {
            state = ACTIVE;
        }
        return new View(Set.copyOf(readable), Set.copyOf(writable), priority, state, cycles, now);
    }

    // ------------------------------------------------------------------------------------------------ escalations (TASK-070)

    /**
     * The list "SLA Eskalationen" of the caller: the reached stages of the groups the caller is a member of, newest first, on
     * tickets the caller may read with the read rules of the deadlines. The rights count when the list is read, not when a
     * stage was reached (acceptance criterion 2).
     */
    @Transactional
    public List<EscalationItem> escalations(AccessContext actor) {
        Set<UUID> mine = groups.groupsOf(actor.companyId(), actor.userId());
        List<SlaRepository.Escalation> rows = repository.escalationsFor(actor.companyId(), mine, ESCALATION_LIST_LIMIT);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> groupNames = groupNames(actor.companyId());
        Map<UUID, String> projectKeys = new HashMap<>();
        projects.searchSources(actor.companyId()).forEach(project -> projectKeys.put(project.projectId(), project.key()));
        Map<UUID, Boolean> readable = new HashMap<>();
        List<EscalationItem> items = new ArrayList<>();
        for (SlaRepository.Escalation row : rows) {
            boolean may = readable.computeIfAbsent(row.projectId(), projectId -> {
                Decision read = permissions.inspect(actor, resource(actor, projectId), READ, FIELDS);
                return read.allowed() && read.readableFields().containsAll(FIELDS);
            });
            if (!may) {
                continue;
            }
            tickets.state(row.ticketId()).ifPresent(state -> items.add(new EscalationItem(row.projectId(), projectKeys.getOrDefault(row.projectId(), ""),
                state.number(), row.cycleNumber(), row.cycleOutcome(), row.kind(), row.percent(), row.groupId(),
                groupNames.getOrDefault(row.groupId(), ""), row.thresholdAt(), row.reachedAt())));
        }
        return items;
    }

    private Map<UUID, String> groupNames(UUID companyId) {
        Map<UUID, String> names = new HashMap<>();
        groups.groups(companyId).forEach(group -> names.put(group.id(), group.name()));
        return names;
    }

    /**
     * Stores the stages of the open cycle of one ticket that are due at {@code now} (rule 3), under the lock of the row of the
     * ticket, so that a change of the ticket and several executors run one after the other and a stage is stored once. Returns
     * how many stages were reached.
     */
    @Transactional
    public int watchTicket(UUID ticketId, Instant now) {
        Optional<SlaRepository.TicketRow> row = repository.lockKnownTicket(ticketId);
        Optional<SlaCycle> open = row.flatMap(found -> repository.openCycle(ticketId));
        if (open.isEmpty()) {
            return 0;
        }
        SlaCycle cycle = open.get();
        List<Rule.Stage> stages = repository.ruleStages(cycle.ruleId(), cycle.ruleVersion());
        if (stages.isEmpty()) {
            return 0;
        }
        WorkCalendar calendar = calendar(cycle);
        List<SlaRepository.Escalation> stored = repository.escalationsOf(cycle.id());
        Instant at = now.truncatedTo(ChronoUnit.SECONDS);
        int reached = 0;
        for (SlaClock clock : List.of(cycle.firstResponse(), cycle.resolution())) {
            Set<Integer> done = new HashSet<>();
            stored.stream().filter(e -> e.kind().equals(clock.kind())).forEach(e -> done.add(e.stage()));
            for (Escalations.Due due : Escalations.due(clock, stages, done, at, calendar)) {
                boolean added = repository.insertEscalation(new SlaRepository.Escalation(UUID.randomUUID(), row.get().companyId(), row.get().projectId(),
                    ticketId, cycle.id(), cycle.number(), null, clock.kind(), due.stage(), due.definition().percent(), due.definition().groupId(),
                    due.thresholdAt(), at, due.skipped()));
                if (added && due.skipped()) {
                    LOG.info("Escalation stage {} of {} on ticket {} cycle {} passed over by a higher one", due.stage(), clock.kind(), ticketId, cycle.number());
                } else if (added) {
                    reached++;
                }
            }
        }
        return reached;
    }

    /** Changes the priority; the open cycle gets the budgets of the new priority (rule 13) and the history an entry. */
    @Transactional
    public View setPriority(AccessContext actor, UUID projectId, long number, String priority) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        boolean read = permissions.inspect(actor, resource, READ, Set.of()).allowed();
        boolean edit = permissions.inspect(actor, resource, EDIT, Set.of()).allowed();
        if (!read && !edit) {
            throw projectNotFound();
        }
        UUID ticket = ticket(actor, projectId, number);
        try {
            permissions.require(actor, resource, EDIT, Set.of(), Set.of(TicketAccess.PRIORITY_FIELD));
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        if (priority == null || !Rule.PRIORITIES.contains(priority)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("priority.invalid"));
        }
        tickets.lockForPriorityChange(ticket);
        SlaRepository.TicketRow row = lockWithStatus(ticket, tickets.state(ticket).orElseThrow());
        if (!row.priority().equals(priority)) {
            Instant now = now();
            repository.ticketPriority(ticket, priority);
            tickets.recordPriorityChange(ticket, now, actor.userId(), row.priority(), priority);
            repository.openCycle(ticket).ifPresent(cycle -> {
                Rule.Budgets budgets = repository.ruleBudgets(cycle.ruleId(), cycle.ruleVersion()).orElseThrow();
                repository.updateCycle(cycle.priorityChanged(priority, budgets.seconds(priority), now, calendar(cycle)));
            });
        }
        return view(actor, projectId, number);
    }

    private Supplier<ResourceContext> resource(AccessContext actor, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
    }

    private UUID ticket(AccessContext actor, UUID projectId, long number) {
        return tickets.find(actor.companyId(), projectId, number).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"));
    }

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }
}
