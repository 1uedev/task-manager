package taskmanager.files.internal;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Prepares the file store when the web application or the worker process starts and refuses to start without it, so that no
 * upload ever lands in a directory of the container that a new container would not have (REQ-DATA-003). Commands such as
 * {@code bootstrap-admin} do not need the store. The clean up runs as a recurring background job since TASK-054
 * ({@link FilesCleanupTask}).
 */
@Component
@ConditionalOnExpression("'${taskmanager.process:web}' != 'command'")
class FilesLifecycle {
    private final FileStorage storage;
    private final FilesProperties properties;

    FilesLifecycle(FileStorage storage, FilesProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @PostConstruct
    void prepare() {
        if (properties.directory().isBlank()) {
            throw new IllegalStateException(
                "The application cannot start because required settings are missing: TASK_MANAGER_FILES_DIR (taskmanager.files.directory)");
        }
        try {
            storage.prepare();
        } catch (IOException failure) {
            throw new IllegalStateException("The directory of the file store cannot be used: " + properties.directory(), failure);
        }
    }
}
