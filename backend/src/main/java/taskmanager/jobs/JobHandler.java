package taskmanager.jobs;

import java.time.Duration;

/**
 * Runs the jobs of one kind (TASK-054). A module registers its handler as a bean. There is no service account behind a job:
 * the handler gets the company and the references of the job and nothing else, so it can only do what its own module offers
 * for that company; a job that acts for a person checks that person's current rights itself when it runs.
 *
 * <p>The handler runs outside any transaction of the executor and opens its own. It ends normally when the job is done.
 * An exception lets the job run again after a growing delay until the last attempt; a {@link JobFailure} with
 * {@code permanent} ends it at once. When the {@link #timeout()} is over, the executor interrupts the thread.
 */
public interface JobHandler {
    /** The kind this handler runs; it must be unique in the application. */
    String kind();

    /** How long one attempt may take before the executor interrupts it. */
    default Duration timeout() {
        return Duration.ofMinutes(5);
    }

    void handle(JobExecution execution) throws Exception;
}
