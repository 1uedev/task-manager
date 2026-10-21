package taskmanager.search.web;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;
import taskmanager.search.internal.SearchService;
import taskmanager.search.internal.SearchService.Page;
import taskmanager.search.internal.SearchService.ProjectHit;
import taskmanager.search.internal.SearchService.ProjectQuery;
import taskmanager.search.internal.SearchService.TagRef;
import taskmanager.search.internal.SearchService.TicketHit;
import taskmanager.search.internal.SearchService.TicketQuery;

/**
 * Search for tickets and projects (TASK-029). Every endpoint needs a complete login; the service decides what the caller
 * may find. Repeated parameters combine with "or" inside one filter and with "and" between filters; every given tag is
 * required. A lock that could not be taken in time ends the search with 503 {@code search.busy} and {@code Retry-After}.
 */
@RestController
class SearchController {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_OFFSET = 1000;
    private static final int MAX_VALUES = 20;
    private static final int MAX_VALUE_LENGTH = 100;

    private final SearchService search;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    SearchController(SearchService search, AccessContexts identity, PermissionContexts contexts) {
        this.search = search;
        this.identity = identity;
        this.contexts = contexts;
    }

    record TicketHitBody(UUID id, UUID projectId, String key, long number, Map<String, String> fields, List<TagRef> tags) {
        static TicketHitBody of(TicketHit hit) {
            return new TicketHitBody(hit.id(), hit.projectId(), hit.projectKey() + "-" + hit.number(), hit.number(), hit.fields(),
                hit.tags());
        }
    }

    record PageBody<T>(long total, List<T> items) {
    }

    @GetMapping("/api/v1/search/tickets")
    PageBody<TicketHitBody> tickets(@RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> project, @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> type, @RequestParam(required = false) List<String> assignee,
            @RequestParam(required = false) List<String> tag, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        AccessContext actor = actor();
        List<String> violations = new ArrayList<>();
        TicketQuery query = new TicketQuery(q, ids(project, "project", violations), texts(status, "status", violations),
            texts(type, "type", violations), ids(assignee, "assignee", violations), ids(tag, "tag", violations),
            limit(limit, violations), offset(offset, violations));
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Page<TicketHit> page = retrying(() -> search.tickets(actor, query));
        return new PageBody<>(page.total(), page.items().stream().map(TicketHitBody::of).toList());
    }

    @GetMapping("/api/v1/search/projects")
    PageBody<ProjectHit> projects(@RequestParam(required = false) String q, @RequestParam(required = false) List<String> tag,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        AccessContext actor = actor();
        List<String> violations = new ArrayList<>();
        ProjectQuery query = new ProjectQuery(q, ids(tag, "tag", violations), limit(limit, violations), offset(offset, violations));
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Page<ProjectHit> page = retrying(() -> search.projects(actor, query));
        return new PageBody<>(page.total(), page.items());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static List<UUID> ids(List<String> raw, String name, List<String> violations) {
        List<UUID> ids = new ArrayList<>();
        if (raw == null) {
            return ids;
        }
        if (raw.size() > MAX_VALUES) {
            violations.add(name + ".invalid");
            return ids;
        }
        for (String value : raw) {
            try {
                UUID id = UUID.fromString(value.strip());
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (IllegalArgumentException invalid) {
                violations.add(name + ".invalid");
                return List.of();
            }
        }
        return ids;
    }

    private static List<String> texts(List<String> raw, String name, List<String> violations) {
        List<String> texts = new ArrayList<>();
        if (raw == null) {
            return texts;
        }
        for (String value : raw) {
            String text = value.strip();
            if (raw.size() > MAX_VALUES || text.isEmpty() || text.length() > MAX_VALUE_LENGTH || text.indexOf('\u0000') >= 0) {
                violations.add(name + ".invalid");
                return List.of();
            }
            if (!texts.contains(text)) {
                texts.add(text);
            }
        }
        return texts;
    }

    private static int limit(Integer limit, List<String> violations) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            violations.add("paging.invalid");
        }
        return size;
    }

    private static int offset(Integer offset, List<String> violations) {
        int start = offset == null ? 0 : offset;
        if ((start < 0 || start > MAX_OFFSET) && !violations.contains("paging.invalid")) {
            violations.add("paging.invalid");
        }
        return start;
    }

    private AccessContext actor() {
        var request = identity.require();
        if (request.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    /** Runs the search transaction and answers a lock timeout or a deadlock as a retryable 503 (P4 of TASK-025). */
    private static <T> T retrying(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "search.busy", 1);
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
}
