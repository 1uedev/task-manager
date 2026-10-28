package taskmanager.helpdesk.internal;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.CompanyContext;
import taskmanager.projects.ProjectAccess;
import taskmanager.tickets.TicketAccess;

/**
 * The rights of the helpdesk (TASK-057). The company right {@code helpdesk.manage} covers mailboxes, customers and
 * contacts; the project action {@code helpdesk.review} covers the mails of the project, needs the membership like every
 * project action and is checked with the project adapter, which locks the project row until the commit.
 */
@Component
public class HelpdeskAccess {
    static final String REVIEW = "helpdesk.review";

    private final ObjectProvider<AdministrationRights> rights;
    private final CompanyContext company;
    private final Permissions permissions;
    private final ProjectAccess projects;

    HelpdeskAccess(ObjectProvider<AdministrationRights> rights, CompanyContext company, Permissions permissions, ProjectAccess projects) {
        this.rights = rights;
        this.company = company;
        this.permissions = permissions;
        this.projects = projects;
    }

    /** The authoritative check of {@code helpdesk.manage}, first in the transaction; returns the company. */
    UUID requireManage() {
        var port = AdministrationRights.unique(rights);
        if (port == null) {
            throw forbidden();
        }
        port.require(AdministrationRight.HELPDESK_MANAGE);
        return company.find().orElseThrow(HelpdeskAccess::forbidden);
    }

    /** A snapshot of {@code helpdesk.manage} for the current request; never authorizes a change. */
    public boolean holdsManage() {
        var port = AdministrationRights.unique(rights);
        return port != null && port.held().contains(AdministrationRight.HELPDESK_MANAGE);
    }

    /** Whether the caller may see and process the mails of the project; locks the project row like any check. */
    boolean mayReview(AccessContext actor, UUID projectId) {
        return permissions.inspect(actor, resource(actor, projectId), REVIEW, Set.of()).allowed();
    }

    /** The authoritative check of {@code helpdesk.review} for a change; a refusal answers like an unknown mail. */
    void requireReview(AccessContext actor, UUID projectId) {
        try {
            permissions.require(actor, resource(actor, projectId), REVIEW, Set.of(), Set.of());
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(404, "helpdesk.message_not_found");
        }
    }

    Supplier<ResourceContext> resource(AccessContext actor, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
    }

    static ApiProblemException forbidden() {
        return ApiProblemException.of(403, "auth.forbidden");
    }
}
