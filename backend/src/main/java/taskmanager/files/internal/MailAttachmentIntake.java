package taskmanager.files.internal;

import java.util.UUID;
import org.springframework.stereotype.Service;
import taskmanager.files.AttachmentIntake;

/** The port for the attachments of mails (TASK-057); the rules live in {@link AttachmentService}. */
@Service
class MailAttachmentIntake implements AttachmentIntake {
    private final AttachmentService attachments;

    MailAttachmentIntake(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @Override
    public Outcome attach(UUID companyId, UUID projectId, UUID ticketId, String name, byte[] content, UUID actor) {
        return attachments.attachFromMail(companyId, projectId, ticketId, name, content, actor);
    }
}
