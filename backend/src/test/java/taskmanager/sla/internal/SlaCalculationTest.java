package taskmanager.sla.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taskmanager.sla.internal.WorkCalendar.Day;
import taskmanager.sla.internal.WorkCalendar.Definition;
import taskmanager.sla.internal.WorkCalendar.Span;

/**
 * TASK-068: the examples S-01 to S-14 and H-09, H-10 and H-12 of docs/HELPDESK_SLA.md, and the resolution of skipped and
 * repeated local times, against the calendar and the cycle without a database. Unless an example says otherwise: Berlin,
 * Monday to Friday 09:00 to 17:00, first response 120 minutes, resolution 480 minutes, all dates in 2026.
 */
class SlaCalculationTest {
    private static final long FIRST = 120 * 60;
    private static final long RESOLUTION = 480 * 60;
    private static final SlaCycle.Budget NORMAL = new SlaCycle.Budget(FIRST, RESOLUTION);

    private static WorkCalendar weekdays(List<WorkCalendar.Exception> exceptions, Span... hours) {
        List<Day> week = List.of(1, 2, 3, 4, 5).stream().map(day -> new Day(day, List.of(hours))).toList();
        return WorkCalendar.of(new Definition("Europe/Berlin", week, exceptions));
    }

    private static WorkCalendar standard() {
        return weekdays(List.of(), new Span("09:00", "17:00"));
    }

    private static WorkCalendar sundayService() {
        return WorkCalendar.of(new Definition("Europe/Berlin", List.of(new Day(7, List.of(new Span("01:00", "06:00")))), List.of()));
    }

    private static Instant at(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime).toInstant();
    }

    private static SlaCycle cycle(Instant start, WorkCalendar calendar) {
        return SlaCycle.start(UUID.randomUUID(), 1, UUID.randomUUID(), 1, UUID.randomUUID(), 1, "normal", NORMAL, start, SlaCycle.OPEN, start, calendar);
    }

    @Test
    void s01NormalCase() {
        SlaCycle cycle = cycle(at("2026-10-19T10:30+02:00"), standard());
        assertEquals(at("2026-10-19T12:30+02:00"), cycle.firstResponse().dueAt());
        assertEquals(at("2026-10-20T10:30+02:00"), cycle.resolution().dueAt());
    }

    @Test
    void s02Weekend() {
        SlaCycle cycle = cycle(at("2026-10-16T16:30+02:00"), standard());
        assertEquals(at("2026-10-19T10:30+02:00"), cycle.firstResponse().dueAt());
        assertEquals(at("2026-10-19T16:30+02:00"), cycle.resolution().dueAt());
    }

    @Test
    void s03OutsideBusinessHours() {
        SlaCycle cycle = cycle(at("2026-10-16T18:00+02:00"), standard());
        assertEquals(at("2026-10-19T11:00+02:00"), cycle.firstResponse().dueAt());
        assertEquals(at("2026-10-19T17:00+02:00"), cycle.resolution().dueAt());
    }

    @Test
    void s04ClosedDay() {
        WorkCalendar calendar = weekdays(List.of(new WorkCalendar.Exception("2026-10-21", List.of())), new Span("09:00", "17:00"));
        SlaCycle cycle = cycle(at("2026-10-20T16:00+02:00"), calendar);
        assertEquals(at("2026-10-22T10:00+02:00"), cycle.firstResponse().dueAt());
        assertEquals(at("2026-10-22T16:00+02:00"), cycle.resolution().dueAt());
    }

    @Test
    void s05WinterTimeOverTheWeekend() {
        SlaCycle cycle = cycle(at("2026-10-23T16:30+02:00"), standard());
        assertEquals(at("2026-10-26T10:30+01:00"), cycle.firstResponse().dueAt());
        assertEquals(Instant.parse("2026-10-26T09:30:00Z"), cycle.firstResponse().dueAt());
    }

    @Test
    void s06RepeatedHourOfASundayService() {
        SlaCycle cycle = cycle(at("2026-10-25T01:30+02:00"), sundayService());
        assertEquals(at("2026-10-25T02:30+01:00"), cycle.firstResponse().dueAt(), "the second 02:30");
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), cycle.firstResponse().dueAt());
    }

    @Test
    void s07MissingHourOfASundayService() {
        SlaCycle cycle = cycle(at("2026-03-29T01:30+01:00"), sundayService());
        assertEquals(at("2026-03-29T04:30+02:00"), cycle.firstResponse().dueAt());
        assertEquals(Instant.parse("2026-03-29T02:30:00Z"), cycle.firstResponse().dueAt());
    }

    @Test
    void s08CustomerPausePausesOnlyTheResolution() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = cycle(at("2026-10-19T10:00+02:00"), calendar)
            .statusChanged(SlaCycle.OPEN, SlaCycle.WAITING, at("2026-10-19T11:00+02:00"), calendar);
        assertTrue(cycle.resolution().paused());
        assertNull(cycle.resolution().dueAt(), "no due time while the customer is waited for");
        assertEquals(420 * 60, cycle.resolution().remainingAt(at("2026-10-20T09:30+02:00"), calendar));
        assertTrue(cycle.firstResponse().running(), "DEC-032: an open first response keeps running");
        assertEquals(at("2026-10-19T12:00+02:00"), cycle.firstResponse().dueAt());
        cycle = cycle.statusChanged(SlaCycle.WAITING, SlaCycle.OPEN, at("2026-10-20T10:00+02:00"), calendar);
        assertEquals(at("2026-10-20T17:00+02:00"), cycle.resolution().dueAt());
    }

    @Test
    void s08AnAnswerOfTheCustomerEndsThePauseAndASupportReplyDoesNot() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = cycle(at("2026-10-19T10:00+02:00"), calendar)
            .statusChanged(SlaCycle.OPEN, SlaCycle.WAITING, at("2026-10-19T11:00+02:00"), calendar)
            .replySent(at("2026-10-19T11:05+02:00"), calendar);
        assertTrue(cycle.resolution().paused(), "a reply of the support does not end the waiting");
        assertEquals(SlaClock.MET, cycle.firstResponse().state());
        cycle = cycle.customerAnswered(at("2026-10-20T10:00+02:00"), calendar);
        assertTrue(cycle.resolution().running());
        assertEquals(at("2026-10-20T17:00+02:00"), cycle.resolution().dueAt());
        assertEquals(cycle.resolution(), cycle.statusChanged(SlaCycle.WAITING, SlaCycle.OPEN, at("2026-10-20T10:30+02:00"), calendar).resolution(),
            "leaving the waiting status later changes nothing");
    }

    @Test
    void s09HigherPriorityKeepsWhatWasUsed() {
        WorkCalendar calendar = standard();
        Instant change = at("2026-10-19T11:00+02:00");
        SlaCycle cycle = cycle(at("2026-10-19T09:00+02:00"), calendar).priorityChanged("high", new SlaCycle.Budget(60 * 60, 240 * 60), change, calendar);
        assertTrue(cycle.firstResponse().breachedAt(change), "120 used of a new budget of 60");
        assertEquals(change, cycle.firstResponse().breachedAt());
        assertEquals(at("2026-10-19T13:00+02:00"), cycle.resolution().dueAt(), "120 used, 120 left");
        assertEquals("high", cycle.priority());
    }

    @Test
    void s10ReopeningStartsANewCycleWithItsOwnBudget() {
        WorkCalendar calendar = standard();
        SlaCycle first = cycle(at("2026-10-19T09:00+02:00"), calendar)
            .replySent(at("2026-10-19T10:30+02:00"), calendar)
            .statusChanged(SlaCycle.OPEN, SlaCycle.RESOLVED, at("2026-10-19T11:00+02:00"), calendar);
        assertEquals(SlaCycle.MET, first.outcome());
        assertEquals(at("2026-10-19T11:00+02:00"), first.resolution().metAt());
        SlaCycle second = SlaCycle.start(first.ticketId(), 2, first.ruleId(), 1, first.calendarId(), 1, "normal", NORMAL,
            at("2026-10-19T14:00+02:00"), SlaCycle.OPEN, at("2026-10-19T14:00+02:00"), calendar);
        assertEquals(at("2026-10-19T16:00+02:00"), second.firstResponse().dueAt());
        assertEquals(at("2026-10-20T14:00+02:00"), second.resolution().dueAt());
    }

    @Test
    void s11ExactlyAtTheClosingTimeAndAnAnswerExactlyAtTheDueTimeIsInTime() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = cycle(at("2026-10-16T17:00+02:00"), calendar);
        assertEquals(at("2026-10-19T11:00+02:00"), cycle.firstResponse().dueAt());
        SlaCycle answered = cycle.replySent(at("2026-10-19T11:00+02:00"), calendar);
        assertEquals(SlaClock.MET, answered.firstResponse().state());
        assertNull(answered.firstResponse().breachedAt());
        assertFalse(answered.firstResponse().breachedAt(at("2026-10-19T12:00+02:00")));
        SlaCycle late = cycle.replySent(at("2026-10-19T11:00:01+02:00"), calendar);
        assertEquals(at("2026-10-19T11:00+02:00"), late.firstResponse().breachedAt(), "one second later is late");
    }

    @Test
    void s12LateAssignmentKeepsTheFirstRegistrationAsStart() {
        WorkCalendar calendar = standard();
        Instant registered = at("2026-10-20T09:00+02:00");
        Instant released = at("2026-10-20T12:00+02:00");
        SlaCycle cycle = SlaCycle.start(UUID.randomUUID(), 1, UUID.randomUUID(), 1, UUID.randomUUID(), 1, "normal", NORMAL, registered,
            SlaCycle.OPEN, released, calendar);
        assertEquals(at("2026-10-20T11:00+02:00"), cycle.firstResponse().dueAt());
        assertTrue(cycle.firstResponse().breachedAt(released), "already missed when the mail is released");
        assertEquals(at("2026-10-20T17:00+02:00"), cycle.resolution().dueAt());
    }

    @Test
    void s13WarningAtEightyPercent() {
        WorkCalendar calendar = standard();
        Instant start = at("2026-10-19T09:00+02:00");
        assertEquals(at("2026-10-19T10:36+02:00"), calendar.add(start, FIRST * 80 / 100));
        assertEquals(at("2026-10-19T11:00+02:00"), cycle(start, calendar).firstResponse().dueAt());
    }

    @Test
    void s14LunchBreak() {
        WorkCalendar calendar = weekdays(List.of(), new Span("09:00", "12:00"), new Span("13:00", "17:00"));
        assertEquals(at("2026-10-19T14:30+02:00"), cycle(at("2026-10-19T11:30+02:00"), calendar).firstResponse().dueAt());
    }

    @Test
    void h09TheResolvedStatusAloneDoesNotMeetTheResolution() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = cycle(at("2026-10-19T09:00+02:00"), calendar)
            .statusChanged(SlaCycle.OPEN, SlaCycle.RESOLVED, at("2026-10-19T10:00+02:00"), calendar);
        assertTrue(cycle.open());
        assertTrue(cycle.resolution().running());
        cycle = cycle.replySent(at("2026-10-19T12:00+02:00"), calendar);
        assertEquals(SlaCycle.MET, cycle.outcome());
        assertEquals(at("2026-10-19T12:00+02:00"), cycle.resolution().metAt(), "the later of status and reply");
        assertEquals(SlaClock.MET, cycle.firstResponse().state());
    }

    @Test
    void h10APauseAfterTheDueTimeKeepsTheBreach() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = cycle(at("2026-10-19T09:00+02:00"), calendar)
            .statusChanged(SlaCycle.OPEN, SlaCycle.WAITING, at("2026-10-20T10:00+02:00"), calendar);
        assertEquals(at("2026-10-19T17:00+02:00"), cycle.resolution().breachedAt());
        assertTrue(cycle.resolution().breachedAt(at("2026-10-20T10:00+02:00")));
        cycle = cycle.customerAnswered(at("2026-10-21T10:00+02:00"), calendar);
        assertEquals(at("2026-10-19T17:00+02:00"), cycle.resolution().breachedAt(), "resuming does not remove it");
    }

    @Test
    void h10APauseBeforeTheDueTimeIsNoBreachEvenWhenTheOldDueTimePasses() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = cycle(at("2026-10-19T09:00+02:00"), calendar)
            .statusChanged(SlaCycle.OPEN, SlaCycle.WAITING, at("2026-10-19T16:00+02:00"), calendar);
        assertFalse(cycle.resolution().breachedAt(at("2026-10-26T09:00+01:00")));
    }

    @Test
    void h12ACycleKeepsItsCalendarVersion() {
        WorkCalendar old = standard();
        WorkCalendar changed = weekdays(List.of(), new Span("08:00", "18:00"));
        SlaCycle cycle = cycle(at("2026-10-19T09:00+02:00"), old);
        assertEquals(at("2026-10-19T11:00+02:00"), cycle.firstResponse().dueAt());
        assertEquals(at("2026-10-19T10:00+02:00"), changed.add(at("2026-10-19T08:00+02:00"), FIRST), "the new version computes differently");
        SlaCycle answered = cycle.replySent(at("2026-10-19T10:59+02:00"), old);
        assertNull(answered.firstResponse().breachedAt());
    }

    @Test
    void aCancelledStatusEndsTheCycleWithoutSuccess() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = cycle(at("2026-10-19T09:00+02:00"), calendar)
            .statusChanged(SlaCycle.OPEN, SlaCycle.CANCELLED, at("2026-10-19T10:00+02:00"), calendar);
        assertEquals(SlaCycle.CANCELLED, cycle.outcome());
        assertEquals(SlaClock.CANCELLED, cycle.firstResponse().state());
        assertEquals(SlaClock.CANCELLED, cycle.resolution().state());
        assertEquals(cycle, cycle.replySent(at("2026-10-19T10:30+02:00"), calendar), "a closed cycle does not change");
    }

    @Test
    void aStartInTheWaitingStatusPausesTheResolutionAtOnce() {
        WorkCalendar calendar = standard();
        SlaCycle cycle = SlaCycle.start(UUID.randomUUID(), 1, UUID.randomUUID(), 1, UUID.randomUUID(), 1, "normal", NORMAL,
            at("2026-10-19T09:00+02:00"), SlaCycle.WAITING, at("2026-10-19T10:00+02:00"), calendar);
        assertTrue(cycle.resolution().paused());
        assertEquals(60 * 60, cycle.resolution().consumedSeconds());
    }

    @Test
    void skippedAndRepeatedBoundariesResolveAsDocumented() {
        // Hours that start at 02:30 on the day the clock jumps from 02:00 to 03:00 begin with the jump, at 03:00.
        WorkCalendar spring = WorkCalendar.of(new Definition("Europe/Berlin", List.of(new Day(7, List.of(new Span("02:30", "04:00")))), List.of()));
        assertEquals(at("2026-03-29T03:00+02:00"), spring.add(at("2026-03-29T00:00+01:00"), 1).minusSeconds(1));
        assertEquals(60 * 60, spring.between(at("2026-03-29T00:00+01:00"), at("2026-03-29T05:00+02:00")));
        // On the day 03:00 goes back to 02:00, hours from 02:30 begin at the first 02:30 and hours to 02:30 end at the second.
        WorkCalendar autumnStart = WorkCalendar.of(new Definition("Europe/Berlin", List.of(new Day(7, List.of(new Span("02:30", "04:00")))), List.of()));
        assertEquals(at("2026-10-25T02:30+02:00"), autumnStart.add(at("2026-10-25T00:00+02:00"), 1).minusSeconds(1));
        WorkCalendar autumnEnd = WorkCalendar.of(new Definition("Europe/Berlin", List.of(new Day(7, List.of(new Span("01:00", "02:30")))), List.of()));
        assertEquals(150 * 60, autumnEnd.between(at("2026-10-25T00:00+02:00"), at("2026-10-25T06:00+01:00")),
            "01:00 to the second 02:30 is two and a half real hours");
    }

    @Test
    void overlappingHoursCountOnceAndMidnightClosesTheDay() {
        WorkCalendar calendar = weekdays(List.of(), new Span("09:00", "13:00"), new Span("12:00", "17:00"));
        assertEquals(480 * 60, calendar.between(at("2026-10-19T00:00+02:00"), at("2026-10-20T00:00+02:00")));
        WorkCalendar late = weekdays(List.of(), new Span("20:00", "24:00"));
        assertEquals(at("2026-10-20T00:00+02:00"), late.add(at("2026-10-19T20:00+02:00"), 4 * 3600));
    }

    @Test
    void invalidDefinitionsAreRefusedWithStableCodes() {
        assertEquals(List.of("timeZone.invalid", "week.closed"), WorkCalendar.violations(new Definition("Mars/Olympus", List.of(), List.of())));
        assertEquals(List.of("week.hours_invalid", "week.closed"),
            WorkCalendar.violations(new Definition("Europe/Berlin", List.of(new Day(1, List.of(new Span("17:00", "09:00")))), List.of())));
        assertEquals(List.of("week.day_duplicate", "exceptions.date_invalid"), WorkCalendar.violations(new Definition("Europe/Berlin",
            List.of(new Day(1, List.of(new Span("09:00", "17:00"))), new Day(1, List.of())), List.of(new WorkCalendar.Exception("2026-02-30", List.of())))));
        assertThrows(IllegalArgumentException.class, () -> WorkCalendar.of(new Definition("UTC", List.of(), List.of())));
    }
}
