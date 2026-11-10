package taskmanager.requirements.web;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.requirements.internal.RequirementService;
import tools.jackson.databind.JsonNode;

/**
 * Requirements (TASK-096, DEC-126). The ETag of a requirement is its version; every change needs it as If-Match (428 without,
 * 412 when the requirement is newer). Approving names the revision it approves in the body as well, so a newer revision is
 * never approved in place of the one shown.
 */
@RestController
@RequestMapping("/api/v1/requirements")
class RequirementController {
    private final RequirementService service;
    private final RequirementRequests requests;

    RequirementController(RequirementService service, RequirementRequests requests) {
        this.service = service;
        this.requests = requests;
    }

    record AccessBody(boolean read, boolean edit, boolean approve, List<String> readableFields, List<String> writableFields) {
        static AccessBody of(RequirementService.Access access) {
            return new AccessBody(access.read(), access.edit(), access.approve(), access.readable().stream().sorted().toList(),
                access.writable().stream().sorted().toList());
        }
    }

    record ProjectBody(UUID projectId, String key, String name, AccessBody access) {
    }

    record CriterionBody(int id, String text) {
    }

    record ContentBody(String title, JsonNode description, String descriptionHtml, List<CriterionBody> criteria, String priority) {
        static ContentBody of(RequirementService.Content c) {
            return new ContentBody(c.title(), c.description(), c.descriptionHtml(),
                c.criteria() == null ? null : c.criteria().stream().map(k -> new CriterionBody(k.id(), k.text())).toList(), c.priority());
        }
    }

    record ApprovalBody(int revision, Instant approvedAt, UUID approvedBy) {
    }

    record RequirementBody(UUID id, UUID projectId, String projectKey, long number, String key, String status, int revision, Integer approvedRevision,
            ApprovalBody approval, ContentBody content, UUID ownerId, AccessBody access, List<UUID> editors, long version, Instant createdAt,
            UUID createdBy, Instant updatedAt, UUID updatedBy) {
        static RequirementBody of(RequirementService.RequirementView v) {
            RequirementService.Approval a = v.approval();
            return new RequirementBody(v.id(), v.projectId(), v.projectKey(), v.number(), v.key(), v.status(), v.revision(), v.approvedRevision(),
                a == null ? null : new ApprovalBody(a.revision(), a.approvedAt(), a.approvedBy()), ContentBody.of(v.content()), v.ownerId(),
                AccessBody.of(v.access()), v.editors(), v.version(), v.createdAt(), v.createdBy(), v.updatedAt(), v.updatedBy());
        }
    }

    record ItemBody(UUID id, long number, String key, String status, int revision, Integer approvedRevision, String title, String priority,
            UUID ownerId, Instant updatedAt) {
    }

    record ListBody(UUID projectId, String projectKey, AccessBody access, List<ItemBody> items, Long nextAfter) {
    }

    record RevisionInfoBody(int revision, Instant openedAt, UUID openedBy, Instant savedAt, UUID savedBy, Instant approvedAt, UUID approvedBy) {
    }

    record EventBody(String kind, int revision, String field, Integer criterion, String from, String to, String reason, UUID actorId,
            Instant occurredAt) {
    }

    record HistoryBody(UUID requirementId, List<RevisionInfoBody> revisions, List<EventBody> events) {
    }

    record RevisionBody(int revision, boolean current, Instant savedAt, UUID savedBy, Instant approvedAt, UUID approvedBy, ContentBody content) {
    }

    record ApproveRequest(Integer revision) {
    }

    record ReasonRequest(String reason) {
    }

    @GetMapping("/projects")
    List<ProjectBody> projects() {
        AccessContext actor = requests.actor();
        return RequirementRequests.retrying(() -> service.projects(actor)).stream()
            .map(p -> new ProjectBody(p.projectId(), p.key(), p.name(), AccessBody.of(p.access()))).toList();
    }

    @GetMapping("/projects/{projectId}/requirements")
    ListBody list(@PathVariable UUID projectId, @RequestParam(required = false) String status, @RequestParam(required = false) String priority,
            @RequestParam(required = false) String owner, @RequestParam(required = false) String text, @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        AccessContext actor = requests.actor();
        RequirementService.ListPage page = RequirementRequests.retrying(() -> service.list(actor, projectId, status, priority, owner, text, after, limit));
        return new ListBody(page.projectId(), page.projectKey(), AccessBody.of(page.access()), page.items().stream()
            .map(i -> new ItemBody(i.id(), i.number(), i.key(), i.status(), i.revision(), i.approvedRevision(), i.title(), i.priority(), i.ownerId(),
                i.updatedAt()))
            .toList(), page.nextAfter());
    }

    @GetMapping("/projects/{projectId}/owners")
    List<UUID> owners(@PathVariable UUID projectId) {
        AccessContext actor = requests.actor();
        return RequirementRequests.retrying(() -> service.owners(actor, projectId));
    }

    @PostMapping("/projects/{projectId}/requirements")
    ResponseEntity<RequirementBody> create(@PathVariable UUID projectId, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = requests.actor();
        RequirementService.Changes changes = changes(body);
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.create(actor, projectId, changes));
        return ResponseEntity.created(URI.create("/api/v1/requirements/" + view.id())).headers(etag(view)).body(RequirementBody.of(view));
    }

    @GetMapping("/{id}")
    ResponseEntity<RequirementBody> get(@PathVariable UUID id) {
        AccessContext actor = requests.actor();
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.get(actor, id));
        return ResponseEntity.ok().headers(etag(view)).cacheControl(CacheControl.noStore()).body(RequirementBody.of(view));
    }

    @PatchMapping("/{id}")
    ResponseEntity<RequirementBody> update(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) JsonNode body) {
        AccessContext actor = requests.actor();
        long version = RequirementRequests.expectedVersion(ifMatch);
        RequirementService.Changes changes = changes(body);
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.update(actor, id, version, changes));
        return ResponseEntity.ok().headers(etag(view)).body(RequirementBody.of(view));
    }

    @PostMapping("/{id}/submit")
    ResponseEntity<RequirementBody> submit(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = requests.actor();
        long version = RequirementRequests.expectedVersion(ifMatch);
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.submit(actor, id, version));
        return ResponseEntity.ok().headers(etag(view)).body(RequirementBody.of(view));
    }

    @PostMapping("/{id}/approve")
    ResponseEntity<RequirementBody> approve(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) ApproveRequest body) {
        AccessContext actor = requests.actor();
        long version = RequirementRequests.expectedVersion(ifMatch);
        Integer revision = body == null ? null : body.revision();
        if (revision == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("revision.required"));
        }
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.approve(actor, id, version, revision));
        return ResponseEntity.ok().headers(etag(view)).body(RequirementBody.of(view));
    }

    @PostMapping("/{id}/return")
    ResponseEntity<RequirementBody> returnToDraft(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody(required = false) ReasonRequest body) {
        AccessContext actor = requests.actor();
        long version = RequirementRequests.expectedVersion(ifMatch);
        String reason = body == null ? null : body.reason();
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.returnToDraft(actor, id, version, reason));
        return ResponseEntity.ok().headers(etag(view)).body(RequirementBody.of(view));
    }

    @PostMapping("/{id}/discard")
    ResponseEntity<RequirementBody> discard(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) ReasonRequest body) {
        AccessContext actor = requests.actor();
        long version = RequirementRequests.expectedVersion(ifMatch);
        String reason = body == null ? null : body.reason();
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.discard(actor, id, version, reason));
        return ResponseEntity.ok().headers(etag(view)).body(RequirementBody.of(view));
    }

    @PostMapping("/{id}/restore")
    ResponseEntity<RequirementBody> restore(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = requests.actor();
        long version = RequirementRequests.expectedVersion(ifMatch);
        RequirementService.RequirementView view = RequirementRequests.retrying(() -> service.restore(actor, id, version));
        return ResponseEntity.ok().headers(etag(view)).body(RequirementBody.of(view));
    }

    @GetMapping("/{id}/history")
    HistoryBody history(@PathVariable UUID id) {
        AccessContext actor = requests.actor();
        RequirementService.History h = RequirementRequests.retrying(() -> service.history(actor, id));
        return new HistoryBody(h.requirementId(),
            h.revisions().stream().map(r -> new RevisionInfoBody(r.revision(), r.openedAt(), r.openedBy(), r.savedAt(), r.savedBy(), r.approvedAt(),
                r.approvedBy())).toList(),
            h.events().stream().map(e -> new EventBody(e.kind(), e.revision(), e.field(), e.criterion(), e.from(), e.to(), e.reason(), e.actorId(),
                e.occurredAt())).toList());
    }

    @GetMapping("/{id}/revisions/{revision}")
    RevisionBody revision(@PathVariable UUID id, @PathVariable int revision) {
        AccessContext actor = requests.actor();
        RequirementService.RevisionContent r = RequirementRequests.retrying(() -> service.revision(actor, id, revision));
        return new RevisionBody(r.revision(), r.current(), r.savedAt(), r.savedBy(), r.approvedAt(), r.approvedBy(), ContentBody.of(r.content()));
    }

    /**
     * The fields a body names, with their types checked: title, priority and ownerId as text or {@code null}, description as a
     * document or {@code null}, criteria as a list of objects with an optional whole {@code id} and a {@code text}.
     */
    static RequirementService.Changes changes(JsonNode body) {
        if (body == null || body.isNull()) {
            return new RequirementService.Changes(false, null, false, null, false, null, false, null, false, null);
        }
        if (!body.isObject()) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("body.invalid"));
        }
        List<String> violations = new ArrayList<>();
        for (String name : body.propertyNames()) {
            if (!List.of("title", "description", "priority", "ownerId", "criteria").contains(name)) {
                violations.add(name + ".unknown");
            }
        }
        String title = text(body, "title", violations);
        String priority = text(body, "priority", violations);
        String owner = text(body, "ownerId", violations);
        JsonNode description = body.has("description") ? body.get("description") : null;
        if (description != null && !description.isNull() && !description.isObject()) {
            violations.add("description.invalid");
        }
        List<RequirementService.CriterionInput> criteria = null;
        if (body.has("criteria")) {
            JsonNode list = body.get("criteria");
            if (!list.isArray()) {
                violations.add("criteria.invalid");
            } else {
                criteria = new ArrayList<>();
                for (JsonNode item : list) {
                    JsonNode id = item.get("id");
                    JsonNode text = item.get("text");
                    if (!item.isObject() || (id != null && !id.isNull() && !id.isIntegralNumber()) || text == null || !text.isString()) {
                        violations.add("criteria.invalid");
                        continue;
                    }
                    Integer number = id == null || id.isNull() ? null : (id.canConvertToInt() ? id.asInt() : -1);
                    criteria.add(new RequirementService.CriterionInput(number, text.asString()));
                }
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations.stream().distinct().toList());
        }
        return new RequirementService.Changes(body.has("title"), title, body.has("description"), description, body.has("priority"), priority,
            body.has("ownerId"), owner, body.has("criteria"), criteria);
    }

    private static String text(JsonNode body, String name, List<String> violations) {
        JsonNode value = body.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            violations.add(name + ".invalid");
            return null;
        }
        return value.asString();
    }

    private static HttpHeaders etag(RequirementService.RequirementView view) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(RequirementRequests.etag(view.version()));
        return headers;
    }
}
