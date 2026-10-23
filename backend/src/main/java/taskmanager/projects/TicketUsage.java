package taskmanager.projects;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the projects module needs of the tickets of a project to publish a configuration (TASK-044, DEC-073). The tickets
 * module implements it, so the projects module never reads ticket tables and never depends on the tickets module. Both
 * methods need the project row locked exclusively in the same transaction, as a configuration change holds it.
 */
public interface TicketUsage {
    /** How many tickets of the project have this type and status. */
    record Use(String type, String status, long count) {
    }

    /** Moves the tickets of one type and status to another type and status. */
    record Move(String fromType, String fromStatus, String toType, String toStatus) {
    }

    /** The types and statuses the tickets of the project use now, with their numbers. */
    List<Use> usage(UUID projectId);

    /**
     * Moves the tickets of every given pair. Each moved ticket gets a new version, a history entry for the changed fields
     * written by {@code actor}, and its change is published like any other change of a ticket. Returns the number of moved
     * tickets.
     */
    long migrate(UUID projectId, List<Move> moves, UUID actor, Instant now);
}
