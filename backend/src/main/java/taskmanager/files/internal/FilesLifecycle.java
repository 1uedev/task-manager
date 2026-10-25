package taskmanager.files.internal;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prepares the file store when the web application starts and refuses to start without it, so that no upload ever lands in a
 * directory of the container that a new container would not have (REQ-DATA-003). Afterwards it runs the clean up once at
 * the start and then at the configured interval. Commands such as {@code bootstrap-admin} do not need the store.
 */
@Component
@ConditionalOnWebApplication
class FilesLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(FilesLifecycle.class);
    private final FileStorage storage;
    private final FilesProperties properties;
    private final AttachmentCleanup cleanup;
    private ScheduledExecutorService scheduler;

    FilesLifecycle(FileStorage storage, FilesProperties properties, AttachmentCleanup cleanup) {
        this.storage = storage;
        this.properties = properties;
        this.cleanup = cleanup;
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

    @EventListener(ApplicationReadyEvent.class)
    void schedule() {
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "file-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        long seconds = Math.max(1, properties.cleanupInterval().toSeconds());
        scheduler.scheduleWithFixedDelay(this::runOnce, 30, seconds, TimeUnit.SECONDS);
    }

    private void runOnce() {
        try {
            cleanup.run();
        } catch (IOException | RuntimeException failure) {
            // A failed run is tried again at the next interval; the store is not endangered by it.
            LOG.warn("The file clean up failed: {}", failure.toString());
        }
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
