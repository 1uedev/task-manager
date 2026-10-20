/**
 * Tags (TASK-028, DEC-068): a catalog of the company and one catalog per project, visible only to the members, and the
 * assignment of tags to tickets and to projects.
 */
@ApplicationModule(displayName = "Tags", allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets" })
package taskmanager.tags;

import org.springframework.modulith.ApplicationModule;
