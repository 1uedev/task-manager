package taskmanager.projects.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.projects.internal.Member;
import taskmanager.projects.internal.ProjectService;
import taskmanager.projects.internal.ProjectService.Members;
import taskmanager.projects.internal.ProjectService.ProjectView;

/**
 * Projects, their configuration and members (TASK-026). Every endpoint needs a complete login; the service decides who may
 * read or change a project. The ETag is the version of the project, which moves with every change of the project, its
 * configuration or its members, and every change needs it as If-Match.
 */
@RestController
@RequestMapping("/api/v1/projects")
class ProjectController {
    private final ProjectService projects;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    ProjectController(ProjectService projects, AccessContexts identity, PermissionContexts contexts) {
        this.projects = projects;
        this.identity = identity;
        this.contexts = contexts;
    }

    record ProjectSummary(UUID id, String key, String name, String description, long version, boolean manageable) {
        static ProjectSummary of(ProjectView view) {
            var project = view.project();
            return new ProjectSummary(project.id(), project.key(), project.name(), project.description(), project.version(),
                view.manageable());
        }
    }

    record ProjectBody(UUID id, String key, String name, String description, long version, long configurationVersion,
            ProjectConfiguration configuration, boolean manageable, Instant createdAt, Instant updatedAt) {
        static ProjectBody of(ProjectView view) {
            var project = view.project();
            return new ProjectBody(project.id(), project.key(), project.name(), project.description(), project.version(),
                project.configurationVersion(), view.configuration(), view.manageable(), project.createdAt(), project.updatedAt());
        }
    }

    record CreateRequest(String key, String name, String description) {
    }

    record UpdateRequest(String name, String description) {
    }

    record MemberBody(UUID accountId, Instant addedAt) {
        static MemberBody of(Member member) {
            return new MemberBody(member.accountId(), member.addedAt());
        }
    }

    @GetMapping
    List<ProjectSummary> list() {
        return projects.list(actor()).stream().map(ProjectSummary::of).toList();
    }

    @PostMapping
    ResponseEntity<ProjectBody> create(@RequestBody(required = false) CreateRequest body) {
        AccessContext actor = actor();
        if (body == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("key.invalid", "name.invalid"));
        }
        return project(HttpStatus.CREATED, projects.create(actor, body.key(), body.name(), body.description()));
    }

    @GetMapping("/{id}")
    ResponseEntity<ProjectBody> get(@PathVariable UUID id) {
        return project(HttpStatus.OK, projects.get(actor(), id));
    }

    @PutMapping("/{id}")
    ResponseEntity<ProjectBody> update(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) UpdateRequest body) {
        AccessContext actor = actor();
        long expected = expectedVersion(ifMatch);
        return project(HttpStatus.OK, projects.update(actor, id, expected, body == null ? null : body.name(),
            body == null ? null : body.description()));
    }

    @PutMapping("/{id}/configuration")
    ResponseEntity<ProjectBody> changeConfiguration(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) ProjectConfiguration body) {
        AccessContext actor = actor();
        long expected = expectedVersion(ifMatch);
        return project(HttpStatus.OK, projects.changeConfiguration(actor, id, expected, body));
    }

    @GetMapping("/{id}/configurations/{version}")
    ProjectConfiguration configuration(@PathVariable UUID id, @PathVariable long version) {
        return projects.configuration(actor(), id, version);
    }

    @GetMapping("/{id}/members")
    ResponseEntity<List<MemberBody>> members(@PathVariable UUID id) {
        return members(projects.members(actor(), id));
    }

    @PutMapping("/{id}/members/{accountId}")
    ResponseEntity<List<MemberBody>> addMember(@PathVariable UUID id, @PathVariable UUID accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = actor();
        return members(projects.addMember(actor, id, expectedVersion(ifMatch), accountId));
    }

    @DeleteMapping("/{id}/members/{accountId}")
    ResponseEntity<List<MemberBody>> removeMember(@PathVariable UUID id, @PathVariable UUID accountId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = actor();
        return members(projects.removeMember(actor, id, expectedVersion(ifMatch), accountId));
    }

    /** The permissions context of a complete login; a login that still has to change its password is refused. */
    private AccessContext actor() {
        var request = identity.require();
        if (request.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    private static ResponseEntity<ProjectBody> project(HttpStatus status, ProjectView view) {
        return ResponseEntity.status(status).eTag("\"" + view.project().version() + "\"").body(ProjectBody.of(view));
    }

    private static ResponseEntity<List<MemberBody>> members(Members members) {
        return ResponseEntity.ok().eTag("\"" + members.version() + "\"").body(members.members().stream().map(MemberBody::of).toList());
    }

    private static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "project.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "project.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "project.version_conflict");
        }
    }
}
