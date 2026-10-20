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
import java.util.UUID;
import java.util.function.Supplier;
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

/**
 * Tickets of a project with history and conflict detection (TASK-027, DEC-067).
 *
 * <p>Who may do what: the actions {@code ticket.read}, {@code ticket.create} and {@code ticket.edit} of the permissions
 * module in the context of the project, which need the membership. Each of the five fields (title, description, type,
 * status, assignee) is shown only with a read rule for the resource type {@code ticket} and changed only with a write rule;
 * a hidden field is left out of every answer and of the history. Without {@code ticket.read}, {@code ticket.create} and
 * {@code ticket.edit} the project does not exist for the caller (404).
 *
 * <p>Locks, in this order: rights, identity of the requester, project row ({@code FOR SHARE}, held by the project adapter
 * until the commit, so neither the membership nor the configuration of the project changes meanwhile), then the ticket row
 * ({@code FOR UPDATE}) or the counter of the project, then the account of a new assignee. A change checks with
 * {@code require} for exactly the fields it writes before it reads the ticket.
 */
@Service
public class TicketService {
    public static final String READ = "ticket.read";
    public static final String CREATE = "ticket.create";
    public static final String EDIT = "ticket.edit";
    static final String RESOURCE = "ticket";
    static final Set<String> FIELDS = Set.copyOf(ProjectConfiguration.FIELD_KEYS);
    private static final int TITLE_MAX = 200;
    private static final int DESCRIPTION_MAX = 20_000;

    private final TicketRepository tickets;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final IdentityVerification identity;
    private final Clock clock;

    TicketService(TicketRepository tickets, ProjectAccess projects, Permissions permissions, IdentityVerification identity, Clock clock) {
        this.tickets = tickets;
        this.projects = projects;
        this.permissions = permissions;
        this.identity = identity;
        this.clock = clock;
    }

    /** A ticket with the key of its project and the fields the caller may read and write. */
    public record TicketView(Ticket ticket, String projectKey, Set<String> readable, Set<String> writable) {
    }

    /** One version of the history with the changes of the fields the caller may read. */
    public record HistoryEntry(long version, Instant occurredAt, UUID actor, List<FieldChange> changes) {
    }

    public record FieldChange(String field, String from, String to) {
    }

    /** What a new ticket brings; {@code null} for a field that is not given. */
    public record Draft(String type, String title, String description, String assignee) {
    }

    @Transactional
    public List<TicketView> list(AccessContext actor, UUID projectId, int limit, Long before) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = readable(actor, resource);
        Set<String> writable = writable(actor, resource);
        String key = projects.key(projectId).orElseThrow(TicketService::projectNotFound);
        return tickets.list(actor.companyId(), projectId, limit, before).stream()
            .map(ticket -> new TicketView(ticket, key, read.readableFields(), writable)).toList();
    }

    @Transactional
    public TicketView get(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Decision read = readable(actor, resource);
        Ticket ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(TicketService::notFound);
        return view(ticket, read.readableFields(), writable(actor, resource));
    }

    @Transactional
    public List<HistoryEntry> history(AccessContext actor, UUID projectId, long number) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        Set<String> readable = readable(actor, resource).readableFields();
        Ticket ticket = tickets.find(actor.companyId(), projectId, number).orElseThrow(TicketService::notFound);
        Map<Long, HistoryEntry> entries = new LinkedHashMap<>();
        for (TicketRepository.Change change : tickets.changes(ticket.id())) {
            if (!readable.contains(change.field())) {
                continue;
            }
            entries.computeIfAbsent(change.version(), version -> new HistoryEntry(version, change.occurredAt(), change.actor(), new ArrayList<>()))
                .changes().add(new FieldChange(change.field(), change.oldValue(), change.newValue()));
        }
        return List.copyOf(entries.values());
    }

    @Transactional
    public TicketView create(AccessContext actor, UUID projectId, Draft draft) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        boolean read = allowed(actor, resource, READ);
        Decision create = permissions.inspect(actor, resource, CREATE, FIELDS);
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
        require(actor, resource, CREATE, written);

        List<String> violations = new ArrayList<>();
        String title = title(values.get("title"), violations);
        String description = description(values.get("description"), configuration, violations);
        String type = values.get("type");
        if (type == null || !configuration.hasTicketType(type)) {
            violations.add("type.unknown");
        }
        UUID assignee = assignee(projectId, values.get("assignee"), configuration, violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        long number = tickets.nextNumber(projectId);
        Ticket ticket = new Ticket(UUID.randomUUID(), actor.companyId(), projectId, number, title, description, type,
            configuration.initialStatus(), assignee, configurationVersion, 1, now, actor.userId(), now, actor.userId());
        tickets.insert(ticket);
        ticket.values().forEach((field, value) -> {
            if (value != null) {
                tickets.change(ticket.id(), 1, now, actor.userId(), field, null, value);
            }
        });
        return view(ticket, readable(actor, resource).readableFields(), writable(actor, resource));
    }

    /**
     * Changes the given fields; a {@code null} value empties description or assignee. Fields that keep their value are
     * not written. Without any real change the ticket and its version stay as they are.
     */
    @Transactional
    public TicketView update(AccessContext actor, UUID projectId, long number, long expectedVersion, Map<String, String> changes) {
        Supplier<ResourceContext> resource = resource(actor, projectId);
        boolean read = allowed(actor, resource, READ);
        boolean edit = allowed(actor, resource, EDIT);
        if (!read && !edit) {
            throw projectNotFound();
        }
        if (!edit) {
            throw forbidden();
        }
        require(actor, resource, EDIT, changes.keySet());
        Ticket ticket = tickets.lock(actor.companyId(), projectId, number).orElseThrow(TicketService::notFound);
        if (ticket.version() != expectedVersion) {
            throw ApiProblemException.of(412, "ticket.version_conflict");
        }
        ProjectConfiguration configuration = configuration(projectId);
        long configurationVersion = projects.currentConfigurationVersion(projectId).orElseThrow(TicketService::projectNotFound);

        List<String> violations = new ArrayList<>();
        String title = changes.containsKey("title") ? title(changes.get("title"), violations) : ticket.title();
        String description = changes.containsKey("description")
            ? description(changes.get("description"), configuration, violations) : ticket.description();
        String type = ticket.type();
        if (changes.containsKey("type")) {
            type = changes.get("type");
            if (type == null || !configuration.hasTicketType(type)) {
                violations.add("type.unknown");
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
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        if (statusChanged && !configuration.allowsTransition(ticket.status(), status)) {
            throw ApiProblemException.of(409, "ticket.transition_not_allowed");
        }

        Instant now = clock.instant();
        Ticket changed = new Ticket(ticket.id(), ticket.companyId(), projectId, number, title, description, type, status, assignee,
            configurationVersion, ticket.version() + 1, ticket.createdAt(), ticket.createdBy(), now, actor.userId());
        Map<String, String> before = ticket.values();
        Map<String, String> after = changed.values();
        List<String> differing = before.keySet().stream().filter(field -> !Objects.equals(before.get(field), after.get(field))).toList();
        if (differing.isEmpty()) {
            return view(ticket, readable(actor, resource).readableFields(), writable(actor, resource));
        }
        tickets.update(changed);
        for (String field : differing) {
            tickets.change(ticket.id(), changed.version(), now, actor.userId(), field, before.get(field), after.get(field));
        }
        return view(changed, readable(actor, resource).readableFields(), writable(actor, resource));
    }

    // ------------------------------------------------------------------------------------------------ rules

    private static String title(String raw, List<String> violations) {
        String title = raw == null ? null : raw.strip();
        if (title == null || title.isEmpty() || title.codePointCount(0, title.length()) > TITLE_MAX) {
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

    // ------------------------------------------------------------------------------------------------ access

    private Supplier<ResourceContext> resource(AccessContext actor, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE, false);
    }

    /** The read decision with the readable fields; without {@code ticket.read} the project does not exist for the caller. */
    private Decision readable(AccessContext actor, Supplier<ResourceContext> resource) {
        Decision read = permissions.inspect(actor, resource, READ, FIELDS);
        if (!read.allowed()) {
            throw projectNotFound();
        }
        return read;
    }

    private Set<String> writable(AccessContext actor, Supplier<ResourceContext> resource) {
        Decision edit = permissions.inspect(actor, resource, EDIT, FIELDS);
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
        return new TicketView(ticket, key, readable, writable);
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
