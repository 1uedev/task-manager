package taskmanager.identity;

import java.util.UUID;

/**
 * Port that the application entry point implements for the container command {@code reset-admin-password} (TASK-040).
 * The command is the way back for a locked out company administration, so it must refuse every other account. Whether an
 * account belongs to the company administration is a fact of the permissions module, which the identity module does not
 * call. Exactly one implementation must be present; without one the command fails and changes nothing.
 */
public interface EmergencyResetPolicy {
    /** True if the account belongs to the company administration. A plain read; the operator is trusted by access to the server. */
    boolean mayReset(UUID accountId);
}
