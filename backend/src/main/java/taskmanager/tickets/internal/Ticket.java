package taskmanager.tickets.internal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One ticket as stored; {@link #values} holds the five configurable fields as text, in the order of the configuration. */
public record Ticket(UUID id, UUID companyId, UUID projectId, long number, String title, String description, String type,
        String status, UUID assignee, long configurationVersion, long version, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy) {

    /** The field values by key; an empty description or a missing assignee is {@code null}. */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", title);
        values.put("description", description);
        values.put("type", type);
        values.put("status", status);
        values.put("assignee", assignee == null ? null : assignee.toString());
        return values;
    }
}
