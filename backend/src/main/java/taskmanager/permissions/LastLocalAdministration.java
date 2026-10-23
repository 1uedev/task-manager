package taskmanager.permissions;

/**
 * A change that would leave no active local account with both the company right {@code permissions.manage} and the company
 * right {@code accounts.manage} (TASK-047, DEC-076). Such an account is the way out when the directory fails, because it does not
 * depend on it. Like {@link LastAdministration} the change is refused and the transaction must roll back.
 */
public final class LastLocalAdministration extends PermissionDenied {
}
