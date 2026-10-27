package taskmanager.jobs;

import java.util.Map;
import java.util.UUID;

/**
 * One run of a job, as its handler sees it (TASK-054). The identifier stays the same over every attempt, so a handler that
 * records it with its effect recognizes a run whose outcome was lost (the process ended after the effect but before the job
 * was marked done) and does not produce the effect a second time (TECHNICAL_CONTRACTS.md C12).
 *
 * @param id the identifier of the job, the same in every attempt
 * @param companyId the company the job works for, or {@code null} for a task of the installation
 * @param kind the kind of the job
 * @param payload the references the job was created with
 * @param attempt the number of this attempt, from 1; above 1 an earlier attempt failed or did not report back
 * @param maxAttempts the last attempt
 */
public record JobExecution(UUID id, UUID companyId, String kind, Map<String, String> payload, int attempt, int maxAttempts) {
    public JobExecution {
        payload = Map.copyOf(payload);
    }

    /** Whether an earlier attempt of the same job ran, so its effect may already exist. */
    public boolean repeated() {
        return attempt > 1;
    }
}
