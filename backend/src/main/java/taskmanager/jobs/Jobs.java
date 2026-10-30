package taskmanager.jobs;

import java.util.UUID;

/**
 * Port through which a module hands work to the background (TASK-054, DEC-083). The job is written in the transaction of the
 * caller, which must exist: if the change of the caller rolls back, there is no job, and if it commits, the job is there even
 * when the process ends right afterwards (outbox). An executor runs it later through the {@link JobHandler} of its kind.
 */
public interface Jobs {
    /**
     * Needs the current transaction. Returns the identifier of the new job, or of the existing one when a job of the same kind
     * and company already carries the {@link NewJob#dedupKey() key}; that one is not changed.
     *
     * @throws IllegalArgumentException when the kind, the key, the payload or the number of attempts is not acceptable
     */
    UUID enqueue(NewJob job);

    /**
     * Whether the job still waits or runs, so that an executor may still run it. A job that ended in any way, or that the
     * clean up removed, is not active; only a retry of the administration makes an ended job wait again.
     */
    boolean active(UUID id);
}
