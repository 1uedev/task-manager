/**
 * AI assistance in the work of the support (TASK-126, DEC-157): the ticket summary from what the person may read and the reply
 * draft from what the customer already saw. The module assembles the sources with the read rules of the modules that own them,
 * starts the execution through the AI foundation and checks every time a suggestion is read that its sources are still the
 * same for the person. It writes nothing: a summary is only shown, a draft goes into the editable text field and is sent only
 * through the check and confirmation of the helpdesk. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "KI Assistenz",
    allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets", "helpdesk", "sla", "ai" })
package taskmanager.assist;

import org.springframework.modulith.ApplicationModule;
