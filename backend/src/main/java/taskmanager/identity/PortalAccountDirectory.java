package taskmanager.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The portal accounts of the identity module for the application entry point (TASK-075, DEC-105). A portal account is a local
 * account of one customer organization, which never changes, and optionally of one contact of that organization. The caller
 * checks the right {@code accounts.manage} and the organization and contact against the helpdesk before it calls.
 */
public interface PortalAccountDirectory {
    /** A portal account as the administration shows it; the password hash never leaves the module. */
    record PortalAccount(UUID id, String username, String displayName, boolean active, UUID organizationId, UUID contactId) {
    }

    /** A new portal account with its one time password, which appears only in the answer to the creation. */
    record Issued(PortalAccount account, String oneTimePassword, Instant oneTimeExpiresAt) {
    }

    /** All portal accounts of the installation. */
    List<PortalAccount> list();

    /** Creates a local portal account with a one time password and the forced change at the first login; audited. */
    Issued create(String actor, String username, String displayName, UUID organizationId, UUID contactId);

    /**
     * Sets or clears the contact of a portal account; audited. Locks the account and hands it to {@code verify} before the change,
     * so the caller can check the contact against the locked organization. Answers 404 for an unknown account and 409
     * {@code account.not_portal} for an internal one.
     */
    PortalAccount setContact(String actor, UUID accountId, UUID contactId, Consumer<PortalAccount> verify);
}
