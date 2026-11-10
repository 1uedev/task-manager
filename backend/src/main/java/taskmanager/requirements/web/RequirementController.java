package taskmanager.requirements.web;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import taskmanager.projects.ProjectConfiguration;
import taskmanager.requirements.internal.RequirementLinks;
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
    private final RequirementLinks links;
    private final RequirementRequests requests;

    RequirementController(RequirementService service, RequirementLinks links, RequirementRequests requests) {
        this.service = service;
        this.links = links;
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

    record ContentBody(String title, JsonNode description, String descriptionHtml, List<CriterionBody> criteria, String priority,
            Map<String, Object> customFields) {
        static ContentBody of(RequirementService.Content c) {
            return new ContentBody(c.title(), c.description(), c.descriptionHtml(),
                c.criteria() == null ? null : c.criteria().stream().map(k -> new CriterionBody(k.id(), k.text())).toList(), c.priority(), c.custom());
        }
    }

    record OptionBody(String key, String name, boolean active) {
    }

    /** A custom field of requirements the caller may read (TASK-098); whether it is writable says {@code access.writableFields}. */
    record FieldBody(String key, String name, String type, boolean required, boolean active, List<OptionBody> options) {
        static List<FieldBody> of(List<ProjectConfiguration.CustomField> fields) {
            return fields.stream().map(f -> new FieldBody(f.key(), f.name(), f.type(), f.required(), f.active(),
                f.options().stream().map(o -> new OptionBody(o.key(), o.name(), o.active())).toList())).toList();
        }
    }

    record ApprovalBody(int revision, Instant approvedAt, UUID approvedBy) {
    }

    record RequirementBody(UUID id, UUID projectId, String projectKey, long number, String key, String status, int revision, Integer approvedRevision,
            ApprovalBody approval, ContentBody content, UUID ownerId, AccessBody access, List<UUID> editors, long version, Instant createdAt,
            UUID createdBy, Instant updatedAt, UUID updatedBy, List<FieldBody> customFields) {
        static RequirementBody of(RequirementService.RequirementView v) {
            RequirementService.Approval a = v.approval();
            return new RequirementBody(v.id(), v.projectId(), v.projectKey(), v.number(), v.key(), v.status(), v.revision(), v.approvedRevision(),
                a == null ? null : new ApprovalBody(a.revision(), a.approvedAt(), a.approvedBy()), ContentBody.of(v.content()), v.ownerId(),
                AccessBody.of(v.access()), v.editors(), v.version(), v.createdAt(), v.createdBy(), v.updatedAt(), v.updatedBy(),
                FieldBody.of(v.customFields()));
        }
    }

    record ItemBody(UUID id, long number, String key, String status, int revision, Integer approvedRevision, String title, String priority,
            UUID ownerId, Instant updatedAt) {
    }

    record ListBody(UUID projectId, String projectKey, AccessBody access, List<ItemBody> items, Long nextAfter, List<FieldBody> customFields) {
    }

    record RevisionInfoBody(int revision, Instant openedAt, UUID openedBy, Instant savedAt, UUID savedBy, Instant approvedAt, UUID approvedBy) {
    }

    record EventBody(String kind, int revision, String field, Integer criterion, String from, String to, String reason, UUID actorId,
            Instant occurredAt) {
    }

    record HistoryBody(UUID requirementId, List<RevisionInfoBody> revisions, List<EventBody> events, Map<UUID, String> pageTitles) {
    }

    record TicketLinkBody(UUID ticketId, long number, String key, String title, String status, String statusName, boolean done, int revision,
            Integer newerRevision, Instant linkedAt, UUID linkedBy) {
    }

    record PageLinkBody(UUID pageId, String title, boolean available, Instant linkedAt, UUID linkedBy) {
    }

    record SummaryBody(int tickets, int done) {
    }

    record LinksBody(UUID requirementId, int revision, boolean edit, boolean ticketsReadable, List<TicketLinkBody> tickets, SummaryBody summary,
            List<PageLinkBody> pages) {
        static LinksBody of(RequirementLinks.Links l) {
            return new LinksBody(l.requirementId(), l.revision(), l.edit(), l.ticketsReadable(), l.tickets().stream()
                .map(t -> new TicketLinkBody(t.ticketId(), t.number(), t.key(), t.title(), t.status(), t.statusName(), t.done(), t.revision(),
                    t.newerRevision(), t.linkedAt(), t.linkedBy()))
                .toList(), new SummaryBody(l.summary().tickets(), l.summary().done()), l.pages().stream()
                .map(p -> new PageLinkBody(p.pageId(), p.title(), p.title() != null, p.linkedAt(), p.linkedBy())).toList());
        }
    }

    record TicketRequirementBody(UUID requirementId, long number, String key, String title, String status, int revision, Integer newerRevision,
            Instant linkedAt, UUID linkedBy) {
    }

    record TicketRequirementsBody(UUID ticketId, boolean read, boolean edit, List<TicketRequirementBody> requirements) {
    }

    record TicketLinkRequest(Long number) {
    }

    record SwitchRequest(Integer revision) {
    }

    record PageLinkRequest(UUID pageId) {
    }

    record RevisionBody(int revision, boolean current, Instant savedAt, UUID savedBy, Instant approvedAt, UUID approvedBy, ContentBody content,
            List<FieldBody> customFields) {
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
            .toList(), page.nextAfter(), FieldBody.of(page.customFields()));
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
    HistoryBody history(@PathVariable UUID id, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        String lang = language(language);
        RequirementService.History h = RequirementRequests.retrying(() -> service.history(actor, id, lang));
        return new HistoryBody(h.requirementId(),
            h.revisions().stream().map(r -> new RevisionInfoBody(r.revision(), r.openedAt(), r.openedBy(), r.savedAt(), r.savedBy(), r.approvedAt(),
                r.approvedBy())).toList(),
            h.events().stream().map(e -> new EventBody(e.kind(), e.revision(), e.field(), e.criterion(), e.from(), e.to(), e.reason(), e.actorId(),
                e.occurredAt())).toList(), h.pageTitles());
    }

    // ------------------------------------------------------------------------------------------------ links (TASK-097)

    @GetMapping("/{id}/links")
    LinksBody links(@PathVariable UUID id, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        String lang = language(language);
        return LinksBody.of(RequirementRequests.retrying(() -> links.links(actor, id, lang)));
    }

    @PostMapping("/{id}/tickets")
    LinksBody linkTicket(@PathVariable UUID id, @RequestParam(required = false) String language, @RequestBody(required = false) TicketLinkRequest body) {
        AccessContext actor = requests.actor();
        String lang = language(language);
        Long number = body == null ? null : body.number();
        return LinksBody.of(RequirementRequests.retrying(() -> links.linkTicket(actor, id, number, lang)));
    }

    @PostMapping("/{id}/tickets/{number}/switch")
    LinksBody switchTicket(@PathVariable UUID id, @PathVariable long number, @RequestParam(required = false) String language,
            @RequestBody(required = false) SwitchRequest body) {
        AccessContext actor = requests.actor();
        String lang = language(language);
        Integer revision = body == null ? null : body.revision();
        return LinksBody.of(RequirementRequests.retrying(() -> links.switchTicket(actor, id, number, revision, lang)));
    }

    @DeleteMapping("/{id}/tickets/{number}")
    LinksBody unlinkTicket(@PathVariable UUID id, @PathVariable long number, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        String lang = language(language);
        return LinksBody.of(RequirementRequests.retrying(() -> links.unlinkTicket(actor, id, number, lang)));
    }

    @PostMapping("/{id}/pages")
    LinksBody linkPage(@PathVariable UUID id, @RequestParam(required = false) String language, @RequestBody(required = false) PageLinkRequest body) {
        AccessContext actor = requests.actor();
        String lang = language(language);
        UUID pageId = body == null ? null : body.pageId();
        return LinksBody.of(RequirementRequests.retrying(() -> links.linkPage(actor, id, pageId, lang)));
    }

    @DeleteMapping("/{id}/pages/{pageId}")
    LinksBody unlinkPage(@PathVariable UUID id, @PathVariable UUID pageId, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        String lang = language(language);
        return LinksBody.of(RequirementRequests.retrying(() -> links.unlinkPage(actor, id, pageId, lang)));
    }

    /** The field "Anforderungen" of a ticket (TASK-097). */
    @GetMapping("/projects/{projectId}/tickets/{number}")
    TicketRequirementsBody ofTicket(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = requests.actor();
        RequirementLinks.TicketRequirements t = RequirementRequests.retrying(() -> links.ofTicket(actor, projectId, number));
        return new TicketRequirementsBody(t.ticketId(), t.read(), t.edit(), t.requirements().stream()
            .map(r -> new TicketRequirementBody(r.requirementId(), r.number(), r.key(), r.title(), r.status(), r.revision(), r.newerRevision(),
                r.linkedAt(), r.linkedBy()))
            .toList());
    }

    /** The language of titles: {@code de} or {@code en}; missing means the source language of each page. */
    private static String language(String language) {
        if (language == null) {
            return null;
        }
        if (!List.of("de", "en").contains(language)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("language.invalid"));
        }
        return language;
    }

    @GetMapping("/{id}/revisions/{revision}")
    RevisionBody revision(@PathVariable UUID id, @PathVariable int revision) {
        AccessContext actor = requests.actor();
        RequirementService.RevisionContent r = RequirementRequests.retrying(() -> service.revision(actor, id, revision));
        return new RevisionBody(r.revision(), r.current(), r.savedAt(), r.savedBy(), r.approvedAt(), r.approvedBy(), ContentBody.of(r.content()),
            FieldBody.of(r.customFields()));
    }

    /**
     * The fields a body names, with their types checked: title, priority and ownerId as text or {@code null}, description as a
     * document or {@code null}, criteria as a list of objects with an optional whole {@code id} and a {@code text}.
     */
    static RequirementService.Changes changes(JsonNode body) {
        if (body == null || body.isNull()) {
            return new RequirementService.Changes(false, null, false, null, false, null, false, null, false, null, Map.of());
        }
        if (!body.isObject()) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("body.invalid"));
        }
        List<String> violations = new ArrayList<>();
        for (String name : body.propertyNames()) {
            if (!List.of("title", "description", "priority", "ownerId", "criteria", "customFields").contains(name)) {
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
        Map<String, Object> custom = customFields(body.get("customFields"), violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations.stream().distinct().toList());
        }
        return new RequirementService.Changes(body.has("title"), title, body.has("description"), description, body.has("priority"), priority,
            body.has("ownerId"), owner, body.has("criteria"), criteria, custom);
    }

    /**
     * The values of {@code customFields} by field key (TASK-098): text, a whole number or a boolean as such, {@code null} to empty
     * a field. Whether the key exists and the value fits its field the service decides.
     */
    private static Map<String, Object> customFields(JsonNode node, List<String> violations) {
        Map<String, Object> custom = new java.util.LinkedHashMap<>();
        if (node == null) {
            return custom;
        }
        if (!node.isObject() || node.size() > 30) {
            violations.add("customFields.invalid");
            return custom;
        }
        for (var entry : node.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!key.matches("[a-z][a-z0-9_]{0,39}")) {
                violations.add("customFields.unknown");
            } else if (value.isNull()) {
                custom.put(key, null);
            } else if (value.isString()) {
                custom.put(key, value.asString());
            } else if (value.isBoolean()) {
                custom.put(key, value.asBoolean());
            } else if (value.isIntegralNumber() && value.canConvertToLong()) {
                custom.put(key, value.asLong());
            } else {
                violations.add(ProjectConfiguration.CUSTOM_PREFIX + key + ".invalid");
            }
        }
        return custom;
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
