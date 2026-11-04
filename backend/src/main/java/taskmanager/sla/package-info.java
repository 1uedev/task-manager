/**
 * Service deadlines (TASK-068, DEC-098): business hours calendars, SLA rules of a project with budgets per priority, the
 * priority of a ticket and the cycles of a ticket with their clocks for first response and resolution. The clocks move in
 * the transaction of the change that moves them: a ticket or its status (tickets), the customer, a sent reply or an answer
 * of the customer (helpdesk). Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "SLA", allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets", "helpdesk", "jobs" })
package taskmanager.sla;

import org.springframework.modulith.ApplicationModule;
