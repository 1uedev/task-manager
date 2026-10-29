package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.internal.ReplyService;
import taskmanager.permissions.AccessContext;

/**
 * The replies that are not known to be sent (TASK-058): pending, failed and uncertain ones of the projects in which the caller
 * may send replies, so that whoever is responsible sees what still waits or needs a decision.
 */
@RestController
@RequestMapping("/api/v1/helpdesk/replies")
class ReplyController {
    private final ReplyService replies;
    private final Requests requests;

    ReplyController(ReplyService replies, Requests requests) {
        this.replies = replies;
        this.requests = requests;
    }

    record OpenBody(UUID id, UUID projectId, long number, String toAddress, String subject, String state, String errorCode,
            Instant createdAt, Instant attemptedAt) {
    }

    @GetMapping
    List<OpenBody> open() {
        AccessContext actor = requests.actor();
        return Requests.retrying(() -> replies.open(actor)).stream().map(open -> {
            var r = open.reply();
            return new OpenBody(r.id(), r.projectId(), open.number(), r.toAddress(), r.subject(), r.state().equals("sending") ? "pending" : r.state(),
                r.errorCode(), r.createdAt(), r.attemptedAt());
        }).toList();
    }
}
