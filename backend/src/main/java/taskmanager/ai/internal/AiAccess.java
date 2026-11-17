package taskmanager.ai.internal;

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
 * The rights of the AI foundation (TASK-125). The company right {@code ai.manage} covers accesses, models, skills, assignments,
 * limits, usage and the record. Using a function needs the project action {@code ai.use} in the project of the object, held
 * with the membership like the other project actions (rule 9); the module of the use case adds the rights of the object.
 * Portal accounts never use an AI function, also not through the API (DEC-035, prüffall A06).
 */
@Component
class AiAccess {
    static final String USE = "ai.use";
    private static final String PROJECT_RESOURCE = "project";

    private final ObjectProvider<AdministrationRights> rights;
    private final CompanyContext company;
    private final Permissions permissions;
    private final ProjectAccess projects;

    AiAccess(ObjectProvider<AdministrationRights> rights, CompanyContext company, Permissions permissions, ProjectAccess projects) {
        this.rights = rights;
        this.company = company;
        this.permissions = permissions;
        this.projects = projects;
    }

    /** The authoritative check of {@code ai.manage}, first in the transaction; returns the company. */
    UUID requireManage() {
        var port = AdministrationRights.unique(rights);
        if (port == null) {
            throw forbidden();
        }
        port.require(AdministrationRight.AI_MANAGE);
        return company();
    }

    /** A snapshot of {@code ai.manage} for the current request; never authorizes a change. */
    boolean holdsManage() {
        var port = AdministrationRights.unique(rights);
        return port != null && port.held().contains(AdministrationRight.AI_MANAGE);
    }

    UUID company() {
        return company.find().orElseThrow(AiAccess::forbidden);
    }

    /**
     * The check of {@code ai.use} in the project, with the locks of the permissions module until the commit. An internal person
     * without it gets 403 {@code ai.use_forbidden}; a portal account always 403 {@code auth.forbidden}.
     */
    void requireUse(AccessContext actor, UUID projectId) {
        internal(actor);
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), PROJECT_RESOURCE, false);
        if (!permissions.inspect(actor, resource, USE, Set.of()).allowed()) {
            throw ApiProblemException.of(403, "ai.use_forbidden");
        }
        permissions.require(actor, resource, USE, Set.of(), Set.of());
    }

    /** Whether the person holds {@code ai.use} in the project; a snapshot for an answer, never authorizes. */
    boolean mayUse(AccessContext actor, UUID projectId) {
        if (actor.external()) {
            return false;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), PROJECT_RESOURCE, false);
        return permissions.inspect(actor, resource, USE, Set.of()).allowed();
    }

    static void internal(AccessContext actor) {
        if (actor.external()) {
            throw forbidden();
        }
    }

    static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
