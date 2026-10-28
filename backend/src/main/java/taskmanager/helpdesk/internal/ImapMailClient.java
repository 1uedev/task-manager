package taskmanager.helpdesk.internal;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.FlagTerm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.SSLException;
import org.springframework.stereotype.Component;

/**
 * IMAP over Jakarta Mail (Eclipse Angus) for the intake (TASK-057). TLS checks the certificate and the host name of the
 * server against the trust of the Java runtime; StartTLS is required once chosen, so a server that does not offer it is
 * refused rather than read without encryption. Mails are read with {@code BODY.PEEK}, so they are marked as seen only after
 * the intake recorded them.
 */
@Component
class ImapMailClient implements MailClient {
    private final HelpdeskProperties properties;

    ImapMailClient(HelpdeskProperties properties) {
        this.properties = properties;
    }

    @Override
    public Connection open(Mailbox mailbox, String password) throws MailFailure {
        String protocol = "tls".equals(mailbox.imapSecurity()) ? "imaps" : "imap";
        Properties settings = new Properties();
        String prefix = "mail." + protocol + ".";
        String timeout = Long.toString(properties.timeout().toMillis());
        settings.put(prefix + "host", mailbox.imapHost());
        settings.put(prefix + "port", Integer.toString(mailbox.imapPort()));
        settings.put(prefix + "connectiontimeout", timeout);
        settings.put(prefix + "timeout", timeout);
        settings.put(prefix + "peek", "true");
        settings.put(prefix + "ssl.checkserveridentity", "true");
        if ("starttls".equals(mailbox.imapSecurity())) {
            settings.put(prefix + "starttls.enable", "true");
            settings.put(prefix + "starttls.required", "true");
        }
        Session session = Session.getInstance(settings);
        Store store = null;
        try {
            store = session.getStore(protocol);
            store.connect(mailbox.imapHost(), mailbox.imapPort(), mailbox.username(), password);
            Folder folder = store.getFolder(mailbox.folder());
            folder.open(Folder.READ_WRITE);
            if (!(folder instanceof UIDFolder)) {
                throw new MailFailure("helpdesk.imap_unsupported", "The folder has no UIDs", null);
            }
            return new ImapConnection(store, folder);
        } catch (MessagingException failure) {
            closeQuietly(store);
            throw failure(failure);
        } catch (MailFailure failure) {
            closeQuietly(store);
            throw failure;
        }
    }

    private static final class ImapConnection implements Connection {
        private final Store store;
        private final Folder folder;
        private final UIDFolder uids;

        ImapConnection(Store store, Folder folder) {
            this.store = store;
            this.folder = folder;
            this.uids = (UIDFolder) folder;
        }

        @Override
        public long uidValidity() throws MailFailure {
            try {
                return uids.getUIDValidity();
            } catch (MessagingException failure) {
                throw failure(failure);
            }
        }

        @Override
        public List<Long> uids(long lastUid, boolean unseenOnly, int limit) throws MailFailure {
            try {
                Message[] messages = unseenOnly && lastUid == 0
                    ? folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
                    : uids.getMessagesByUID(lastUid + 1, UIDFolder.MAXUID);
                List<Long> result = new ArrayList<>();
                for (Message message : messages) {
                    if (message == null || message.isExpunged()) {
                        continue;
                    }
                    long uid = uids.getUID(message);
                    // A range above the highest UID still returns the last mail, so the bound is checked here.
                    if (uid > lastUid) {
                        result.add(uid);
                    }
                }
                return result.stream().sorted().distinct().limit(limit).toList();
            } catch (MessagingException failure) {
                throw failure(failure);
            }
        }

        @Override
        public long size(long uid) throws MailFailure {
            try {
                Message message = uids.getMessageByUID(uid);
                return message == null ? -1 : Math.max(0, message.getSize());
            } catch (MessagingException failure) {
                throw failure(failure);
            }
        }

        @Override
        public byte[] fetch(long uid) throws MailFailure {
            try {
                Message message = uids.getMessageByUID(uid);
                if (message == null) {
                    throw new MailFailure("helpdesk.message_gone", "The mail is no longer in the folder", null);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, message.getSize()));
                message.writeTo(out);
                return out.toByteArray();
            } catch (MessagingException failure) {
                throw failure(failure);
            } catch (IOException failure) {
                throw new MailFailure("helpdesk.connection_failed", "The mail could not be read", failure);
            }
        }

        @Override
        public ParsedMail header(long uid) throws MailFailure {
            try {
                Message message = uids.getMessageByUID(uid);
                if (message == null) {
                    throw new MailFailure("helpdesk.message_gone", "The mail is no longer in the folder", null);
                }
                return ParsedMail.headerOnly(message);
            } catch (MessagingException failure) {
                throw failure(failure);
            }
        }

        @Override
        public void markSeen(long uid) throws MailFailure {
            try {
                Message message = uids.getMessageByUID(uid);
                if (message != null) {
                    message.setFlag(Flags.Flag.SEEN, true);
                }
            } catch (MessagingException failure) {
                throw failure(failure);
            }
        }

        @Override
        public void close() {
            try {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            } catch (MessagingException ignored) {
                // The connection ends anyway.
            }
            closeQuietly(store);
        }
    }

    /** The code of a failure; the message of the server goes only to the log of the caller. */
    static MailFailure failure(MessagingException failure) {
        if (failure instanceof AuthenticationFailedException) {
            return new MailFailure("helpdesk.authentication_failed", "The mail server refused the login", failure);
        }
        if (failure instanceof FolderNotFoundException) {
            return new MailFailure("helpdesk.folder_missing", "The folder does not exist", failure);
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) {
                return new MailFailure("helpdesk.tls_failed", "The encrypted connection failed", failure);
            }
            if (cause instanceof UnknownHostException || cause instanceof ConnectException) {
                return new MailFailure("helpdesk.connection_failed", "The mail server is not reachable", failure);
            }
            if (cause instanceof SocketTimeoutException) {
                return new MailFailure("helpdesk.timeout", "The mail server did not answer in time", failure);
            }
        }
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains("STARTTLS")) {
            return new MailFailure("helpdesk.starttls_unavailable", "The mail server does not offer STARTTLS", failure);
        }
        if (message.contains("Couldn't connect") || message.contains("connect")) {
            return new MailFailure("helpdesk.connection_failed", "The mail server is not reachable", failure);
        }
        return new MailFailure("helpdesk.protocol_error", "The mail server answered unexpectedly", failure);
    }

    private static void closeQuietly(Store store) {
        if (store == null) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
            // Nothing to keep.
        }
    }
}
