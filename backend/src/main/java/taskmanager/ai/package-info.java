/**
 * AI foundation (TASK-125, DEC-156): provider accesses for OpenAI (or a server compatible with it), Anthropic and Ollama with
 * their models, versioned skills per use case and language with built-in skills, the assignment of one model per use case,
 * daily limits, the record of every execution without content and the suggestions kept for at most 24 hours. The company
 * right {@code ai.manage} covers the administration, the project action {@code ai.use} the use. An AI function never
 * changes an object and never sends anything: its result is a suggestion the person takes over through the normal ways of
 * the owning module. Everything in sub-packages is internal.
 */
@ApplicationModule(displayName = "AI", allowedDependencies = { "platform", "identity", "permissions", "projects", "jobs" })
package taskmanager.ai;

import org.springframework.modulith.ApplicationModule;
