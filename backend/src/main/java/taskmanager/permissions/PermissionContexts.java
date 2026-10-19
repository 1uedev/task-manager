package taskmanager.permissions;

import java.util.Optional;

/**
 * The permissions context of the current request, built on the server from the identity of the request, the company
 * of the installation and the current rights version (TASK-039). Nothing in it comes from the client. Implemented
 * at the application entry point, so business modules depend on this port and neither on the identity module's
 * session handling nor on the company storage.
 *
 * <p>Call it inside the transaction that also calls {@link Permissions#require}: a context carries the rights version
 * that was current when it was built, and a later change of the rights makes it stale, which denies.
 */
public interface PermissionContexts {
    /** Empty without an authenticated request, without a company or while the login is incomplete. */
    Optional<AccessContext> current();

    /** Fails as a wiring error when there is no context; an empty context in a handler means the request must not run. */
    default AccessContext require() {
        return current().orElseThrow(() -> new IllegalStateException("No permission context for this request"));
    }
}
