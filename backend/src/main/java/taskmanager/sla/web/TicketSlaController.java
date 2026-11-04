package taskmanager.sla.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.permissions.AccessContext;
import taskmanager.sla.internal.SlaClock;
import taskmanager.sla.internal.SlaCycle;
import taskmanager.sla.internal.TicketSla;

/**
 * The priority and the deadlines of a ticket (TASK-068). The priority needs the read rule of {@code priority}; the
 * deadlines also need the read rule of {@code customer}, since a ticket has deadlines only with a customer. {@code readable}
 * and {@code writable} name the fields, so an interface offers exactly what the server allows.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tickets/{number}/sla")
class TicketSlaController {
    private final TicketSla service;
    private final SlaRequests requests;

    TicketSlaController(TicketSla service, SlaRequests requests) {
        this.service = service;
        this.requests = requests;
    }

    record ClockBody(String kind, String state, boolean breached, long budgetSeconds, long remainingSeconds, Instant dueAt, Instant pausedAt,
            Instant metAt, Instant breachedAt) {
        static ClockBody of(SlaClock clock, long remaining, Instant now) {
            return new ClockBody(clock.kind(), clock.state(), clock.breachedAt(now), clock.budgetSeconds(), remaining, clock.dueAt(), clock.pausedAt(),
                clock.metAt(), clock.breachedAt() != null ? clock.breachedAt() : clock.breachedAt(now) ? clock.dueAt() : null);
        }
    }

    record CycleBody(int number, UUID ruleId, String ruleName, long ruleVersion, UUID calendarId, String calendarName, long calendarVersion,
            String timeZone, String priority, Instant startedAt, String outcome, String reason, Instant closedAt, ClockBody firstResponse,
            ClockBody resolution, List<TicketSla.StageView> escalations) {
        static CycleBody of(TicketSla.CycleView view, Instant now) {
            SlaCycle c = view.cycle();
            return new CycleBody(c.number(), c.ruleId(), view.ruleName(), c.ruleVersion(), c.calendarId(), view.calendarName(), c.calendarVersion(),
                view.timeZone(), c.priority(), c.startedAt(), c.outcome(), c.reason(), c.closedAt(),
                ClockBody.of(c.firstResponse(), view.firstResponseRemaining(), now), ClockBody.of(c.resolution(), view.resolutionRemaining(), now),
                view.escalations());
        }
    }

    record ViewBody(List<String> readable, List<String> writable, String priority, String state, List<CycleBody> cycles, Instant now) {
        static ViewBody of(TicketSla.View view) {
            Instant now = view.now();
            return new ViewBody(view.readable().stream().sorted().toList(), view.writable().stream().sorted().toList(), view.priority(), view.state(),
                view.state() == null ? null : view.cycles().stream().map(cycle -> CycleBody.of(cycle, now)).toList(), now);
        }
    }

    record PriorityRequest(String priority) {
    }

    @GetMapping
    ViewBody view(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        return ViewBody.of(SlaRequests.retrying(() -> service.view(actor, projectId, number)));
    }

    @PutMapping("/priority")
    ViewBody priority(@PathVariable UUID projectId, @PathVariable long number, @RequestBody(required = false) PriorityRequest body) {
        AccessContext actor = requests.actor();
        String priority = body == null ? null : body.priority();
        return ViewBody.of(SlaRequests.retrying(() -> service.setPriority(actor, projectId, number, priority)));
    }
}
