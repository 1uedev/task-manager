package taskmanager.tickets.internal;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.tickets.TicketChanged;

/**
 * One ticket as stored; {@link #values} holds the five fixed fields as text, in the order of the configuration, and
 * {@code custom} the values of the custom fields by key in their canonical text form (TASK-044).
 */
public record Ticket(UUID id, UUID companyId, UUID projectId, long number, String title, String description, String type,
        String status, UUID assignee, Map<String, String> custom, long configurationVersion, long version, Instant createdAt,
        UUID createdBy, Instant updatedAt, UUID updatedBy) {

    public Ticket {
        custom = Collections.unmodifiableMap(new TreeMap<>(custom == null ? Map.of() : custom));
    }

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

    /** The five fixed fields and the custom fields by their identifier {@code custom.<key>}, for the comparison of two states. */
    public Map<String, String> allValues() {
        Map<String, String> values = values();
        custom.forEach((key, value) -> values.put(ProjectConfiguration.CUSTOM_PREFIX + key, value));
        return values;
    }

    /** The event other modules derive their data from (TASK-029); the configuration gives the kind of each custom field. */
    TicketChanged changed(ProjectConfiguration configuration) {
        List<TicketChanged.CustomValue> values = configuration.customFields().stream().filter(field -> custom.containsKey(field.key()))
            .map(field -> new TicketChanged.CustomValue(field.key(), field.type(), custom.get(field.key()))).toList();
        return new TicketChanged(id, companyId, projectId, number, title, description, type, status, assignee, values);
    }
}
