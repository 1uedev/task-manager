package taskmanager.sla.internal;

import java.time.Instant;

/**
 * One deadline of a cycle (TASK-068): the first response or the resolution. It counts the open time of its calendar from
 * {@code checkpoint} on while it runs; {@code consumed} is what it used before. A paused clock has no due time; a clock
 * that is met or cancelled no longer changes. {@code breachedAt} records a breach that is already certain, so that a
 * later pause or a later answer does not remove it (H-10); a running clock past its due time is breached as well.
 * Every operation returns a new state and leaves this one as it is.
 */
public record SlaClock(String kind, String state, long budgetSeconds, long consumedSeconds, Instant checkpoint, Instant dueAt,
        Instant pausedAt, Instant metAt, Instant breachedAt) {
    public static final String FIRST_RESPONSE = "first_response";
    public static final String RESOLUTION = "resolution";
    public static final String RUNNING = "running";
    public static final String PAUSED = "paused";
    public static final String MET = "met";
    public static final String CANCELLED = "cancelled";

    /** A clock that starts at {@code at} with its whole budget. */
    public static SlaClock start(String kind, long budgetSeconds, Instant at, WorkCalendar calendar) {
        return new SlaClock(kind, RUNNING, budgetSeconds, 0, at, calendar.add(at, budgetSeconds), null, null, null);
    }

    public boolean running() {
        return RUNNING.equals(state);
    }

    public boolean paused() {
        return PAUSED.equals(state);
    }

    /** Whether the clock still counts or waits: neither met nor cancelled. */
    public boolean open() {
        return running() || paused();
    }

    /** The open time used up to {@code at}. */
    public long consumedAt(Instant at, WorkCalendar calendar) {
        return running() ? consumedSeconds + calendar.between(checkpoint, at) : consumedSeconds;
    }

    /** The open time left at {@code at}; negative when the budget is exceeded. */
    public long remainingAt(Instant at, WorkCalendar calendar) {
        return budgetSeconds - consumedAt(at, calendar);
    }

    /** Whether the deadline is missed as seen at {@code at}: an answer exactly at the due time is in time. */
    public boolean breachedAt(Instant at) {
        if (breachedAt != null) {
            return true;
        }
        return running() && dueAt != null && at.isAfter(dueAt);
    }

    /** The waiting for the customer starts (DEC-032); only a running clock pauses. A breach that already happened stays. */
    public SlaClock pause(Instant at, WorkCalendar calendar) {
        if (!running()) {
            return this;
        }
        Instant breach = breachedAt != null ? breachedAt : at.isAfter(dueAt) ? dueAt : null;
        return new SlaClock(kind, PAUSED, budgetSeconds, consumedAt(at, calendar), at, null, at, null, breach);
    }

    /** The waiting ends; the rest of the budget runs from {@code at}, which is never before the start of the pause. */
    public SlaClock resume(Instant at, WorkCalendar calendar) {
        if (!paused()) {
            return this;
        }
        Instant from = at.isBefore(pausedAt) ? pausedAt : at;
        long rest = Math.max(0, budgetSeconds - consumedSeconds);
        return new SlaClock(kind, RUNNING, budgetSeconds, consumedSeconds, from, calendar.add(from, rest), null, null, breachedAt);
    }

    /** The deadline is met at {@code at}; when that is after the due time, the clock is met late and stays breached. */
    public SlaClock meet(Instant at, WorkCalendar calendar) {
        if (!open()) {
            return this;
        }
        Instant met = running() && at.isBefore(checkpoint) ? checkpoint : at;
        Instant breach = breachedAt != null ? breachedAt : running() && met.isAfter(dueAt) ? dueAt : null;
        return new SlaClock(kind, MET, budgetSeconds, consumedAt(met, calendar), running() ? met : checkpoint, dueAt, null, met, breach);
    }

    /** The cycle ends without success (status of the kind cancelled, customer removed). */
    public SlaClock cancel(Instant at, WorkCalendar calendar) {
        if (!open()) {
            return this;
        }
        Instant breach = breachedAt != null ? breachedAt : running() && at.isAfter(dueAt) ? dueAt : null;
        return new SlaClock(kind, CANCELLED, budgetSeconds, consumedAt(at, calendar), at, dueAt, null, null, breach);
    }

    /**
     * A new budget, for example after a change of the priority (S-09): what was used stays used. When the new budget is
     * already exceeded, the clock is breached from {@code at} on; a clock that is met or cancelled keeps its outcome.
     */
    public SlaClock rebudget(long newBudgetSeconds, Instant at, WorkCalendar calendar) {
        if (!open()) {
            return this;
        }
        long consumed = consumedAt(at, calendar);
        long rest = newBudgetSeconds - consumed;
        Instant breach = breachedAt;
        if (breach == null && running() && at.isAfter(dueAt)) {
            breach = dueAt;
        }
        if (breach == null && rest < 0) {
            breach = at;
        }
        if (paused()) {
            return new SlaClock(kind, PAUSED, newBudgetSeconds, consumed, at, null, pausedAt, null, breach);
        }
        return new SlaClock(kind, RUNNING, newBudgetSeconds, consumed, at, calendar.add(at, Math.max(0, rest)), null, null, breach);
    }
}
