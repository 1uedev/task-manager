package taskmanager.ai.internal;

import java.time.Instant;
import java.util.UUID;
import taskmanager.ai.AiUseCase;

/** One stored version of the skill of a use case and language (TASK-125, rule 6). */
public record SkillVersion(UUID id, UUID companyId, AiUseCase useCase, String language, int number, String name, String content,
        State state, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy, Instant activatedAt, UUID activatedBy,
        Instant archivedAt) {

    public enum State {
        DRAFT("draft"), ACTIVE("active"), ARCHIVED("archived");

        private final String key;

        State(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static State of(String key) {
            for (State state : values()) {
                if (state.key.equals(key)) {
                    return state;
                }
            }
            throw new IllegalStateException("Unknown skill state " + key);
        }
    }

    /** The label the record of an execution keeps, such as {@code v3} or {@code draft-4}. */
    String label() {
        return state == State.DRAFT ? "draft-" + number : "v" + number;
    }
}
