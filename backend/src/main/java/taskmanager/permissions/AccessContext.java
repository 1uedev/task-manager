package taskmanager.permissions;

import java.util.Objects;
import java.util.UUID;

/** Server-side identity projection, never a browser payload. Revalidated through IdentityAccess. */
public record AccessContext(UUID companyId, UUID userId, UUID sessionId, long rightsVersion,
        boolean passwordChangeRequired, boolean external, UUID organizationId) {
    public AccessContext {
        Objects.requireNonNull(companyId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(sessionId);
        if (rightsVersion < 0 || (external && organizationId == null)) throw new IllegalArgumentException("Invalid context");
    }
}
