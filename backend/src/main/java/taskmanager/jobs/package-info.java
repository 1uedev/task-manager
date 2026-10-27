/**
 * Durable background jobs (TASK-054, DEC-083): jobs in PostgreSQL that a module creates in the transaction of its own change,
 * executors in the web process or in a separate worker process that run them once with a lease, retries with a growing
 * delay, recurring tasks of the installation and the view of the operation with the right {@code jobs.manage}. The public
 * types of this package are the contract for other modules; everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "Hintergrundaufträge", allowedDependencies = { "platform", "identity" })
package taskmanager.jobs;

import org.springframework.modulith.ApplicationModule;
