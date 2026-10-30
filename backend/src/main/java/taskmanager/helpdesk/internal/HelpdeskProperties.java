package taskmanager.helpdesk.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the operation sets for the helpdesk (TASK-057, DEC-086).
 *
 * @param secretKey 32 random bytes in Base64 that encrypt the passwords of the mailboxes ({@code TASK_MANAGER_SECRET_KEY});
 *        empty means no mailbox password can be stored or used
 * @param pollInterval time between two fetches of every active mailbox
 * @param maxBytes the default size limit of one mail for a new mailbox (decimal, 25 MB); a larger mail goes to the review
 *        queue without its content
 * @param batch how many mails one fetch takes from a mailbox at most; the rest follows with the next fetch
 * @param timeout how long a connection to a mail server may take to open, answer or accept data
 * @param smtpTimeout how long the replies wait for each answer of an SMTP server once connected (TASK-062): ten minutes,
 *        the longest wait RFC 5321 section 4.5.3.2 asks for, which is the one for the answer to the end of the mail; a
 *        server that checks a mail for viruses or spam before it answers is not taken for one that dropped it
 */
@ConfigurationProperties(prefix = "taskmanager.helpdesk")
public record HelpdeskProperties(
        @DefaultValue("") String secretKey,
        @DefaultValue("60s") Duration pollInterval,
        @DefaultValue("25000000") long maxBytes,
        @DefaultValue("50") int batch,
        @DefaultValue("20s") Duration timeout,
        @DefaultValue("10m") Duration smtpTimeout) {
    public HelpdeskProperties {
        if (batch < 1 || batch > 1000) {
            throw new IllegalArgumentException("taskmanager.helpdesk.batch must lie between 1 and 1000");
        }
        if (smtpTimeout.isNegative() || smtpTimeout.isZero()) {
            throw new IllegalArgumentException("taskmanager.helpdesk.smtp-timeout must be positive");
        }
        if (maxBytes < 1) {
            throw new IllegalArgumentException("taskmanager.helpdesk.max-bytes must be positive");
        }
    }
}
