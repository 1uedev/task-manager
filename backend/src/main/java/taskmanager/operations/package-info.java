/**
 * Operation of an installation (TASK-131, DEC-161): the command {@code after-restore}, which prepares a restored
 * installation before anyone works with it again, and the refusal of the web server and the worker while that command has
 * not run. Nothing here is reachable over HTTP. The module calls what the other modules offer for a restore
 * ({@code IdentityRestore}, {@code HelpdeskRestore}, {@code AiRestore}, {@code FilesRestore}); it owns no business data.
 */
@ApplicationModule(displayName = "Betrieb", allowedDependencies = { "platform", "identity", "permissions", "helpdesk", "ai", "files" })
package taskmanager.operations;

import org.springframework.modulith.ApplicationModule;
