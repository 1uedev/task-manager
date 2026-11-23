package taskmanager.operations.internal;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** What the command {@code migrate} did at the start of its context, for its report (TASK-132). */
@Component
class SchemaMigration {
    record Result(String before, String after, int applied) {
    }

    private volatile Result result;

    void record(String before, String after, int applied) {
        result = new Result(before, after, applied);
    }

    Optional<Result> result() {
        return Optional.ofNullable(result);
    }
}
