package taskmanager.files.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The file store on a directory of the host. The layout is {@code staging/} for uploads in progress and
 * {@code content/<first two characters>/<identifier>} for stored content and {@code quarantine/<identifier>} for content
 * without attachment that waits to be deleted. The identifier is that of the attachment, so nothing the client sends ever
 * becomes part of a path.
 */
final class FileSystemStorage implements FileStorage {
    private static final int HEAD = 16;
    private final String configured;
    private Path root;

    FileSystemStorage(String directory) {
        this.configured = directory;
    }

    private Path root() {
        if (root == null) {
            if (configured == null || configured.isBlank()) {
                throw new IllegalStateException("The directory of the file store is not set.");
            }
            root = Path.of(configured).toAbsolutePath().normalize();
        }
        return root;
    }

    private Path staging() {
        return root().resolve("staging");
    }

    private Path content() {
        return root().resolve("content");
    }

    private Path quarantine() {
        return root().resolve("quarantine");
    }

    private Path path(UUID id) {
        String name = id.toString();
        return content().resolve(name.substring(0, 2)).resolve(name);
    }

    @Override
    public void prepare() throws IOException {
        Files.createDirectories(staging());
        Files.createDirectories(content());
        Files.createDirectories(quarantine());
        Path probe = Files.createTempFile(staging(), "probe-", ".tmp");
        Files.delete(probe);
    }

    @Override
    public Staged stage(InputStream in, long limit) throws IOException {
        Path file = Files.createTempFile(staging(), "upload-", ".part");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] head = new byte[HEAD];
            int headLength = 0;
            long total = 0;
            byte[] buffer = new byte[64 * 1024];
            try (var out = Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                while (total <= limit) {
                    int wanted = (int) Math.min(buffer.length, limit + 1 - total);
                    int read;
                    try {
                        read = in.read(buffer, 0, wanted);
                    } catch (IOException broken) {
                        throw new UploadInterrupted(broken);
                    }
                    if (read < 0) {
                        break;
                    }
                    if (headLength < HEAD) {
                        int take = Math.min(HEAD - headLength, read);
                        System.arraycopy(buffer, 0, head, headLength, take);
                        headLength += take;
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    total += read;
                }
            }
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            return new Staged(file, total, HexFormat.of().formatHex(digest.digest()), Arrays.copyOf(head, headLength));
        } catch (IOException | RuntimeException | NoSuchAlgorithmException failure) {
            Files.deleteIfExists(file);
            if (failure instanceof IOException io) {
                throw io;
            }
            throw new IOException("The upload could not be staged", failure);
        }
    }

    @Override
    public void store(Staged staged, UUID id) throws IOException {
        Path target = path(id);
        Files.createDirectories(target.getParent());
        try {
            Files.move(staged.path(), target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            // Staging and content lie below one root; this only happens when the volume is a union of file systems.
            throw new IOException("The file store does not support an atomic move", notAtomic);
        }
        // The age of a file counts from the moment it was put in place, not from the start of a slow upload.
        Files.setLastModifiedTime(target, FileTime.from(Instant.now()));
    }

    @Override
    public void discard(Staged staged) {
        try {
            Files.deleteIfExists(staged.path());
        } catch (IOException ignored) {
            // The clean up removes what is left of the staging area.
        }
    }

    @Override
    public InputStream open(UUID id) throws IOException {
        return Files.newInputStream(path(id));
    }

    @Override
    public void delete(UUID id) throws IOException {
        Files.deleteIfExists(path(id));
    }

    @Override
    public List<Stored> list() throws IOException {
        List<Stored> stored = new ArrayList<>();
        try (Stream<Path> directories = Files.list(content())) {
            for (Path directory : (Iterable<Path>) directories.filter(Files::isDirectory)::iterator) {
                try (Stream<Path> files = Files.list(directory)) {
                    for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                        UUID id = identifier(file);
                        if (id != null) {
                            stored.add(new Stored(id, Files.getLastModifiedTime(file).toInstant()));
                        }
                    }
                }
            }
        }
        return stored;
    }

    @Override
    public void quarantine(UUID id) throws IOException {
        Path target = quarantine().resolve(id.toString());
        try {
            Files.move(path(id), target, StandardCopyOption.ATOMIC_MOVE);
        } catch (NoSuchFileException gone) {
            return;
        }
        // The period of the quarantine counts from now, not from the upload.
        Files.setLastModifiedTime(target, FileTime.from(Instant.now()));
    }

    @Override
    public int purgeQuarantine(Instant before) throws IOException {
        return purgeOld(quarantine(), before);
    }

    @Override
    public int purgeStaging(Instant before) throws IOException {
        return purgeOld(staging(), before);
    }

    private static int purgeOld(Path directory, Instant before) throws IOException {
        int purged = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(before) && Files.deleteIfExists(file)) {
                    purged++;
                }
            }
        }
        return purged;
    }

    private static UUID identifier(Path file) {
        try {
            return UUID.fromString(file.getFileName().toString());
        } catch (IllegalArgumentException notAnIdentifier) {
            return null;
        }
    }
}
