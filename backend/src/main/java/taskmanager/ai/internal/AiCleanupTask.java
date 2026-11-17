package taskmanager.ai.internal;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import taskmanager.jobs.RecurringTask;

/**
 * Deletes the suggestions older than 24 hours and ends executions that no process finishes any more (TASK-125, rule 12), as a
 * recurring task of the job executor every 15 minutes, once per interval however many processes run.
 */
@Component
@ConditionalOnExpression("'${taskmanager.process:web}' != 'command'")
class AiCleanupTask implements RecurringTask {
    private final RunService runs;

    AiCleanupTask(RunService runs) {
        this.runs = runs;
    }

    @Override
    public String name() {
        return "ai.cleanup";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(15);
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(5);
    }

    @Override
    public void run() {
        runs.cleanUp();
    }
}
