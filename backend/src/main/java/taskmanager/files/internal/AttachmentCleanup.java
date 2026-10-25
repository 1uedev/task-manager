package taskmanager.files.internal;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the file store and the database consistent (TASK-051, DEC-080). One run
 * <ol>
 *   <li>deletes abandoned uploads from the staging area,</li>
 *   <li>moves stored content that no attachment row refers to and that is older than the orphan age (a crash between
 *       putting the content in place and committing the row) to the quarantine, and deletes what has been in the
 *       quarantine longer than the retention period,</li>
 *   <li>deletes the content of attachments that were removed longer ago than the retention period and marks them.</li>
 * </ol>
 * Content that is younger than the orphan age is never touched, so an upload that is just being committed is safe. Content
 * without a row is not deleted at once: a store that was started with the wrong or an empty database would otherwise lose
 * every file; the quarantine leaves the retention period to notice and move the files back.
 */
@Component
public class AttachmentCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentCleanup.class);
    private static final int BATCH = 200;

    /** What one run did. */
    public record Result(int staging, int orphans, int expired, int purged) {
    }

    private final AttachmentRepository attachments;
    private final FileStorage storage;
    private final FilesProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    AttachmentCleanup(AttachmentRepository attachments, FileStorage storage, FilesProperties properties, Clock clock,
            PlatformTransactionManager transactions) {
        this.attachments = attachments;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    public Result run() throws IOException {
        Instant now = clock.instant();
        Instant orphanLimit = now.minus(properties.orphanAge());
        int staging = storage.purgeStaging(orphanLimit);
        int orphans = quarantineOrphans(orphanLimit);
        int expired = storage.purgeQuarantine(now.minus(properties.retention()));
        int purged = purgeRemoved(now);
        if (staging + orphans + expired + purged > 0) {
            LOG.info("File clean up: {} abandoned uploads deleted, {} files without attachment quarantined and {} of them deleted, "
                + "{} contents of removed attachments deleted.", staging, orphans, expired, purged);
        }
        return new Result(staging, orphans, expired, purged);
    }

    private int quarantineOrphans(Instant limit) throws IOException {
        List<FileStorage.Stored> old = storage.list().stream().filter(stored -> stored.modified().isBefore(limit)).toList();
        int removed = 0;
        for (int from = 0; from < old.size(); from += BATCH) {
            List<UUID> ids = old.subList(from, Math.min(old.size(), from + BATCH)).stream().map(FileStorage.Stored::id).toList();
            Set<UUID> known = transaction.execute(status -> attachments.known(ids));
            for (UUID id : ids) {
                if (!known.contains(id)) {
                    storage.quarantine(id);
                    removed++;
                }
            }
        }
        return removed;
    }

    private int purgeRemoved(Instant now) throws IOException {
        Instant limit = now.minus(properties.retention());
        int purged = 0;
        List<Attachment> due = transaction.execute(status -> attachments.toPurge(limit, BATCH));
        for (Attachment candidate : due) {
            // The row is locked while its content goes, so a second instance or run cannot do it twice.
            Boolean done = transaction.execute(status -> attachments.lockForPurge(candidate.id(), limit).map(attachment -> {
                try {
                    storage.delete(attachment.id());
                } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
                Instant at = clock.instant();
                attachments.markPurged(attachment.id(), at);
                attachments.audit(attachment.companyId(), attachment.id(), attachment.ticketId(), "attachment.purged", null, null, at);
                return true;
            }).orElse(false));
            if (Boolean.TRUE.equals(done)) {
                purged++;
            }
        }
        return purged;
    }
}
