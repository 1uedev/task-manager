package taskmanager.files;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Port through which another module keeps images in the file store (TASK-093, DEC-123): the images of wiki pages. An image is
 * a PNG, JPEG or WebP file of at most {@link #MAX_BYTES}, recognized by its first bytes, never by a name or a type the client
 * names; SVG and every other format are refused. The content goes to the same store as attachments and is kept consistent by
 * the same clean up. No right is checked here; the caller decided that the image may be added or read.
 */
public interface ImageStore {
    long MAX_BYTES = 5L * 1024 * 1024;

    /** A stored image with the media type its first bytes show. */
    record Stored(UUID id, String mediaType, long size) {
    }

    /** The content of a stored image; the caller closes the stream. */
    record Content(String mediaType, long size, InputStream stream) {
    }

    /**
     * Writes the content to the file store and its row in the current transaction. Refuses with 413 {@code image.too_large},
     * 400 {@code image.empty} or 415 {@code image.type_unsupported}; a failing store answers 503
     * {@code image.storage_unavailable}.
     */
    Stored store(UUID companyId, String owner, long declaredLength, InputStream content, UUID actor);

    /** The content of an image of the company, or empty when there is none. */
    Optional<Content> open(UUID companyId, UUID id);
}
