package taskmanager.ai.internal;

import java.util.UUID;

/**
 * Published inside the transaction of every change of an access, a model, an assignment or a skill (TASK-129, F4). After the
 * commit the executions of this process compare their versions and end with {@code ai.configuration_changed} when they no
 * longer match, a running request included.
 */
record AiConfigurationChanged(UUID companyId) {
}
