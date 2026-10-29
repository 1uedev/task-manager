package taskmanager.helpdesk.internal;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A folder of a mail server held in memory, for the tests of the intake that need a fixed order between fetches (TASK-060).
 * The parser, the transactions and the database stay real; {@link #beforeFetch} and
 * {@link #beforeOpen} let a test hold a fetch.
 */
public final class ScriptedMailbox {
    private final long validity;
    private final SortedMap<Long, byte[]> mails = new TreeMap<>();
    private final Set<Long> seen = ConcurrentHashMap.newKeySet();
    private volatile LongConsumer beforeFetch = uid -> { };
    private volatile Runnable beforeOpen = () -> { };

    public ScriptedMailbox(long validity) {
        this.validity = validity;
    }

    public synchronized ScriptedMailbox put(long uid, byte[] raw) {
        mails.put(uid, raw);
        return this;
    }

    public ScriptedMailbox beforeFetch(LongConsumer action) {
        beforeFetch = action;
        return this;
    }

    /** Runs when a fetch opens the folder, after it read the state of the mailbox. */
    public ScriptedMailbox beforeOpen(Runnable action) {
        beforeOpen = action;
        return this;
    }

    public boolean seen(long uid) {
        return seen.contains(uid);
    }

    /** An intake of the application that reads this folder instead of the mail server. */
    public MailIntake intake(ApplicationContext context, int batch) {
        HelpdeskProperties defaults = context.getBean(HelpdeskProperties.class);
        HelpdeskProperties properties = new HelpdeskProperties(defaults.secretKey(), Duration.ofSeconds(60), defaults.maxBytes(), batch,
            defaults.timeout());
        MailClient client = (mailbox, password) -> {
            beforeOpen.run();
            return new Connection();
        };
        return new MailIntake(context.getBean(HelpdeskRepository.class), client, context.getBean(SecretBox.class),
            context.getBean(TicketOpener.class), properties, context.getBean(Clock.class), context.getBean(PlatformTransactionManager.class));
    }

    private final class Connection implements MailClient.Connection {
        @Override
        public long uidValidity() {
            return validity;
        }

        @Override
        public List<Long> uids(long lastUid, boolean unseenOnly, int limit) {
            synchronized (ScriptedMailbox.this) {
                return mails.keySet().stream().filter(uid -> uid > lastUid && (!unseenOnly || !seen.contains(uid))).limit(limit).toList();
            }
        }

        @Override
        public long size(long uid) {
            synchronized (ScriptedMailbox.this) {
                byte[] raw = mails.get(uid);
                return raw == null ? -1 : raw.length;
            }
        }

        @Override
        public byte[] fetch(long uid) {
            byte[] raw;
            synchronized (ScriptedMailbox.this) {
                raw = mails.get(uid);
            }
            beforeFetch.accept(uid);
            return raw;
        }

        /** The path of a mail too large to fetch, as the IMAP adapter takes it: only the header (TASK-061, H1). */
        @Override
        public ParsedMail header(long uid) {
            byte[] raw;
            synchronized (ScriptedMailbox.this) {
                raw = mails.get(uid);
            }
            beforeFetch.accept(uid);
            try {
                return ParsedMail.headerOnly(new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw)));
            } catch (MessagingException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public void markSeen(long uid) {
            seen.add(uid);
        }

        @Override
        public void close() {
        }
    }
}
