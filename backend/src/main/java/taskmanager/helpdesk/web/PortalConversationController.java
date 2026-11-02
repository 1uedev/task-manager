package taskmanager.helpdesk.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.files.AttachmentLookup;
import taskmanager.helpdesk.internal.PortalConversation;
import taskmanager.permissions.AccessContext;

/**
 * The conversation of the customer portal for portal accounts (TASK-076, DEC-106): creating a request, its conversation, a post,
 * the marker of new answers. An internal account gets 404 on every route, a request of another organization 404 like an
 * unknown one.
 */
@RestController
@RequestMapping("/api/v1/portal/projects/{projectId}")
class PortalConversationController {
    private final PortalConversation conversation;
    private final Requests requests;

    PortalConversationController(PortalConversation conversation, Requests requests) {
        this.conversation = conversation;
        this.requests = requests;
    }

    record CreateRequest(String type, String title, String description) {
    }

    record CreatedBody(UUID id, long number) {
    }

    record PostRequest(String body, List<UUID> attachmentIds) {
    }

    record FileBody(UUID id, String name, long size, Instant uploadedAt) {
        static List<FileBody> of(List<AttachmentLookup.Summary> files) {
            return files.stream().map(f -> new FileBody(f.id(), f.name(), f.size(), f.uploadedAt())).toList();
        }
    }

    /** An entry of the conversation; {@code author} is {@code customer} or {@code support}, {@code own} marks posts of the caller. */
    record EntryBody(String kind, UUID id, Instant at, String author, boolean own, String subject, String body, List<FileBody> attachments) {
        static EntryBody of(PortalConversation.Entry e) {
            return new EntryBody(e.kind(), e.id(), e.at(), e.author(), e.own(), e.subject(), e.body(), FileBody.of(e.attachments()));
        }
    }

    record ActivityBody(long number, Instant lastActivityAt, Instant lastSupportAt, Instant seenAt, boolean unread) {
    }

    @PostMapping("/tickets")
    ResponseEntity<CreatedBody> create(@PathVariable UUID projectId, @RequestBody CreateRequest request) {
        AccessContext actor = requests.actor();
        PortalConversation.Created created = Requests.retrying(() ->
            conversation.create(actor, projectId, request.type(), request.title(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedBody(created.ticketId(), created.number()));
    }

    @GetMapping("/activity")
    List<ActivityBody> activity(@PathVariable UUID projectId) {
        AccessContext actor = requests.actor();
        return Requests.retrying(() -> conversation.activity(actor, projectId)).stream()
            .map(a -> new ActivityBody(a.number(), a.lastActivityAt(), a.lastSupportAt(), a.seenAt(), a.unread())).toList();
    }

    @GetMapping("/tickets/{number}/conversation")
    List<EntryBody> timeline(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        return Requests.retrying(() -> conversation.timeline(actor, projectId, number)).stream().map(EntryBody::of).toList();
    }

    @PostMapping("/tickets/{number}/conversation")
    ResponseEntity<EntryBody> post(@PathVariable UUID projectId, @PathVariable long number, @RequestBody PostRequest request) {
        AccessContext actor = requests.actor();
        PortalConversation.Entry entry = Requests.retrying(() ->
            conversation.post(actor, projectId, number, request.body(), request.attachmentIds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(EntryBody.of(entry));
    }

    @PutMapping("/tickets/{number}/seen")
    ResponseEntity<Void> seen(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        Requests.retrying(() -> {
            conversation.seen(actor, projectId, number);
            return null;
        });
        return ResponseEntity.noContent().build();
    }
}
