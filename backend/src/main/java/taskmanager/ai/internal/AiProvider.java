package taskmanager.ai.internal;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** A provider access of the company (TASK-125, rule 3). The API key is only ever held encrypted. */
public record AiProvider(UUID id, UUID companyId, Kind kind, String name, String baseUrl, byte[] keyCipher, String caCertificate,
        DataPath dataPath, boolean enabled, long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy,
        Instant removedAt) {

    /** The three adapters of V1 (rule 2). */
    public enum Kind {
        OPENAI("openai"), ANTHROPIC("anthropic"), OLLAMA("ollama");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static Kind of(String key) {
            return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst().orElse(null);
        }
    }

    /** Where the data goes, as the administration states it (rule 3). */
    public enum DataPath {
        LOCAL("local"), EXTERNAL("external");

        private final String key;

        DataPath(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static DataPath of(String key) {
            return Arrays.stream(values()).filter(path -> path.key.equals(key)).findFirst().orElse(null);
        }
    }

    public boolean keySet() {
        return keyCipher != null;
    }

    public boolean removed() {
        return removedAt != null;
    }

    /** Whether calls need a key: OpenAI and Anthropic always, Ollama only when one is stored. */
    boolean keyRequired() {
        return kind != Kind.OLLAMA;
    }

    /** What the cipher text of the API key is bound to. */
    static String keyPurpose(UUID providerId) {
        return "provider:" + providerId + ":key";
    }
}
