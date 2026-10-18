/**
 * Local accounts, login, logout, deactivation, forced password change and fixed session periods (TASK-024).
 * Public: {@link taskmanager.identity.AccessContext} and {@link taskmanager.identity.AccessContexts}. Everything
 * in sub-packages is internal. LDAP and portal functions follow later; the provider interface keeps them apart.
 */
@ApplicationModule(displayName = "Identität", allowedDependencies = { "platform" })
package taskmanager.identity;

import org.springframework.modulith.ApplicationModule;
