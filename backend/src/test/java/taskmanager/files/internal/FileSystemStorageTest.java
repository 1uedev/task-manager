package taskmanager.files.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TASK-051: the file store on a directory, without database and without HTTP. */
class FileSystemStorageTest {
    @TempDir Path root;
    FileSystemStorage storage;

    @BeforeEach
    void prepare() throws IOException {
        storage = new FileSystemStorage(root.resolve("files").toString());
        storage.prepare();
    }

    private static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 31 + 7);
        }
        return data;
    }

    @Test
    void aStagedFileKeepsItsBytesAndShowsSizeChecksumAndFirstBytes() throws Exception {
        byte[] data = bytes(150_000);
        var staged = storage.stage(new ByteArrayInputStream(data), 1_000_000);
        assertEquals(data.length, staged.size());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)), staged.sha256());
        assertArrayEquals(java.util.Arrays.copyOf(data, 16), staged.head());
        assertArrayEquals(data, Files.readAllBytes(staged.path()));
        UUID id = UUID.randomUUID();
        storage.store(staged, id);
        assertFalse(Files.exists(staged.path()), "the staging area keeps nothing of a stored file");
        try (var in = storage.open(id)) {
            assertArrayEquals(data, in.readAllBytes());
        }
        assertEquals(id.toString().substring(0, 2), root.resolve("files/content").toFile().list()[0], "sharded by the first two characters of the identifier");
    }

    @Test
    void aStreamBeyondTheLimitStopsOneByteAfterIt() throws Exception {
        var staged = storage.stage(new ByteArrayInputStream(bytes(5_000)), 4_096);
        assertEquals(4_097, staged.size(), "one byte more than the limit shows that the stream was larger");
        assertEquals(4_097, Files.size(staged.path()));
        var exact = storage.stage(new ByteArrayInputStream(bytes(4_096)), 4_096);
        assertEquals(4_096, exact.size());
    }

    @Test
    void anEmptyStreamIsAnEmptyFileWithoutFirstBytes() throws Exception {
        var staged = storage.stage(new ByteArrayInputStream(new byte[0]), 100);
        assertEquals(0, staged.size());
        assertEquals(0, staged.head().length);
    }

    @Test
    void aFailingStreamLeavesNothingInTheStagingArea() {
        var broken = new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection lost");
            }
        };
        // A stream that breaks off is told apart from a failure of the store (review F7 of TASK-051).
        assertThrows(FileStorage.UploadInterrupted.class, () -> storage.stage(broken, 100));
        assertEquals(0, root.resolve("files/staging").toFile().list().length);
    }

    @Test
    void discardingAndDeletingNeverFailForMissingFiles() throws Exception {
        var staged = storage.stage(new ByteArrayInputStream(bytes(10)), 100);
        storage.discard(staged);
        storage.discard(staged);
        UUID id = UUID.randomUUID();
        storage.delete(id);
        assertThrows(NoSuchFileException.class, () -> storage.open(id));
    }

    @Test
    void listingShowsOnlyIdentifiedContentWithItsAge() throws Exception {
        UUID id = UUID.randomUUID();
        storage.store(storage.stage(new ByteArrayInputStream(bytes(10)), 100), id);
        Path stray = root.resolve("files/content/zz");
        Files.createDirectories(stray);
        Files.writeString(stray.resolve("not-an-identifier"), "x");
        var listed = storage.list();
        assertEquals(1, listed.size());
        assertEquals(id, listed.getFirst().id());
        assertTrue(listed.getFirst().modified().isAfter(Instant.now().minusSeconds(60)));
    }

    @Test
    void onlyOldUploadsAreRemovedFromTheStagingArea() throws Exception {
        var old = storage.stage(new ByteArrayInputStream(bytes(10)), 100);
        var fresh = storage.stage(new ByteArrayInputStream(bytes(10)), 100);
        Files.setLastModifiedTime(old.path(), FileTime.from(Instant.now().minusSeconds(3 * 86_400)));
        assertEquals(1, storage.purgeStaging(Instant.now().minusSeconds(86_400)));
        assertFalse(Files.exists(old.path()));
        assertTrue(Files.exists(fresh.path()));
    }

    @Test
    void quarantinedContentLeavesTheStoreAndIsDeletedOnlyAfterItsPeriod() throws Exception {
        UUID id = UUID.randomUUID();
        storage.store(storage.stage(new ByteArrayInputStream(bytes(10)), 100), id);
        Path content = root.resolve("files/content").resolve(id.toString().substring(0, 2)).resolve(id.toString());
        Files.setLastModifiedTime(content, FileTime.from(Instant.now().minusSeconds(30 * 86_400)));
        storage.quarantine(id);
        storage.quarantine(UUID.randomUUID());
        Path kept = root.resolve("files/quarantine").resolve(id.toString());
        assertFalse(Files.exists(content));
        assertTrue(Files.exists(kept));
        assertThrows(NoSuchFileException.class, () -> storage.open(id));
        assertEquals(0, storage.purgeQuarantine(Instant.now().minusSeconds(86_400)), "the period counts from the quarantine, not from the upload");
        assertEquals(1, storage.purgeQuarantine(Instant.now().plusSeconds(60)));
        assertFalse(Files.exists(kept));
    }

    @Test
    void anUnsetDirectoryIsRefused() {
        assertThrows(IllegalStateException.class, () -> new FileSystemStorage("").prepare());
    }
}
