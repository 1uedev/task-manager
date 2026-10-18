/** Ticket core with history and conflict detection (TASK-027). Empty boundary. */
@ApplicationModule(displayName = "Tickets", allowedDependencies = { "platform", "identity", "permissions", "projects" })
package taskmanager.tickets;

import org.springframework.modulith.ApplicationModule;
