package taskmanager.files.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import taskmanager.files.FilesRestore;

/**
 * Compares the file store with the database after a restore (TASK-131): every attachment whose content was not deleted and
 * every image must be there with its size and SHA-256. Reads the rows in pages, so a large store needs no large memory.
 */
@Component
class ContentCheck implements FilesRestore {
    private static final int PAGE = 500;

    private final JdbcClient jdbc;
    private final FileStorage storage;

    ContentCheck(JdbcClient jdbc, FileStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    private record Expected(UUID id, long size, String sha256) {
    }

    @Override
    public Check checkContents() {
        List<UUID> missing = new ArrayList<>();
        List<UUID> damaged = new ArrayList<>();
        int checked = 0;
        UUID after = null;
        while (true) {
            List<Expected> page = jdbc.sql("""
                    select id, size, sha256 from (
                        select id, size_bytes as size, sha256 from file_attachment where purged_at is null
                        union all
                        select id, size, sha256 from file_image
                    ) c where ?::uuid is null or id > ? order by id limit ?
                    """).params(after, after, PAGE)
                .query((rs, row) -> new Expected(rs.getObject("id", UUID.class), rs.getLong("size"), rs.getString("sha256"))).list();
            for (Expected expected : page) {
                checked++;
                switch (compare(expected)) {
                    case MISSING -> missing.add(expected.id());
                    case DAMAGED -> damaged.add(expected.id());
                    case FINE -> { }
                }
            }
            if (page.size() < PAGE) {
                break;
            }
            after = page.getLast().id();
        }
        return new Check(checked, missing, damaged);
    }

    private enum Outcome { FINE, MISSING, DAMAGED }

    private Outcome compare(Expected expected) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        long size = 0;
        try (InputStream in = storage.open(expected.id())) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                size += read;
            }
        } catch (NoSuchFileException absent) {
            return Outcome.MISSING;
        } catch (IOException unreadable) {
            return Outcome.DAMAGED;
        }
        boolean same = size == expected.size() && HexFormat.of().formatHex(digest.digest()).equals(expected.sha256());
        return same ? Outcome.FINE : Outcome.DAMAGED;
    }
}
