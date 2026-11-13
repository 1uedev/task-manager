package taskmanager.git.internal;

import java.time.Instant;
import java.util.UUID;

/** The connection of one person with one provider (TASK-111). Tokens are only ever held encrypted. */
record Connection(UUID providerId, UUID accountId, UUID companyId, String externalUserId, String state, byte[] accessCipher,
        byte[] refreshCipher, Instant accessExpiresAt, Instant refreshExpiresAt, Instant connectedAt, Instant updatedAt) {

    static final String ACTIVE = "active";
    static final String RENEW = "renew";

    boolean active() {
        return ACTIVE.equals(state);
    }

    static String accessPurpose(UUID providerId, UUID accountId) {
        return "connection:" + providerId + ":" + accountId + ":access";
    }

    static String refreshPurpose(UUID providerId, UUID accountId) {
        return "connection:" + providerId + ":" + accountId + ":refresh";
    }
}
