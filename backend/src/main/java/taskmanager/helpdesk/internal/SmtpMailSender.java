package taskmanager.helpdesk.internal;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.springframework.stereotype.Component;

/**
 * SMTP over Jakarta Mail (Eclipse Angus) for the replies (TASK-058). Server and encryption come from the SMTP settings of the
 * mailbox, the login from its account; TLS checks the certificate and the host name, StartTLS is required once chosen.
 *
 * <p>The outcome is split in two phases (TASK-062, C-F4). Until the end of the mail is sent, the server holds nothing it
 * could deliver: a broken connection, a missing answer or a local error there certainly sends nothing and may be tried
 * again. Only the answer to the end of the mail accepts it; if that answer is missing the server may have taken the mail and
 * the send is uncertain. An answer with an error code proves in either phase that the server did not take the mail. Each
 * answer may take {@code smtp-timeout} (ten minutes by default) once connected, so a slow check of the mail at the server
 * does not end as uncertain.
 */
@Component
class SmtpMailSender implements MailSender {
    private final HelpdeskProperties properties;

    SmtpMailSender(HelpdeskProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(Mailbox mailbox, String password, Outgoing mail) throws SendFailure {
        boolean ssl = "tls".equals(mailbox.smtpSecurity());
        String protocol = ssl ? "smtps" : "smtp";
        Properties settings = new Properties();
        String prefix = "mail." + protocol + ".";
        String answer = Long.toString(properties.smtpTimeout().toMillis());
        settings.put(prefix + "host", mailbox.smtpHost());
        settings.put(prefix + "port", Integer.toString(smtpPort(mailbox)));
        settings.put(prefix + "connectiontimeout", Long.toString(properties.timeout().toMillis()));
        settings.put(prefix + "timeout", answer);
        settings.put(prefix + "writetimeout", answer);
        settings.put(prefix + "ssl.checkserveridentity", "true");
        settings.put(prefix + "auth", Boolean.toString(!password.isEmpty()));
        if ("starttls".equals(mailbox.smtpSecurity())) {
            settings.put(prefix + "starttls.enable", "true");
            settings.put(prefix + "starttls.required", "true");
        }
        Session session = Session.getInstance(settings);
        MimeMessage message;
        try {
            message = message(session, mail);
        } catch (MessagingException | UnsupportedEncodingException invalid) {
            throw new SendFailure("helpdesk.message_invalid", true, true, "The reply could not be built", invalid);
        }
        PhasedTransport transport = new PhasedTransport(session, new URLName(protocol, mailbox.smtpHost(), smtpPort(mailbox), null, null, null),
            protocol, ssl);
        try {
            if (password.isEmpty()) {
                transport.connect(mailbox.smtpHost(), smtpPort(mailbox), null, null);
            } else {
                transport.connect(mailbox.smtpHost(), smtpPort(mailbox), mailbox.username(), password);
            }
        } catch (AuthenticationFailedException refused) {
            throw new SendFailure("helpdesk.authentication_failed", true, true, "The mail server refused the login", refused);
        } catch (MessagingException failure) {
            MailClient.MailFailure known = ImapMailClient.failure(failure);
            throw new SendFailure(known.code(), true, false, known.getMessage(), failure);
        }
        try {
            transport.sendMessage(message, message.getAllRecipients());
        } catch (SendFailedException refused) {
            // An answer of the server with an error code to the sender, the recipient or the mail: it did not take the mail. Without
            // such a code the connection ended before an answer; that is uncertain only after the end of the mail was sent.
            int code = returnCode(refused);
            if (code < 400 || code > 599) {
                throw broken(transport, refused);
            }
            String reason = refused instanceof SMTPSendFailedException && !(refused instanceof SMTPAddressFailedException)
                && !(refused instanceof SMTPSenderFailedException) ? "helpdesk.send_refused" : "helpdesk.recipient_refused";
            throw new SendFailure(reason, true, code >= 500, "The mail server refused the mail", refused);
        } catch (MessagingException failure) {
            throw broken(transport, failure);
        } finally {
            try {
                transport.close();
            } catch (MessagingException ignored) {
                // The mail is handed over or not; closing changes nothing about it.
            }
        }
    }

    /** A send that ended without an answer with a code: certainly not sent before the end of the mail, uncertain after it. */
    private static SendFailure broken(PhasedTransport transport, MessagingException failure) {
        if (transport.ended()) {
            return new SendFailure("helpdesk.send_uncertain", false, false, "The answer of the mail server is missing", failure);
        }
        MailClient.MailFailure known = ImapMailClient.failure(failure);
        String code = known.code().equals("helpdesk.protocol_error") ? "helpdesk.send_broken" : known.code();
        return new SendFailure(code, true, false, "The mail server ended before it got the whole mail", failure);
    }

    /**
     * The transport of Angus that notes when it starts to end the mail; the end of the data and its answer are the only part
     * of a send after which the server may hold the mail. Built directly, so the session cannot swap in another class.
     */
    static final class PhasedTransport extends SMTPTransport {
        private volatile boolean ended;

        PhasedTransport(Session session, URLName url, String protocol, boolean ssl) {
            super(session, url, protocol, ssl);
        }

        @Override
        protected void finishData() throws IOException, MessagingException {
            ended = true;
            super.finishData();
        }

        boolean ended() {
            return ended;
        }
    }

    /** The code of the answer that refused the mail, from the failure or the first failure it carries with a code; -1 without. */
    static int returnCode(MessagingException failure) {
        Exception current = failure;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof SMTPAddressFailedException address) {
                return address.getReturnCode();
            }
            if (current instanceof SMTPSenderFailedException sender) {
                return sender.getReturnCode();
            }
            if (current instanceof SMTPSendFailedException send) {
                return send.getReturnCode();
            }
            current = current instanceof MessagingException messaging ? messaging.getNextException() : null;
        }
        return -1;
    }

    private static int smtpPort(Mailbox mailbox) {
        return mailbox.smtpPort() == null ? defaultPort(mailbox.smtpSecurity()) : mailbox.smtpPort();
    }

    private static int defaultPort(String security) {
        return "tls".equals(security) ? 465 : "starttls".equals(security) ? 587 : 25;
    }

    /** A Message-ID as {@code <...>} of printable ASCII without spaces or angle brackets inside (TASK-062, C-F3, X-F3). */
    static final Pattern MESSAGE_ID = Pattern.compile("<[\\x21-\\x3b\\x3d\\x3f-\\x7e]{1,250}>");

    /**
     * The reply as plain text in UTF-8 with the own Message-ID instead of one the library would make up. Sender and recipient
     * must be single plain addresses and the identifiers of the thread valid Message-IDs; {@code References} is folded so that
     * no line of the header grows beyond the limit of RFC 5322.
     */
    static MimeMessage message(Session session, Outgoing mail) throws MessagingException, UnsupportedEncodingException {
        if (!MESSAGE_ID.matcher(mail.messageId()).matches() || mail.inReplyTo() != null && !MESSAGE_ID.matcher(mail.inReplyTo()).matches()) {
            throw new MessagingException("A Message-ID of the reply is not valid");
        }
        List<String> references = mail.references() == null ? List.of() : List.of(mail.references().trim().split("\\s+"));
        if (references.stream().anyMatch(id -> !MESSAGE_ID.matcher(id).matches())) {
            throw new MessagingException("A reference of the reply is not valid");
        }
        MimeMessage message = new MimeMessage(session) {
            @Override
            protected void updateMessageID() throws MessagingException {
                setHeader("Message-ID", mail.messageId());
            }
        };
        message.setFrom(address(mail.fromAddress(), mail.fromName()));
        message.setRecipient(Message.RecipientType.TO, address(mail.toAddress(), mail.toName()));
        message.setHeader("Subject", subject(mail.subject()));
        message.setText(mail.body(), StandardCharsets.UTF_8.name());
        message.setSentDate(Date.from(mail.date()));
        if (mail.inReplyTo() != null) {
            message.setHeader("In-Reply-To", mail.inReplyTo());
        }
        if (!references.isEmpty()) {
            // One identifier per line: each stays below 998 characters, whatever the number of identifiers.
            message.setHeader("References", String.join("\r\n ", references));
        }
        message.saveChanges();
        // Whatever the values, no line of a header may grow beyond the limit of RFC 5322 (TASK-063, F2).
        for (String header : java.util.Collections.list(message.getAllHeaderLines())) {
            for (String line : header.split("\r\n")) {
                if (line.length() > LINE_MAX) {
                    throw new MessagingException("A header of the reply is too long");
                }
            }
        }
        return message;
    }

    /** The longest line of a header that RFC 5322 allows, without its line break. */
    static final int LINE_MAX = 998;

    /** The longest name the reply shows for sender or recipient; a longer one is cut, the address stays as it is. */
    static final int NAME_MAX = 200;

    /**
     * The subject as a header value: encoded where needed and folded. A long run without spaces cannot be folded as text, so
     * the subject then goes as a series of short encoded words that can (TASK-063, F2). Control characters other than the tab
     * are refused.
     */
    static String subject(String subject) throws MessagingException, UnsupportedEncodingException {
        if (subject == null || subject.isEmpty() || subject.codePoints().anyMatch(point -> point != '\t' && Character.isISOControl(point))) {
            throw new MessagingException("The subject of the reply is not a single line");
        }
        String plain = MimeUtility.fold(9, MimeUtility.encodeText(subject, StandardCharsets.UTF_8.name(), null));
        if (java.util.Arrays.stream(plain.split("\r\n")).allMatch(line -> line.length() <= 76)) {
            return plain;
        }
        // Encoded words of at most 45 bytes of UTF-8 each, never splitting a character, one per line (RFC 2047, 75 characters).
        List<String> words = new java.util.ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int bytes = 0;
        for (int point : subject.codePoints().toArray()) {
            int size = new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 45) {
                words.add(encodedWord(chunk.toString()));
                chunk.setLength(0);
                bytes = 0;
            }
            chunk.appendCodePoint(point);
            bytes += size;
        }
        words.add(encodedWord(chunk.toString()));
        return String.join("\r\n ", words);
    }

    private static String encodedWord(String text) {
        return "=?UTF-8?B?" + java.util.Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /** A single plain address with an encoded name; anything else, such as a group or a comment, is refused. */
    private static InternetAddress address(String address, String name) throws AddressException, UnsupportedEncodingException {
        if (address == null || !address.equals(DirectoryService.normalizedAddress(address))) {
            throw new AddressException("Not a plain address");
        }
        InternetAddress result = new InternetAddress(address, true);
        // A name never breaks the line of its header, whatever was stored before (TASK-062, X-F3), and stays short (TASK-063, F2).
        String shown = name == null ? null : name.replaceAll("\\p{Cntrl}+", " ").strip();
        if (shown != null && shown.codePointCount(0, shown.length()) > NAME_MAX) {
            shown = shown.substring(0, shown.offsetByCodePoints(0, NAME_MAX));
        }
        result.setPersonal(shown == null || shown.isEmpty() ? null : shown, StandardCharsets.UTF_8.name());
        return result;
    }
}
