package taskmanager.jobs.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.jobs.Jobs;
import taskmanager.jobs.NewJob;

/** Creates jobs in the transaction of the caller (TASK-054, DEC-083). */
@Service
class JobService implements Jobs {
    static final Pattern KIND = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    static final int KIND_MAX = 64;
    private static final Pattern PAYLOAD_KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,39}");
    private static final int PAYLOAD_ENTRIES = 20;
    private static final int PAYLOAD_VALUE_MAX = 200;
    private static final int DEDUP_MAX = 200;

    private final JobRepository jobs;
    private final JobsProperties properties;
    private final Clock clock;

    JobService(JobRepository jobs, JobsProperties properties, Clock clock) {
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(NewJob job) {
        validKind(job.kind());
        validPayload(job.payload());
        if (job.dedupKey() != null && (job.dedupKey().isEmpty() || job.dedupKey().length() > DEDUP_MAX)) {
            throw new IllegalArgumentException("A job key has between 1 and " + DEDUP_MAX + " characters");
        }
        int attempts = job.maxAttempts() == null ? properties.maxAttempts() : job.maxAttempts();
        if (attempts < 1 || attempts > 100) {
            throw new IllegalArgumentException("A job runs between 1 and 100 times");
        }
        Instant now = clock.instant();
        Instant runAt = job.runAt() == null || job.runAt().isBefore(now) ? now : job.runAt();
        return jobs.insert(UUID.randomUUID(), job.companyId(), job.kind(), job.payload(), job.dedupKey(), runAt, attempts, now);
    }

    static void validKind(String kind) {
        if (kind == null || kind.length() > KIND_MAX || !KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("A job kind is lower case words separated by dots, at most " + KIND_MAX + " characters: " + kind);
        }
    }

    private static void validPayload(Map<String, String> payload) {
        if (payload.size() > PAYLOAD_ENTRIES) {
            throw new IllegalArgumentException("A job carries at most " + PAYLOAD_ENTRIES + " references");
        }
        payload.forEach((key, value) -> {
            if (!PAYLOAD_KEY.matcher(key).matches() || value == null || value.length() > PAYLOAD_VALUE_MAX) {
                throw new IllegalArgumentException("A job reference has a short key and a value of at most " + PAYLOAD_VALUE_MAX + " characters");
            }
        });
    }
}
