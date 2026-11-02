package taskmanager.permissions;

/**
 * A portal account would hold a company wide right (TASK-075, DEC-105): it would join a group with such a right, or a group
 * with a portal member would receive one. Portal accounts never hold company rights; the change is refused and the
 * transaction must roll back.
 */
public final class PortalCompanyRight extends PermissionDenied {
}
