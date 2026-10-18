/** Groups, memberships, current action/field authorization and audited administration (TASK-025).
 * Consumers supply trusted identity/resource projections, never foreign entities or SQL access. */
@ApplicationModule(displayName = "Berechtigungen", allowedDependencies = { "platform", "identity" })
package taskmanager.permissions;

import org.springframework.modulith.ApplicationModule;
