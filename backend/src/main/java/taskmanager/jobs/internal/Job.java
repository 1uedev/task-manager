package taskmanager.jobs.internal;

import java.time.Instant;
import java.util.UUID;

/** A job as the administration sees it: no payload, because it may name objects the viewer may not know (TASK-054). */
public record Job(UUID id, long seq, UUID companyId, String kind, String state, int attempts, int maxAttempts, Instant runAt,
        Instant createdAt, Instant startedAt, Instant finishedAt, String errorCode, UUID errorReference) {
}
