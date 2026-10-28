/**
 * Helpdesk intake over IMAP (TASK-057, DEC-086): customer organizations and their contacts, mailboxes with encrypted
 * passwords, the recurring fetch of new mails, the confirmation of the sender through the Authentication-Results of the own
 * receiving server, tickets from mails of confirmed contacts, the review queue for everything else (DEC-031) and the
 * customer, contact and mails of a ticket. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Helpdesk", allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets", "files", "jobs" })
package taskmanager.helpdesk;

import org.springframework.modulith.ApplicationModule;
