package taskmanager.helpdesk.internal;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the intake reads from a mail (TASK-057): sender, recipients, subject, the text, the files and the signals of an
 * automatic message and of the authentication the receiving server recorded. HTML becomes plain text; nothing of the mail
 * runs or is shown as markup. Every text is cut to a size the database and the interface can hold.
 */
record ParsedMail(String messageId, Instant sentAt, List<Sender> from, List<String> to, List<String> cc, String subject,
        String body, List<File> files, boolean automatic, List<String> authenticationResults) {

    static final int BODY_MAX = 200_000;
    private static final int SUBJECT_MAX = 1_000;
    private static final int ADDRESSES_MAX = 50;
    private static final Session SESSION = Session.getInstance(new Properties());
    private static final Pattern BLOCKS = Pattern.compile("(?is)<(script|style|head)[^>]*>.*?</\\1\\s*>");
    private static final Pattern BREAKS = Pattern.compile("(?i)<\\s*(br|/p|/div|/li|/tr|/h[1-6])\\b[^>]*>");
    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

    record Sender(String address, String name) {
    }

    record File(String name, byte[] content) {
    }

    /** Reads a whole mail as the server delivered it. */
    static ParsedMail parse(byte[] raw) throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
        List<File> files = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        String[] html = {null};
        walk(message, files, text, html, 0);
        String body = text.length() > 0 ? text.toString() : html[0] == null ? null : htmlToText(html[0]);
        return new ParsedMail(header(message, "Message-ID"), sentAt(message), senders(message), addresses(message, "To"),
            addresses(message, "Cc"), subject(message), cut(clean(body), BODY_MAX), List.copyOf(files), automatic(message),
            headers(message, "Authentication-Results"));
    }

    /** Reads only the header of a mail that is too large to fetch; it has no text and no files. */
    static ParsedMail headerOnly(jakarta.mail.Message message) throws MessagingException {
        return new ParsedMail(header(message, "Message-ID"), sentAt(message), senders(message), addresses(message, "To"),
            addresses(message, "Cc"), subject(message), null, List.of(), automatic(message), headers(message, "Authentication-Results"));
    }

    /** The domain of an address, lower case. */
    static String domain(String address) {
        int at = address.lastIndexOf('@');
        return at < 0 ? "" : address.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------------------------------ parts

    private static void walk(Part part, List<File> files, StringBuilder text, String[] html, int depth) throws MessagingException, IOException {
        if (depth > 20) {
            return;
        }
        String disposition = safe(part::getDisposition);
        String fileName = fileName(part);
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart child = multipart.getBodyPart(i);
                    walk(child, files, text, html, depth + 1);
                }
            }
            return;
        }
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(disposition) || fileName != null;
        if (!attachment && part.isMimeType("text/plain") && text.length() == 0) {
            text.append(readText(part));
            return;
        }
        if (!attachment && part.isMimeType("text/html") && html[0] == null) {
            html[0] = readText(part);
            return;
        }
        if (attachment || part.isMimeType("message/rfc822")) {
            try (InputStream in = part.getInputStream()) {
                files.add(new File(fileName == null ? "nachricht.eml" : fileName, in.readAllBytes()));
            }
        }
    }

    private static String readText(Part part) throws MessagingException, IOException {
        Object content;
        try {
            content = part.getContent();
        } catch (UnsupportedEncodingException unknownCharset) {
            // An unknown character set: read the bytes as UTF-8 rather than lose the text.
            try (InputStream in = part.getInputStream()) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return content instanceof String string ? string : "";
    }

    private static String fileName(Part part) {
        try {
            String name = part.getFileName();
            if (name == null) {
                return null;
            }
            try {
                return MimeUtility.decodeText(name);
            } catch (UnsupportedEncodingException undecodable) {
                return name;
            }
        } catch (MessagingException unreadable) {
            return null;
        }
    }

    /** A crude but safe conversion: no script, style or head, line breaks for blocks, no tags, the common entities. */
    static String htmlToText(String html) {
        String text = BLOCKS.matcher(html).replaceAll(" ");
        text = BREAKS.matcher(text).replaceAll("\n");
        text = TAGS.matcher(text).replaceAll("");
        Matcher entities = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (entities.find()) {
            entities.appendReplacement(out, Matcher.quoteReplacement(entity(entities.group(1))));
        }
        entities.appendTail(out);
        return out.toString().replaceAll("[ \\t\\x0B\\f\\r]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
    }

    private static String entity(String name) {
        try {
            if (name.startsWith("#x") || name.startsWith("#X")) {
                return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
            }
            if (name.startsWith("#")) {
                return new String(Character.toChars(Integer.parseInt(name.substring(1))));
            }
        } catch (IllegalArgumentException invalid) {
            return "";
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            case "auml" -> name.equals("Auml") ? "Ä" : "ä";
            case "ouml" -> name.equals("Ouml") ? "Ö" : "ö";
            case "uuml" -> name.equals("Uuml") ? "Ü" : "ü";
            case "szlig" -> "ß";
            case "euro" -> "€";
            default -> "&" + name + ";";
        };
    }

    // ------------------------------------------------------------------------------------------------ header

    /**
     * Whether the mail says it was sent automatically or reports a delivery: {@code Auto-Submitted} other than {@code no}
     * (RFC 3834), {@code Precedence} bulk, junk or list, the common auto reply headers, an empty {@code Return-Path} or a
     * delivery report.
     */
    private static boolean automatic(jakarta.mail.Message message) throws MessagingException {
        String submitted = header(message, "Auto-Submitted");
        if (submitted != null && !submitted.strip().toLowerCase(Locale.ROOT).startsWith("no")) {
            return true;
        }
        String precedence = header(message, "Precedence");
        if (precedence != null && List.of("bulk", "junk", "list", "auto_reply").contains(precedence.strip().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (header(message, "X-Autoreply") != null || header(message, "X-Autorespond") != null
            || header(message, "X-Auto-Response-Suppress") != null && header(message, "X-Auto-Response-Suppress").toLowerCase(Locale.ROOT).contains("all")) {
            return true;
        }
        String returnPath = header(message, "Return-Path");
        if (returnPath != null && returnPath.replaceAll("\\s", "").equals("<>")) {
            return true;
        }
        try {
            ContentType type = new ContentType(message.getContentType());
            return type.match("multipart/report") || type.match("message/delivery-status");
        } catch (MessagingException | RuntimeException unreadable) {
            return false;
        }
    }

    private static List<Sender> senders(jakarta.mail.Message message) throws MessagingException {
        String[] values = message.getHeader("From");
        List<Sender> senders = new ArrayList<>();
        if (values == null) {
            return senders;
        }
        for (String value : values) {
            try {
                for (InternetAddress address : InternetAddress.parseHeader(value, false)) {
                    String mail = address.getAddress();
                    if (mail != null && mail.contains("@")) {
                        senders.add(new Sender(cut(mail.strip().toLowerCase(Locale.ROOT), 254), cut(clean(address.getPersonal()), 200)));
                    }
                }
            } catch (AddressException invalid) {
                // An unreadable sender counts as no sender; the mail goes to the review queue.
            }
        }
        return senders;
    }

    private static List<String> addresses(jakarta.mail.Message message, String header) throws MessagingException {
        String[] values = message.getHeader(header);
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            try {
                for (Address address : InternetAddress.parseHeader(value, false)) {
                    if (address instanceof InternetAddress internet && internet.getAddress() != null && result.size() < ADDRESSES_MAX) {
                        result.add(cut(internet.getAddress().strip().toLowerCase(Locale.ROOT), 254));
                    }
                }
            } catch (AddressException invalid) {
                // Ignored like an unreadable sender.
            }
        }
        return result;
    }

    private static String subject(jakarta.mail.Message message) {
        try {
            return cut(clean(message.getSubject()), SUBJECT_MAX);
        } catch (MessagingException unreadable) {
            return null;
        }
    }

    private static Instant sentAt(jakarta.mail.Message message) {
        try {
            var date = message.getSentDate();
            return date == null ? null : date.toInstant();
        } catch (MessagingException unreadable) {
            return null;
        }
    }

    private static String header(jakarta.mail.Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values == null || values.length == 0 ? null : cut(clean(values[0]), 998);
    }

    /** Every value of the header from the top of the mail down, unfolded. */
    private static List<String> headers(jakarta.mail.Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values == null ? List.of() : Arrays.stream(values).map(MimeUtility::unfold).toList();
    }

    // ------------------------------------------------------------------------------------------------ text

    /** Without NUL and other control characters except line breaks and tabs, with line breaks as {@code \n}. */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(unified.length());
        unified.codePoints().filter(point -> point == '\n' || point == '\t' || !Character.isISOControl(point)).forEach(out::appendCodePoint);
        String result = out.toString().strip();
        return result.isEmpty() ? null : result;
    }

    static String cut(String text, int max) {
        if (text == null || text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max - 1)) + "…";
    }

    private interface Reading {
        String read() throws MessagingException;
    }

    private static String safe(Reading reading) {
        try {
            return reading.read();
        } catch (MessagingException unreadable) {
            return null;
        }
    }
}
