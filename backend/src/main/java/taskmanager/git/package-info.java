/**
 * Git link (TASK-111, DEC-141): provider instances for GitHub.com, GitLab.com and a GitLab of its own with the company right
 * {@code git.manage}, the connection of the own Git account over OAuth with tokens kept encrypted per person and instance,
 * and the repositories released per project. Every call to a provider uses the token of the person who looks; there is no
 * service account. Nothing is ever written to Git. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Git", allowedDependencies = { "platform", "identity", "permissions", "projects" })
package taskmanager.git;

import org.springframework.modulith.ApplicationModule;
