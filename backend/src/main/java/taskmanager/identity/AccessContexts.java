package taskmanager.identity;

import java.util.Optional;

/**
 * Entry point for other modules. Requests to {@code /api/**} without a complete login never reach a
 * handler (default deny), so a handler under {@code /api/v1} can rely on {@link #require()}.
 */
public interface AccessContexts {
    /** The identity of the current request, if the request was authenticated. */
    Optional<AccessContext> current();

    /** The identity of the current request; fails when there is none, which signals a wiring error, not a client error. */
    default AccessContext require() {
        return current().orElseThrow(() -> new IllegalStateException("No authenticated request in scope"));
    }
}
