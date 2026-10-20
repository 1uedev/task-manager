/**
 * Ticket core with history and conflict detection (TASK-027, DEC-067): tickets of a project with the five fields of the
 * project configuration, numbers per project, field rights and a history of every change.
 */
@ApplicationModule(displayName = "Tickets", allowedDependencies = { "platform", "identity", "permissions", "projects" })
package taskmanager.tickets;

import org.springframework.modulith.ApplicationModule;
