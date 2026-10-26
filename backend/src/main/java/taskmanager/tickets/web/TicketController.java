package taskmanager.tickets.web;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.projects.ProjectConfiguration.CustomField;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.internal.Ticket;
import taskmanager.tickets.internal.TicketService;
import taskmanager.tickets.internal.TicketService.Draft;
import taskmanager.tickets.internal.TicketService.HistoryEntry;
import taskmanager.tickets.internal.TicketService.TicketView;
import tools.jackson.databind.JsonNode;

/**
 * Tickets of a project (TASK-027). Every endpoint needs a complete login; the service decides who may read or change a
 * ticket and which fields. The ETag is the version of the ticket, and every change needs it as If-Match. A lock that could
 * not be taken in time, or a deadlock PostgreSQL resolved, ends the transaction; the answer is 503 {@code ticket.busy} with
 * {@code Retry-After}, and the whole request may be repeated.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tickets")
class TicketController {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_CUSTOM_VALUES = 30;
    private static final String KEY = "[a-z][a-z0-9_]{0,39}";

    private final TicketService tickets;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    TicketController(TicketService tickets, AccessContexts identity, PermissionContexts contexts) {
        this.tickets = tickets;
        this.identity = identity;
        this.contexts = contexts;
    }

    record TicketBody(UUID id, UUID projectId, String key, long number, long version, long configurationVersion,
            Map<String, String> fields, Map<String, Object> customFields, List<String> writableFields, Instant createdAt,
            UUID createdBy, Instant updatedAt, UUID updatedBy) {
        static TicketBody of(TicketView view) {
            Ticket ticket = view.ticket();
            Map<String, String> fields = new LinkedHashMap<>();
            ticket.values().forEach((field, value) -> {
                if (view.readable().contains(field)) {
                    fields.put(field, value);
                }
            });
            // The custom fields the caller may read and that hold a value, typed as their kind says; a field of the
            // configuration that is writable is offered even without a value, so an interface can show it.
            Map<String, Object> custom = new LinkedHashMap<>();
            List<String> writable = new ArrayList<>(ProjectConfiguration.FIELD_KEYS.stream().filter(view.writable()::contains).toList());
            for (CustomField field : view.configuration().customFields()) {
                if (view.readable().contains(field.id()) && ticket.custom().containsKey(field.key())) {
                    custom.put(field.key(), typed(field, ticket.custom().get(field.key())));
                }
                if (field.active() && view.writable().contains(field.id())) {
                    writable.add(field.id());
                }
            }
            // Tags and attachments are changed through their own endpoints, but follow the same rules; naming them here lets an
            // interface offer exactly what the server allows (review F1 of TASK-051).
            for (String field : List.of(TicketAccess.TAGS_FIELD, TicketAccess.ATTACHMENTS_FIELD)) {
                if (view.writable().contains(field)) {
                    writable.add(field);
                }
            }
            return new TicketBody(ticket.id(), ticket.projectId(), view.projectKey() + "-" + ticket.number(), ticket.number(),
                ticket.version(), ticket.configurationVersion(), fields, custom, writable, ticket.createdAt(), ticket.createdBy(),
                ticket.updatedAt(), ticket.updatedBy());
        }

        /** The JSON value of a stored canonical text: a whole number and a boolean as such, everything else as text. */
        private static Object typed(CustomField field, String stored) {
            return switch (field.type()) {
                case ProjectConfiguration.INTEGER -> Long.parseLong(stored);
                case ProjectConfiguration.BOOLEAN -> Boolean.parseBoolean(stored);
                default -> stored;
            };
        }
    }

    /** The fields a creation may name; the status always starts as the initial status of the configuration. */
    private static final List<String> CREATE_FIELDS = List.of("type", "title", "description", "assignee");

    @GetMapping
    List<TicketBody> list(@PathVariable UUID projectId, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long before) {
        AccessContext actor = actor();
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT || (before != null && before < 1)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("paging.invalid"));
        }
        return retrying(() -> tickets.list(actor, projectId, size, before)).stream().map(TicketBody::of).toList();
    }

    @PostMapping
    ResponseEntity<TicketBody> create(@PathVariable UUID projectId, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        if (body == null || !body.isObject()) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("title.invalid", "type.unknown"));
        }
        Map<String, Object> custom = customFields(body);
        Map<String, String> values = strings(body, CREATE_FIELDS);
        Draft draft = new Draft(values.get("type"), values.get("title"), values.get("description"), values.get("assignee"), custom);
        return ticket(HttpStatus.CREATED, retrying(() -> tickets.create(actor, projectId, draft)));
    }

    @GetMapping("/{number}")
    ResponseEntity<TicketBody> get(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = actor();
        return ticket(HttpStatus.OK, retrying(() -> tickets.get(actor, projectId, number)));
    }

    /** Changes exactly the fields in the body; {@code null} empties description or assignee. */
    @PatchMapping("/{number}")
    ResponseEntity<TicketBody> update(@PathVariable UUID projectId, @PathVariable long number,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        long expected = expectedVersion(ifMatch);
        Map<String, String> changes = changes(body);
        Map<String, Object> custom = customFields(body);
        return ticket(HttpStatus.OK, retrying(() -> tickets.update(actor, projectId, number, expected, changes, custom)));
    }

    @GetMapping("/{number}/history")
    List<HistoryEntry> history(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = actor();
        return retrying(() -> tickets.history(actor, projectId, number));
    }

    private static Map<String, String> changes(JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("fields.required"));
        }
        return strings(body, ProjectConfiguration.FIELD_KEYS);
    }

    /**
     * The values of {@code customFields} by field key: text, a whole number or a boolean as such, {@code null} to empty a
     * field. Anything else is refused here; whether the key exists and the value fits its field the service decides.
     */
    private static Map<String, Object> customFields(JsonNode body) {
        JsonNode node = body.get("customFields");
        Map<String, Object> custom = new LinkedHashMap<>();
        if (node == null) {
            return custom;
        }
        if (!node.isObject() || node.size() > MAX_CUSTOM_VALUES) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("customFields.invalid"));
        }
        List<String> violations = new ArrayList<>();
        for (var entry : node.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!key.matches(KEY)) {
                violations.add("customFields.unknown");
            } else if (value.isNull()) {
                custom.put(key, null);
            } else if (value.isString()) {
                custom.put(key, value.asString());
            } else if (value.isBoolean()) {
                custom.put(key, value.asBoolean());
            } else if (value.isIntegralNumber() && value.canConvertToLong()) {
                custom.put(key, value.asLong());
            } else {
                violations.add(ProjectConfiguration.CUSTOM_PREFIX + key + ".invalid");
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations.stream().distinct().toList());
        }
        return custom;
    }

    /** The string or {@code null} values of an object body; another key or another kind of value is refused. */
    private static Map<String, String> strings(JsonNode body, List<String> allowed) {
        Map<String, String> changes = new HashMap<>();
        List<String> violations = new ArrayList<>();
        for (var entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if ("customFields".equals(field)) {
                continue;
            }
            if (!allowed.contains(field)) {
                violations.add("fields.unknown");
            } else if (value.isNull()) {
                changes.put(field, null);
            } else if (value.isString()) {
                changes.put(field, value.asString());
            } else {
                violations.add(field + ".invalid");
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations.stream().distinct().toList());
        }
        return changes;
    }

    /** The permissions context of a complete login; a login that still has to change its password is refused. */
    private AccessContext actor() {
        var request = identity.require();
        if (request.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    /** Runs one transaction of the service and answers a lock timeout or a deadlock as a retryable 503 (P4 of TASK-025). */
    private static <T> T retrying(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "ticket.busy", 1);
            }
            throw failure;
        }
    }

    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || (cause instanceof SQLException sql && RETRYABLE_STATES.contains(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<TicketBody> ticket(HttpStatus status, TicketView view) {
        return ResponseEntity.status(status).eTag("\"" + view.ticket().version() + "\"").body(TicketBody.of(view));
    }

    private static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "ticket.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "ticket.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "ticket.version_conflict");
        }
    }
}
