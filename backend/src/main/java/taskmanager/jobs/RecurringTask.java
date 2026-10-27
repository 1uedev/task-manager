package taskmanager.jobs;

import java.time.Duration;

/**
 * A task of the installation that runs at a fixed interval (TASK-054), such as a clean up. A module registers it as a bean.
 * Each due time is put in the job table once, even with several processes; the run is then an ordinary job of the kind
 * {@code recurring.<name>} with one attempt, because the next interval tries again anyway. While a run is still waiting or
 * running, a due time adds no second one. A process that was down does not catch up on the runs it missed: the next run
 * follows when an executor is back.
 */
public interface RecurringTask {
    /** The name, lower case words separated by dots, unique in the application; the job kind is {@code recurring.<name>}. */
    String name();

    /** The time between two runs. */
    Duration interval();

    /** How long one run may take before the executor interrupts it. */
    default Duration timeout() {
        return Duration.ofMinutes(30);
    }

    void run() throws Exception;
}
