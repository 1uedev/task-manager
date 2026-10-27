package taskmanager.jobs.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the operation sets for the background jobs (TASK-054, DEC-083).
 *
 * @param workerEnabled whether the web process runs jobs too; a small installation leaves it on, an installation with a
 *        separate {@code worker} process may turn it off ({@code TASK_MANAGER_JOBS_WORKER=false})
 * @param workerThreads how many jobs one process runs at the same time
 * @param pollInterval how long an idle executor waits before it looks for due jobs again
 * @param lease how long a job stays reserved for its executor without a sign of life; a running executor renews it every third
 *        of this period, so only an executor that ended lets it expire
 * @param maxAttempts how often a job runs before it fails for good, unless it was created with its own number
 * @param retryDelay the delay before the second attempt; it doubles for every further attempt
 * @param retryMaxDelay the longest delay between two attempts
 * @param keepSucceeded how long a finished job stays visible
 * @param keepFailed how long a failed or cancelled job stays visible
 * @param backlogAfter from which age a due job that nobody ran counts as backlog
 * @param retentionInterval time between two runs of the clean up of old jobs
 */
@ConfigurationProperties(prefix = "taskmanager.jobs")
public record JobsProperties(
        @DefaultValue("true") boolean workerEnabled,
        @DefaultValue("2") int workerThreads,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("2m") Duration lease,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("30s") Duration retryDelay,
        @DefaultValue("1h") Duration retryMaxDelay,
        @DefaultValue("7d") Duration keepSucceeded,
        @DefaultValue("30d") Duration keepFailed,
        @DefaultValue("15m") Duration backlogAfter,
        @DefaultValue("1h") Duration retentionInterval) {
    public JobsProperties {
        if (workerThreads < 1 || workerThreads > 32) {
            throw new IllegalArgumentException("taskmanager.jobs.worker-threads must lie between 1 and 32");
        }
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("taskmanager.jobs.max-attempts must lie between 1 and 100");
        }
        if (lease.toSeconds() < 3) {
            throw new IllegalArgumentException("taskmanager.jobs.lease must be at least three seconds");
        }
    }

    /** The delay after the given failed attempt: the base doubled per attempt, at most the longest delay. */
    public Duration delayAfter(int attempt) {
        Duration delay = retryDelay;
        for (int i = 1; i < attempt && delay.compareTo(retryMaxDelay) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
    }
}
