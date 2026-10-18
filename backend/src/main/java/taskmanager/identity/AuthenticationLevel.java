package taskmanager.identity;

/** How far the login of the current request is complete. */
public enum AuthenticationLevel {
    /** The one time password was accepted, but the password has not been changed yet. Regular access is refused. */
    PASSWORD_CHANGE_REQUIRED,
    /** Fully logged in. */
    FULL
}
