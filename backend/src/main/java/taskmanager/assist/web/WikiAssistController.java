package taskmanager.assist.web;

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
import taskmanager.assist.internal.WikiAssist;
import taskmanager.permissions.AccessContext;
import tools.jackson.databind.JsonNode;

/**
 * The AI revision of a wiki draft (TASK-127): whether the person may start it, the start for a section or the whole language
 * version, and the result brought into the form of the editor. The execution is read and cancelled through
 * {@code /api/v1/ai/runs/{id}}; nothing here changes the page.
 */
@RestController
@RequestMapping("/api/v1/wiki/pages/{pageId}/ai")
class WikiAssistController {
    private final WikiAssist assist;
    private final AssistRequests requests;

    WikiAssistController(WikiAssist assist, AssistRequests requests) {
        this.assist = assist;
        this.requests = requests;
    }

    record OptionsBody(boolean revision) {
    }

    record RevisionRequest(Integer revision, Integer section, String goal, String text) {
    }

    record StartedBody(UUID runId, String language, int revision, Integer section, String heading) {
    }

    record ResultBody(int revision, Integer section, String heading, JsonNode original, JsonNode revised, JsonNode document,
            List<String> notes) {
    }

    @GetMapping
    OptionsBody options(@PathVariable UUID pageId, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        return new OptionsBody(AssistRequests.retrying(() -> assist.usable(actor, pageId, language)));
    }

    @PostMapping("/revision")
    ResponseEntity<StartedBody> start(@PathVariable UUID pageId, @RequestParam(required = false) String language,
            @RequestBody(required = false) RevisionRequest body) {
        AccessContext actor = requests.actor();
        RevisionRequest request = body == null ? new RevisionRequest(null, null, null, null) : body;
        WikiAssist.Started started = AssistRequests.retrying(() -> assist.start(actor, pageId, language, request.revision(), request.section(),
            request.goal(), request.text()));
        return ResponseEntity.accepted().body(new StartedBody(started.runId(), started.language(), started.revision(), started.section(),
            started.heading()));
    }

    @GetMapping("/revision/{runId}")
    ResultBody result(@PathVariable UUID pageId, @PathVariable UUID runId, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        WikiAssist.Result result = AssistRequests.retrying(() -> assist.result(actor, pageId, language, runId));
        return new ResultBody(result.revision(), result.section(), result.heading(), result.original(), result.revised(), result.document(),
            result.notes());
    }
}
