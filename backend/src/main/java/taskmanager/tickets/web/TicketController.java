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

    private final TicketService tickets;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    TicketController(TicketService tickets, AccessContexts identity, PermissionContexts contexts) {
        this.tickets = tickets;
        this.identity = identity;
        this.contexts = contexts;
    }

    record TicketBody(UUID id, UUID projectId, String key, long number, long version, long configurationVersion,
            Map<String, String> fields, List<String> writableFields, Instant createdAt, UUID createdBy, Instant updatedAt,
            UUID updatedBy) {
        static TicketBody of(TicketView view) {
            Ticket ticket = view.ticket();
            Map<String, String> fields = new LinkedHashMap<>();
            ticket.values().forEach((field, value) -> {
                if (view.readable().contains(field)) {
                    fields.put(field, value);
                }
            });
            List<String> writable = ProjectConfiguration.FIELD_KEYS.stream().filter(view.writable()::contains).toList();
            return new TicketBody(ticket.id(), ticket.projectId(), view.projectKey() + "-" + ticket.number(), ticket.number(),
                ticket.version(), ticket.configurationVersion(), fields, writable, ticket.createdAt(), ticket.createdBy(),
                ticket.updatedAt(), ticket.updatedBy());
        }
    }

    record CreateRequest(String type, String title, String description, String assignee) {
    }

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
    ResponseEntity<TicketBody> create(@PathVariable UUID projectId, @RequestBody(required = false) CreateRequest body) {
        AccessContext actor = actor();
        if (body == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("title.invalid", "type.unknown"));
        }
        Draft draft = new Draft(body.type(), body.title(), body.description(), body.assignee());
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
        return ticket(HttpStatus.OK, retrying(() -> tickets.update(actor, projectId, number, expected, changes)));
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
        Map<String, String> changes = new HashMap<>();
        List<String> violations = new ArrayList<>();
        for (var entry : body.properties()) {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if (!ProjectConfiguration.FIELD_KEYS.contains(field)) {
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
