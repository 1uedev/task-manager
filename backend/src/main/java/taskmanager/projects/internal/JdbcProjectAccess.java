package taskmanager.projects.internal;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.ResourceContext;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;

/** The project adapter of the permissions contract; see {@link ProjectAccess} for the locks. */
@Service
class JdbcProjectAccess implements ProjectAccess {
    private final ProjectRepository projects;

    JdbcProjectAccess(ProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    public Supplier<ResourceContext> resource(UUID companyId, UUID projectId, UUID accountId, String resourceType, boolean exclusive) {
        return () -> locked(companyId, projectId, accountId, resourceType, exclusive);
    }

    /** Runs inside the transaction of the permissions check that calls the supplier. */
    private ResourceContext locked(UUID companyId, UUID projectId, UUID accountId, String resourceType, boolean exclusive) {
        boolean member = projectId != null && accountId != null && projects.lock(companyId, projectId, exclusive).isPresent()
            && projects.isMember(projectId, accountId);
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
}
