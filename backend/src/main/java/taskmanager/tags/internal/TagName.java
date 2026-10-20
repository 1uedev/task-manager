package taskmanager.tags.internal;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The rules for tag names (TASK-028, DEC-068): Unicode NFKC, outer white space removed, inner white space of any Unicode kind
 * (also line and paragraph separators) collapsed to one space, 1 to 50 characters, no control or format characters. Two
 * names are the same when their case folded forms match, so {@code STRASSE} and {@code Straße} are one name.
 */
final class TagName {
    static final int MAX = 50;
    /** Every Unicode white space, not only ASCII (review F1 of TASK-028). */
    private static final Pattern WHITE_SPACE = Pattern.compile("[\\s\\p{Z}]+", Pattern.UNICODE_CHARACTER_CLASS);

    private TagName() {
    }

    /** The cleaned name, or {@code null} when it breaks a rule. */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String name = collapse(Normalizer.normalize(raw, Normalizer.Form.NFKC));
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX) {
            return null;
        }
        boolean invalid = name.codePoints().anyMatch(c -> {
            int type = Character.getType(c);
            return type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE
                || type == Character.PRIVATE_USE || type == Character.UNASSIGNED;
        });
        return invalid ? null : name;
    }

    /** The search text in the form of normalized names, or {@code null} for no search. */
    static String searchKey(String raw) {
        if (raw == null) {
            return null;
        }
        String key = collapse(Normalizer.normalize(raw, Normalizer.Form.NFKC)).replace("\u0000", "");
        return key.isEmpty() ? null : normalized(key);
    }

    /**
     * The form for comparisons of a cleaned name: upper case first, then lower case, which folds {@code ß} and {@code SS}
     * together (review F2 of TASK-028).
     */
    static String normalized(String cleaned) {
        return cleaned.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    private static String collapse(String text) {
        return WHITE_SPACE.matcher(text).replaceAll(" ").strip();
    }
}
