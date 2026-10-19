/**
 * Company appearance (TASK-036): the accent color and the logo of the interface. Everyone with a complete
 * login reads them; only a holder of the right {@code appearance.manage} changes them. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Erscheinungsbild", allowedDependencies = { "platform", "identity" })
package taskmanager.appearance;

import org.springframework.modulith.ApplicationModule;
