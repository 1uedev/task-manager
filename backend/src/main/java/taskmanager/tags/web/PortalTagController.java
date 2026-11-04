package taskmanager.tags.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;
import taskmanager.tags.internal.PortalTags;
import taskmanager.tags.internal.Tag;
import tools.jackson.databind.JsonNode;

/**
 * Tags in the portal (TASK-077, DEC-108): suggestions of a project and the tags of a request of the organization of a portal
 * account. Only tags visible to the organization appear; an internal tag or one of another organization answers like an
 * unknown one. An internal account answers 404 like an unknown project.
 */
@RestController
@RequestMapping("/api/v1/portal/projects/{projectId}")
class PortalTagController {
    private final PortalTags tags;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    PortalTagController(PortalTags tags, AccessContexts identity, PermissionContexts contexts) {
        this.tags = tags;
        this.identity = identity;
        this.contexts = contexts;
    }

    /** A tag as the portal shows it; {@code own} marks a tag of the organization of the caller. */
    record PortalTagBody(UUID id, String name, String description, String color, boolean archived, boolean own) {
        static PortalTagBody of(Tag tag) {
            return new PortalTagBody(tag.id(), tag.name(), tag.description(), tag.color(), tag.archived(), tag.organizationId() != null);
        }
    }

    record RequestTagsBody(List<PortalTagBody> items, boolean writable) {
    }

    record SuggestionsBody(List<PortalTagBody> items, boolean create) {
    }

    @GetMapping("/tags")
    SuggestionsBody suggestions(@PathVariable UUID projectId, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        AccessContext actor = actor();
        int size = TagController.suggestionLimit(limit);
        PortalTags.Suggestions found = TagController.retrying(() -> tags.suggestions(actor, projectId, q, size));
        return new SuggestionsBody(found.items().stream().map(PortalTagBody::of).toList(), found.create());
    }

    @GetMapping("/tickets/{number}/tags")
    RequestTagsBody ticketTags(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = actor();
        PortalTags.RequestTags found = TagController.retrying(() -> tags.ticketTags(actor, projectId, number));
        return new RequestTagsBody(found.items().stream().map(PortalTagBody::of).toList(), found.writable());
    }

    /** Sets a tag: {@code tagId} of a visible tag, or {@code name}, which creates a tag of the organization when none is visible. */
    @PostMapping("/tickets/{number}/tags")
    PortalTagBody assign(@PathVariable UUID projectId, @PathVariable long number, @RequestBody(required = false) JsonNode body) {
        AccessContext actor = actor();
        if (body == null || !body.isObject()) {
            throw TagController.invalid(List.of("target.required"));
        }
        UUID tagId = null;
        String name = null;
        List<String> violations = new ArrayList<>();
        for (var entry : body.properties()) {
            JsonNode value = entry.getValue();
            switch (entry.getKey()) {
                case "tagId" -> {
                    tagId = TagController.uuid(value);
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
                default -> violations.add("fields.unknown");
            }
        }
        if (body.has("tagId") == body.has("name")) {
            violations.add(body.has("tagId") ? "target.ambiguous" : "target.required");
        }
        if (!violations.isEmpty()) {
            throw TagController.invalid(violations.stream().distinct().toList());
        }
        UUID id = tagId;
        String named = name;
        return PortalTagBody.of(TagController.retrying(() -> tags.assign(actor, projectId, number, id, named)));
    }

    @DeleteMapping("/tickets/{number}/tags/{tagId}")
    ResponseEntity<Void> unassign(@PathVariable UUID projectId, @PathVariable long number, @PathVariable UUID tagId) {
        AccessContext actor = actor();
        TagController.retrying(() -> {
            tags.unassign(actor, projectId, number, tagId);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private AccessContext actor() {
        if (identity.require().level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }
}
