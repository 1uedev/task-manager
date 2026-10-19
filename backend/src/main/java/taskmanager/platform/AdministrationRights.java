package taskmanager.platform;

import java.util.Set;

/**
 * Port through which the product modules ask whether the current request may use an {@link AdministrationRight}
 * (TASK-040, DEC-064). The application entry point implements it with the permissions module, so no module depends on
 * the permissions module and the permissions module does not depend on them. Exactly one implementation must be present;
 * without one every administrative request is refused.
 *
 * <p>{@link #require} is the authoritative check. It joins the transaction of the change, takes the rights lock and the
 * identity locks of the requester and holds them until the commit, so a right or a login that is withdrawn is seen by the
 * change that waits for it. Call it before any other lock of the change, except those a module orders ahead of the rights
 * on purpose (the setting row of the session duration, the serialization of deactivations), which the module documents.
 */
public interface AdministrationRights {
    /**
     * Needs the current transaction. Returns normally only if the current request is a complete login of an active account
     * and the account holds the right; otherwise it throws {@link ApiProblemException} with status 403 and code {@code auth.forbidden}.
     */
    void require(AdministrationRight right);

    /**
     * The rights the current request holds right now, as a snapshot in its own transaction. Empty for an anonymous request,
     * a login that is not complete or an installation without a company. Good for deciding what an interface shows and for
     * refusing a request early; it never authorizes a change.
     */
    Set<AdministrationRight> held();
}
