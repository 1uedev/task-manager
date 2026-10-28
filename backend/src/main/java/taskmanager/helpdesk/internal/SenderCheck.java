package taskmanager.helpdesk.internal;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether the own receiving server confirmed the sender of a mail (TASK-057, DEC-086). The server writes its verdict into
 * an {@code Authentication-Results} header (RFC 8601) that starts with its authserv-id; the mailbox names that id. Only the
 * topmost header with that id counts, because the server adds its own header on top of everything the sender wrote, and a
 * header further down with the same id may be forged. The sender counts as confirmed when that header says
 * {@code dmarc=pass} for the domain of the From address. A From address alone confirms nothing.
 */
final class SenderCheck {
    private static final Pattern COMMENT = Pattern.compile("\\([^()]*\\)");
    private static final Pattern DMARC = Pattern.compile("(?i)(?:^|;)\\s*dmarc\\s*=\\s*([a-z]+)([^;]*)");
    private static final Pattern HEADER_FROM = Pattern.compile("(?i)header\\.from\\s*=\\s*\"?([^\\s;\"]+)\"?");

    private SenderCheck() {
    }

    /**
     * @param authservId the authserv-id of the own receiving server; {@code null} confirms nothing
     * @param headers the values of {@code Authentication-Results} from the top of the mail down
     * @param fromAddress the one sender of the mail
     */
    static boolean confirmed(String authservId, List<String> headers, String fromAddress) {
        if (authservId == null || authservId.isBlank() || fromAddress == null) {
            return false;
        }
        for (String header : headers) {
            String value = withoutComments(header).strip();
            int semicolon = value.indexOf(';');
            String id = (semicolon < 0 ? value : value.substring(0, semicolon)).strip().split("\\s+")[0];
            if (!id.equalsIgnoreCase(authservId.strip())) {
                continue;
            }
            // The topmost header of the own server decides, whatever it says.
            return semicolon >= 0 && dmarcPass(value.substring(semicolon), ParsedMail.domain(fromAddress));
        }
        return false;
    }

    private static boolean dmarcPass(String results, String fromDomain) {
        Matcher dmarc = DMARC.matcher(results);
        if (!dmarc.find() || !dmarc.group(1).toLowerCase(Locale.ROOT).equals("pass")) {
            return false;
        }
        Matcher from = HEADER_FROM.matcher(dmarc.group(2));
        return from.find() && from.group(1).toLowerCase(Locale.ROOT).equals(fromDomain);
    }

    private static String withoutComments(String header) {
        String previous;
        String current = header;
        do {
            previous = current;
            current = COMMENT.matcher(current).replaceAll(" ");
        } while (!current.equals(previous));
        return current;
    }
}
