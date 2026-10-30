package taskmanager.helpdesk.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** TASK-062, X-F3 and C-F3: the addresses and headers of a reply as they go to the mail server. */
class SmtpMailSenderTest {
    private static final Session SESSION = Session.getInstance(new Properties());

    private static MailSender.Outgoing outgoing(String from, String fromName, String to, String toName, String inReplyTo, String references) {
        return new MailSender.Outgoing("<reply-1@firma.example>", from, fromName, to, toName, "Re: Anfrage", "Text", inReplyTo, references,
            Instant.parse("2026-10-30T08:00:00Z"));
    }

    private static String raw(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void onlyPlainAddressesAreAccepted() {
        assertEquals("lea@nordwerk.example", DirectoryService.normalizedAddress(" Lea@Nordwerk.Example "));
        assertEquals("a.b-c+d@sub.nordwerk.example", DirectoryService.normalizedAddress("a.b-c+d@sub.nordwerk.example"));
        for (String invalid : List.of(
                "Lea <lea@nordwerk.example>",
                "lea@nordwerk.example (Lea)",
                "(x) lea@nordwerk.example",
                "lea@nordwerk.example\r\nBcc: mia@suedtechnik.example",
                "lea@nordwerk.example\nBcc: mia@suedtechnik.example",
                "lea@nord\u0000werk.example",
                "lea\t@nordwerk.example",
                "Gruppe: lea@nordwerk.example;",
                "Gruppe:;",
                "lea@nordwerk.example, mia@suedtechnik.example",
                "\"lea lang\"@nordwerk.example",
                "lea@[127.0.0.1]",
                "lea..lang@nordwerk.example",
                "lea@-nordwerk.example",
                "")) {
            assertNull(DirectoryService.normalizedAddress(invalid), invalid);
        }
    }

    @Test
    void aGroupOrCommentStoredBeforeIsNotSent() {
        assertThrows(MessagingException.class, () -> SmtpMailSender.message(SESSION,
            outgoing("support@firma.example", "Support", "Gruppe: lea@nordwerk.example;", "Lea", null, null)));
        assertThrows(MessagingException.class, () -> SmtpMailSender.message(SESSION,
            outgoing("support@firma.example (x)", "Support", "lea@nordwerk.example", "Lea", null, null)));
    }

    @Test
    void aNameNeverBreaksItsHeader() throws Exception {
        MimeMessage message = SmtpMailSender.message(SESSION,
            outgoing("support@firma.example", "Support\r\nBcc: mia@suedtechnik.example", "lea@nordwerk.example", "Lea\nLang", null, null));
        String raw = raw(message);
        assertFalse(raw.contains("\r\nBcc:"), raw);
        assertEquals(1, message.getAllRecipients().length);
        assertTrue(message.getHeader("To")[0].contains("Lea Lang"), message.getHeader("To")[0]);
    }

    @Test
    void invalidIdentifiersOfTheThreadAreRefused() {
        for (String invalid : List.of("<a b@x>", "<a@x>\r\nBcc: mia@suedtechnik.example", "a@x", "<>", "<a<b@x>", "<ä@x>")) {
            assertThrows(MessagingException.class, () -> SmtpMailSender.message(SESSION,
                outgoing("support@firma.example", "Support", "lea@nordwerk.example", "Lea", invalid, null)), invalid);
            assertThrows(MessagingException.class, () -> SmtpMailSender.message(SESSION,
                outgoing("support@firma.example", "Support", "lea@nordwerk.example", "Lea", null, "<ok@x> " + invalid)), invalid);
        }
    }

    @Test
    void twentyLongReferencesStayWithinTheLineLimit() throws Exception {
        List<String> ids = IntStream.range(0, 20).mapToObj(i -> "<" + "r".repeat(230) + i + "@nordwerk.example>").toList();
        String references = String.join(" ", ids);
        MimeMessage message = SmtpMailSender.message(SESSION,
            outgoing("support@firma.example", "Support", "lea@nordwerk.example", "Lea", ids.get(19), references));
        String raw = raw(message);
        String head = raw.substring(0, raw.indexOf("\r\n\r\n"));
        for (String line : head.split("\r\n")) {
            assertTrue(line.length() <= 998, "line of " + line.length() + " characters");
        }
        // Unfolded, the header names every identifier in order.
        String unfolded = message.getHeader("References")[0].replaceAll("\r\n\\s+", " ");
        assertEquals(ids.stream().collect(Collectors.joining(" ")), unfolded);
    }
}
