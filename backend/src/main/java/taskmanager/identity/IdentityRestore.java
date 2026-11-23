package taskmanager.identity;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What the identity module does after a restore from a backup point (TASK-131, DEC-161). The command {@code after-restore}
 * calls it before the web server and the worker start again.
 */
public interface IdentityRestore {
    /** Ends every session that is still open, so nobody keeps working with a login from before the failure. Returns how many. */
    int revokeAllSessions(Instant at);

    /** The login names of those accounts in the collection that are active, by account. */
    Map<UUID, String> activeLoginNames(Collection<UUID> accountIds);
}
