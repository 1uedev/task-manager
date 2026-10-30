package taskmanager.sla.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * One cycle of work on a ticket (TASK-068): from the first registration, or from a reopening, until the resolution is met
 * or the cycle is cancelled. It keeps the rule and calendar versions it began with (rule 6) and its two clocks. The
 * transitions follow the confirmed rules of TASK-068: the first response is met by the first reply the mail server accepted
 * (rule 8), the waiting for the customer pauses only the resolution (rule 10, DEC-032), the resolution needs a status of the
 * kind resolved and a sent reply (rule 11), a priority change keeps what was used (rule 13). Every operation returns a new
 * state.
 *
 * @param repliedAt the first sent reply of the cycle
 * @param resolvedSince since when the ticket is in a status of the kind resolved, while it is
 * @param outcome {@code null} while open, else {@code met} or {@code cancelled}
 */
public record SlaCycle(UUID id, UUID ticketId, int number, UUID ruleId, long ruleVersion, UUID calendarId, long calendarVersion,
        String priority, Instant startedAt, SlaClock firstResponse, SlaClock resolution, Instant repliedAt, Instant resolvedSince,
        String outcome, String reason, Instant closedAt, long version) {
    public static final String OPEN = "open";
    public static final String WAITING = "waiting";
    public static final String RESOLVED = "resolved";
    public static final String CANCELLED = "cancelled";
    public static final String MET = "met";

    /** The budgets of one priority in seconds. */
    public record Budget(long firstResponseSeconds, long resolutionSeconds) {
    }

    /** A new cycle from {@code startedAt}, in the status kind the ticket has at {@code now}. */
    public static SlaCycle start(UUID ticketId, int number, UUID ruleId, long ruleVersion, UUID calendarId, long calendarVersion, String priority,
            Budget budget, Instant startedAt, String statusKind, Instant now, WorkCalendar calendar) {
        SlaCycle cycle = new SlaCycle(UUID.randomUUID(), ticketId, number, ruleId, ruleVersion, calendarId, calendarVersion, priority, startedAt,
            SlaClock.start(SlaClock.FIRST_RESPONSE, budget.firstResponseSeconds(), startedAt, calendar),
            SlaClock.start(SlaClock.RESOLUTION, budget.resolutionSeconds(), startedAt, calendar), null, null, null, null, null, 0);
        return switch (statusKind) {
            case WAITING -> cycle.with(cycle.firstResponse, cycle.resolution.pause(later(now, startedAt), calendar));
            case RESOLVED -> cycle.resolvedFrom(later(now, startedAt));
            default -> cycle;
        };
    }

    public boolean open() {
        return outcome == null;
    }

    /** The ticket moves from one status kind to another at {@code at}. A cycle that is closed does not change. */
    public SlaCycle statusChanged(String from, String to, Instant at, WorkCalendar calendar) {
        if (!open() || from.equals(to)) {
            return this;
        }
        if (to.equals(CANCELLED)) {
            return close(CANCELLED, "status_cancelled", at, calendar);
        }
        SlaCycle cycle = this;
        if (from.equals(WAITING)) {
            cycle = cycle.with(firstResponse, resolution.resume(at, calendar));
        }
        if (from.equals(RESOLVED)) {
            cycle = cycle.withResolvedSince(null);
        }
        if (to.equals(WAITING)) {
            cycle = cycle.with(cycle.firstResponse, cycle.resolution.pause(at, calendar));
        }
        if (to.equals(RESOLVED)) {
            cycle = cycle.resolvedFrom(at);
        }
        return cycle.tryResolve(calendar);
    }

    /** A reply was accepted by the mail server at {@code sentAt}. */
    public SlaCycle replySent(Instant sentAt, WorkCalendar calendar) {
        if (!open() || repliedAt != null) {
            return this;
        }
        Instant at = later(sentAt, startedAt);
        SlaCycle cycle = new SlaCycle(id, ticketId, number, ruleId, ruleVersion, calendarId, calendarVersion, priority, startedAt,
            firstResponse.meet(at, calendar), resolution, at, resolvedSince, outcome, reason, closedAt, version);
        return cycle.tryResolve(calendar);
    }

    /** An answer of the customer joined the ticket at {@code at}: a waiting resolution runs again. */
    public SlaCycle customerAnswered(Instant at, WorkCalendar calendar) {
        if (!open() || !resolution.paused()) {
            return this;
        }
        return with(firstResponse, resolution.resume(at, calendar));
    }

    /** The priority changed: both clocks get the budgets of the new priority. */
    public SlaCycle priorityChanged(String newPriority, Budget budget, Instant at, WorkCalendar calendar) {
        if (!open()) {
            return this;
        }
        return new SlaCycle(id, ticketId, number, ruleId, ruleVersion, calendarId, calendarVersion, newPriority, startedAt,
            firstResponse.rebudget(budget.firstResponseSeconds(), at, calendar), resolution.rebudget(budget.resolutionSeconds(), at, calendar),
            repliedAt, resolvedSince, outcome, reason, closedAt, version);
    }

    /** Ends the cycle without success, for example when the customer is removed. */
    public SlaCycle close(String newOutcome, String why, Instant at, WorkCalendar calendar) {
        if (!open()) {
            return this;
        }
        return new SlaCycle(id, ticketId, number, ruleId, ruleVersion, calendarId, calendarVersion, priority, startedAt,
            firstResponse.cancel(at, calendar), resolution.cancel(at, calendar), repliedAt, resolvedSince, newOutcome, why, at, version);
    }

    private SlaCycle resolvedFrom(Instant at) {
        return withResolvedSince(at);
    }

    /** The resolution is met when the ticket is resolved and a reply was sent, at the later of both (H-09). */
    private SlaCycle tryResolve(WorkCalendar calendar) {
        if (!open() || resolvedSince == null || repliedAt == null) {
            return this;
        }
        Instant at = later(resolvedSince, repliedAt);
        return new SlaCycle(id, ticketId, number, ruleId, ruleVersion, calendarId, calendarVersion, priority, startedAt,
            firstResponse.meet(at, calendar), resolution.meet(at, calendar), repliedAt, resolvedSince, MET, null, at, version);
    }

    private SlaCycle with(SlaClock first, SlaClock second) {
        return new SlaCycle(id, ticketId, number, ruleId, ruleVersion, calendarId, calendarVersion, priority, startedAt, first, second,
            repliedAt, resolvedSince, outcome, reason, closedAt, version);
    }

    private SlaCycle withResolvedSince(Instant since) {
        return new SlaCycle(id, ticketId, number, ruleId, ruleVersion, calendarId, calendarVersion, priority, startedAt, firstResponse,
            resolution, repliedAt, since, outcome, reason, closedAt, version);
    }

    static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
