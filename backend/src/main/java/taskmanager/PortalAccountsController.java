package taskmanager;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.identity.PortalAccountDirectory.Issued;
import taskmanager.identity.PortalAccountDirectory.PortalAccount;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.PortalCompanyRight;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.helpdesk.CustomerDirectory;

/**
 * Portal accounts (TASK-075, DEC-105); every endpoint needs the company right {@code accounts.manage}. Deactivation,
 * reactivation, password reset, display name and the waiting time after failed logins use the endpoints of all accounts under
 * {@code /api/v1/accounts}. The one time password appears only in the answer to the creation.
 */
@RestController
@RequestMapping("/api/v1/portal-accounts")
class PortalAccountsController {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");

    private final PortalAccounts portal;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    PortalAccountsController(PortalAccounts portal, AccessContexts identity, PermissionContexts contexts) {
        this.portal = portal;
        this.identity = identity;
        this.contexts = contexts;
    }

    record CreateRequest(String username, String displayName, UUID organizationId, UUID contactId, List<UUID> projectIds) {
    }

    record UpdateRequest(UUID contactId, List<UUID> projectIds) {
    }

    record PortalAccountBody(UUID id, String username, String displayName, boolean active, UUID organizationId, UUID contactId,
            List<UUID> projectIds) {
        static PortalAccountBody of(PortalAccount account, List<UUID> projects) {
            return new PortalAccountBody(account.id(), account.username(), account.displayName(), account.active(),
                account.organizationId(), account.contactId(), projects.stream().sorted().toList());
        }
    }

    record IssuedBody(PortalAccountBody account, String oneTimePassword, Instant oneTimePasswordExpiresAt) {
    }

    record ProjectBody(UUID id, String key, String name) {
        static ProjectBody of(ProjectAccess.Summary project) {
            return new ProjectBody(project.id(), project.key(), project.name());
        }
    }

    record ContactBody(UUID id, String name) {
        static ContactBody of(CustomerDirectory.ContactSummary contact) {
            return new ContactBody(contact.id(), contact.name());
        }
    }

    record OptionBody(UUID id, String name, List<ProjectBody> projects, List<ContactBody> contacts) {
    }

    @GetMapping
    List<PortalAccountBody> list() {
        return run(actor -> portal.list(actor).stream().map(detail -> PortalAccountBody.of(detail.account(), detail.projects())).toList());
    }

    /** The customer organizations with their released projects and contacts, for the form of a new portal account. */
    @GetMapping("/options")
    List<OptionBody> options() {
        return run(actor -> portal.options(actor).stream().map(option -> new OptionBody(option.id(), option.name(),
            option.projects().stream().map(ProjectBody::of).toList(), option.contacts().stream().map(ContactBody::of).toList())).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IssuedBody create(@RequestBody(required = false) CreateRequest body) {
        if (body == null || body.username() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("username.required"));
        }
        String actorName = identity.require().username();
        return run(actor -> {
            Issued issued = portal.create(actor, actorName, body.username(), body.displayName(), body.organizationId(), body.contactId(),
                body.projectIds());
            return new IssuedBody(PortalAccountBody.of(issued.account(), body.projectIds() == null ? List.of()
                : body.projectIds().stream().distinct().toList()), issued.oneTimePassword(), issued.oneTimeExpiresAt());
        });
    }

    /** Sets the contact and the projects of the portal account; the organization never changes. */
    @PutMapping("/{id}")
    PortalAccountBody update(@PathVariable UUID id, @RequestBody(required = false) UpdateRequest body) {
        if (body == null || body.projectIds() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("projectIds.required"));
        }
        String actorName = identity.require().username();
        return run(actor -> {
            var detail = portal.update(actor, actorName, id, body.contactId(), body.projectIds());
            return PortalAccountBody.of(detail.account(), detail.projects());
        });
    }

    private <T> T run(Function<AccessContext, T> work) {
        if (identity.require().level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        AccessContext actor = contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
        try {
            return work.apply(actor);
        } catch (PortalCompanyRight companyRight) {
            throw ApiProblemException.of(409, "permissions.portal_company_right");
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(403, "auth.forbidden");
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "account.busy", 1);
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
}
