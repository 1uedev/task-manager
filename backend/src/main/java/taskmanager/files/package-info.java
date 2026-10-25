/**
 * Files and attachments (TASK-051, DEC-080): the attachments of tickets, a file store on a persistent volume outside the
 * database, the checks of an upload and the clean up of content nobody references any more.
 */
@ApplicationModule(displayName = "Dateien", allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets" })
package taskmanager.files;

import org.springframework.modulith.ApplicationModule;
