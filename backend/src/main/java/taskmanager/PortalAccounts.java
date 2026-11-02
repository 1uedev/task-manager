package taskmanager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.helpdesk.CustomerDirectory;
import taskmanager.identity.PortalAccountDirectory;
import taskmanager.identity.PortalAccountDirectory.Issued;
import taskmanager.identity.PortalAccountDirectory.PortalAccount;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionAdministration.Operation;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.PortalAdministration;
import taskmanager.permissions.PortalAdministration.FieldGrant;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;

/**
 * The administration of portal accounts (TASK-075, DEC-105). It lives at the application entry point because one change
 * touches four modules in one transaction: the permissions module (right, group "Portal"), the identity module (the account),
 * the helpdesk (organization, contact, released projects) and the projects module (memberships).
 *
 * <p>Locks, in this order: the exclusive revision lock of the rights with the check of {@code accounts.manage}, then the account,
 * then the contact ({@code FOR SHARE}), then each project row ({@code FOR UPDATE}) in the order of their identifiers. The
 * exclusive rights lock comes first because the group membership needs it and a shared lock cannot be raised later without
 * a deadlock; it also orders every administration of portal accounts.
 */
@Service
class PortalAccounts {
    static final int MAX_PROJECTS = 100;
    /** The actions of the group "Portal" when it is created: read and create requests. Answering follows with TASK-076. */
    static final Set<String> DEFAULT_ACTIONS = Set.of("ticket.read", "ticket.create");
    /** The field rules of the group "Portal" when it is created; everything else of a ticket stays hidden. */
    static final Set<FieldGrant> DEFAULT_FIELDS;

    static {
        Set<FieldGrant> fields = new LinkedHashSet<>();
        for (String field : List.of("title", "description", "type", "status", "priority", "tags", "attachments")) {
            fields.add(new FieldGrant("ticket", field, Operation.READ));
        }
        for (String field : List.of("title", "description", "attachments")) {
            fields.add(new FieldGrant("ticket", field, Operation.WRITE));
        }
        DEFAULT_FIELDS = Set.copyOf(fields);
    }

    private final PortalAdministration portal;
    private final Permissions permissions;
    private final PortalAccountDirectory accounts;
    private final CustomerDirectory customers;
    private final ProjectAccess projects;

    PortalAccounts(PortalAdministration portal, Permissions permissions, PortalAccountDirectory accounts, CustomerDirectory customers,
            ProjectAccess projects) {
        this.portal = portal;
        this.permissions = permissions;
        this.accounts = accounts;
        this.customers = customers;
        this.projects = projects;
    }

    /** A portal account with its organization, contact and the projects it is a member of. */
    record Detail(PortalAccount account, List<UUID> projects) {
    }

    /** A customer organization as the form of a new portal account offers it, with its released projects and its contacts. */
    record Option(UUID id, String name, List<ProjectAccess.Summary> projects, List<CustomerDirectory.ContactSummary> contacts) {
    }

    @Transactional
    public List<Detail> list(AccessContext actor) {
        requireRight(actor);
        return accounts.list().stream()
            .map(account -> new Detail(account, projects.memberships(actor.companyId(), account.id()))).toList();
    }

    @Transactional
    public List<Option> options(AccessContext actor) {
        requireRight(actor);
        List<Option> options = new ArrayList<>();
        for (CustomerDirectory.Summary customer : customers.customers(actor.companyId())) {
            List<ProjectAccess.Summary> released = customer.projects().stream()
                .map(id -> projects.summary(actor.companyId(), id)).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(ProjectAccess.Summary::key)).toList();
            options.add(new Option(customer.id(), customer.name(), released, customers.contacts(actor.companyId(), customer.id())));
        }
        options.sort(Comparator.comparing(option -> option.name().toLowerCase(java.util.Locale.ROOT)));
        return options;
    }

    /**
     * Creates the portal account with a one time password, adds it to the group "Portal" (created with the first portal account)
     * and to the chosen projects of its organization.
     */
    @Transactional
    public Issued create(AccessContext actor, String actorName, String username, String displayName, UUID organizationId,
            UUID contactId, List<UUID> projectIds) {
        portal.lockForPortalAccounts(actor);
        List<UUID> chosen = projects(projectIds);
        if (organizationId == null) {
            throw invalid("organizationId.required");
        }
        Issued issued = accounts.create(actorName, username, displayName, organizationId, contactId);
        CustomerDirectory.Summary customer = customers.customer(actor.companyId(), organizationId)
            .orElseThrow(() -> invalid("organizationId.unknown"));
        checkContact(actor, organizationId, contactId);
        released(customer, chosen);
        UUID account = issued.account().id();
        portal.admitToPortalGroup(actor, account, DEFAULT_ACTIONS, DEFAULT_FIELDS);
        for (UUID project : chosen) {
            if (!projects.admit(actor.companyId(), project, account, actor.userId())) {
                throw invalid("projectIds.not_released");
            }
        }
        return issued;
    }

    /** Sets contact and projects of a portal account; the organization stays. Projects left out lose the membership. */
    @Transactional
    public Detail update(AccessContext actor, String actorName, UUID accountId, UUID contactId, List<UUID> projectIds) {
        portal.lockForPortalAccounts(actor);
        List<UUID> chosen = projects(projectIds);
        PortalAccount account = accounts.setContact(actorName, accountId, contactId,
            locked -> checkContact(actor, locked.organizationId(), contactId));
        CustomerDirectory.Summary customer = customers.customer(actor.companyId(), account.organizationId())
            .orElseThrow(() -> ApiProblemException.of(409, "account.organization_unknown"));
        released(customer, chosen);
        // The account is a member of the group "Portal" already unless an administration removed it; that stays its decision.
        List<UUID> current = projects.memberships(actor.companyId(), accountId);
        List<UUID> touched = new ArrayList<>(current);
        chosen.stream().filter(id -> !touched.contains(id)).forEach(touched::add);
        touched.sort(Comparator.naturalOrder());
        for (UUID project : touched) {
            if (chosen.contains(project) && !current.contains(project)) {
                projects.admit(actor.companyId(), project, accountId, actor.userId());
            } else if (!chosen.contains(project)) {
                projects.dismiss(actor.companyId(), project, accountId, actor.userId());
            }
        }
        return new Detail(account, projects.memberships(actor.companyId(), accountId));
    }

    private void requireRight(AccessContext actor) {
        permissions.require(actor, () -> ResourceContext.company(actor.companyId()), AdministrationRight.ACCOUNTS_MANAGE.key(), Set.of(), Set.of());
    }

    private void checkContact(AccessContext actor, UUID organizationId, UUID contactId) {
        if (contactId != null && !customers.lockCustomerOfContact(actor.companyId(), contactId)
                .filter(customer -> Objects.equals(customer, organizationId)).isPresent()) {
            throw invalid("contactId.not_of_organization");
        }
    }

    private static void released(CustomerDirectory.Summary customer, List<UUID> chosen) {
        if (!customer.projects().containsAll(chosen)) {
            throw invalid("projectIds.not_released");
        }
    }

    /** Distinct and sorted, so the project rows are always locked in the same order. */
    private static List<UUID> projects(List<UUID> projectIds) {
        if (projectIds == null) {
            return List.of();
        }
        if (projectIds.size() > MAX_PROJECTS || projectIds.contains(null)) {
            throw invalid("projectIds.invalid");
        }
        return projectIds.stream().distinct().sorted().toList();
    }

    private static ApiProblemException invalid(String violation) {
        return ApiProblemException.withViolations(400, "request.invalid", List.of(violation));
    }
}
