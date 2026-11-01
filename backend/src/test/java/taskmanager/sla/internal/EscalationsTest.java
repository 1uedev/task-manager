package taskmanager.sla.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taskmanager.sla.internal.WorkCalendar.Day;
import taskmanager.sla.internal.WorkCalendar.Definition;
import taskmanager.sla.internal.WorkCalendar.Span;

/**
 * TASK-070: when the stages of a clock are due, without a database. S-13 and H-11 of docs/HELPDESK_SLA.md: Berlin, Monday to
 * Friday 09:00 to 17:00, first response 120 minutes, all dates in 2026.
 */
class EscalationsTest {
    private static final UUID SUPPORT = UUID.randomUUID();
    private static final UUID LEAD = UUID.randomUUID();
    private static final WorkCalendar STANDARD = WorkCalendar.of(new Definition("Europe/Berlin",
        List.of(1, 2, 3, 4, 5).stream().map(day -> new Day(day, List.of(new Span("09:00", "17:00")))).toList(), List.of()));
    private static final List<Rule.Stage> STAGES = List.of(
        new Rule.Stage(SlaClock.FIRST_RESPONSE, 80, SUPPORT),
        new Rule.Stage(SlaClock.FIRST_RESPONSE, 100, LEAD),
        new Rule.Stage(SlaClock.RESOLUTION, 80, SUPPORT));

    private static Instant at(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime).toInstant();
    }

    private static SlaClock started() {
        return SlaClock.start(SlaClock.FIRST_RESPONSE, 120 * 60, at("2026-10-19T09:00+02:00"), STANDARD);
    }

    @Test
    void s13TheWarningAt80PercentIsReachedAt1036AndTheDeadlineIs1100() {
        SlaClock clock = started();
        assertEquals(at("2026-10-19T11:00+02:00"), clock.dueAt());
        assertEquals(List.of(), Escalations.due(clock, STAGES, Set.of(), at("2026-10-19T10:35:59+02:00"), STANDARD), "not yet");
        List<Escalations.Due> due = Escalations.due(clock, STAGES, Set.of(), at("2026-10-19T10:36+02:00"), STANDARD);
        assertEquals(1, due.size());
        assertEquals(0, due.get(0).stage());
        assertEquals(at("2026-10-19T10:36+02:00"), due.get(0).thresholdAt());
        assertFalse(due.get(0).skipped());
        assertEquals(List.of(), Escalations.due(clock, STAGES, Set.of(0), at("2026-10-19T10:50+02:00"), STANDARD), "a stored stage never comes again");
        List<Escalations.Due> late = Escalations.due(clock, STAGES, Set.of(0), at("2026-10-19T11:00+02:00"), STANDARD);
        assertEquals(1, late.size());
        assertEquals(1, late.get(0).stage());
        assertEquals(at("2026-10-19T11:00+02:00"), late.get(0).thresholdAt());
    }

    @Test
    void h11AfterAnOutageOnlyTheHighestStageIsReachedAndTheLowerOneSkipped() {
        SlaClock clock = started();
        List<Escalations.Due> due = Escalations.due(clock, STAGES, Set.of(), at("2026-10-19T14:00+02:00"), STANDARD);
        assertEquals(2, due.size(), due.toString());
        assertEquals(0, due.get(0).stage());
        assertTrue(due.get(0).skipped(), "the warning is passed over");
        assertEquals(at("2026-10-19T10:36+02:00"), due.get(0).thresholdAt(), "the skipped stage keeps the time the clock passed it");
        assertEquals(1, due.get(1).stage());
        assertFalse(due.get(1).skipped());
        assertEquals(at("2026-10-19T11:00+02:00"), due.get(1).thresholdAt());
    }

    @Test
    void theThresholdCountsBusinessTimeOverNightAndAfterAPause() {
        SlaClock evening = SlaClock.start(SlaClock.FIRST_RESPONSE, 120 * 60, at("2026-10-19T16:30+02:00"), STANDARD);
        List<Escalations.Due> due = Escalations.due(evening, STAGES, Set.of(), at("2026-10-20T10:06+02:00"), STANDARD);
        assertEquals(1, due.size(), "30 minutes on Monday and 66 on Tuesday");
        assertEquals(at("2026-10-20T10:06+02:00"), due.get(0).thresholdAt());

        SlaClock resumed = started().pause(at("2026-10-19T10:00+02:00"), STANDARD).resume(at("2026-10-19T13:00+02:00"), STANDARD);
        List<Escalations.Due> after = Escalations.due(resumed, STAGES, Set.of(), at("2026-10-19T13:40+02:00"), STANDARD);
        assertEquals(1, after.size());
        assertEquals(at("2026-10-19T13:36+02:00"), after.get(0).thresholdAt(), "60 minutes before the pause and 36 after");
    }

    @Test
    void aPausedOrMetClockReachesNoStageAndStagesFollowTheirClock() {
        SlaClock clock = started();
        assertEquals(List.of(), Escalations.due(clock.pause(at("2026-10-19T10:00+02:00"), STANDARD), STAGES, Set.of(), at("2026-10-19T16:00+02:00"), STANDARD));
        assertEquals(List.of(), Escalations.due(clock.meet(at("2026-10-19T10:00+02:00"), STANDARD), STAGES, Set.of(), at("2026-10-19T16:00+02:00"), STANDARD));
        SlaClock resolution = SlaClock.start(SlaClock.RESOLUTION, 480 * 60, at("2026-10-19T09:00+02:00"), STANDARD);
        List<Escalations.Due> due = Escalations.due(resolution, STAGES, Set.of(), at("2026-10-20T09:00+02:00"), STANDARD);
        assertEquals(1, due.size());
        assertEquals(2, due.get(0).stage(), "only the stage of the resolution clock");
        assertEquals(at("2026-10-19T15:24+02:00"), due.get(0).thresholdAt());
    }

    @Test
    void stagesOfTheSameShareForTwoGroupsAreBothReachedAndTheThresholdRoundsUp() {
        List<Rule.Stage> stages = List.of(new Rule.Stage(SlaClock.FIRST_RESPONSE, 50, SUPPORT), new Rule.Stage(SlaClock.FIRST_RESPONSE, 50, LEAD),
            new Rule.Stage(SlaClock.FIRST_RESPONSE, 10, LEAD));
        List<Escalations.Due> due = Escalations.due(started(), stages, Set.of(), at("2026-10-19T10:00+02:00"), STANDARD);
        assertEquals(List.of(2, 0, 1), due.stream().map(Escalations.Due::stage).toList());
        assertEquals(List.of(true, false, false), due.stream().map(Escalations.Due::skipped).toList());
        assertEquals(1, new Rule.Stage(SlaClock.FIRST_RESPONSE, 1, SUPPORT).thresholdSeconds(1), "never zero");
        assertEquals(97, new Rule.Stage(SlaClock.FIRST_RESPONSE, 80, SUPPORT).thresholdSeconds(121));
    }

    @Test
    void stagesAreChecked() {
        Set<UUID> groups = Set.of(SUPPORT);
        assertEquals(List.of(), Rule.Stage.violations(List.of(new Rule.Stage(SlaClock.FIRST_RESPONSE, 80, SUPPORT)), groups));
        assertEquals(List.of("escalations.invalid"), Rule.Stage.violations(List.of(new Rule.Stage("later", 80, SUPPORT)), groups));
        assertEquals(List.of("escalations.invalid"), Rule.Stage.violations(List.of(new Rule.Stage(SlaClock.RESOLUTION, 0, SUPPORT)), groups));
        assertEquals(List.of("escalations.group_unknown"), Rule.Stage.violations(List.of(new Rule.Stage(SlaClock.RESOLUTION, 80, LEAD)), groups));
        assertEquals(List.of("escalations.duplicate"), Rule.Stage.violations(List.of(new Rule.Stage(SlaClock.RESOLUTION, 80, SUPPORT),
            new Rule.Stage(SlaClock.RESOLUTION, 80, SUPPORT)), groups));
    }
    @Test
    void storedAtThePauseTheStagesKeepTheTimesTheClockPassedThem() {
        // TASK-071, F1: the review case, Monday from 09:00, pause at 11:00. The change stores what is due at 11:00 before the pause.
        SlaClock clock = started();
        List<Escalations.Due> due = Escalations.due(clock, STAGES, Set.of(), at("2026-10-19T11:00+02:00"), STANDARD);
        assertEquals(List.of(at("2026-10-19T10:36+02:00"), at("2026-10-19T11:00+02:00")), due.stream().map(Escalations.Due::thresholdAt).toList());
        SlaClock resumed = clock.pause(at("2026-10-19T11:00+02:00"), STANDARD).resume(at("2026-10-19T13:00+02:00"), STANDARD);
        assertEquals(List.of(), Escalations.due(resumed, STAGES, Set.of(0, 1), at("2026-10-19T13:01+02:00"), STANDARD), "nothing left after the resume");
    }

    @Test
    void aSmallerBudgetMakesAStageDueAtTheChangeAndAnEarlierTimeCountsAsTheCheckpoint() {
        SlaClock clock = started().rebudget(15 * 60, at("2026-10-19T09:20+02:00"), STANDARD);
        List<Escalations.Due> due = Escalations.due(clock, STAGES, Set.of(), at("2026-10-19T09:21+02:00"), STANDARD);
        assertEquals(List.of(true, false), due.stream().map(Escalations.Due::skipped).toList(), "20 of 15 minutes: 100 % reached, 80 % skipped");
        assertEquals(at("2026-10-19T09:20+02:00"), due.get(1).thresholdAt(), "due from the change of the budget on");
        assertEquals(List.of(), Escalations.due(started(), STAGES, Set.of(), at("2026-10-19T08:00+02:00"), STANDARD), "a time before the checkpoint uses it");
    }
}
