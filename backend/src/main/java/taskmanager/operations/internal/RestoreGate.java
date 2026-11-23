package taskmanager.operations.internal;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Keeps the web server and the worker from starting while a restore was not released (TASK-131, rule 10). Otherwise a
 * restored installation would hand out sessions, send the replies of its jobs again and offer suggestions of before the
 * failure, before anyone checked that database and file store belong together. The command {@code after-restore} removes
 * the marker; commands themselves are not held, so the operator can still reach them.
 */
@Component
@ConditionalOnExpression("'${taskmanager.process:web}' != 'command'")
class RestoreGate {
    private final RestoreRepository repository;

    RestoreGate(RestoreRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void refuseWhileRestorePending() {
        repository.pending().ifPresent(pending -> {
            throw new IllegalStateException("This installation was restored from the backup point " + pending.point()
                + " at " + pending.restoredAt() + " and was not released yet. Run 'after-restore <operator>' first; "
                + "it ends the old sessions, holds the open mail replies, discards the AI suggestions and checks the file store.");
        });
    }
}
