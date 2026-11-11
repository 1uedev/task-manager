/**
 * Permission aware search for tickets and projects (TASK-029, DEC-069) and wiki pages (TASK-102, DEC-132) and requirements (TASK-103, DEC-133). The module keeps its own projection of the searchable
 * fields, fed by the events of the projects, tickets, tags, wiki and requirements modules inside their transactions, and checks the current
 * rights of the caller at every search before it matches, ranks, counts or pages.
 */
@ApplicationModule(displayName = "Suche", allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets", "tags", "wiki", "requirements" })
package taskmanager.search;

import org.springframework.modulith.ApplicationModule;
