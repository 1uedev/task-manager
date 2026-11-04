/**
 * Company appearance (TASK-036, TASK-081): the name, the accent color and the logo of the interface, the login page and the
 * portal. Everyone with a complete login reads the setting; the login page reads name, color and logo without a session;
 * only a holder of the right {@code appearance.manage} changes them. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Erscheinungsbild", allowedDependencies = { "platform", "identity" })
package taskmanager.appearance;

import org.springframework.modulith.ApplicationModule;
