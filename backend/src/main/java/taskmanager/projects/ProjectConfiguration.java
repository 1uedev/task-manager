package taskmanager.projects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One version of the configuration of a project (TASK-026, DEC-065): its ticket types, its statuses with the allowed
 * transitions, the status of a new ticket and which of the fixed ticket fields are required. A version never changes once
 * stored; a change of the configuration stores the next version. Tickets (TASK-027) refer to the version they were checked
 * against. The confirmed minimal configuration is the {@link #initial() initial} one: the fields title, description, type,
 * status and assignee and the workflow "Neu", "In Arbeit", "Erledigt", which every project can change.
 */
public record ProjectConfiguration(List<TicketType> ticketTypes, List<Status> statuses, String initialStatus,
        List<Transition> transitions, List<Field> fields) {
    /** The fixed ticket fields of the first section, in their order. */
    public static final List<String> FIELD_KEYS = List.of("title", "description", "type", "status", "assignee");
    /** Fields a ticket always needs; a configuration cannot make them optional. */
    public static final Set<String> ALWAYS_REQUIRED = Set.of("title", "type", "status");
    /** The categories a status belongs to, for later views and searches across projects. */
    public static final Set<String> CATEGORIES = Set.of("open", "in_progress", "done");
    static final int MAX_TYPES = 20;
    static final int MAX_STATUSES = 30;
    static final int MAX_TRANSITIONS = 200;
    private static final String KEY = "[a-z][a-z0-9_]{0,39}";

    public record TicketType(String key, String name) {
    }

    public record Status(String key, String name, String category) {
    }

    public record Transition(String from, String to) {
    }

    public record Field(String key, boolean required) {
    }

    public ProjectConfiguration {
        ticketTypes = ticketTypes == null ? null : List.copyOf(ticketTypes);
        statuses = statuses == null ? null : List.copyOf(statuses);
        transitions = transitions == null ? null : List.copyOf(transitions);
        fields = fields == null ? null : List.copyOf(fields);
    }

    /** The configuration of a new project. */
    public static ProjectConfiguration initial() {
        return new ProjectConfiguration(
            List.of(new TicketType("task", "Aufgabe"), new TicketType("bug", "Fehler")),
            List.of(new Status("new", "Neu", "open"), new Status("in_progress", "In Arbeit", "in_progress"),
                new Status("done", "Erledigt", "done")),
            "new",
            List.of(new Transition("new", "in_progress"), new Transition("in_progress", "done"),
                new Transition("in_progress", "new"), new Transition("done", "in_progress")),
            List.of(new Field("title", true), new Field("description", false), new Field("type", true),
                new Field("status", true), new Field("assignee", false)));
    }

    /** Whether a ticket may move from one status to another; staying in the same status is not a transition. */
    public boolean allowsTransition(String from, String to) {
        return transitions.stream().anyMatch(transition -> transition.from().equals(from) && transition.to().equals(to));
    }

    public boolean hasTicketType(String key) {
        return ticketTypes.stream().anyMatch(type -> type.key().equals(key));
    }

    public boolean hasStatus(String key) {
        return statuses.stream().anyMatch(status -> status.key().equals(key));
    }

    public boolean isRequired(String field) {
        return fields.stream().anyMatch(candidate -> candidate.key().equals(field) && candidate.required());
    }

    /** The same configuration with surrounding blanks removed from the names, as it is stored. */
    public ProjectConfiguration normalized() {
        return new ProjectConfiguration(
            ticketTypes.stream().map(t -> new TicketType(t.key(), t.name().strip())).toList(),
            statuses.stream().map(s -> new Status(s.key(), s.name().strip(), s.category())).toList(),
            initialStatus, transitions, fields);
    }

    /**
     * The reasons why this configuration cannot be stored, as stable codes for the problem response; empty when it can.
     * Keys are small ASCII identifiers, names are free text of 1 to 80 characters.
     */
    public List<String> violations() {
        List<String> violations = new ArrayList<>();
        keys(ticketTypes == null ? null : ticketTypes.stream().map(t -> t == null ? null : new Named(t.key(), t.name())).toList(),
            "ticketTypes", MAX_TYPES, violations);
        Set<String> statusKeys = keys(statuses == null ? null : statuses.stream().map(s -> s == null ? null : new Named(s.key(), s.name())).toList(),
            "statuses", MAX_STATUSES, violations);
        if (statuses != null && statuses.stream().anyMatch(s -> s != null && (s.category() == null || !CATEGORIES.contains(s.category())))) {
            violations.add("statuses.category_invalid");
        }
        if (initialStatus == null || !statusKeys.contains(initialStatus)) {
            violations.add("initialStatus.unknown");
        }
        if (transitions == null) {
            violations.add("transitions.required");
        } else {
            if (transitions.size() > MAX_TRANSITIONS) {
                violations.add("transitions.too_many");
            }
            Set<Transition> seen = new HashSet<>();
            for (Transition transition : transitions) {
                if (transition == null || !statusKeys.contains(transition.from()) || !statusKeys.contains(transition.to())) {
                    add(violations, "transitions.status_unknown");
                } else if (transition.from().equals(transition.to())) {
                    add(violations, "transitions.same_status");
                } else if (!seen.add(transition)) {
                    add(violations, "transitions.duplicate");
                }
            }
        }
        if (fields == null || fields.size() != FIELD_KEYS.size() || fields.stream().anyMatch(f -> f == null)
                || !fields.stream().map(Field::key).toList().equals(FIELD_KEYS)) {
            violations.add("fields.invalid");
        } else if (fields.stream().anyMatch(f -> ALWAYS_REQUIRED.contains(f.key()) && !f.required())) {
            violations.add("fields.required_fixed");
        }
        return List.copyOf(violations);
    }

    private record Named(String key, String name) {
    }

    private static Set<String> keys(List<Named> entries, String list, int max, List<String> violations) {
        Set<String> keys = new HashSet<>();
        if (entries == null || entries.isEmpty()) {
            violations.add(list + ".required");
            return keys;
        }
        if (entries.size() > max) {
            violations.add(list + ".too_many");
        }
        for (Named entry : entries) {
            if (entry == null || entry.key() == null || !entry.key().matches(KEY)) {
                add(violations, list + ".key_invalid");
            } else if (!keys.add(entry.key())) {
                add(violations, list + ".key_duplicate");
            }
            if (entry == null || entry.name() == null || entry.name().isBlank() || entry.name().strip().codePointCount(0, entry.name().strip().length()) > 80) {
                add(violations, list + ".name_invalid");
            }
        }
        return keys;
    }

    private static void add(List<String> violations, String code) {
        if (!violations.contains(code)) {
            violations.add(code);
        }
    }
}
