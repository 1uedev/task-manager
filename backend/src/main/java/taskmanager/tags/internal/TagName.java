package taskmanager.tags.internal;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The rules for tag names (TASK-028, DEC-068): Unicode NFKC, outer white space removed, inner white space collapsed to one
 * space, 1 to 50 characters, no control or format characters. Two names are the same when their lower case forms match.
 */
final class TagName {
    static final int MAX = 50;

    private TagName() {
    }

    /** The cleaned name, or {@code null} when it breaks a rule. */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String name = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
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
        String key = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").replace("\u0000", "");
        return key.isEmpty() ? null : normalized(key);
    }

    /** The form for comparisons of a cleaned name. */
    static String normalized(String cleaned) {
        return cleaned.toLowerCase(Locale.ROOT);
    }
}
