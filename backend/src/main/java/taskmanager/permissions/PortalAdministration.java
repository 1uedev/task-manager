package taskmanager.permissions;

import java.util.Set;
import java.util.UUID;

/**
 * What the administration of portal accounts needs of the permissions module (TASK-075, DEC-105). Both methods join the
 * transaction of the caller and run under the exclusive revision lock of the company, which the first one takes, so call it
 * before any identity lock and before the other one.
 */
public interface PortalAdministration {
    /** An allowing field rule of the group "Portal" for every project. */
    record FieldGrant(String resourceType, String field, PermissionAdministration.Operation operation) {
    }

    /**
     * Takes the exclusive revision lock and checks that the actor holds the company right {@code accounts.manage}.
     * Throws {@link PermissionDenied} otherwise.
     */
    void lockForPortalAccounts(AccessContext actor);

    /**
     * Adds the portal account to the group "Portal" and creates that group first if the company has none yet, with the given
     * actions and allowing field rules for every project. The group is found by its purpose, not by its name. Refused with
     * {@link PortalCompanyRight} if the group holds a company right, and with {@link PermissionDenied} if the account is not a
     * portal account of the company. Every change is audited with the actor.
     *
     * @return the identifier of the group
     */
    UUID admitToPortalGroup(AccessContext actor, UUID accountId, Set<String> actions, Set<FieldGrant> fields);
}
