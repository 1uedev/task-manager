package taskmanager.files;

import java.util.List;
import java.util.UUID;

/**
 * What the files module checks after a restore from a backup point (TASK-131, DEC-161): whether every content the database
 * refers to is in the file store with its checksum.
 */
public interface FilesRestore {
    /** The result: how many contents were checked, which are missing and which have another checksum, each sorted. */
    record Check(int checked, List<UUID> missing, List<UUID> damaged) {
        public Check {
            missing = List.copyOf(missing);
            damaged = List.copyOf(damaged);
        }

        public boolean complete() {
            return missing.isEmpty() && damaged.isEmpty();
        }
    }

    /** Reads every attachment whose content was not deleted and every image, and compares size and SHA-256. */
    Check checkContents();
}
