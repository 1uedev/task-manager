/**
 * Wiki (TASK-093, DEC-123): pages of a project in a tree, a shared draft per page whose every save is a revision that is
 * never changed, the explicit publication of one revision, history with comparison, archiving and restoring, images in the
 * file store and links between pages, with the project actions {@code wiki.read}, {@code wiki.edit} and {@code wiki.publish}.
 * Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Wiki", allowedDependencies = { "platform", "identity", "permissions", "projects", "files" })
package taskmanager.wiki;

import org.springframework.modulith.ApplicationModule;
