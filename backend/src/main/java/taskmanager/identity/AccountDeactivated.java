package taskmanager.identity;

import java.util.UUID;

/**
 * Published in the transaction that deactivates an account (TASK-111), after the account is marked inactive and its sessions
 * ended. A listener runs in the same transaction, so whatever it removes for the account goes or stays with the deactivation.
 */
public record AccountDeactivated(UUID accountId) {
}
