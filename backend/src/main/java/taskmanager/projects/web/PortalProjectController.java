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

    record PortalProjectBody(UUID id, String key, String name) {
    }

    @GetMapping
    List<PortalProjectBody> list() {
        if (identity.require().level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        AccessContext actor = contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
        return ProjectController.retrying(() -> projects.portalProjects(actor)).stream()
            .map(project -> new PortalProjectBody(project.id(), project.key(), project.name())).toList();
    }
}
