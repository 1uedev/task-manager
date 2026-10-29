package taskmanager.helpdesk.internal;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.springframework.stereotype.Component;

/**
 * SMTP over Jakarta Mail (Eclipse Angus) for the replies (TASK-058). Server and encryption come from the SMTP settings of the
 * mailbox, the login from its account; TLS checks the certificate and the host name, StartTLS is required once chosen.
 *
 * <p>The outcome is split in two phases. Everything until the connection and login stand certainly sends nothing. Once the
 * mail is being handed over, only an answer of the server with a code proves that it did not accept the mail; a broken
 * connection or a missing answer leaves it uncertain, because the server may have taken the mail before the answer was lost.
 */
@Component
class SmtpMailSender implements MailSender {
    private final HelpdeskProperties properties;

    SmtpMailSender(HelpdeskProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(Mailbox mailbox, String password, Outgoing mail) throws SendFailure {
        String protocol = "tls".equals(mailbox.smtpSecurity()) ? "smtps" : "smtp";
        Properties settings = new Properties();
        String prefix = "mail." + protocol + ".";
        String timeout = Long.toString(properties.timeout().toMillis());
        settings.put(prefix + "host", mailbox.smtpHost());
        settings.put(prefix + "port", Integer.toString(smtpPort(mailbox)));
        settings.put(prefix + "connectiontimeout", timeout);
        settings.put(prefix + "timeout", timeout);
        settings.put(prefix + "writetimeout", timeout);
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
        Transport transport;
        try {
            transport = session.getTransport(protocol);
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
            // such a code (the connection ended while waiting for the answer) the mail may have been taken.
            int code = returnCode(refused);
            if (code < 400 || code > 599) {
                throw new SendFailure("helpdesk.send_uncertain", false, false, "The answer of the mail server is missing", refused);
            }
            String reason = refused instanceof SMTPSendFailedException && !(refused instanceof SMTPAddressFailedException)
                && !(refused instanceof SMTPSenderFailedException) ? "helpdesk.send_refused" : "helpdesk.recipient_refused";
            throw new SendFailure(reason, true, code >= 500, "The mail server refused the mail", refused);
        } catch (MessagingException broken) {
            throw new SendFailure("helpdesk.send_uncertain", false, false, "The answer of the mail server is missing", broken);
        } finally {
            try {
                transport.close();
            } catch (MessagingException ignored) {
                // The mail is handed over or not; closing changes nothing about it.
            }
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

    /** The reply as plain text in UTF-8 with the own Message-ID instead of one the library would make up. */
    static MimeMessage message(Session session, Outgoing mail) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = new MimeMessage(session) {
            @Override
            protected void updateMessageID() throws MessagingException {
                setHeader("Message-ID", mail.messageId());
            }
        };
        message.setFrom(new InternetAddress(mail.fromAddress(), mail.fromName(), StandardCharsets.UTF_8.name()));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(mail.toAddress(), mail.toName(), StandardCharsets.UTF_8.name()));
        message.setSubject(mail.subject(), StandardCharsets.UTF_8.name());
        message.setText(mail.body(), StandardCharsets.UTF_8.name());
        message.setSentDate(Date.from(mail.date()));
        if (mail.inReplyTo() != null) {
            message.setHeader("In-Reply-To", mail.inReplyTo());
        }
        if (mail.references() != null) {
            message.setHeader("References", mail.references());
        }
        message.saveChanges();
        return message;
    }
}
