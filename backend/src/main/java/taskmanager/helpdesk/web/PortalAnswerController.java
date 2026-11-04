package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.internal.PortalConversation;
import taskmanager.permissions.AccessContext;

/**
 * The portal conversation in the internal ticket view (TASK-076, DEC-106): the posts of a request with the read rule of
 * {@code mail}, and the answer of the support through the portal with the rights of a mail answer.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tickets/{number}/helpdesk/portal-posts")
class PortalAnswerController {
    private final PortalConversation conversation;
    private final Requests requests;

    PortalAnswerController(PortalConversation conversation, Requests requests) {
        this.conversation = conversation;
        this.requests = requests;
    }

    record AnswerRequest(String body) {
    }

    record PostBody(UUID id, String authorKind, UUID authorId, String body, Instant createdAt,
            List<PortalConversationController.FileBody> attachments) {
        static PostBody of(PortalConversation.PostView p) {
            return new PostBody(p.id(), p.authorKind(), p.authorId(), p.body(), p.createdAt(), PortalConversationController.FileBody.of(p.attachments()));
        }
    }

    @GetMapping
    List<PostBody> list(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        return Requests.retrying(() -> conversation.posts(actor, projectId, number)).stream().map(PostBody::of).toList();
    }

    @PostMapping
    ResponseEntity<PostBody> answer(@PathVariable UUID projectId, @PathVariable long number, @RequestBody AnswerRequest request) {
        AccessContext actor = requests.actor();
        PortalConversation.PostView post = Requests.retrying(() -> conversation.answer(actor, projectId, number, request.body()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PostBody.of(post));
    }
}
