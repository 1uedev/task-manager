package taskmanager.assist.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.assist.internal.TicketAssist;
import taskmanager.helpdesk.TicketConversation;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;

/**
 * The AI assistance at a ticket (TASK-126): which use case the person may start, the start of the summary and of the draft of an
 * answer by mail or in the portal. The execution is read and cancelled through {@code /api/v1/ai/runs/{id}}; the answer of a
 * start names the sources the suggestion may refer to.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tickets/{number}/ai")
class TicketAssistController {
    private final TicketAssist assist;
    private final AssistRequests requests;

    TicketAssistController(TicketAssist assist, AssistRequests requests) {
        this.assist = assist;
        this.requests = requests;
    }

    record OptionsBody(boolean summary, boolean replyDraft) {
    }

    record SourceBody(String id, String kind, UUID entryId, Instant at, String author) {
    }

    record StartedBody(UUID runId, String language, List<SourceBody> sources) {
        static StartedBody of(TicketAssist.Started started) {
            return new StartedBody(started.runId(), started.language(),
                started.sources().stream().map(s -> new SourceBody(s.id(), s.kind(), s.entryId(), s.at(), s.author())).toList());
        }
    }

    record SummaryRequest(String language) {
    }

    record DraftRequest(String channel, String goal) {
    }

    @GetMapping
    OptionsBody options(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        TicketAssist.Options options = AssistRequests.retrying(() -> assist.options(actor, projectId, number));
        return new OptionsBody(options.summary(), options.replyDraft());
    }

    @PostMapping("/summary")
    ResponseEntity<StartedBody> summary(@PathVariable UUID projectId, @PathVariable long number,
            @RequestBody(required = false) SummaryRequest body) {
        AccessContext actor = requests.actor();
        String language = body == null ? null : body.language();
        if (language != null && !List.of("de", "en").contains(language)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("language.invalid"));
        }
        TicketAssist.Started started = AssistRequests.retrying(() -> assist.summary(actor, projectId, number, language));
        return ResponseEntity.accepted().body(StartedBody.of(started));
    }

    @PostMapping("/reply-draft")
    ResponseEntity<StartedBody> draft(@PathVariable UUID projectId, @PathVariable long number, @RequestBody(required = false) DraftRequest body) {
        AccessContext actor = requests.actor();
        TicketConversation.Channel channel = body == null || body.channel() == null ? null : switch (body.channel()) {
            case "mail" -> TicketConversation.Channel.MAIL;
            case "portal" -> TicketConversation.Channel.PORTAL;
            default -> null;
        };
        if (channel == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("channel.invalid"));
        }
        TicketAssist.Started started = AssistRequests.retrying(() -> assist.draft(actor, projectId, number, channel, body.goal()));
        return ResponseEntity.accepted().body(StartedBody.of(started));
    }
}
