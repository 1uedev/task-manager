package taskmanager.helpdesk.internal;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A reply to the contact of a ticket over the SMTP server of a mailbox (TASK-058, DEC-091) and where its sending stands.
 * {@code sending} is internal: an attempt began, and a later attempt that finds it does not know whether the server accepted
 * the mail. The interface shows it as pending.
 */
public record Reply(UUID id, UUID companyId, UUID projectId, UUID ticketId, UUID mailboxId, UUID contactId, String fromAddress,
        String toAddress, String subject, String body, String messageId, String inReplyTo, String references, String state,
        String errorCode, UUID jobId, int sends, Instant attemptedAt, Instant sentAt, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version) {

    /** The states in which the reply is not known to be sent; the list of open sends shows them. */
    public static final Set<String> OPEN = Set.of("pending", "sending", "failed", "uncertain");
}
