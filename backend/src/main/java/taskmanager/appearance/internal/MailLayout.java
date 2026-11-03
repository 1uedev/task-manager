package taskmanager.appearance.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import taskmanager.appearance.MailTemplates;

/**
 * The fixed layout of the mail "Antwort an Kunden" (TASK-082, DEC-112) and the placeholders of its texts. The editable parts
 * are plain text; the HTML comes only from this class, which escapes every text and every value, so a template or a value
 * can never add markup, styles or scripts. Lines break as typed.
 */
final class MailLayout {
    static final String COMPANY = "company.name";
    static final String CONTACT = "contact.name";
    static final String NUMBER = "ticket.number";
    static final String TITLE = "ticket.title";
    static final String SENDER = "sender.name";
    static final String MAILBOX = "mailbox.address";
    static final String PORTAL = "portal.url";

    /** The placeholders in the order the page lists them. */
    static final List<String> PLACEHOLDERS = List.of(COMPANY, CONTACT, NUMBER, TITLE, SENDER, MAILBOX, PORTAL);

    /** The longest editable part, in characters. */
    static final int PART_MAX = 2000;

    static final String LOGO_CID = "logo";

    private static final Pattern TOKEN = Pattern.compile("\\{\\{([^{}]{0,60})\\}\\}");

    private MailLayout() {
    }

    /** The three editable parts of one language. */
    record Texts(String greeting, String signature, String footer) {
    }

    /** The built-in template, valid while nothing is stored and after a reset. */
    static Texts builtIn(String language) {
        if (MailTemplates.ENGLISH.equals(language)) {
            return new Texts("Hello {{contact.name}},", "Kind regards\n{{sender.name}}\n{{company.name}}",
                "Ticket {{ticket.number}}: {{ticket.title}}\nYour requests in the customer portal: {{portal.url}}");
        }
        return new Texts("Guten Tag {{contact.name}},", "Viele Grüße\n{{sender.name}}\n{{company.name}}",
            "Ticket {{ticket.number}}: {{ticket.title}}\nIhre Anfragen im Kundenportal: {{portal.url}}");
    }

    /**
     * The placeholders of the text that are not in the catalog, as written, in their order; also an opening or closing pair
     * of braces that belongs to no placeholder, so that a mistyped one is never sent as it stands.
     */
    static List<String> unknownPlaceholders(String text) {
        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text);
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            if (!PLACEHOLDERS.contains(matcher.group(1))) {
                unknown.add(matcher.group());
            }
            rest.append(text, last, matcher.start()).append(' ');
            last = matcher.end();
        }
        rest.append(text.substring(last));
        int open = rest.indexOf("{{");
        int close = rest.indexOf("}}");
        if (open >= 0) {
            unknown.add(rest.substring(open, Math.min(rest.length(), open + 30)).strip());
        } else if (close >= 0) {
            unknown.add(rest.substring(Math.max(0, close - 28), close + 2).strip());
        }
        return List.copyOf(unknown);
    }

    /**
     * The text after the placeholders are replaced, as plain text or as escaped HTML with line breaks. A line that names the
     * address of the application is left out while there is none, so no mail carries a link to nowhere. Values are single
     * lines; the address becomes a link in HTML.
     */
    static String fill(String template, Map<String, String> values, boolean html) {
        String portal = values.getOrDefault(PORTAL, "");
        List<String> lines = new ArrayList<>();
        for (String line : template.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (portal.isEmpty() && line.contains("{{" + PORTAL + "}}")) {
                continue;
            }
            StringBuilder out = new StringBuilder();
            Matcher matcher = TOKEN.matcher(line);
            int last = 0;
            while (matcher.find()) {
                String literal = line.substring(last, matcher.start());
                out.append(html ? escape(literal) : literal);
                String value = oneLine(values.getOrDefault(matcher.group(1), ""));
                if (html && matcher.group(1).equals(PORTAL) && !value.isEmpty()) {
                    out.append("<a href=\"").append(escape(value)).append("\" style=\"color:#1d4ed8;\">").append(escape(value)).append("</a>");
                } else {
                    out.append(html ? escape(value) : value);
                }
                last = matcher.end();
            }
            String tail = line.substring(last);
            out.append(html ? escape(tail) : tail);
            lines.add(out.toString());
        }
        return trimLines(lines, html ? "<br>\n" : "\n");
    }

    /** The text of the support with its line breaks, as plain text or as escaped HTML. */
    static String body(String body, boolean html) {
        List<String> lines = List.of(body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
        return trimLines(html ? lines.stream().map(MailLayout::escape).toList() : lines, html ? "<br>\n" : "\n");
    }

    /** The plain text of the mail: greeting, text, signature and, below the usual separator, the footer. */
    static String text(MailTemplates.Parts parts) {
        List<String> sections = new ArrayList<>();
        if (!parts.greeting().isEmpty()) {
            sections.add(parts.greeting());
        }
        sections.add(parts.body());
        if (!parts.signature().isEmpty()) {
            sections.add(parts.signature());
        }
        String text = String.join("\n\n", sections);
        return parts.footer().isEmpty() ? text + "\n" : text + "\n\n-- \n" + parts.footer() + "\n";
    }

    /**
     * The HTML of the mail in a layout of tables with inline styles, as mail programs show it. The accent color draws the line
     * above the head; text never stands on it, so its contrast does not matter.
     */
    static String html(String language, String name, String accentColor, boolean logo, String greeting, String body, String signature,
            String footer) {
        String shownName = escape(name);
        StringBuilder out = new StringBuilder(body.length() + 3000);
        out.append("<!DOCTYPE html>\n<html lang=\"").append(MailTemplates.ENGLISH.equals(language) ? "en" : "de").append("\">\n<head>\n")
            .append("<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            .append("<title>").append(shownName).append("</title>\n</head>\n")
            .append("<body style=\"margin:0;padding:0;background-color:#f4f4f5;\">\n")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background-color:#f4f4f5;\">\n")
            .append("<tr><td align=\"center\" style=\"padding:24px 12px;\">\n")
            .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;max-width:600px;")
            .append("background-color:#ffffff;border-top:4px solid ").append(accentColor).append(";font-family:Arial,Helvetica,sans-serif;")
            .append("font-size:15px;line-height:1.5;color:#18181b;\">\n")
            .append("<tr><td style=\"padding:20px 24px;border-bottom:1px solid #e4e4e7;\">");
        if (logo) {
            out.append("<img src=\"cid:").append(LOGO_CID).append("\" alt=\"\" height=\"40\" style=\"height:40px;width:auto;vertical-align:middle;border:0;margin-right:12px;\">");
        }
        out.append("<span style=\"font-size:18px;font-weight:bold;vertical-align:middle;\">").append(shownName).append("</span></td></tr>\n")
            .append("<tr><td style=\"padding:24px;\">\n");
        if (!greeting.isEmpty()) {
            out.append("<p style=\"margin:0 0 16px 0;\">").append(greeting).append("</p>\n");
        }
        out.append("<p style=\"margin:0 0 16px 0;\">").append(body).append("</p>\n");
        if (!signature.isEmpty()) {
            out.append("<p style=\"margin:0;\">").append(signature).append("</p>\n");
        }
        out.append("</td></tr>\n");
        if (!footer.isEmpty()) {
            out.append("<tr><td style=\"padding:16px 24px;border-top:1px solid #e4e4e7;font-size:13px;color:#52525b;\">").append(footer)
                .append("</td></tr>\n");
        }
        out.append("</table>\n</td></tr>\n</table>\n</body>\n</html>\n");
        return out.toString();
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** A value on one line: line breaks and other control characters become spaces. */
    private static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").strip();
    }

    /** The lines without empty ones at the start and the end, and without spaces at the end of a line. */
    private static String trimLines(List<String> lines, String separator) {
        int from = 0;
        int to = lines.size();
        while (from < to && lines.get(from).isBlank()) {
            from++;
        }
        while (to > from && lines.get(to - 1).isBlank()) {
            to--;
        }
        return String.join(separator, lines.subList(from, to).stream().map(String::stripTrailing).toList());
    }
}
