package taskmanager.sla.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.permissions.AccessContext;
import taskmanager.sla.internal.TicketSla;

/**
 * The list "SLA Eskalationen" (TASK-070): for every full login the reached stages of its groups on tickets it may read; for
 * someone in no recipient group the list is empty.
 */
@RestController
@RequestMapping("/api/v1/sla/escalations")
class SlaEscalationController {
    private final TicketSla service;
    private final SlaRequests requests;

    SlaEscalationController(TicketSla service, SlaRequests requests) {
        this.service = service;
        this.requests = requests;
    }

    @GetMapping
    List<TicketSla.EscalationItem> escalations() {
        AccessContext actor = requests.actor();
        return SlaRequests.retrying(() -> service.escalations(actor));
    }
}
