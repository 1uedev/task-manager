package taskmanager.permissions;

import java.util.Set;

/** Snapshot for reads/serialization. A snapshot is NOT authority for a later mutation. */
public record Decision(boolean allowed, Set<String> readableFields, Set<String> writableFields) {
    public Decision {
        readableFields = Set.copyOf(readableFields);
        writableFields = Set.copyOf(writableFields);
    }

    public static Decision denied() { return new Decision(false, Set.of(), Set.of()); }
}
