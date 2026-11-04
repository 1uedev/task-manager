package taskmanager.identity.account;

import java.time.Instant;
import java.util.UUID;

public record AccountRecord(
        UUID id,
        String username,
        String displayName,
        String passwordHash,
        boolean passwordChangeRequired,
        Instant oneTimeExpiresAt,
        Instant oneTimeUsedAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        String authKind,
        String externalId,
        String kind,
        UUID organizationId,
        UUID contactId) {

    public static final String LOCAL = "local";
    public static final String LDAP = "ldap";
    /** An account of the staff of the company. */
    public static final String INTERNAL = "internal";
    /**
     * A local account of a customer for the portal (TASK-075, DEC-105). It belongs to exactly one customer organization of
     * the helpdesk, which never changes, and optionally to one contact of that organization.
     */
    public static final String PORTAL = "portal";

    /** An internal account; the form every account had before portal accounts existed. */
    public AccountRecord(UUID id, String username, String displayName, String passwordHash, boolean passwordChangeRequired,
            Instant oneTimeExpiresAt, Instant oneTimeUsedAt, boolean active, Instant createdAt, Instant updatedAt, String authKind,
            String externalId) {
        this(id, username, displayName, passwordHash, passwordChangeRequired, oneTimeExpiresAt, oneTimeUsedAt, active, createdAt,
            updatedAt, authKind, externalId, INTERNAL, null, null);
    }

    /** True for an account of the portal (TASK-075). */
    public boolean portal() {
        return PORTAL.equals(kind);
    }

    /** True for an account that the directory authenticates (TASK-046). Its password hash is random and never matches. */
    public boolean fromDirectory() {
        return LDAP.equals(authKind);
    }

    /** What the account list says about the one time password (TASK-045). The password itself is never part of it. */
    public enum OneTimeState {
        /** The password was chosen by the account holder; there is no one time password. */
        NONE,
        /** Issued and not used yet, still valid. */
        OPEN,
        /** Issued and not used, and its validity has ended: an administration has to issue a new one. */
        EXPIRED,
        /** Used for the login; the forced change is still pending, and an administration must issue a new one if its session ended. */
        USED
    }

    public OneTimeState oneTimeState(Instant now) {
        if (!passwordChangeRequired) {
            return OneTimeState.NONE;
        }
        if (oneTimeUsedAt != null) {
            return OneTimeState.USED;
        }
        return oneTimeExpiresAt != null && !oneTimeExpiresAt.isAfter(now) ? OneTimeState.EXPIRED : OneTimeState.OPEN;
    }
}
