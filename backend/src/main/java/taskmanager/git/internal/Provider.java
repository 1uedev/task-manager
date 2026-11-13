package taskmanager.git.internal;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** A provider instance of the company (TASK-111). The secret of the application is only ever held encrypted. */
public record Provider(UUID id, UUID companyId, Kind kind, String name, String baseUrl, String clientId, byte[] secretCipher,
        String caCertificate, long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy, Instant removedAt) {

    /** The three kinds of DEC-036; GitHub Enterprise Server follows later. */
    public enum Kind {
        GITHUB("github"), GITLAB_COM("gitlab_com"), GITLAB("gitlab");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public boolean gitlab() {
            return this != GITHUB;
        }

        static Kind of(String key) {
            return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst().orElse(null);
        }
    }

    public boolean secretSet() {
        return secretCipher != null;
    }

    public boolean removed() {
        return removedAt != null;
    }

    /** What the cipher text of the application secret is bound to. */
    static String secretPurpose(UUID providerId) {
        return "provider:" + providerId + ":secret";
    }
}
