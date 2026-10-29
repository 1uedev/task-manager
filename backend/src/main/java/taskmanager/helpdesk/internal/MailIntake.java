package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.jobs.RecurringTask;
import taskmanager.tickets.TicketAccess;

/**
 * The fetch of new mails as a recurring task of the job executor (TASK-057, DEC-086): once per interval, however many
 * processes run, every active mailbox is read in turn.
 *
 * <p>Per mail, in this order: the mail is read without being marked as seen, recorded in one transaction together with
 * its ticket, if it gets one, then marked as seen, and only then the position in the folder moves on. The origin (mailbox,
 * source, UIDVALIDITY, UID) is unique, so a mail whose outcome was recorded but whose position was lost is recognized at the
 * next fetch and adds nothing. The source changes with server, port, account or folder, so a mail of a new folder is never
 * taken for one of the old (TASK-059, F3), and with a new UIDVALIDITY. A mail is recorded and the position moves only under
 * the lock of the mailbox and only for the source and UIDVALIDITY the fetch read; from the moment a start over of the folder
 * is committed, a fetch that still reads the old state writes neither mail, ticket nor position (TASK-060, R1; TASK-061, S1). A failure of one mailbox is recorded
 * with its code and does not stop the others.
 *
 * <p>Where the intake starts: at the first fetch of a folder, or after the server changed its UIDVALIDITY, at the oldest
 * mail not marked as seen; older mails stay untouched. After a new UIDVALIDITY every mail up to the highest UID the folder
 * had then is compared by its Message-ID, and one the mailbox already holds is skipped; the bound is stored with the
 * mailbox, so the comparison lasts across batches, failures and restarts (TASK-059, F2). The mailbox shows a notice.
 *
 * <p>Replies of the helpdesk (TASK-058): a mail that names the Message-ID of a reply of the project in In-Reply-To or References
 * joins the ticket of that reply instead of opening a new one, if the intake would have made a ticket of it and its sender
 * is a contact of the customer of that ticket; otherwise it waits in the review queue with that ticket as a hint. A reply of
 * the helpdesk itself that arrives in the folder is recorded as automatic and gets no ticket; automatic mails never get an
 * answer, so an auto responder cannot start a loop.
 */
@Component
@ConditionalOnExpression("'${taskmanager.process:web}' != 'command'")
public class MailIntake implements RecurringTask {
    private static final Logger LOG = LoggerFactory.getLogger(MailIntake.class);

    private final HelpdeskRepository repository;
    private final MailClient client;
    private final SecretBox secrets;
    private final TicketOpener opener;
    private final HelpdeskProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    MailIntake(HelpdeskRepository repository, MailClient client, SecretBox secrets, TicketOpener opener, HelpdeskProperties properties,
            Clock clock, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.client = client;
        this.secrets = secrets;
        this.opener = opener;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public String name() {
        return "helpdesk.fetch";
    }

    @Override
    public Duration interval() {
        return properties.pollInterval().toSeconds() < 1 ? Duration.ofSeconds(1) : properties.pollInterval();
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(10);
    }

    @Override
    public void run() {
        for (UUID mailbox : transaction.execute(status -> repository.enabledMailboxes())) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                fetch(mailbox);
            } catch (RuntimeException failure) {
                // Only the kinds of the failure: a message may carry text of the mail server, and with it the password (TASK-059, F1).
                LOG.warn("The fetch of mailbox {} failed with {}", mailbox, MailClient.kinds(failure));
                record(mailbox, "helpdesk.intake_failed");
            }
        }
    }

    /** Reads the new mails of one mailbox. Public for the tests, which run a fetch without the executor. */
    public void fetch(UUID mailboxId) {
        Optional<Mailbox> found = transaction.execute(status -> repository.mailbox(mailboxId));
        if (found.isEmpty() || !found.get().enabled()) {
            return;
        }
        Mailbox mailbox = found.get();
        transaction.executeWithoutResult(status -> repository.fetchStarted(mailboxId, clock.instant()));
        String password;
        try {
            password = mailbox.passwordCipher() == null ? "" : secrets.open(mailbox.id(), mailbox.passwordCipher());
        } catch (SecretBox.KeyMissing missing) {
            record(mailboxId, "helpdesk.secret_key_missing");
            return;
        } catch (SecretBox.Unreadable unreadable) {
            record(mailboxId, "helpdesk.password_unreadable");
            return;
        }
        try (MailClient.Connection connection = client.open(mailbox, password)) {
            long validity = connection.uidValidity();
            long lastUid = mailbox.lastUid();
            int source = mailbox.source();
            Long compareUntil = mailbox.uidValidity() != null && mailbox.uidValidity() == validity ? mailbox.compareUntil() : null;
            if (mailbox.uidValidity() == null || mailbox.uidValidity() != validity) {
                Long previous = mailbox.uidValidity();
                List<Long> unseen = connection.uids(0, true, 1);
                long top = highest(connection);
                long start = unseen.isEmpty() ? top : unseen.get(0) - 1;
                Long bound = previous != null && top > start ? top : null;
                Instant now = clock.instant();
                int current = source;
                // Only from the state this fetch read: another fetch that already started the folder wins, and this one
                // ends without writing (TASK-060, R1).
                boolean started = transaction.execute(status -> {
                    if (previous == null) {
                        return repository.startFolder(mailboxId, current, validity, start);
                    }
                    if (!repository.restartFolder(mailboxId, current, previous, validity, start, bound, "helpdesk.uidvalidity_changed", now)) {
                        return false;
                    }
                    repository.audit(mailbox.companyId(), "mailbox", mailboxId, "mailbox.uidvalidity_changed",
                        "{\"from\":" + previous + ",\"to\":" + validity + "}", null, now);
                    return true;
                });
                if (!started) {
                    return;
                }
                source = previous == null ? source : source + 1;
                lastUid = start;
                compareUntil = bound;
            }
            for (long uid : connection.uids(lastUid, false, properties.batch())) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (!take(mailbox, connection, source, validity, uid, compareUntil != null && uid <= compareUntil)) {
                    // The folder was started over or the settings changed meanwhile; the next fetch reads the new state.
                    return;
                }
            }
            transaction.executeWithoutResult(status -> repository.fetchSucceeded(mailboxId, clock.instant()));
        } catch (MailClient.MailFailure failure) {
            LOG.warn("The fetch of mailbox {} failed with {} ({})", mailboxId, failure.code(), MailClient.kinds(failure.getCause()));
            record(mailboxId, failure.code());
        }
    }

    /** The highest UID of the folder, or 0 for an empty one. */
    private static long highest(MailClient.Connection connection) throws MailClient.MailFailure {
        List<Long> all = connection.uids(0, false, Integer.MAX_VALUE);
        return all.isEmpty() ? 0 : all.get(all.size() - 1);
    }

    /** Takes one mail; false when the position could not move because this fetch no longer reads the current state. */
    private boolean take(Mailbox mailbox, MailClient.Connection connection, int source, long validity, long uid, boolean compare)
            throws MailClient.MailFailure {
        if (!transaction.execute(status -> repository.knownOrigin(mailbox.id(), source, validity, uid))) {
            long size = connection.size(uid);
            if (size < 0) {
                return true;
            }
            byte[] raw = null;
            ParsedMail parsed = null;
            String unreadable = null;
            if (size > mailbox.maxBytes()) {
                parsed = connection.header(uid);
            } else {
                raw = connection.fetch(uid);
                try {
                    parsed = ParsedMail.parse(raw);
                } catch (Exception failure) {
                    LOG.info("Mail {} of mailbox {} could not be read: {}", uid, mailbox.id(), failure.toString());
                    unreadable = "unreadable";
                }
            }
            ParsedMail mail = parsed;
            byte[] content = raw;
            String failure = unreadable;
            // The mail is recorded under the lock of the mailbox, and only while this fetch still reads its current source
            // and UIDVALIDITY: a fetch overtaken by a start over of the folder writes neither mail nor ticket (TASK-061, S1).
            // After a new UIDVALIDITY a mail the mailbox already holds keeps its first outcome; the same lock makes the
            // comparison and the insert one decision, also against a second fetch of the same mailbox.
            boolean current = transaction.execute(status -> {
                if (!repository.lockCurrent(mailbox.id(), source, validity)) {
                    return false;
                }
                if (compare && mail != null && mail.messageId() != null && repository.knownMessageId(mailbox.id(), mail.messageId())) {
                    return true;
                }
                record(mailbox, source, validity, uid, size, mail, content, failure);
                return true;
            });
            if (!current) {
                return false;
            }
        }
        try {
            connection.markSeen(uid);
        } catch (MailClient.MailFailure failure) {
            LOG.info("Mail {} of mailbox {} could not be marked as seen: {}", uid, mailbox.id(), failure.code());
        }
        return transaction.execute(status -> repository.position(mailbox.id(), source, validity, uid));
    }

    /** Records one mail and, for a confirmed contact of a released customer, its ticket; runs in one transaction. */
    private void record(Mailbox mailbox, int source, long validity, long uid, long size, ParsedMail parsed, byte[] raw,
            String unreadable) {
        Instant now = clock.instant();
        Decision decision;
        // The ticket of a reply this mail answers, through In-Reply-To or References (TASK-058).
        UUID replied = parsed == null ? null : repository.repliedTicket(mailbox.companyId(), mailbox.projectId(), parsed.references()).orElse(null);
        if (unreadable != null) {
            decision = Decision.review(unreadable);
        } else if (repository.ownReply(mailbox.companyId(), parsed.messageId())) {
            // A reply of the helpdesk that came back, for example as a copy: never a ticket and never answered.
            decision = new Decision("automatic", "own_reply", null, null);
        } else if (parsed.automatic()) {
            decision = Decision.automatic();
        } else if (size > mailbox.maxBytes()) {
            decision = Decision.review("too_large");
        } else {
            decision = classify(mailbox, parsed);
            if (decision.state().equals("ticket") && replied != null && !sameCustomer(replied, decision.customer())) {
                decision = Decision.review("reply_customer_mismatch");
            }
        }
        ParsedMail.Sender sender = parsed == null || parsed.from().isEmpty() ? null : parsed.from().get(0);
        List<Message.AttachmentNote> files = parsed == null ? List.of()
            : parsed.files().stream().map(file -> new Message.AttachmentNote(file.name(), file.content().length, null, null)).toList();
        Message message = new Message(UUID.randomUUID(), mailbox.companyId(), mailbox.id(), mailbox.projectId(), validity, uid,
            parsed == null ? null : parsed.messageId(), now, parsed == null ? null : parsed.sentAt(), sender == null ? null : sender.address(),
            sender == null ? null : sender.name(), parsed == null ? null : String.join(", ", parsed.to()),
            parsed == null ? null : String.join(", ", parsed.cc()), parsed == null ? null : parsed.subject(),
            parsed == null ? null : parsed.body(), size, files, raw != null,
            // A mail that gets a ticket waits as review until its ticket is written in this transaction.
            decision.state().equals("ticket") ? "review" : decision.state(), decision.reason(), null, null, null,
            null, null, null, decision.state().equals("review") ? replied : null, 1);
        if (!repository.insertMessage(message, source, raw)) {
            return;
        }
        if (decision.state().equals("ticket") && replied != null) {
            // The answer of a confirmed contact of the customer of the ticket joins that ticket instead of opening a new one.
            opener.append(message, parsed, replied, decision.customer(), decision.contact(), TicketAccess.MAIL_INTAKE, now);
            return;
        }
        if (decision.state().equals("ticket")) {
            Optional<TicketOpener.Opened> opened = opener.open(message, parsed, mailbox.ticketType(), decision.customer(), decision.contact(),
                TicketAccess.MAIL_INTAKE, now);
            if (opened.isEmpty()) {
                repository.reviewInstead(message.id(), "ticket_type_unavailable");
            }
        }
    }

    /** What the intake does with a mail it could read. */
    record Decision(String state, String reason, Customer customer, Contact contact) {
        static Decision review(String reason) {
            return new Decision("review", reason, null, null);
        }

        static Decision automatic() {
            return new Decision("automatic", "automatic", null, null);
        }
    }

    /**
     * A ticket only for one sender who is a known contact, confirmed by the own receiving server, of a customer released
     * for the project, with no contact of another customer among the recipients; everything else waits for a person.
     */
    private Decision classify(Mailbox mailbox, ParsedMail parsed) {
        if (parsed.from().isEmpty()) {
            return Decision.review("sender_missing");
        }
        if (parsed.from().size() > 1) {
            return Decision.review("sender_ambiguous");
        }
        String address = parsed.from().get(0).address();
        Optional<Contact> contact = repository.contactByAddress(mailbox.companyId(), address);
        if (contact.isEmpty()) {
            return Decision.review("sender_unknown");
        }
        if (!SenderCheck.confirmed(mailbox.authservId(), parsed.authenticationResults(), address)) {
            return Decision.review("sender_unconfirmed");
        }
        Optional<Customer> customer = repository.customer(mailbox.companyId(), contact.get().customerId());
        if (customer.isEmpty() || !customer.get().projects().contains(mailbox.projectId())) {
            return Decision.review("customer_not_released");
        }
        if (!parsed.recipientsComplete()) {
            return Decision.review("recipients_incomplete");
        }
        if (repository.otherCustomerAmong(mailbox.companyId(), customer.get().id(), concat(parsed.to(), parsed.cc()))) {
            return Decision.review("foreign_customer");
        }
        return new Decision("ticket", null, customer.get(), contact.get());
    }

    /** Whether the ticket has this customer; a reply joins its ticket only from a contact of the customer of the ticket. */
    private boolean sameCustomer(UUID ticket, Customer customer) {
        return repository.ticketCustomer(ticket).map(HelpdeskRepository.TicketCustomer::customerId).filter(customer.id()::equals).isPresent();
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private void record(UUID mailboxId, String code) {
        transaction.executeWithoutResult(status -> repository.fetchFailed(mailboxId, code, clock.instant()));
    }
}
