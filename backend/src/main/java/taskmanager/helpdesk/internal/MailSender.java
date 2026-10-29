package taskmanager.helpdesk.internal;

import java.time.Instant;

/**
 * The connection of the replies to the SMTP server of a mailbox (TASK-058). The adapter speaks SMTP; tests may replace it.
 * A failure says whether the mail was certainly not accepted, and then whether trying again may help; otherwise the server
 * may have accepted it and nobody repeats it by itself.
 */
interface MailSender {
    void send(Mailbox mailbox, String password, Outgoing mail) throws SendFailure;

    /** One plain text mail with the own Message-ID and the references of the thread. */
    record Outgoing(String messageId, String fromAddress, String fromName, String toAddress, String toName, String subject, String body,
            String inReplyTo, String references, Instant date) {
    }

    /**
     * A failed send with a stable code without secrets. {@code certain}: the server did not accept the mail, which either
     * may work later ({@code permanent} false) or not. Not certain: the answer of the server is missing after the mail may
     * have been handed over.
     */
    final class SendFailure extends Exception {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final boolean certain;
        private final boolean permanent;

        SendFailure(String code, boolean certain, boolean permanent, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.certain = certain;
            this.permanent = permanent;
        }

        String code() {
            return code;
        }

        boolean certain() {
            return certain;
        }

        boolean permanent() {
            return permanent;
        }
    }
}
