package taskmanager.platform;

/**
 * The rights of the company administration that the product modules enforce on their administrative endpoints
 * (TASK-040, DEC-064). Each one is a company wide action of the permissions module without a project; the key is the
 * action key stored there. The catalog lives here, so the modules that enforce a right and the start of an installation
 * that grants it agree on the same spelling without depending on each other.
 */
public enum AdministrationRight {
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
    PROJECTS_MANAGE("projects.manage");

    private final String key;

    AdministrationRight(String key) {
        this.key = key;
    }

    /** The action key of the permissions module. */
    public String key() {
        return key;
    }
}
