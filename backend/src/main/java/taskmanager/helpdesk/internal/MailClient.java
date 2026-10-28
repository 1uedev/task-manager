package taskmanager.helpdesk.internal;

import java.util.List;

/**
 * The connection of the intake to a mailbox (TASK-057). The adapter speaks IMAP; tests may replace it. A failure carries a
 * stable code without secrets, which the mailbox list shows.
 */
interface MailClient {
    /** Opens the folder of the mailbox for reading and for setting the seen flag. */
    Connection open(Mailbox mailbox, String password) throws MailFailure;

    interface Connection extends AutoCloseable {
        long uidValidity() throws MailFailure;

        /**
         * The UIDs to take, in ascending order, at most {@code limit}: every UID above {@code lastUid}, or at the first fetch
         * of the folder ({@code lastUid} 0 and {@code unseenOnly}) the mails not yet marked as seen.
         */
        List<Long> uids(long lastUid, boolean unseenOnly, int limit) throws MailFailure;

        /** The size the server reports, without fetching the mail; -1 when the mail is gone. */
        long size(long uid) throws MailFailure;

        /** The whole mail as the server delivers it, without marking it as seen. */
        byte[] fetch(long uid) throws MailFailure;

        /** Only the header, for a mail too large to fetch. */
        ParsedMail header(long uid) throws MailFailure;

        void markSeen(long uid) throws MailFailure;

        @Override
        void close();
    }

    /** A failure of the mail server or the connection, with its code. */
    final class MailFailure extends Exception {
        private static final long serialVersionUID = 1L;
        private final String code;

        MailFailure(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
