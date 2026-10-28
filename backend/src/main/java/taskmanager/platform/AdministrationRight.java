package taskmanager.platform;

/**
 * The rights of the company administration that the product modules enforce on their administrative endpoints
 * (TASK-040, DEC-064). Each one is a company wide action of the permissions module without a project; the key is the
 * action key stored there. The catalog lives here, so the modules that enforce a right and the start of an installation
 * that grants it agree on the same spelling without depending on each other.
 */
public enum AdministrationRight {
    /**
     * Manage groups, their members, actions and field rules (TASK-025, TASK-030). The company administration of the
     * permissions module: the start of an installation always grants it, and the permissions module refuses a change that
     * would leave no active account holding it.
     */
    PERMISSIONS_MANAGE("permissions.manage"),
    /** Create, deactivate and reactivate accounts, reset their passwords and list them. */
    ACCOUNTS_MANAGE("accounts.manage"),
    /** Read and change the fixed session duration. */
    SESSION_DURATION_MANAGE("session_duration.manage"),
    /** Change the accent color and the logo of the company. */
    APPEARANCE_MANAGE("appearance.manage"),
    /**
     * Create projects and manage every project of the company, its configuration and its members, without being a member
     * (TASK-026, DEC-065). Gives no access to the content of a project such as tickets.
     */
    PROJECTS_MANAGE("projects.manage"),
    /**
     * Rename, archive and restore the tags of the company catalog (TASK-028, DEC-068). Granted for one project, or held
     * company wide by a member, it covers the catalog of that project too; the permissions module then requires the
     * membership.
     */
    TAGS_MANAGE("tags.manage"),
    /**
     * See the background jobs of the installation and of the company, run a failed job again and cancel a waiting one
     * (TASK-054, DEC-083). Gives no access to what a job works on.
     */
    JOBS_MANAGE("jobs.manage"),
    /**
     * Manage the mailboxes of the helpdesk, the customer organizations and their contacts (TASK-057). Gives no access to the
     * mails of a mailbox; the review queue needs the project action {@code helpdesk.review}.
     */
    HELPDESK_MANAGE("helpdesk.manage");

    private final String key;

    AdministrationRight(String key) {
        this.key = key;
    }

    /** The action key of the permissions module. */
    public String key() {
        return key;
    }
}
