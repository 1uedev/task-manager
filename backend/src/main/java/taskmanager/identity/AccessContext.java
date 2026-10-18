package taskmanager.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Server side identity of the current request, as the permissions module and the business modules see it.
 * It says who is logged in and how far the login is complete. It says nothing about what the person may
 * do: rights are never calculated in the identity module.
 *
 * @param accountId stable internal identity
 * @param username normalized login name
 * @param sessionId server side session reference
 * @param level {@link AuthenticationLevel#FULL} for regular access
 * @param systemAdministrator technical marker for the accounts that administer accounts and the session
 *        duration. It is not a group or project right (TASK-025).
 * @param sessionExpiresAt end of the fixed session period
 */
public record AccessContext(
        UUID accountId,
        String username,
        UUID sessionId,
        AuthenticationLevel level,
        boolean systemAdministrator,
        Instant sessionExpiresAt) {
}
