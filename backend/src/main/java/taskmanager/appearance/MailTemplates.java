package taskmanager.appearance;

/**
 * The mail template "Antwort an Kunden" for other modules (TASK-082, DEC-112). It puts the text the support wrote into the
 * fixed layout with head, greeting, signature and footer of the stored template, or of the built-in one while nothing is
 * stored, in German or English. The values of the placeholders come from the caller, the name of the company and the address
 * of the application from the appearance. Whatever the stored template holds, rendering cannot fail: every template is
 * checked when it is saved.
 */
public interface MailTemplates {
    /** The languages of a mail; anything else counts as German. */
    String GERMAN = "de";
    String ENGLISH = "en";

    /** What the caller knows about the reply; {@code null} counts as an empty value. */
    record ReplyValues(String contactName, String ticketNumber, String ticketTitle, String senderName, String mailboxAddress) {
    }

    /** An image embedded in the mail, referenced from the HTML as {@code cid:logo}. */
    record Image(String contentType, byte[] content) {
    }

    /**
     * The parts of the mail as they appear in the layout, for a preview that cannot show the HTML itself: the name and the
     * accent color of the head, whether a logo stands beside the name, and the filled texts in which the placeholders are
     * replaced. {@code greeting}, {@code signature} and {@code footer} may be empty.
     */
    record Parts(String name, String accentColor, boolean logo, String greeting, String body, String signature, String footer) {
    }

    /** The finished mail: its language, the plain text and the HTML of the same message, the logo or {@code null}, and its parts. */
    record RenderedMail(String language, String text, String html, Image logo, Parts parts) {
    }

    /** The reply with the template that is valid now, in the language given or German. */
    RenderedMail reply(String language, ReplyValues values, String body);
}
