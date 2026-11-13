package taskmanager.git.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.CompanyContext;
import taskmanager.projects.ProjectAccess;

/**
 * The rights of the Git link (TASK-111). The company right {@code git.manage} covers the provider instances. Connecting the
 * own account needs only a complete internal login; portal accounts never connect (rule 18). The repositories of a project
 * follow the rights of the project: {@code projects.manage} for every project, otherwise {@code project.read} or
 * {@code project.manage} shows them and {@code project.manage} releases and withdraws, both with the membership. The project
 * check locks the project row until the commit.
 */
@Component
class GitAccess {
    private static final String PROJECT_RESOURCE = "project";
    private static final String PROJECT_READ = "project.read";
    private static final String PROJECT_MANAGE = "project.manage";

    private final ObjectProvider<AdministrationRights> rights;
    private final CompanyContext company;
    private final Permissions permissions;
    private final ProjectAccess projects;

    GitAccess(ObjectProvider<AdministrationRights> rights, CompanyContext company, Permissions permissions, ProjectAccess projects) {
        this.rights = rights;
        this.company = company;
        this.permissions = permissions;
        this.projects = projects;
    }

    /** The authoritative check of {@code git.manage}, first in the transaction; returns the company. */
    UUID requireManage() {
        var port = AdministrationRights.unique(rights);
        if (port == null) {
            throw forbidden();
        }
        port.require(AdministrationRight.GIT_MANAGE);
        return company();
    }

    /** A snapshot of {@code git.manage} for the current request; never authorizes a change. */
    boolean holdsManage() {
        var port = AdministrationRights.unique(rights);
        return port != null && port.held().contains(AdministrationRight.GIT_MANAGE);
    }

    UUID company() {
        return company.find().orElseThrow(GitAccess::forbidden);
    }

    /**
     * Whether the caller may see the repositories of the project, checked with locks in the transaction; an unknown or
     * invisible project answers 404 like everywhere else.
     */
    void requireProject(AccessContext actor, UUID projectId, boolean change) {
        internal(actor);
        String companyRight = AdministrationRight.PROJECTS_MANAGE.key();
        Supplier<ResourceContext> companyResource = () -> ResourceContext.company(actor.companyId());
        if (permissions.inspect(actor, companyResource, companyRight, Set.of()).allowed()) {
            permissions.require(actor, companyResource, companyRight, Set.of(), Set.of());
            projects.key(projectId).orElseThrow(GitAccess::projectNotFound);
            return;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), PROJECT_RESOURCE, false);
        String action = List.of(PROJECT_READ, PROJECT_MANAGE).stream()
            .filter(candidate -> permissions.inspect(actor, resource, candidate, Set.of()).allowed()).findFirst()
            .orElseThrow(GitAccess::projectNotFound);
        if (change && !permissions.inspect(actor, resource, PROJECT_MANAGE, Set.of()).allowed()) {
            throw forbidden();
        }
        permissions.require(actor, resource, change ? PROJECT_MANAGE : action, Set.of(), Set.of());
    }

    /** Whether the caller may release repositories in the project; a snapshot for the answer, never authorizes. */
    boolean mayChangeProject(AccessContext actor, UUID projectId) {
        if (actor.external()) {
            return false;
        }
        Supplier<ResourceContext> companyResource = () -> ResourceContext.company(actor.companyId());
        if (permissions.inspect(actor, companyResource, AdministrationRight.PROJECTS_MANAGE.key(), Set.of()).allowed()) {
            return true;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), PROJECT_RESOURCE, false);
        return permissions.inspect(actor, resource, PROJECT_MANAGE, Set.of()).allowed();
    }

    static void internal(AccessContext actor) {
        if (actor.external()) {
            throw forbidden();
        }
    }

    static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }

    static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }
}
