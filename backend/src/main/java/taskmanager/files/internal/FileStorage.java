package taskmanager.files.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Where the content of attachments lives (TASK-051, DEC-080). The metadata is in the database, the content is not: a
 * file is first written to a staging area, checked, and only then put in its place under the identifier of its
 * attachment, where it is never changed again. A later object store can replace the adapter without touching the rules.
 * Database and file store are no common transaction; the order "content first, row second" and the clean up of content
 * without a row keep them consistent.
 */
interface FileStorage {
    /** What was written to the staging area: the file, its size in bytes, its SHA-256 as lower case hex and its first bytes. */
    record Staged(Path path, long size, String sha256, byte[] head) {
    }

    /** One stored content with the time of its last change. */
    record Stored(UUID id, Instant modified) {
    }

    /** Creates the directories and proves that they can be written; fails when they cannot. */
    void prepare() throws IOException;

    /** Writes at most {@code limit} + 1 bytes of the stream to the staging area, so a larger stream shows as larger than the limit. */
    Staged stage(InputStream in, long limit) throws IOException;

    /** Puts a staged file in its place under the identifier, atomically. */
    void store(Staged staged, UUID id) throws IOException;

    /** Deletes a staged file; a missing one is not an error. */
    void discard(Staged staged);

    /** Opens the content; a missing one ends in {@link java.nio.file.NoSuchFileException}. */
    InputStream open(UUID id) throws IOException;

    /** Deletes the content; a missing one is not an error. */
    void delete(UUID id) throws IOException;

    /** Every stored content, for the clean up of content without attachment. */
    List<Stored> list() throws IOException;

    /**
     * Moves content that no attachment refers to out of the way instead of deleting it, so a store that was started with the
     * wrong database loses nothing at once; {@link #purgeQuarantine} deletes it later.
     */
    void quarantine(UUID id) throws IOException;

    /** Deletes the quarantined content that has been there since before {@code before}; returns how many. */
    int purgeQuarantine(Instant before) throws IOException;

    /** Deletes the files of the staging area that have not been written since {@code before}; returns how many. */
    int purgeStaging(Instant before) throws IOException;
}
