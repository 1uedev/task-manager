package taskmanager.files.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import taskmanager.files.ImageStore;
import taskmanager.platform.ApiProblemException;

/**
 * The images of other modules in the file store (TASK-093). The content is staged, its first bytes decide the type, and only
 * then it is put in its place and its row written in the transaction of the caller. If that transaction rolls back, the
 * content has no row and the clean up of the files module quarantines it like any other content without a row.
 */
@Service
class ImageStoreService implements ImageStore {
    private static final Logger LOG = LoggerFactory.getLogger(ImageStoreService.class);

    private final FileStorage storage;
    private final JdbcClient jdbc;
    private final Clock clock;

    ImageStoreService(FileStorage storage, JdbcClient jdbc, Clock clock) {
        this.storage = storage;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The media type the first bytes show, or {@code null} for anything but PNG, JPEG and WebP. */
    static String mediaType(byte[] head) {
        if (starts(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (starts(head, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (starts(head, 0, 0x52, 0x49, 0x46, 0x46) && starts(head, 8, 0x57, 0x45, 0x42, 0x50)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean starts(byte[] head, int offset, int... signature) {
        if (head.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Stored store(UUID companyId, String owner, long declaredLength, InputStream content, UUID actor) {
        if (declaredLength > MAX_BYTES) {
            throw ApiProblemException.of(413, "image.too_large");
        }
        FileStorage.Staged staged;
        try {
            staged = storage.stage(content, MAX_BYTES);
        } catch (FileStorage.UploadInterrupted interrupted) {
            throw ApiProblemException.of(400, "image.incomplete");
        } catch (IOException failure) {
            throw unavailable("write an image to the staging area", failure);
        }
        UUID id = UUID.randomUUID();
        String type;
        try {
            if (staged.size() > MAX_BYTES) {
                throw ApiProblemException.of(413, "image.too_large");
            }
            if (staged.size() == 0) {
                throw ApiProblemException.of(400, "image.empty");
            }
            type = mediaType(staged.head());
            if (type == null) {
                throw ApiProblemException.of(415, "image.type_unsupported");
            }
            try {
                storage.store(staged, id);
            } catch (IOException failure) {
                throw unavailable("put image " + id + " in its place", failure);
            }
        } catch (RuntimeException refused) {
            storage.discard(staged);
            throw refused;
        }
        jdbc.sql("""
                insert into file_image (id, company_id, owner, media_type, size, sha256, created_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(id, companyId, owner, type, staged.size(), staged.sha256(), Timestamp.from(clock.instant()), actor).update();
        return new Stored(id, type, staged.size());
    }

    @Override
    public Optional<Content> open(UUID companyId, UUID id) {
        record Row(String mediaType, long size) {
        }
        Optional<Row> row = jdbc.sql("select media_type, size from file_image where company_id = ? and id = ?").params(companyId, id)
            .query((rs, n) -> new Row(rs.getString("media_type"), rs.getLong("size"))).optional();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Content(row.get().mediaType(), row.get().size(), storage.open(id)));
        } catch (NoSuchFileException missing) {
            LOG.warn("The content of image {} is missing in the file store.", id);
            throw ApiProblemException.of(410, "image.content_missing");
        } catch (IOException failure) {
            throw unavailable("open image " + id, failure);
        }
    }

    private static ApiProblemException unavailable(String step, IOException failure) {
        LOG.warn("The file store could not {}: {}", step, failure.toString());
        return ApiProblemException.retryAfter(503, "image.storage_unavailable", 5);
    }
}
