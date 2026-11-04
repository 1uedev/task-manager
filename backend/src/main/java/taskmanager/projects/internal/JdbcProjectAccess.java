package taskmanager.projects.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.ResourceContext;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectChanged;
import taskmanager.projects.ProjectConfiguration;

/** The project adapter of the permissions contract; see {@link ProjectAccess} for the locks. */
@Service
class JdbcProjectAccess implements ProjectAccess {
    private final ProjectRepository projects;
    private final java.time.Clock clock;

    JdbcProjectAccess(ProjectRepository projects, java.time.Clock clock) {
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean admit(UUID companyId, UUID projectId, UUID accountId, UUID actor) {
        if (projectId == null || projects.lock(companyId, projectId, true).isEmpty()) {
            return false;
        }
        java.time.Instant now = clock.instant();
        if (projects.addMember(projectId, accountId, now, actor)) {
            projects.touch(projectId, now, actor);
            projects.audit(projectId, actor, "project.member_added", accountId.toString(), now);
        }
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean dismiss(UUID companyId, UUID projectId, UUID accountId, UUID actor) {
        if (projectId == null || projects.lock(companyId, projectId, true).isEmpty()) {
            return false;
        }
        java.time.Instant now = clock.instant();
        if (projects.removeMember(projectId, accountId)) {
            projects.touch(projectId, now, actor);
            projects.audit(projectId, actor, "project.member_removed", accountId.toString(), now);
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Summary> summary(UUID companyId, UUID projectId) {
        return projects.all(companyId).stream().filter(project -> project.id().equals(projectId)).findFirst()
            .map(project -> new Summary(project.id(), project.key(), project.name()));
    }

    @Override
    public Supplier<ResourceContext> resource(UUID companyId, UUID projectId, UUID accountId, String resourceType, boolean exclusive) {
        return () -> locked(companyId, projectId, accountId, resourceType, exclusive);
    }

    /** Runs inside the transaction of the permissions check that calls the supplier. */
    private ResourceContext locked(UUID companyId, UUID projectId, UUID accountId, String resourceType, boolean exclusive) {
        // A non member is refused without waiting for the project row: a project that is locked by a concurrent change
        // must answer like an unknown one (review F2 of TASK-026). A membership that is just being added counts from its commit.
        boolean member = projectId != null && accountId != null && projects.isMember(projectId, accountId)
            && projects.lock(companyId, projectId, exclusive).isPresent() && projects.isMember(projectId, accountId);
        // Without the project the context still names it, so the permissions module refuses it as a project resource.
        return new ResourceContext(companyId, projectId == null ? new UUID(0, 0) : projectId, null, member, resourceType);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> currentConfigurationVersion(UUID projectId) {
        return projects.configurationVersion(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectConfiguration> configuration(UUID projectId, long version) {
        return projects.configuration(projectId, version);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isMember(UUID projectId, UUID accountId) {
        return projects.isMember(projectId, accountId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> members(UUID projectId) {
        return projects.members(projectId).stream().map(Member::accountId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> key(UUID projectId) {
        return projects.key(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> memberships(UUID companyId, UUID accountId) {
        return projects.memberships(companyId, accountId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockForIntake(UUID companyId, UUID projectId) {
        return projects.lock(companyId, projectId, false).isPresent();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ProjectChanged> searchSources(UUID companyId) {
        return projects.all(companyId).stream().map(Project::changed).toList();
    }
}
