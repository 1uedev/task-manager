package taskmanager.files;

import java.util.UUID;

/**
 * Port through which the helpdesk adds the attachments of a mail to a ticket (TASK-057). The same checks as for an upload
 * apply (name, blocked extensions, size, content that starts like a program); a refused file is reported, not thrown, so
 * the other files of the mail still arrive. The caller decided that the work is allowed: no right of the actor is checked.
 */
public interface AttachmentIntake {
    /**
     * Needs the current transaction. Puts the content in the file store first and then writes the attachment and its entry
     * in the history of the ticket in that transaction; if it rolls back, the clean up removes the content later.
     *
     * @throws java.io.UncheckedIOException when the file store fails, so the caller can try again later
     */
    Outcome attach(UUID companyId, UUID projectId, UUID ticketId, String name, byte[] content, UUID actor);

    /**
     * What became of one file: the new attachment, or the code of the refusal such as {@code attachment.type_blocked},
     * {@code attachment.too_large}, {@code attachment.empty} or {@code attachment.name_invalid}.
     */
    record Outcome(UUID attachmentId, String name, String refusal) {
        public boolean added() {
            return attachmentId != null;
        }
    }
}
