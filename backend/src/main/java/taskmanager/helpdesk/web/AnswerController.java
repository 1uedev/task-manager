package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.internal.AnswerService;
import taskmanager.helpdesk.internal.Message;
import taskmanager.permissions.AccessContext;

/**
 * New answers of customers (TASK-067): mails that joined an existing ticket and nobody has marked as seen, in the projects in
 * which the caller may send replies.
 */
@RestController
@RequestMapping("/api/v1/helpdesk/answers")
class AnswerController {
    private final AnswerService answers;
    private final Requests requests;

    AnswerController(AnswerService answers, Requests requests) {
        this.answers = answers;
        this.requests = requests;
    }

    record AnswerBody(UUID id, UUID projectId, long number, String fromAddress, String fromName, String subject, Instant receivedAt,
            long version) {
    }

    record SeenBody(UUID id, long version) {
    }

    @GetMapping
    List<AnswerBody> list() {
        AccessContext actor = requests.actor();
        return Requests.retrying(() -> answers.list(actor)).stream().map(answer -> {
            Message m = answer.mail();
            return new AnswerBody(m.id(), m.projectId(), answer.number(), m.fromAddress(), m.fromName(), m.subject(), m.receivedAt(), m.version());
        }).toList();
    }

    /** Marks a new answer as seen for everybody; needs If-Match with the version of the mail. */
    @PostMapping("/{id}/seen")
    ResponseEntity<SeenBody> seen(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        AccessContext actor = requests.actor();
        long version = Requests.expectedVersion(ifMatch);
        Message mail = Requests.retrying(() -> answers.seen(actor, id, version));
        return ResponseEntity.ok().eTag(Requests.etag(mail.version())).body(new SeenBody(mail.id(), mail.version()));
    }
}
