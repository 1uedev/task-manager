/**
 * Time tracking (TASK-069, DEC-099): time entries of a person in a project, optionally on a ticket of that project, with
 * corrections and cancellations that keep every earlier state, the project actions {@code time.book}, {@code time.read} and
 * {@code time.manage}, and the evaluation with sums and a CSV export. Tickets know nothing of time entries. Everything in
 * sub-packages is internal.
 */
@ApplicationModule(displayName = "Time", allowedDependencies = { "platform", "identity", "permissions", "projects", "tickets" })
package taskmanager.time;

import org.springframework.modulith.ApplicationModule;
