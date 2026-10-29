package taskmanager.helpdesk.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TASK-057: how a mail is read: text before markup, files, senders, automatic mails and the limits of every text. */
class ParsedMailTest {
    private static ParsedMail parse(String raw) throws Exception {
        return ParsedMail.parse(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void plainTextWinsAndFilesAndForwardedMailsAreKept() throws Exception {
        ParsedMail mail = parse("""
            From: "Lang, Lea" <Lea@Nordwerk.Example>\r
            To: Support <support@firma.example>, b@firma.example\r
            Cc: chef@nordwerk.example\r
            Subject: =?UTF-8?Q?Pr=C3=BCfung?=\r
            Message-ID: <m1@nordwerk.example>\r
            Date: Wed, 28 Oct 2026 10:00:00 +0100\r
            Authentication-Results: mx.firma.example;\r
             dmarc=pass header.from=nordwerk.example\r
            MIME-Version: 1.0\r
            Content-Type: multipart/mixed; boundary="a"\r
            \r
            --a\r
            Content-Type: multipart/alternative; boundary="b"\r
            \r
            --b\r
            Content-Type: text/plain; charset=UTF-8\r
            \r
            Klartext\u0000 hier\r
            --b\r
            Content-Type: text/html\r
            \r
            <p>Markup</p>\r
            --b--\r
            --a\r
            Content-Type: text/plain; name="notiz.txt"\r
            Content-Disposition: attachment; filename="notiz.txt"\r
            \r
            Anhang\r
            --a\r
            Content-Type: message/rfc822\r
            \r
            Subject: alt\r
            \r
            weitergeleitet\r
            --a--\r
            """);
        assertEquals("<m1@nordwerk.example>", mail.messageId());
        assertEquals(List.of(new ParsedMail.Sender("lea@nordwerk.example", "Lang, Lea")), mail.from());
        assertEquals(List.of("support@firma.example", "b@firma.example"), mail.to());
        assertEquals(List.of("chef@nordwerk.example"), mail.cc());
        assertEquals("Prüfung", mail.subject());
        assertEquals("Klartext hier", mail.body(), "no control characters");
        assertEquals(List.of("notiz.txt", "nachricht.eml"), mail.files().stream().map(ParsedMail.File::name).toList());
        assertEquals(1, mail.authenticationResults().size());
        assertTrue(mail.authenticationResults().get(0).contains("dmarc=pass"));
        assertFalse(mail.automatic());
        assertTrue(mail.sentAt() != null);
    }

    @Test
    void markupBecomesTextWithoutScripts() {
        String text = ParsedMail.htmlToText("<html><head><title>T</title></head><body><script>alert(1)</script><style>p{}</style>"
            + "<p>Gr&uuml;&szlig;e &amp; &#x263A; &lt;b&gt;</p><div>zweite<br>Zeile</div><img src=x onerror=y></body></html>");
        assertEquals("Grüße & ☺ <b>\nzweite\nZeile", ParsedMail.clean(text));
    }

    @Test
    void automaticMailsAreRecognized() throws Exception {
        for (String header : List.of("Auto-Submitted: auto-replied", "Precedence: bulk", "Precedence: list", "X-Autoreply: yes",
                "X-Auto-Response-Suppress: All", "Return-Path: <>")) {
            assertTrue(parse(header + "\r\nFrom: a@b.example\r\nSubject: x\r\n\r\ntext\r\n").automatic(), header);
        }
        assertTrue(parse("From: mailer-daemon@b.example\r\nContent-Type: multipart/report; report-type=delivery-status; boundary=r\r\n\r\n--r\r\n"
            + "Content-Type: text/plain\r\n\r\nfailed\r\n--r--\r\n").automatic());
        assertFalse(parse("Auto-Submitted: no\r\nFrom: a@b.example\r\n\r\ntext\r\n").automatic());
    }

    @Test
    void missingAndOversizedPartsStayWithinLimits() throws Exception {
        ParsedMail empty = parse("From: kein-absender\r\n\r\n");
        assertEquals(List.of(), empty.from());
        assertNull(empty.subject());
        assertNull(empty.body());
        String big = "x".repeat(ParsedMail.BODY_MAX + 10);
        ParsedMail large = parse("From: a@b.example\r\nSubject: " + "s".repeat(1200) + "\r\n\r\n" + big);
        assertEquals(ParsedMail.BODY_MAX, large.body().codePointCount(0, large.body().length()));
        assertTrue(large.body().endsWith("…"));
        assertEquals(1000, large.subject().length());
        assertEquals("nordwerk.example", ParsedMail.domain("Lea@NORDWERK.example"));
        assertEquals("", ParsedMail.domain("ohne"));
    }

    /**
     * TASK-061, S2: a nested group, a group without its end and an unfinished display name are not repaired into an address
     * that no contact matches; the recipients count as incomplete, also when only the header of a large mail is read.
     */
    @Test
    void brokenGroupsLeaveTheRecipientsIncomplete() throws Exception {
        List<String> broken = List.of("Outer: Inner: lea@nordwerk.example;;", "MissingEnd: lea@nordwerk.example",
            "Broken: \"unterminated <lea@nordwerk.example>;");
        for (String header : List.of("To", "Cc")) {
            for (String value : broken) {
                String raw = "From: a@firma.example\r\n" + header + ": " + value + "\r\nSubject: x\r\n\r\nText\r\n";
                assertFalse(parse(raw).recipientsComplete(), header + ": " + value);
                assertFalse(headerOnly(raw).recipientsComplete(), "header only, " + header + ": " + value);
            }
        }
        String fine = "From: a@firma.example\r\nTo: Team: Lea <LEA@nordwerk.example>, ben@nordwerk.example;\r\n"
            + "Cc: Leer:;, \"Lang, Lea\" <lea@nordwerk.example>\r\nSubject: x\r\n\r\nText\r\n";
        for (ParsedMail mail : List.of(parse(fine), headerOnly(fine))) {
            assertTrue(mail.recipientsComplete());
            assertEquals(List.of("lea@nordwerk.example", "ben@nordwerk.example"), mail.to());
            assertEquals(List.of("lea@nordwerk.example"), mail.cc());
        }
    }

    private static ParsedMail headerOnly(String raw) throws Exception {
        return ParsedMail.headerOnly(new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()),
            new java.io.ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))));
    }
}
