package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.jobs.JobExecution;
import taskmanager.jobs.JobFailure;
import taskmanager.jobs.JobHandler;
import taskmanager.permissions.IdentityAccess;
import taskmanager.projects.ProjectAccess;

/**
 * Sends a confirmed reply (TASK-058, DEC-091) as the job {@code helpdesk.reply}.
 *
 * <p>An attempt first marks the reply as {@code sending} in a transaction of its own, after checking again that the author
 * is an active member of the project, that the mailbox still sends for the project from the same address and that the
 * recipient is still an address of the contact of the ticket; a precondition that no longer holds fails the reply for good.
 * Then it talks to the SMTP server and records the outcome:
 * <ul>
 *   <li>accepted: {@code sent};</li>
 *   <li>certainly not accepted and worth another try: back to {@code pending}, the job runs again after its delay, and after
 *       the last attempt {@code failed};</li>
 *   <li>certainly not accepted for good: {@code failed};</li>
 *   <li>handed over without an answer: {@code uncertain}.</li>
 * </ul>
 * An attempt that finds the reply still {@code sending} comes after one whose outcome was lost (the process ended, the time
 * limit or the stop interrupted it) and records {@code uncertain} without sending: nothing is ever sent twice by itself. The
 * log names the reply and the code, never the text of the mail server.
 */
@Component
class ReplySender implements JobHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ReplySender.class);

    private final HelpdeskRepository repository;
    private final MailSender sender;
    private final SecretBox secrets;
    private final ProjectAccess projects;
    private final ObjectProvider<IdentityAccess> identity;
    private final Clock clock;
    private final TransactionTemplate transaction;

    ReplySender(HelpdeskRepository repository, MailSender sender, SecretBox secrets, ProjectAccess projects,
            ObjectProvider<IdentityAccess> identity, Clock clock, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.sender = sender;
        this.secrets = secrets;
        this.projects = projects;
        this.identity = identity;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public String kind() {
        return ReplyService.JOB_KIND;
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(2);
    }

    /** What an attempt does after the first transaction: send this mail with this password, or nothing. */
    private record Prepared(Mailbox mailbox, String password, MailSender.Outgoing mail) {
    }

    @Override
    public void handle(JobExecution execution) {
        UUID replyId;
        try {
            replyId = UUID.fromString(execution.payload().getOrDefault("reply", ""));
        } catch (IllegalArgumentException invalid) {
            throw JobFailure.permanent("helpdesk.reply_unknown", "The job names no reply", invalid);
        }
        Optional<Prepared> prepared = transaction.execute(status -> prepare(execution, replyId));
        if (prepared.isEmpty()) {
            return;
        }
        Prepared work = prepared.get();
        try {
            sender.send(work.mailbox(), work.password(), work.mail());
        } catch (MailSender.SendFailure failure) {
            LOG.info("Reply {} was not sent in attempt {} of {}: {} ({})", replyId, execution.attempt(), execution.maxAttempts(),
                failure.code(), MailClient.kinds(failure.getCause()));
            if (!failure.certain()) {
                finish(replyId, "uncertain", failure.code(), null);
                throw JobFailure.permanent(failure.code(), "The mail server may have accepted the reply", null);
            }
            if (failure.permanent() || execution.attempt() >= execution.maxAttempts()) {
                finish(replyId, "failed", failure.code(), null);
                throw JobFailure.permanent(failure.code(), "The reply was not sent", null);
            }
            finish(replyId, "pending", failure.code(), null);
            throw JobFailure.retryable(failure.code(), "The reply was not sent yet", null);
        }
        finish(replyId, "sent", null, clock.instant());
    }

    /**
     * Locks the reply and decides whether this attempt sends: only a pending reply of this job. A reply found in
     * {@code sending} becomes uncertain; a precondition that fails ends the reply as failed.
     */
    private Optional<Prepared> prepare(JobExecution execution, UUID replyId) {
        Reply reply = repository.lockReply(replyId).orElse(null);
        if (reply == null || !reply.jobId().equals(execution.id())) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (reply.state().equals("sending")) {
            LOG.warn("Reply {} was handed over before without a recorded outcome; it is uncertain and not sent again", replyId);
            repository.replyState(replyId, "uncertain", "helpdesk.send_interrupted", null, null, now, reply.createdBy());
            return Optional.empty();
        }
        if (!reply.state().equals("pending")) {
            return Optional.empty();
        }
        String refused = refusal(reply);
        Mailbox mailbox = repository.mailbox(reply.companyId(), reply.mailboxId()).orElse(null);
        String password = null;
        if (refused == null) {
            try {
                password = mailbox.passwordCipher() == null ? "" : secrets.open(mailbox.id(), mailbox.passwordCipher());
            } catch (SecretBox.KeyMissing missing) {
                refused = "helpdesk.secret_key_missing";
            } catch (SecretBox.Unreadable unreadable) {
                refused = "helpdesk.password_unreadable";
            }
        }
        if (refused != null) {
            LOG.info("Reply {} is not sent: {}", replyId, refused);
            repository.replyState(replyId, "failed", refused, null, null, now, reply.createdBy());
            return Optional.empty();
        }
        repository.replyState(replyId, "sending", reply.errorCode(), now, null, now, reply.createdBy());
        String toName = repository.contact(reply.companyId(), reply.contactId()).map(Contact::name).orElse(null);
        return Optional.of(new Prepared(mailbox, password, new MailSender.Outgoing(reply.messageId(), reply.fromAddress(), mailbox.name(),
            reply.toAddress(), toName, reply.subject(), reply.body(), reply.inReplyTo(), reply.references(), now)));
    }

    /** Why the reply may no longer be sent as confirmed, or {@code null}. */
    private String refusal(Reply reply) {
        if (!projects.lockForIntake(reply.companyId(), reply.projectId())) {
            return "helpdesk.project_missing";
        }
        IdentityAccess accounts = identity.getIfUnique();
        if (accounts == null || !accounts.userActive(reply.companyId(), reply.createdBy()) || !projects.isMember(reply.projectId(), reply.createdBy())) {
            return "helpdesk.author_inactive";
        }
        Optional<Mailbox> mailbox = repository.mailbox(reply.companyId(), reply.mailboxId());
        if (mailbox.isEmpty() || !ReplyService.sends(mailbox.get(), reply.projectId()) || !mailbox.get().address().equals(reply.fromAddress())) {
            return "helpdesk.mailbox_unavailable";
        }
        boolean recipient = repository.ticketCustomer(reply.ticketId()).map(HelpdeskRepository.TicketCustomer::contactId)
            .filter(reply.contactId()::equals).flatMap(id -> repository.contact(reply.companyId(), id))
            .map(contact -> contact.addresses().contains(reply.toAddress())).orElse(false);
        return recipient ? null : "helpdesk.recipient_changed";
    }

    private void finish(UUID replyId, String state, String code, Instant sentAt) {
        transaction.executeWithoutResult(status -> repository.lockReply(replyId).ifPresent(reply -> {
            if (reply.state().equals("sending")) {
                repository.replyState(replyId, state, code, null, sentAt, clock.instant(), reply.createdBy());
            }
        }));
    }
}
