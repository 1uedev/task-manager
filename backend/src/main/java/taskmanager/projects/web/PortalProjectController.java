package taskmanager.projects.web;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.internal.ProjectService;

/** The projects of a portal account with key and name (TASK-075, DEC-105); an internal account gets 404. */
@RestController
@RequestMapping("/api/v1/portal/projects")
class PortalProjectController {
    private final ProjectService projects;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    PortalProjectController(ProjectService projects, AccessContexts identity, PermissionContexts contexts) {
        this.projects = projects;
        this.identity = identity;
        this.contexts = contexts;
    }

    /**
     * A project of the portal with the ticket types offered for new requests and the statuses with name and category, so that
     * the portal shows the status by its name (TASK-076).
     */
    record PortalProjectBody(UUID id, String key, String name, List<TypeBody> ticketTypes, List<StatusBody> statuses) {
    }

    record TypeBody(String key, String name) {
    }

    record StatusBody(String key, String name, String category) {
    }

    @GetMapping
    List<PortalProjectBody> list() {
        if (identity.require().level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        AccessContext actor = contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
        return ProjectController.retrying(() -> projects.portalProjectsWithConfiguration(actor)).stream()
            .map(entry -> new PortalProjectBody(entry.project().id(), entry.project().key(), entry.project().name(),
                entry.configuration().portalTypes().stream().map(type -> new TypeBody(type.key(), type.name())).toList(),
                entry.configuration().statuses().stream().map(status -> new StatusBody(status.key(), status.name(), status.category())).toList()))
            .toList();
    }
}
