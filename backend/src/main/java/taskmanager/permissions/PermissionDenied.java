package taskmanager.permissions;

/** Intentionally gives no account, object, field, group or existence information. */
public class PermissionDenied extends RuntimeException {
    public PermissionDenied() { super("Access denied"); }
}
