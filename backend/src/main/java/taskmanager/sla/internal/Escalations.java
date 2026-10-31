package taskmanager.sla.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which escalation stages of one clock are due (TASK-070). Only a running clock reaches a stage, so a pause or a met clock
 * before the threshold produces none (acceptance criterion 3). Of the stages that are due and not yet stored, only the one
 * with the highest share is reached; the lower ones are skipped (rule 3, H-11): after an outage nobody gets a series of old
 * warnings, and a stage once reached or skipped never comes again.
 */
final class Escalations {
    private Escalations() {
    }

    /** A stage that is due, with its position in the stages of the rule version and the time the clock passed it. */
    record Due(int stage, Rule.Stage definition, Instant thresholdAt, boolean skipped) {
    }

    /**
     * The stages of {@code stages} for the clock that are due at {@code now} and not in {@code stored} (positions already reached
     * or skipped); the reached one last.
     */
    static List<Due> due(SlaClock clock, List<Rule.Stage> stages, Set<Integer> stored, Instant now, WorkCalendar calendar) {
        if (!clock.running()) {
            return List.of();
        }
        long consumed = clock.consumedAt(now, calendar);
        List<Due> due = new ArrayList<>();
        int highest = -1;
        for (int position = 0; position < stages.size(); position++) {
            Rule.Stage stage = stages.get(position);
            if (!clock.kind().equals(stage.clock()) || stored.contains(position)) {
                continue;
            }
            long threshold = stage.thresholdSeconds(clock.budgetSeconds());
            if (threshold > consumed) {
                continue;
            }
            Instant at = threshold <= clock.consumedSeconds() ? clock.checkpoint() : calendar.add(clock.checkpoint(), threshold - clock.consumedSeconds());
            due.add(new Due(position, stage, at, true));
            if (highest < 0 || stage.percent() > due.get(highest).definition().percent()) {
                highest = due.size() - 1;
            }
        }
        if (highest < 0) {
            return List.of();
        }
        // Every stage of the highest share is reached, one per group; the lower ones are skipped.
        int top = due.get(highest).definition().percent();
        List<Due> result = new ArrayList<>();
        for (Due item : due) {
            if (item.definition().percent() != top) {
                result.add(item);
            }
        }
        for (Due item : due) {
            if (item.definition().percent() == top) {
                result.add(new Due(item.stage(), item.definition(), item.thresholdAt(), false));
            }
        }
        return result;
    }
}
