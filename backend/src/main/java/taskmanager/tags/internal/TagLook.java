package taskmanager.tags.internal;

import java.text.Normalizer;
import java.util.List;

/**
 * Description and color of a tag (TASK-072, DEC-102). The description is plain text of 1 to 500 characters after removing
 * outer white space; line breaks and tabs are allowed, other control and format characters are not. The color is the name of
 * one of twelve colors of a fixed palette, which the interface shows readable in light and dark display; the name of the
 * tag always stays readable as text.
 */
final class TagLook {
    static final int DESCRIPTION_MAX = 500;
    static final List<String> PALETTE = List.of("red", "orange", "amber", "yellow", "lime", "green", "teal", "cyan", "blue",
        "indigo", "violet", "pink");

    private TagLook() {
    }

    /** The cleaned description, {@code null} for an empty one; throws for one that breaks a rule. */
    static String description(String raw) throws IllegalArgumentException {
        if (raw == null) {
            return null;
        }
        String text = Normalizer.normalize(raw.replace("\r\n", "\n"), Normalizer.Form.NFC).strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("description.invalid");
        }
        boolean allowed = text.codePoints().allMatch(point -> point == '\n' || point == '\t'
            || (Character.getType(point) != Character.CONTROL && Character.getType(point) != Character.FORMAT
                && Character.getType(point) != Character.UNASSIGNED && Character.getType(point) != Character.SURROGATE));
        if (!allowed) {
            throw new IllegalArgumentException("description.invalid");
        }
        return text;
    }

    static boolean color(String value) {
        return value == null || PALETTE.contains(value);
    }
}
