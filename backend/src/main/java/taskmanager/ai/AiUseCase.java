package taskmanager.ai;

import java.util.Arrays;
import java.util.Optional;

/** The four use cases of V1 (DEC-034); each has its skill per language, its model assignment and its answer format. */
public enum AiUseCase {
    TICKET_SUMMARY("ticket_summary"), REPLY_DRAFT("reply_draft"), REQUIREMENT_REVIEW("requirement_review"), WIKI_REVISION("wiki_revision");

    private final String key;

    AiUseCase(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<AiUseCase> of(String key) {
        return Arrays.stream(values()).filter(useCase -> useCase.key.equals(key)).findFirst();
    }
}
