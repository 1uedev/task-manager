package taskmanager.projects;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One version of the configuration of a project (TASK-026, DEC-065; extended by TASK-044, DEC-073): its ticket types, its
 * statuses with the allowed transitions, the status of a new ticket, which of the fixed ticket fields are required, the
 * workflows of single ticket types and the custom fields. A version never changes once stored; a change of the
 * configuration stores the next version. The confirmed minimal configuration is the {@link #initial() initial} one: the
 * fields title, description, type, status and assignee and the workflow "Neu", "In Arbeit", "Erledigt", which every
 * project can change.
 *
 * <p>The top level {@code initialStatus} and {@code transitions} are the workflow of every ticket type that has no entry in
 * {@code workflows}. A type with an entry uses that entry alone: its first status and its transitions, and the only
 * statuses it knows are the first one and those its transitions name. A transition can require fields (a standard field
 * or {@code custom.<key>}); a ticket must carry a value for them after the change that takes the transition. Documents
 * written before TASK-044 lack the new members and read as having no workflows, no custom fields and no required fields.
 */
public record ProjectConfiguration(List<TicketType> ticketTypes, List<Status> statuses, String initialStatus,
        List<Transition> transitions, List<Field> fields, List<Workflow> workflows, List<CustomField> customFields) {
    /** The fixed ticket fields of the first section, in their order. */
    public static final List<String> FIELD_KEYS = List.of("title", "description", "type", "status", "assignee");
    /** Fields a ticket always needs; a configuration cannot make them optional. */
    public static final Set<String> ALWAYS_REQUIRED = Set.of("title", "type", "status");
    /** The categories a status belongs to, for later views and searches across projects. */
    public static final Set<String> CATEGORIES = Set.of("open", "in_progress", "done");
    /** The kinds of a status for service deadlines (TASK-068). */
    public static final String SLA_OPEN = "open";
    public static final Set<String> SLA_KINDS = Set.of(SLA_OPEN, "waiting", "resolved", "cancelled");
    /** The kinds of custom fields (TASK-044). */
    public static final String TEXT = "text";
    public static final String LONGTEXT = "longtext";
    public static final String INTEGER = "integer";
    public static final String DATE = "date";
    public static final String BOOLEAN = "boolean";
    public static final String SELECT = "select";
    public static final String USER = "user";
    public static final Set<String> FIELD_TYPES = Set.of(TEXT, LONGTEXT, INTEGER, DATE, BOOLEAN, SELECT, USER);
    /** The identifier of a custom field in rights, history, requirements of transitions and search. */
    public static final String CUSTOM_PREFIX = "custom.";
    /** The standard fields a transition can require; title, type and status are always set. */
    public static final Set<String> REQUIRABLE_STANDARD = Set.of("description", "assignee");
    public static final int TEXT_MAX = 200;
    public static final int LONGTEXT_MAX = 20_000;
    /** The largest whole number every client reads without loss (2^53 - 1). */
    public static final long INTEGER_MAX = 9_007_199_254_740_991L;
    static final int MAX_TYPES = 20;
    static final int MAX_STATUSES = 30;
    static final int MAX_TRANSITIONS = 200;
    static final int MAX_CUSTOM_FIELDS = 30;
    static final int MAX_OPTIONS = 50;
    static final int MAX_REQUIRED_PER_TRANSITION = 10;
    private static final String KEY = "[a-z][a-z0-9_]{0,39}";

    public record TicketType(String key, String name) {
    }

    /**
     * A status. {@code sla} is its kind for service deadlines (TASK-068): {@code open}, {@code waiting} (for the customer,
     * pauses the resolution), {@code resolved} or {@code cancelled}; documents written before TASK-068 read as {@code open}.
     */
    public record Status(String key, String name, String category, String sla) {
        public Status {
            sla = sla == null ? SLA_OPEN : sla;
        }

        public Status(String key, String name, String category) {
            this(key, name, category, SLA_OPEN);
        }
    }

    /** A step from one status to another; {@code requiredFields} must carry a value after the change that takes it. */
    public record Transition(String from, String to, List<String> requiredFields) {
        public Transition {
            requiredFields = requiredFields == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(requiredFields));
        }

        public Transition(String from, String to) {
            this(from, to, List.of());
        }
    }

    public record Field(String key, boolean required) {
    }

    /** The workflow of one ticket type: its first status and its transitions. */
    public record Workflow(String type, String initialStatus, List<Transition> transitions) {
        public Workflow {
            transitions = transitions == null ? null : Collections.unmodifiableList(new ArrayList<>(transitions));
        }
    }

    /**
     * A field of the tickets of this project. {@code required} makes a value necessary when a ticket is created and keeps
     * it from being emptied; {@code active = false} keeps the stored values readable but accepts no new value. A field is
     * never removed from a published configuration and never changes its type.
     */
    public record CustomField(String key, String name, String type, Boolean required, Boolean active, List<Option> options) {
        public CustomField {
            required = required != null && required;
            active = active == null || active;
            options = options == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(options));
        }

        public String id() {
            return CUSTOM_PREFIX + key;
        }

        public Optional<Option> option(String optionKey) {
            return options.stream().filter(option -> option != null && option.key().equals(optionKey)).findFirst();
        }
    }

    /** A choice of a {@code select} field. An inactive option keeps its stored values but can no longer be chosen. */
    public record Option(String key, String name, Boolean active) {
        public Option {
            active = active == null || active;
        }
    }

    public ProjectConfiguration {
        ticketTypes = ticketTypes == null ? null : List.copyOf(ticketTypes);
        statuses = statuses == null ? null : List.copyOf(statuses);
        transitions = transitions == null ? null : List.copyOf(transitions);
        fields = fields == null ? null : List.copyOf(fields);
        workflows = workflows == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(workflows));
        customFields = customFields == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(customFields));
    }

    /** The configuration of the first section, without workflows of single types and without custom fields. */
    public ProjectConfiguration(List<TicketType> ticketTypes, List<Status> statuses, String initialStatus,
            List<Transition> transitions, List<Field> fields) {
        this(ticketTypes, statuses, initialStatus, transitions, fields, List.of(), List.of());
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

    // ------------------------------------------------------------------------------------------------ reading

    /** Whether a ticket may move from one status to another in the default workflow; staying is not a transition. */
    public boolean allowsTransition(String from, String to) {
        return allowsTransition(null, from, to);
    }

    /** Whether a ticket of the type may move from one status to another; staying in the same status is not a transition. */
    public boolean allowsTransition(String type, String from, String to) {
        return transition(type, from, to).isPresent();
    }

    /** The transition of the workflow of the type; an unknown type uses the default workflow. */
    public Optional<Transition> transition(String type, String from, String to) {
        return transitionsOf(type).stream().filter(transition -> transition.from().equals(from) && transition.to().equals(to)).findFirst();
    }

    public List<Transition> transitionsOf(String type) {
        return workflow(type).map(Workflow::transitions).orElse(transitions);
    }

    /** The status a new ticket of the type starts in. */
    public String initialStatusOf(String type) {
        return workflow(type).map(Workflow::initialStatus).orElse(initialStatus);
    }

    /** The statuses a ticket of the type can have: all, or for a type with its own workflow the first one and those it names. */
    public Set<String> statusKeysOf(String type) {
        Optional<Workflow> own = workflow(type);
        if (own.isEmpty()) {
            Set<String> all = new LinkedHashSet<>();
            statuses.forEach(status -> all.add(status.key()));
            return all;
        }
        Set<String> keys = new LinkedHashSet<>();
        keys.add(own.get().initialStatus());
        own.get().transitions().forEach(transition -> {
            keys.add(transition.from());
            keys.add(transition.to());
        });
        return keys;
    }

    private Optional<Workflow> workflow(String type) {
        return type == null ? Optional.empty() : workflows.stream().filter(workflow -> workflow.type().equals(type)).findFirst();
    }

    public boolean hasTicketType(String key) {
        return ticketTypes.stream().anyMatch(type -> type.key().equals(key));
    }

    /** The kind of the status for service deadlines; an unknown status counts as open (TASK-068). */
    public String slaKindOf(String key) {
        return statuses.stream().filter(status -> status.key().equals(key)).findFirst().map(Status::sla).orElse(SLA_OPEN);
    }

    public boolean hasStatus(String key) {
        return statuses.stream().anyMatch(status -> status.key().equals(key));
    }

    public boolean isRequired(String field) {
        return fields.stream().anyMatch(candidate -> candidate.key().equals(field) && candidate.required());
    }

    public Optional<CustomField> customField(String key) {
        return customFields.stream().filter(field -> field.key().equals(key)).findFirst();
    }

    /** The identifiers {@code custom.<key>} of all custom fields, active or not. */
    public List<String> customFieldIds() {
        return customFields.stream().map(CustomField::id).toList();
    }

    /** The same configuration with surrounding blanks removed from the names, as it is stored. */
    public ProjectConfiguration normalized() {
        return new ProjectConfiguration(
            ticketTypes.stream().map(t -> new TicketType(t.key(), t.name().strip())).toList(),
            statuses.stream().map(s -> new Status(s.key(), s.name().strip(), s.category(), s.sla())).toList(),
            initialStatus, transitions, fields, workflows,
            customFields.stream().map(field -> new CustomField(field.key(), field.name().strip(), field.type(), field.required(),
                field.active(), field.options().stream().map(option -> new Option(option.key(), option.name().strip(), option.active())).toList()))
                .toList());
    }

    // ------------------------------------------------------------------------------------------------ rules

    /**
     * The reasons why this configuration cannot be stored, as stable codes for the problem response; empty when it can.
     * Keys are small ASCII identifiers, names are free text of 1 to 80 characters.
     */
    public List<String> violations() {
        List<String> violations = new ArrayList<>();
        keys(ticketTypes == null ? null : ticketTypes.stream().map(t -> t == null ? null : new Named(t.key(), t.name())).toList(),
            "ticketTypes", MAX_TYPES, violations);
        Set<String> typeKeys = ticketTypes == null ? Set.of()
            : ticketTypes.stream().filter(t -> t != null && t.key() != null).map(TicketType::key).collect(java.util.stream.Collectors.toSet());
        Set<String> statusKeys = keys(statuses == null ? null : statuses.stream().map(s -> s == null ? null : new Named(s.key(), s.name())).toList(),
            "statuses", MAX_STATUSES, violations);
        if (statuses != null && statuses.stream().anyMatch(s -> s != null && (s.category() == null || !CATEGORIES.contains(s.category())))) {
            violations.add("statuses.category_invalid");
        }
        if (statuses != null && statuses.stream().anyMatch(s -> s != null && !SLA_KINDS.contains(s.sla()))) {
            violations.add("statuses.sla_invalid");
        }
        Set<String> fieldIds = customFieldChecks(violations);
        if (initialStatus == null || !statusKeys.contains(initialStatus)) {
            violations.add("initialStatus.unknown");
        }
        transitions(transitions, statusKeys, fieldIds, "transitions", violations);
        if (workflows.size() > MAX_TYPES) {
            violations.add("workflows.too_many");
        }
        Set<String> seenTypes = new HashSet<>();
        for (Workflow workflow : workflows) {
            if (workflow == null || workflow.type() == null || !typeKeys.contains(workflow.type())) {
                add(violations, "workflows.type_unknown");
                continue;
            }
            if (!seenTypes.add(workflow.type())) {
                add(violations, "workflows.type_duplicate");
            }
            if (workflow.initialStatus() == null || !statusKeys.contains(workflow.initialStatus())) {
                add(violations, "workflows.initialStatus.unknown");
            }
            transitions(workflow.transitions(), statusKeys, fieldIds, "workflows.transitions", violations);
        }
        if (fields == null || fields.size() != FIELD_KEYS.size() || fields.stream().anyMatch(f -> f == null)
                || !fields.stream().map(Field::key).toList().equals(FIELD_KEYS)) {
            violations.add("fields.invalid");
        } else if (fields.stream().anyMatch(f -> ALWAYS_REQUIRED.contains(f.key()) && !f.required())) {
            violations.add("fields.required_fixed");
        }
        return List.copyOf(violations);
    }

    /**
     * What this configuration may not do compared with the published one it replaces: no custom field disappears or changes
     * its type, no option of a select field disappears. Fields and options are deactivated instead, so no stored value
     * loses its meaning (F-06 of DOMAIN_MODEL.md). Statuses and ticket types may go, but only with a mapping for the
     * tickets that still use them; that check needs the tickets and belongs to the publication.
     */
    public List<String> incompatibilities(ProjectConfiguration previous) {
        List<String> violations = new ArrayList<>();
        for (CustomField old : previous.customFields) {
            Optional<CustomField> current = customField(old.key());
            if (current.isEmpty()) {
                add(violations, "customFields.removed");
                continue;
            }
            if (!current.get().type().equals(old.type())) {
                add(violations, "customFields.type_changed");
            }
            for (Option option : old.options) {
                if (current.get().option(option.key()).isEmpty()) {
                    add(violations, "customFields.option_removed");
                }
            }
        }
        return List.copyOf(violations);
    }

    private Set<String> customFieldChecks(List<String> violations) {
        Set<String> ids = new HashSet<>();
        if (customFields.size() > MAX_CUSTOM_FIELDS) {
            violations.add("customFields.too_many");
        }
        for (CustomField field : customFields) {
            if (field == null || field.key() == null || !field.key().matches(KEY)) {
                add(violations, "customFields.key_invalid");
                continue;
            }
            if (!ids.add(field.id())) {
                add(violations, "customFields.key_duplicate");
            }
            if (field.name() == null || field.name().isBlank() || field.name().strip().codePointCount(0, field.name().strip().length()) > 80) {
                add(violations, "customFields.name_invalid");
            }
            if (field.type() == null || !FIELD_TYPES.contains(field.type())) {
                add(violations, "customFields.type_invalid");
                continue;
            }
            options(field, violations);
        }
        return ids;
    }

    private static void options(CustomField field, List<String> violations) {
        if (!SELECT.equals(field.type())) {
            if (!field.options().isEmpty()) {
                add(violations, "customFields.options_unexpected");
            }
            return;
        }
        if (field.options().isEmpty()) {
            add(violations, "customFields.options_required");
        }
        if (field.options().size() > MAX_OPTIONS) {
            add(violations, "customFields.options_too_many");
        }
        Set<String> keys = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Option option : field.options()) {
            if (option == null || option.key() == null || !option.key().matches(KEY)) {
                add(violations, "customFields.option_key_invalid");
                continue;
            }
            if (!keys.add(option.key())) {
                add(violations, "customFields.option_key_duplicate");
            }
            String name = option.name() == null ? "" : option.name().strip();
            if (name.isEmpty() || name.codePointCount(0, name.length()) > 80) {
                add(violations, "customFields.option_name_invalid");
            } else if (!names.add(name.toLowerCase(java.util.Locale.ROOT))) {
                add(violations, "customFields.option_name_duplicate");
            }
        }
    }

    private static void transitions(List<Transition> list, Set<String> statusKeys, Set<String> fieldIds, String prefix, List<String> violations) {
        if (list == null) {
            add(violations, prefix + ".required");
            return;
        }
        if (list.size() > MAX_TRANSITIONS) {
            add(violations, prefix + ".too_many");
        }
        Set<List<String>> seen = new HashSet<>();
        for (Transition transition : list) {
            if (transition == null || !statusKeys.contains(transition.from()) || !statusKeys.contains(transition.to())) {
                add(violations, prefix + ".status_unknown");
            } else if (transition.from().equals(transition.to())) {
                add(violations, prefix + ".same_status");
            } else if (!seen.add(List.of(transition.from(), transition.to()))) {
                add(violations, prefix + ".duplicate");
            }
            if (transition == null) {
                continue;
            }
            if (transition.requiredFields().size() > MAX_REQUIRED_PER_TRANSITION) {
                add(violations, prefix + ".required_fields_too_many");
            }
            Set<String> required = new HashSet<>();
            for (String field : transition.requiredFields()) {
                if (field == null || !(REQUIRABLE_STANDARD.contains(field) || fieldIds.contains(field))) {
                    add(violations, prefix + ".required_field_unknown");
                } else if (!required.add(field)) {
                    add(violations, prefix + ".required_field_duplicate");
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ values

    /**
     * The stored text of a value for a custom field, or {@code null} when the value is not valid for it. A value is JSON
     * text, number or boolean as the API sends it; the stored text is the canonical form: text trimmed, a whole number
     * without sign for zero, a date as {@code yyyy-MM-dd}, a boolean as {@code true} or {@code false}, an option or an
     * account by its key or identifier. Whether the option is active or the account a member is checked by the caller.
     */
    public static String canonical(CustomField field, Object raw) {
        if (raw == null) {
            return null;
        }
        switch (field.type()) {
            case TEXT, LONGTEXT -> {
                if (!(raw instanceof String text)) {
                    return null;
                }
                String stripped = text.strip();
                int max = TEXT.equals(field.type()) ? TEXT_MAX : LONGTEXT_MAX;
                if (stripped.isEmpty() || stripped.codePointCount(0, stripped.length()) > max || stripped.indexOf('\u0000') >= 0) {
                    return null;
                }
                return stripped;
            }
            case INTEGER -> {
                if (!(raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte)) {
                    return null;
                }
                long value = ((Number) raw).longValue();
                // Without Math.abs, whose result for the smallest long is negative (review F2 of TASK-044).
                return value > INTEGER_MAX || value < -INTEGER_MAX ? null : Long.toString(value);
            }
            case DATE -> {
                if (!(raw instanceof String text)) {
                    return null;
                }
                try {
                    LocalDate date = LocalDate.parse(text.strip());
                    return date.toString().equals(text.strip()) ? date.toString() : null;
                } catch (DateTimeParseException invalid) {
                    return null;
                }
            }
            case BOOLEAN -> {
                return raw instanceof Boolean flag ? flag.toString() : null;
            }
            case SELECT -> {
                return raw instanceof String key && field.option(key).isPresent() ? key : null;
            }
            case USER -> {
                if (!(raw instanceof String text)) {
                    return null;
                }
                try {
                    return java.util.UUID.fromString(text.strip()).toString();
                } catch (IllegalArgumentException invalid) {
                    return null;
                }
            }
            default -> {
                return null;
            }
        }
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
