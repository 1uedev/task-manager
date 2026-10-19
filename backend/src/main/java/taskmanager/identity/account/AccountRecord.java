package taskmanager.identity.account;

import java.time.Instant;
import java.util.UUID;

public record AccountRecord(
        UUID id,
        String username,
        String displayName,
        String passwordHash,
        boolean passwordChangeRequired,
        Instant oneTimeExpiresAt,
        Instant oneTimeUsedAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
