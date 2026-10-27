package taskmanager.files.internal;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import taskmanager.jobs.RecurringTask;

/**
 * The clean up of the file store as a recurring task of the job executor (TASK-054): the first run follows soon after the
 * first executor starts, then one per interval, and only once per interval however many processes run. A failed run is not
 * repeated; the next interval tries again, as before. Only processes that prepare the file store offer it.
 */
@Component
@ConditionalOnExpression("'${taskmanager.process:web}' != 'command'")
class FilesCleanupTask implements RecurringTask {
    private final AttachmentCleanup cleanup;
    private final FilesProperties properties;

    FilesCleanupTask(AttachmentCleanup cleanup, FilesProperties properties) {
        this.cleanup = cleanup;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "files.cleanup";
    }

    @Override
    public Duration interval() {
        return properties.cleanupInterval().toSeconds() < 1 ? Duration.ofSeconds(1) : properties.cleanupInterval();
    }

    @Override
    public void run() throws Exception {
        cleanup.run();
    }
}
