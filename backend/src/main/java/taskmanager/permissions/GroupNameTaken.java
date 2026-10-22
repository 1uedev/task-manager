package taskmanager.permissions;

/**
 * A new group would carry the name of an existing group of the company, compared without regard to case (TASK-043, P4).
 * Only an actor who passed the check of the company administration can reach it. Still a {@link PermissionDenied}: the
 * change is refused and the transaction must roll back.
 */
public final class GroupNameTaken extends PermissionDenied {
}
