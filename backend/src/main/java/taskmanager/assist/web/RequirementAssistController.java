package taskmanager.assist.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.assist.internal.RequirementAssist;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;

/**
 * The AI review of a requirement (TASK-127): whether the person may start it for a revision and the start. The execution is read
 * and cancelled through {@code /api/v1/ai/runs/{id}}; the answer of a start names what each source refers to.
 */
@RestController
@RequestMapping("/api/v1/requirements/{requirementId}/ai")
class RequirementAssistController {
    private final RequirementAssist assist;
    private final AssistRequests requests;

    RequirementAssistController(RequirementAssist assist, AssistRequests requests) {
        this.assist = assist;
        this.requests = requests;
    }

    record OptionsBody(boolean review) {
    }

    record SourceBody(String id, String part, Integer criterion) {
    }

    record StartedBody(UUID runId, String language, int revision, List<SourceBody> sources) {
    }

    record ReviewRequest(Integer revision, String language) {
    }

    @GetMapping
    OptionsBody options(@PathVariable UUID requirementId, @RequestParam int revision) {
        AccessContext actor = requests.actor();
        return new OptionsBody(AssistRequests.retrying(() -> assist.usable(actor, requirementId, revision)));
    }

    @PostMapping("/review")
    ResponseEntity<StartedBody> review(@PathVariable UUID requirementId, @RequestBody(required = false) ReviewRequest body) {
        AccessContext actor = requests.actor();
        List<String> violations = new ArrayList<>();
        if (body == null || body.revision() == null || body.revision() < 1) {
            violations.add("revision.invalid");
        }
        String language = body == null ? null : body.language();
        if (language != null && !List.of("de", "en").contains(language)) {
            violations.add("language.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        RequirementAssist.Started started = AssistRequests.retrying(() -> assist.review(actor, requirementId, body.revision(), language));
        return ResponseEntity.accepted().body(new StartedBody(started.runId(), started.language(), started.revision(),
            started.sources().stream().map(s -> new SourceBody(s.id(), s.part(), s.criterion())).toList()));
    }
}
