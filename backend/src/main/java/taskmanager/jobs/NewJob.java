package taskmanager.jobs;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A job to create (TASK-054). The payload carries references such as identifiers, never confidential text or secrets: the
 * handler loads what it may use when it runs, with the rights valid then.
 *
 * @param companyId the company the job works for; {@code null} only for a task of the installation
 * @param kind the kind, which names the {@link JobHandler}: lower case words separated by dots, at most 64 characters
 * @param payload references for the handler, at most 20 entries with short keys and values
 * @param dedupKey optional key; a second job with the same key for the same kind and company is not created
 * @param runAt the earliest time to run the job; {@code null} for now
 * @param maxAttempts how often the job may run before it fails for good; {@code null} for the configured default
 */
public record NewJob(UUID companyId, String kind, Map<String, String> payload, String dedupKey, Instant runAt, Integer maxAttempts) {
    public NewJob {
        Objects.requireNonNull(kind, "kind");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** A job of the kind for the company, without payload, to run now with the default number of attempts. */
    public static NewJob of(UUID companyId, String kind) {
        return new NewJob(companyId, kind, Map.of(), null, null, null);
    }

    public NewJob withPayload(Map<String, String> value) {
        return new NewJob(companyId, kind, value, dedupKey, runAt, maxAttempts);
    }

    public NewJob withDedupKey(String value) {
        return new NewJob(companyId, kind, payload, value, runAt, maxAttempts);
    }

    public NewJob withRunAt(Instant value) {
        return new NewJob(companyId, kind, payload, dedupKey, value, maxAttempts);
    }

    public NewJob withMaxAttempts(Integer value) {
        return new NewJob(companyId, kind, payload, dedupKey, runAt, value);
    }
}
