/**
 * Requirements (TASK-096, DEC-126): requirements of a project with title, description, acceptance criteria, owner, priority
 * and a fixed simple status, technical revisions with the approval of exactly one revision after DEC-030, history,
 * comparison and field rights, with the project actions {@code requirement.read}, {@code requirement.edit} and
 * {@code requirement.approve}. Links with tickets and wiki pages of the same project (TASK-097, DEC-127). Everything in
 * sub-packages is internal.
 */
@ApplicationModule(displayName = "Requirements", allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets", "wiki" })
package taskmanager.requirements;

import org.springframework.modulith.ApplicationModule;
