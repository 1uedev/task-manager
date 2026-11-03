package taskmanager.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * The name an account shows to others (TASK-082): the display name, or the login name when no display name is set. A mail
 * to a customer signs with it. Nothing else of the account leaves the module this way.
 */
public interface AccountNames {
    /** The shown name of the account, or empty for an unknown identifier. */
    Optional<String> shownName(UUID accountId);
}
