package taskmanager.time.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One state of a time entry with the change that led to it: created, corrected or cancelled. */
public record TimeRevision(int revision, String kind, Instant occurredAt, UUID actorId, UUID ticketId, LocalDate date, int minutes,
        String description, String reason) {
    public static final String CREATED = "created";
    public static final String CORRECTED = "corrected";
    public static final String CANCELLED = "cancelled";
}
