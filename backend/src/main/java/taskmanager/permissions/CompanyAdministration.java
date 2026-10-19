package taskmanager.permissions;

import java.util.UUID;

/**
 * Operations on the company administration (right {@code permissions.manage} without project scope) that exist
 * outside a request: the initial setup and the protection of the last administration (TASK-039). Both join the caller's
 * transaction and need the identity adapter ({@link IdentityAccess}) like every other operation of this module.
 */
public interface CompanyAdministration {
    /**
     * Creates the rights of a new company in one step: the revision, one group with the company right
     * {@code permissions.manage} and the account as its only member. No business rights, no audit right, no further group.
     * Refused (the transaction must roll back) if the company already has rights or the account does not exist.
     */
    void initialize(UUID companyId, UUID administratorId);

    /**
     * Whether an active company administration would remain if the account no longer counted. Takes the exclusive
     * revision lock of the company, like every rights change, so it must run before the identity locks of the caller.
     * True for an account that is not part of the administration. False for an unknown company.
     */
    boolean remainsWithout(UUID companyId, UUID userId);
}
