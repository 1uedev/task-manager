package taskmanager.operations.internal;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import taskmanager.operations.ContainerCommand;

/**
 * Keeps the web server and the worker from starting while a restore was not released (TASK-131, rule 10). Otherwise a
 * restored installation would hand out sessions, send the replies of its jobs again and offer suggestions of before the
 * failure, before anyone checked that database and file store belong together. The command {@code after-restore} removes
 * the marker; commands themselves are not held, so the operator can still reach them.
 *
 * <p>Since TASK-135 (finding F6) the gate exists in every process and decides by what the process really is, not by a
 * setting: only a context without a web server that the entry point started for a container command
 * ({@link ContainerCommand}) skips the check. The setting {@code taskmanager.process} must agree with that; a web server or a
 * worker with {@code taskmanager.process=command}, or a command context with a web server, does not start.
 */
@Component
class RestoreGate {
    private final RestoreRepository repository;
    private final ApplicationContext context;
    private final String process;

    RestoreGate(RestoreRepository repository, ApplicationContext context, @Value("${taskmanager.process:web}") String process) {
        this.repository = repository;
        this.context = context;
        this.process = process;
    }

    @PostConstruct
    void refuseWhileRestorePending() {
        boolean web = context instanceof WebApplicationContext;
        boolean command = context.containsBean(ContainerCommand.BEAN);
        if (!"web".equals(process) && !"worker".equals(process) && !"command".equals(process)) {
            throw new IllegalStateException("taskmanager.process must be 'web', 'worker' or 'command', not '" + process + "'.");
        }
        if (command != "command".equals(process) || (command && web) || ("worker".equals(process) && web)) {
            throw new IllegalStateException("taskmanager.process=" + process + " does not fit this process ("
                + (web ? "with" : "without") + " web server, " + (command ? "a container command" : "no container command")
                + "). The setting is chosen by the entry point of the image; do not set it yourself.");
        }
        if (command) {
            return;
        }
        repository.pending().ifPresent(pending -> {
            throw new IllegalStateException("This installation was restored from the backup point " + pending.point()
                + " at " + pending.restoredAt() + " and was not released yet. Run 'after-restore <operator>' first; "
                + "it ends the old sessions, holds the open mail replies, discards the AI suggestions and checks the file store.");
        });
    }
}
