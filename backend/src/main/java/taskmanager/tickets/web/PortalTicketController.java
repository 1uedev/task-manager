package taskmanager.tickets.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;
import taskmanager.tickets.internal.TicketService;
import taskmanager.tickets.web.TicketController.TicketBody;

/**
 * The requests of the organization of a portal account (TASK-075, DEC-105): the list of one project and one request, with the
 * fields the groups of the account may read and without writable fields. An internal account, a project without membership
 * and a request of another organization answer 404 as if they did not exist; history, attachments and search stay closed
 * to portal accounts.
 */
@RestController
@RequestMapping("/api/v1/portal/projects/{projectId}/tickets")
class PortalTicketController {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final TicketService tickets;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    PortalTicketController(TicketService tickets, AccessContexts identity, PermissionContexts contexts) {
        this.tickets = tickets;
        this.identity = identity;
        this.contexts = contexts;
    }

    @GetMapping
    List<TicketBody> list(@PathVariable UUID projectId, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long before, @RequestParam(required = false) String status,
            @RequestParam(required = false) String state, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID tag) {
        AccessContext actor = actor();
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT || (before != null && before < 1)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("paging.invalid"));
        }
        if (q != null && q.length() > 200) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("q.invalid"));
        }
        return TicketController.retrying(() -> tickets.portalList(actor, projectId, size, before,
            new TicketService.PortalFilter(status, state, q, tag))).stream().map(TicketBody::of).toList();
    }

    @GetMapping("/{number}")
    ResponseEntity<TicketBody> get(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = actor();
        return TicketController.ticket(HttpStatus.OK, TicketController.retrying(() -> tickets.portalGet(actor, projectId, number)));
    }

    private AccessContext actor() {
        if (identity.require().level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }
}
