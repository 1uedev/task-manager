package taskmanager.permissions;

/**
 * A change by the company administration that would leave no active account with the company right
 * {@code permissions.manage} (TASK-030). Only an actor who passed the check of the company administration can reach it, so
 * it tells nothing a caller without that right could not see. Still a {@link PermissionDenied}: the change is refused and the
 * transaction must roll back.
 */
public final class LastAdministration extends PermissionDenied {
}
