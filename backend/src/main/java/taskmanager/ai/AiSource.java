package taskmanager.ai;

/**
 * One source of the context of an execution (TASK-125, rule 9), assembled by the module that owns the object with its own read
 * rules. The identifier ({@code S1}, {@code S2}, ...) is what the answer may refer to; the label names the source for the
 * model without content of its own. Sources that are not {@code required} are dropped from the front of the list when the
 * context is too large, so a caller puts the oldest entries first.
 */
public record AiSource(String id, String label, String text, boolean required) {
}
