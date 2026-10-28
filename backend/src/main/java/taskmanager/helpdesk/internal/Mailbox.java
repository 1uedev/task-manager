package taskmanager.helpdesk.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * A mailbox of the intake (TASK-057) with where the intake stands in its folder and the outcome of the last fetch. The
 * password is only present as cipher text and never leaves the module.
 *
 * <p>{@code source} counts the changes of server, port, account or folder; it is part of the origin of a mail, because the
 * same UIDVALIDITY and UID may name another mail in another folder. {@code compareUntil} is the highest UID up to which the
 * intake still compares Message-IDs after a new UIDVALIDITY, or null (TASK-059, F2 and F3).
 */
public record Mailbox(UUID id, UUID companyId, String name, boolean enabled, UUID projectId, String ticketType, String imapHost,
        int imapPort, String imapSecurity, String username, byte[] passwordCipher, String folder, String smtpHost, Integer smtpPort,
        String smtpSecurity, String address, String authservId, long maxBytes, Long uidValidity, long lastUid, int source, Long compareUntil,
        Instant polledAt,
        Instant succeededAt, String errorCode, Instant errorAt, String notice, Instant noticeAt, long version, Instant createdAt,
        UUID createdBy, Instant updatedAt, UUID updatedBy) {

    public boolean passwordSet() {
        return passwordCipher != null;
    }
}
