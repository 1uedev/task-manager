/** Projects, their members and configuration versions (TASK-026, DEC-065). Public: {@link taskmanager.projects.ProjectAccess}, {@link taskmanager.projects.ProjectConfiguration} and the port {@link taskmanager.projects.TicketUsage} that the tickets module implements (TASK-044). */
@ApplicationModule(displayName = "Projekte", allowedDependencies = { "platform", "identity", "permissions" })
package taskmanager.projects;

import org.springframework.modulith.ApplicationModule;
