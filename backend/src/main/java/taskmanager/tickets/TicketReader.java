package taskmanager.tickets;

import java.util.Map;
import java.util.UUID;
import taskmanager.permissions.AccessContext;
import taskmanager.projects.ProjectConfiguration;

/**
 * A ticket as a person may read it, for modules that pass its fields on, such as the AI assistance (TASK-126). The checks are
 * those of the ticket route: without {@code ticket.read} the project does not exist for the caller (404), an unknown number
 * answers 404 too. Only the fields with a read rule for the caller are in {@link Readable#values}.
 */
public interface TicketReader {
    Readable read(AccessContext actor, UUID projectId, long number);

    /**
     * The readable fields by their identifier ({@code title}, {@code description}, {@code type}, {@code status},
     * {@code assignee}, {@code custom.<key>}) in their canonical text form, with the configuration that names types, statuses,
     * custom fields and options. A field without a value is missing.
     */
    record Readable(UUID id, UUID projectId, String projectKey, long number, long version, Map<String, String> values,
            ProjectConfiguration configuration) {
        public Readable {
            values = Map.copyOf(values);
        }
    }
}
