package taskmanager.tags.web;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;
import taskmanager.tags.internal.Tag;
import taskmanager.tags.internal.TagService;
import taskmanager.tags.internal.TagService.Catalog;
import taskmanager.tags.internal.TagService.Change;
import taskmanager.tags.internal.TagService.Target;
import tools.jackson.databind.JsonNode;

/**
 * Tag catalogs and tag assignments (TASK-028). Every endpoint needs a complete login; the service decides who may do what.
 * The ETag of a tag is its version, and every change of a tag needs it as If-Match. Assignments are idempotent and answer
 * 204. A lock that could not be taken in time, or a deadlock PostgreSQL resolved, ends the transaction; the answer is 503
 * {@code tag.busy} with {@code Retry-After}, and the whole request may be repeated.
 */
@RestController
class TagController {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final TagService tags;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    TagController(TagService tags, AccessContexts identity, PermissionContexts contexts) {
        this.tags = tags;
        this.identity = identity;
        this.contexts = contexts;
    }

    record TagBody(UUID id, String name, String catalog, UUID projectId, boolean archived, long version, Instant createdAt,
            UUID createdBy, Instant updatedAt, UUID updatedBy) {
        static TagBody of(Tag tag) {
            return new TagBody(tag.id(), tag.name(), tag.inCompanyCatalog() ? "company" : "project", tag.projectId(), tag.archived(),
                tag.version(), tag.createdAt(), tag.createdBy(), tag.updatedAt(), tag.updatedBy());
        }
    }

    // ------------------------------------------------------------------------------------------------ company catalog

    @GetMapping("/api/v1/tags")
    List<TagBody> companyCatalog(@RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) String after, @RequestParam(required = false) Integer limit) {
        AccessContext actor = actor();
        int size = limit(limit);
        return bodies(retrying(() -> tags.companyCatalog(actor, q, archived, after, size)));
    }

    @PostMapping("/api/v1/tags")
    ResponseEntity<TagBody> createCompanyTag(@RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        String name = name(body);
        return tag(HttpStatus.CREATED, retrying(() -> tags.createCompanyTag(actor, name)));
    }

    @GetMapping("/api/v1/tags/{id}")
    ResponseEntity<TagBody> companyTag(@PathVariable UUID id) {
        AccessContext actor = actor();
        return tag(HttpStatus.OK, retrying(() -> tags.companyTag(actor, id)));
    }

    @PatchMapping("/api/v1/tags/{id}")
    ResponseEntity<TagBody> changeCompanyTag(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        long expected = expectedVersion(ifMatch);
        Change change = change(body);
        return tag(HttpStatus.OK, retrying(() -> tags.changeCompanyTag(actor, id, expected, change)));
    }

    // ------------------------------------------------------------------------------------------------ project catalog

    @GetMapping("/api/v1/projects/{projectId}/catalog/tags")
    List<TagBody> projectCatalog(@PathVariable UUID projectId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean archived, @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer limit) {
        AccessContext actor = actor();
        int size = limit(limit);
        return bodies(retrying(() -> tags.projectCatalog(actor, projectId, q, archived, after, size)));
    }

    @PostMapping("/api/v1/projects/{projectId}/catalog/tags")
    ResponseEntity<TagBody> createProjectTag(@PathVariable UUID projectId, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        String name = name(body);
        return tag(HttpStatus.CREATED, retrying(() -> tags.createProjectTag(actor, projectId, name)));
    }

    @GetMapping("/api/v1/projects/{projectId}/catalog/tags/{id}")
    ResponseEntity<TagBody> projectTag(@PathVariable UUID projectId, @PathVariable UUID id) {
        AccessContext actor = actor();
        return tag(HttpStatus.OK, retrying(() -> tags.projectTag(actor, projectId, id)));
    }

    @PatchMapping("/api/v1/projects/{projectId}/catalog/tags/{id}")
    ResponseEntity<TagBody> changeProjectTag(@PathVariable UUID projectId, @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        long expected = expectedVersion(ifMatch);
        Change change = change(body);
        return tag(HttpStatus.OK, retrying(() -> tags.changeProjectTag(actor, projectId, id, expected, change)));
    }

    // ------------------------------------------------------------------------------------------------ project tags

    @GetMapping("/api/v1/projects/{projectId}/tags")
    List<TagBody> projectTags(@PathVariable UUID projectId) {
        AccessContext actor = actor();
        return bodies(retrying(() -> tags.projectTags(actor, projectId)));
    }

    @PostMapping("/api/v1/projects/{projectId}/tags")
    ResponseEntity<Void> assignProject(@PathVariable UUID projectId, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        Target target = target(body, false);
        retrying(() -> {
            tags.assignProject(actor, projectId, target);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/projects/{projectId}/tags/{tagId}")
    ResponseEntity<Void> unassignProject(@PathVariable UUID projectId, @PathVariable UUID tagId) {
        AccessContext actor = actor();
        retrying(() -> {
            tags.unassignProject(actor, projectId, tagId);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------------------------------ ticket tags

    @GetMapping("/api/v1/projects/{projectId}/tickets/{number}/tags")
    List<TagBody> ticketTags(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = actor();
        return bodies(retrying(() -> tags.ticketTags(actor, projectId, number)));
    }

    @PostMapping("/api/v1/projects/{projectId}/tickets/{number}/tags")
    ResponseEntity<Void> assignTicket(@PathVariable UUID projectId, @PathVariable long number,
            @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        Target target = target(body, true);
        retrying(() -> {
            tags.assignTicket(actor, projectId, number, target);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/projects/{projectId}/tickets/{number}/tags/{tagId}")
    ResponseEntity<Void> unassignTicket(@PathVariable UUID projectId, @PathVariable long number, @PathVariable UUID tagId) {
        AccessContext actor = actor();
        retrying(() -> {
            tags.unassignTicket(actor, projectId, number, tagId);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------------------------------ bodies

    /** The name of a new tag: an object with exactly the string {@code name}. */
    private static String name(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid(List.of("name.invalid"));
        }
        List<String> violations = new ArrayList<>();
        for (var entry : body.properties()) {
            if (!entry.getKey().equals("name")) {
                violations.add("fields.unknown");
            }
        }
        JsonNode name = body.get("name");
        if (name == null || !name.isString()) {
            violations.add("name.invalid");
        }
        if (!violations.isEmpty()) {
            throw invalid(violations.stream().distinct().toList());
        }
        return name.asString();
    }

    /** A change of a tag: {@code name} as a string and {@code archived} as a boolean, at least one of them. */
    private static Change change(JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty()) {
            throw invalid(List.of("fields.required"));
        }
        String name = null;
        Boolean archived = null;
        List<String> violations = new ArrayList<>();
        for (var entry : body.properties()) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "name" -> {
                    if (value.isString()) {
                        name = value.asString();
                    } else {
                        violations.add("name.invalid");
                    }
                }
                case "archived" -> {
                    if (value.isBoolean()) {
                        archived = value.asBoolean();
                    } else {
                        violations.add("archived.invalid");
                    }
                }
                default -> violations.add("fields.unknown");
            }
        }
        if (!violations.isEmpty()) {
            throw invalid(violations.stream().distinct().toList());
        }
        return new Change(name, archived);
    }

    /**
     * The tag to assign: {@code tagId}, or {@code name} with, for tickets, the optional {@code catalog} {@code project}
     * (the default) or {@code company}. A project only takes tags of the company catalog.
     */
    private static Target target(JsonNode body, boolean ticket) {
        if (body == null || !body.isObject()) {
            throw invalid(List.of("target.required"));
        }
        UUID tagId = null;
        String name = null;
        Catalog catalog = ticket ? Catalog.PROJECT : Catalog.COMPANY;
        boolean catalogGiven = false;
        List<String> violations = new ArrayList<>();
        for (var entry : body.properties()) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "tagId" -> {
                    tagId = uuid(value);
                    if (tagId == null) {
                        violations.add("tagId.invalid");
                    }
                }
                case "name" -> {
                    if (value.isString()) {
                        name = value.asString();
                    } else {
                        violations.add("name.invalid");
                    }
                }
                case "catalog" -> {
                    catalogGiven = true;
                    if (ticket && value.isString() && value.asString().equals("project")) {
                        catalog = Catalog.PROJECT;
                    } else if (ticket && value.isString() && value.asString().equals("company")) {
                        catalog = Catalog.COMPANY;
                    } else {
                        violations.add(ticket ? "catalog.invalid" : "fields.unknown");
                    }
                }
                default -> violations.add("fields.unknown");
            }
        }
        boolean hasId = body.has("tagId");
        boolean hasName = body.has("name");
        if (hasId == hasName) {
            violations.add(hasId ? "target.ambiguous" : "target.required");
        } else if (hasId && catalogGiven) {
            violations.add("target.ambiguous");
        }
        if (!violations.isEmpty()) {
            throw invalid(violations.stream().distinct().toList());
        }
        return new Target(tagId, name, catalog);
    }

    private static UUID uuid(JsonNode value) {
        if (!value.isString()) {
            return null;
        }
        try {
            return UUID.fromString(value.asString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static int limit(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw invalid(List.of("paging.invalid"));
        }
        return size;
    }

    private static ApiProblemException invalid(List<String> violations) {
        return ApiProblemException.withViolations(400, "request.invalid", violations);
    }

    private static List<TagBody> bodies(List<Tag> tags) {
        return tags.stream().map(TagBody::of).toList();
    }

    private static ResponseEntity<TagBody> tag(HttpStatus status, Tag tag) {
        return ResponseEntity.status(status).eTag("\"" + tag.version() + "\"").body(TagBody.of(tag));
    }

    /** The permissions context of a complete login; a login that still has to change its password is refused. */
    private AccessContext actor() {
        var request = identity.require();
        if (request.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    /** Runs one transaction of the service and answers a lock timeout or a deadlock as a retryable 503 (P4 of TASK-025). */
    private static <T> T retrying(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "tag.busy", 1);
            }
            throw failure;
        }
    }

    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || (cause instanceof SQLException sql && RETRYABLE_STATES.contains(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    private static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "tag.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "tag.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "tag.version_conflict");
        }
    }
}
