package taskmanager.tickets.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.IdentityVerification;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.projects.ProjectConfiguration.CustomField;
import taskmanager.tickets.TicketAccess;

/**
 * Tickets of a project with history and conflict detection (TASK-027, DEC-067), with the custom fields, the workflows of the
 * ticket types and the fields a transition requires of the project configuration (TASK-044, DEC-073).
 *
 * <p>Who may do what: the actions {@code ticket.read}, {@code ticket.create} and {@code ticket.edit} of the permissions
 * module in the context of the project, which need the membership. Each of the five fixed fields (title, description, type,
 * status, assignee) and each custom field ({@code custom.<key>}) is shown only with a read rule for the resource type
 * {@code ticket} and changed only with a write rule; a hidden field is left out of every answer and of the history. Without
 * {@code ticket.read}, {@code ticket.create} and {@code ticket.edit} the project does not exist for the caller (404).
 *
 * <p>Locks, in this order: rights, identity of the requester, project row ({@code FOR SHARE}, held by the project adapter
 * until the commit, so neither the membership nor the configuration of the project changes meanwhile), then the ticket row
 * ({@code FOR UPDATE}) or the counter of the project, then the account of a new assignee. A change checks with
 * {@code require} for exactly the fields it writes before it reads the ticket.
 *
 * <p>The configuration in force is always the current one: a published change counts for every ticket of the project at
 * once. Required fields bind a creation, a write that would empty them and a transition; a ticket that lacks a field
 * required later stays valid until someone writes it or takes a transition that requires it.
 */
@Service
public class TicketService {
    public static final String READ = "ticket.read";
    public static final String CREATE = "ticket.create";
    public static final String EDIT = "ticket.edit";
    static final String RESOURCE = "ticket";
    static final Set<String> FIELDS = Set.copyOf(ProjectConfiguration.FIELD_KEYS);
    /**
     * The fields of the history besides the custom ones: the five fields, the tags the tags module records (TASK-042) and the
     * attachments the files module records (TASK-051).
     */
    private static final Set<String> HISTORY_FIELDS = with(with(FIELDS, TicketAccess.TAGS_FIELD), TicketAccess.ATTACHMENTS_FIELD);
    private static final int TITLE_MAX = 200;
    private static final int DESCRIPTION_MAX = 20_000;

    private final TicketRepository tickets;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final IdentityVerification identity;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    TicketService(TicketRepository tickets, ProjectAccess projects, Permissions permissions, IdentityVerification identity, Clock clock,
            ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.projects = projects;
        this.permissions = permissions;
        this.identity = identity;
        this.clock = clock;
        this.events = events;
    }

    /** A ticket with the key of its project, the fields the caller may read and write and the configuration they refer to. */
    public record TicketView(Ticket ticket, String projectKey, Set<String> readable, Set<String> writable,
            ProjectConfiguration configuration) {
    }

    /** One version of the history with the changes of the fields the caller may read. */
    public record HistoryEntry(long version, Instant occurredAt, UUID actor, List<FieldChange> changes) {
    }

    public record FieldChange(String field, String from, String to) {
    }

    /** What a new ticket brings; {@code null} for a field that is not given. {@code custom} maps keys to JSON values. */
    public record Draft(String type, String title, String description, String assignee, Map<String, Object> custom) {
        public Draft {
            custom = custom == null ? Map.of() : custom;
        }
    }

    @Transactional
    public List<TicketView> list(AccessContext actor, UUID projectId, int limit, Long before) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = readable(actor, resource, projectId);
        Set<String> writable = writable(actor, resource, projectId);
        String key = projects.key(projectId).orElseThrow(TicketService::projectNotFound);
        ProjectConfiguration configuration = configuration(projectId);
        return tickets.list(actor.companyId(), projectId, limit, before).stream()
            .map(ticket -> new TicketView(ticket, key, read.readableFields(), writable, configuration)).toList();
    }

    @Transactional
    public TicketView get(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = readable(actor, resource, projectId);
        Ticket ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(TicketService::notFound);
        return view(ticket, read.readableFields(), writable(actor, resource, projectId));
    }

    /**
     * The history, oldest first. The changes of the fields of one version form one entry. Each assignment or removal of a
     * tag forms an entry of its own with the field {@code tags} and the version the ticket had then, so several entries can
     * share a version (TASK-042); they need the read rule for {@code tags}. The same holds for each addition or removal of an
     * attachment with the field {@code attachments} (TASK-051). A custom field is shown with its read rule.
     */
    @Transactional
    public List<HistoryEntry> history(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = inspect(actor, resource, READ, projectId, HISTORY_FIELDS);
        if (!read.allowed()) {
            throw projectNotFound();
        }
        Set<String> readable = read.readableFields();
        Ticket ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(TicketService::notFound);
        List<HistoryEntry> entries = new ArrayList<>();
        HistoryEntry fields = null;
        for (TicketRepository.Change change : tickets.changes(ticket.id())) {
            if (!readable.contains(change.field())) {
                continue;
            }
            FieldChange fieldChange = new FieldChange(change.field(), change.oldValue(), change.newValue());
            if (TicketAccess.TAGS_FIELD.equals(change.field()) || TicketAccess.ATTACHMENTS_FIELD.equals(change.field())) {
                entries.add(new HistoryEntry(change.version(), change.occurredAt(), change.actor(), List.of(fieldChange)));
                fields = null;
            } else if (fields != null && fields.version() == change.version()) {
                fields.changes().add(fieldChange);
            } else {
                fields = new HistoryEntry(change.version(), change.occurredAt(), change.actor(), new ArrayList<>(List.of(fieldChange)));
                entries.add(fields);
            }
        }
        return entries.stream().map(entry -> new HistoryEntry(entry.version(), entry.occurredAt(), entry.actor(), List.copyOf(entry.changes())))
            .toList();
    }

    @Transactional
    public TicketView create(AccessContext actor, UUID projectId, Draft draft) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        boolean read = allowed(actor, resource, READ);
        Decision create = inspect(actor, resource, CREATE, projectId, FIELDS);
        if (!read && !create.allowed() && !allowed(actor, resource, EDIT)) {
            throw projectNotFound();
        }
        if (!create.allowed()) {
            throw forbidden();
        }
        ProjectConfiguration configuration = configuration(projectId);
        long configurationVersion = projects.currentConfigurationVersion(projectId).orElseThrow(TicketService::projectNotFound);
        // A required field the caller may not write is a conflict of the configuration and the rights, not a missing input.
        for (String field : List.of("description", "assignee")) {
            if (configuration.isRequired(field) && !create.writableFields().contains(field)) {
                throw ApiProblemException.of(409, "ticket.required_field_inaccessible");
            }
        }
        for (CustomField field : configuration.customFields()) {
            if (field.required() && field.active() && !create.writableFields().contains(field.id())) {
                throw ApiProblemException.of(409, "ticket.required_field_inaccessible");
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", draft.title());
        values.put("description", draft.description());
        values.put("type", draft.type());
        values.put("assignee", draft.assignee());
        Set<String> written = new HashSet<>(Set.of("title", "type"));
        values.forEach((field, value) -> {
            if (value != null) {
                written.add(field);
            }
        });
        unknownCustomFields(configuration, draft.custom().keySet());
        Map<String, Object> given = new TreeMap<>();
        draft.custom().forEach((key, value) -> {
            if (value != null) {
                given.put(key, value);
                written.add(ProjectConfiguration.CUSTOM_PREFIX + key);
            }
        });
        require(actor, resource, CREATE, written);

        List<String> violations = new ArrayList<>();
        String title = title(values.get("title"), violations);
        String description = description(values.get("description"), configuration, violations);
        String type = values.get("type");
        if (type == null || !configuration.hasTicketType(type)) {
            violations.add("type.unknown");
        }
        UUID assignee = assignee(projectId, values.get("assignee"), configuration, violations);
        Map<String, String> custom = new TreeMap<>();
        for (CustomField field : configuration.customFields()) {
            if (!given.containsKey(field.key())) {
                if (field.required() && field.active()) {
                    violations.add(field.id() + ".required");
                }
                continue;
            }
            String value = customValue(projectId, field, given.get(field.key()), null, violations);
            if (value != null) {
                custom.put(field.key(), value);
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations.stream().distinct().toList());
        }
        Instant now = clock.instant();
        long number = tickets.nextNumber(projectId);
        Ticket ticket = new Ticket(UUID.randomUUID(), actor.companyId(), projectId, number, title, description, type,
            configuration.initialStatusOf(type), assignee, custom, configurationVersion, 1, now, actor.userId(), now, actor.userId());
        tickets.insert(ticket);
        ticket.allValues().forEach((field, value) -> {
            if (value != null) {
                tickets.change(ticket.id(), 1, now, actor.userId(), field, null, value);
            }
        });
        events.publishEvent(ticket.changed(configuration));
        return view(ticket, readableFields(actor, resource, projectId), writable(actor, resource, projectId));
    }

    /**
     * Changes the given fields; a {@code null} value empties description, assignee or a custom field. Fields that keep their
     * value are not written. Without any real change the ticket and its version stay as they are.
     */
    @Transactional
    public TicketView update(AccessContext actor, UUID projectId, long number, long expectedVersion, Map<String, String> changes,
            Map<String, Object> customChanges) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        boolean read = allowed(actor, resource, READ);
        boolean edit = allowed(actor, resource, EDIT);
        if (!read && !edit) {
            throw projectNotFound();
        }
        if (!edit) {
            throw forbidden();
        }
        // The check of edit has locked the project, so its configuration is fixed until the commit.
        ProjectConfiguration configuration = configuration(projectId);
        unknownCustomFields(configuration, customChanges.keySet());
        Set<String> written = new HashSet<>(changes.keySet());
        customChanges.keySet().forEach(key -> written.add(ProjectConfiguration.CUSTOM_PREFIX + key));
        require(actor, resource, EDIT, written);
        Ticket ticket = tickets.lock(actor.companyId(), projectId, number).orElseThrow(TicketService::notFound);
        if (ticket.version() != expectedVersion) {
            throw ApiProblemException.of(412, "ticket.version_conflict");
        }
        long configurationVersion = projects.currentConfigurationVersion(projectId).orElseThrow(TicketService::projectNotFound);

        List<String> violations = new ArrayList<>();
        String title = changes.containsKey("title") ? title(changes.get("title"), violations) : ticket.title();
        String description = changes.containsKey("description")
            ? description(changes.get("description"), configuration, violations) : ticket.description();
        String type = ticket.type();
        boolean typeChanged = false;
        if (changes.containsKey("type")) {
            type = changes.get("type");
            if (type == null || !configuration.hasTicketType(type)) {
                violations.add("type.unknown");
            } else {
                typeChanged = !type.equals(ticket.type());
            }
        }
        String status = ticket.status();
        boolean statusChanged = false;
        if (changes.containsKey("status")) {
            status = changes.get("status");
            if (status == null || !configuration.hasStatus(status)) {
                violations.add("status.unknown");
            } else {
                statusChanged = !status.equals(ticket.status());
            }
        }
        UUID assignee = changes.containsKey("assignee")
            ? assignee(projectId, changes.get("assignee"), configuration, violations) : ticket.assignee();
        Map<String, String> custom = new TreeMap<>(ticket.custom());
        for (var change : customChanges.entrySet()) {
            CustomField field = configuration.customField(change.getKey()).orElseThrow();
            String current = ticket.custom().get(change.getKey());
            if (change.getValue() == null) {
                if (field.required() && field.active()) {
                    violations.add(field.id() + ".required");
                }
                custom.remove(change.getKey());
                continue;
            }
            String value = customValue(projectId, field, change.getValue(), current, violations);
            if (value != null) {
                custom.put(change.getKey(), value);
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations.stream().distinct().toList());
        }
        if (statusChanged && !configuration.statusKeysOf(type).contains(status)) {
            // The status the ticket is moved to, alone or together with its type, does not exist in the workflow of the type.
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("status.unavailable"));
        }
        if (typeChanged && !statusChanged && !configuration.statusKeysOf(type).contains(status)) {
            // A type whose workflow does not know the status starts the ticket over in its first status (review F3 of
            // TASK-044). That is no transition, so no field it requires can be skipped, but the status is written.
            if (!writable(actor, resource, projectId).contains("status")) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("type.status_unavailable"));
            }
            status = configuration.initialStatusOf(type);
        } else if (statusChanged) {
            var transition = configuration.transition(type, ticket.status(), status)
                .orElseThrow(() -> ApiProblemException.of(409, "ticket.transition_not_allowed"));
            // A field the caller may not read or write is a conflict of the configuration and the rights, whatever it holds,
            // so the answer never tells whether a hidden field has a value (review F1 of TASK-044).
            Set<String> readable = readableFields(actor, resource, projectId);
            Set<String> writable = writable(actor, resource, projectId);
            for (String required : transition.requiredFields()) {
                if (requires(required, configuration) && !(readable.contains(required) && writable.contains(required))) {
                    throw ApiProblemException.of(409, "ticket.required_field_inaccessible");
                }
            }
            List<String> missing = new ArrayList<>();
            for (String required : transition.requiredFields()) {
                if (!hasValue(required, description, assignee, custom, configuration)) {
                    missing.add(required + ".required_for_transition");
                }
            }
            if (!missing.isEmpty()) {
                throw ApiProblemException.withViolations(400, "request.invalid", missing);
            }
        }

        Instant now = clock.instant();
        Ticket changed = new Ticket(ticket.id(), ticket.companyId(), projectId, number, title, description, type, status, assignee,
            custom, configurationVersion, ticket.version() + 1, ticket.createdAt(), ticket.createdBy(), now, actor.userId());
        Map<String, String> before = ticket.allValues();
        Map<String, String> after = changed.allValues();
        Set<String> fields = new java.util.TreeSet<>(before.keySet());
        fields.addAll(after.keySet());
        List<String> differing = fields.stream().filter(field -> !Objects.equals(before.get(field), after.get(field))).toList();
        if (differing.isEmpty()) {
            return view(ticket, readableFields(actor, resource, projectId), writable(actor, resource, projectId));
        }
        tickets.update(changed);
        for (String field : differing) {
            tickets.change(ticket.id(), changed.version(), now, actor.userId(), field, before.get(field), after.get(field));
        }
        events.publishEvent(changed.changed(configuration));
        return view(changed, readableFields(actor, resource, projectId), writable(actor, resource, projectId));
    }

    // ------------------------------------------------------------------------------------------------ rules

    private static String title(String raw, List<String> violations) {
        String title = raw == null ? null : raw.strip();
        if (title == null || title.isEmpty() || title.codePointCount(0, title.length()) > TITLE_MAX || title.indexOf('\u0000') >= 0) {
            violations.add("title.invalid");
            return null;
        }
        return title;
    }

    private static String description(String raw, ProjectConfiguration configuration, List<String> violations) {
        String description = raw == null || raw.isBlank() ? null : raw.strip();
        if (description == null && configuration.isRequired("description")) {
            violations.add("description.required");
        } else if (description != null && description.codePointCount(0, description.length()) > DESCRIPTION_MAX) {
            violations.add("description.too_long");
        } else if (description != null && description.indexOf('\u0000') >= 0) {
            // PostgreSQL cannot store the NUL character in text (review F2 of TASK-027).
            violations.add("description.invalid");
        }
        return description;
    }

    /** The assignee must be an active member of the project; the project row is locked, the account row gets locked here. */
    private UUID assignee(UUID projectId, String raw, ProjectConfiguration configuration, List<String> violations) {
        if (raw == null) {
            if (configuration.isRequired("assignee")) {
                violations.add("assignee.required");
            }
            return null;
        }
        UUID account;
        try {
            account = UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            violations.add("assignee.not_member");
            return null;
        }
        if (!projects.isMember(projectId, account) || !identity.accountIsActive(account)) {
            violations.add("assignee.not_member");
            return null;
        }
        return account;
    }

    /**
     * The canonical text of a value for a custom field, or {@code null} with a violation. A value the ticket already holds
     * is accepted as it is even if the field or option is inactive or the account left the project; only a new value is
     * checked against that (F-06 of DOMAIN_MODEL.md).
     */
    private String customValue(UUID projectId, CustomField field, Object raw, String current, List<String> violations) {
        String value = ProjectConfiguration.canonical(field, raw);
        if (value == null) {
            violations.add(field.id() + ".invalid");
            return null;
        }
        if (value.equals(current)) {
            return value;
        }
        if (!field.active()) {
            violations.add(field.id() + ".inactive");
            return null;
        }
        if (ProjectConfiguration.SELECT.equals(field.type()) && !field.option(value).orElseThrow().active()) {
            violations.add(field.id() + ".option_inactive");
            return null;
        }
        if (ProjectConfiguration.USER.equals(field.type()) && (!projects.isMember(projectId, UUID.fromString(value))
                || !identity.accountIsActive(UUID.fromString(value)))) {
            violations.add(field.id() + ".not_member");
            return null;
        }
        return value;
    }

    /** Whether a field a transition names is required at all: an inactive custom field requires nothing. */
    private static boolean requires(String field, ProjectConfiguration configuration) {
        if (!field.startsWith(ProjectConfiguration.CUSTOM_PREFIX)) {
            return true;
        }
        return configuration.customField(field.substring(ProjectConfiguration.CUSTOM_PREFIX.length())).map(CustomField::active).orElse(false);
    }

    /** A key in {@code customFields} the configuration does not know is a wrong request, not a missing right (review F5 of TASK-044). */
    private static void unknownCustomFields(ProjectConfiguration configuration, Set<String> keys) {
        if (keys.stream().anyMatch(key -> configuration.customField(key).isEmpty())) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("customFields.unknown"));
        }
    }

    /** Whether the field a transition requires has a value after the change. A field that is inactive is not required. */
    private static boolean hasValue(String field, String description, UUID assignee, Map<String, String> custom, ProjectConfiguration configuration) {
        return switch (field) {
            case "description" -> description != null;
            case "assignee" -> assignee != null;
            default -> {
                String key = field.substring(ProjectConfiguration.CUSTOM_PREFIX.length());
                yield configuration.customField(key).map(f -> !f.active() || custom.containsKey(key)).orElse(true);
            }
        };
    }

    // ------------------------------------------------------------------------------------------------ access

    private Supplier<ResourceContext> resource(AccessContext actor, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE, false);
    }

    /**
     * The decision for the five fixed fields and, once the project is locked, for the custom fields of its configuration.
     * The custom fields are asked separately because their identifiers are known only from the configuration, which may be
     * read only after the first check has locked the project.
     */
    private Decision inspect(AccessContext actor, Supplier<ResourceContext> resource, String action, UUID projectId,
            Set<String> fixed) {
        Decision standard = permissions.inspect(actor, resource, action, fixed);
        if (!standard.allowed()) {
            return standard;
        }
        List<String> custom = configuration(projectId).customFieldIds();
        if (custom.isEmpty()) {
            return standard;
        }
        Decision extra = permissions.inspect(actor, resource, action, Set.copyOf(custom));
        return new Decision(true, union(standard.readableFields(), extra.readableFields()),
            union(standard.writableFields(), extra.writableFields()));
    }

    /** The read decision with the readable fields; without {@code ticket.read} the project does not exist for the caller. */
    private Decision readable(AccessContext actor, Supplier<ResourceContext> resource, UUID projectId) {
        Decision read = inspect(actor, resource, READ, projectId, FIELDS);
        if (!read.allowed()) {
            throw projectNotFound();
        }
        return read;
    }

    /**
     * The fields the answer of a creation or change shows. Without {@code ticket.read} it shows none, as {@code ticket.create}
     * or {@code ticket.edit} alone may write a ticket they cannot read (review F1 of TASK-027).
     */
    private Set<String> readableFields(AccessContext actor, Supplier<ResourceContext> resource, UUID projectId) {
        Decision read = inspect(actor, resource, READ, projectId, FIELDS);
        return read.allowed() ? read.readableFields() : Set.of();
    }

    private Set<String> writable(AccessContext actor, Supplier<ResourceContext> resource, UUID projectId) {
        Decision edit = inspect(actor, resource, EDIT, projectId, FIELDS);
        return edit.allowed() ? edit.writableFields() : Set.of();
    }

    private boolean allowed(AccessContext actor, Supplier<ResourceContext> resource, String action) {
        return permissions.inspect(actor, resource, action, Set.of()).allowed();
    }

    private void require(AccessContext actor, Supplier<ResourceContext> resource, String action, Set<String> writeFields) {
        try {
            permissions.require(actor, resource, action, Set.of(), writeFields);
        } catch (PermissionDenied denied) {
            throw forbidden();
        }
    }

    private ProjectConfiguration configuration(UUID projectId) {
        long version = projects.currentConfigurationVersion(projectId).orElseThrow(TicketService::projectNotFound);
        return projects.configuration(projectId, version).orElseThrow(TicketService::projectNotFound);
    }

    private TicketView view(Ticket ticket, Set<String> readable, Set<String> writable) {
        String key = projects.key(ticket.projectId()).orElseThrow(TicketService::projectNotFound);
        return new TicketView(ticket, key, readable, writable, configuration(ticket.projectId()));
    }

    private static Set<String> with(Set<String> fields, String field) {
        Set<String> all = new HashSet<>(fields);
        all.add(field);
        return Set.copyOf(all);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> all = new HashSet<>(first);
        all.addAll(second);
        return all;
    }

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }

    private static ApiProblemException notFound() {
        return ApiProblemException.of(404, "ticket.not_found");
    }

    private static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
