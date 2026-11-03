package taskmanager.helpdesk.internal;

import java.time.Instant;

/**
 * The connection of the replies to the SMTP server of a mailbox (TASK-058). The adapter speaks SMTP; tests may replace it.
 * A failure says whether the mail was certainly not accepted, and then whether trying again may help; otherwise the server
 * may have accepted it and nobody repeats it by itself.
 */
interface MailSender {
    void send(Mailbox mailbox, String password, Outgoing mail) throws SendFailure;

    /**
     * One mail with the own Message-ID and the references of the thread. With {@code rendered} it goes as plain text and HTML of
     * the same message with the logo embedded (TASK-082); without, as the plain text {@code body}, as replies confirmed before.
     */
    record Outgoing(String messageId, String fromAddress, String fromName, String toAddress, String toName, String subject, String body,
            String inReplyTo, String references, Instant date, Rendered rendered) {

        Outgoing(String messageId, String fromAddress, String fromName, String toAddress, String toName, String subject, String body,
                String inReplyTo, String references, Instant date) {
            this(messageId, fromAddress, fromName, toAddress, toName, subject, body, inReplyTo, references, date, null);
        }
    }

    /** The finished mail of a reply: plain text, HTML and the logo the HTML shows as {@code cid:logo}, or none. */
    record Rendered(String text, String html, String logoType, byte[] logo) {
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
