package taskmanager.search.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A short excerpt of a found text with the places of the search words (TASK-102, DEC-132). The excerpt is plain text; the
 * marks are offsets in UTF-16 code units, as JavaScript counts them, so the interface highlights them without reading HTML.
 * A word of the text counts as found when its normalized form (NFKC, lower case) starts with a search word, the rule of the
 * index.
 */
public record SearchSnippet(String text, List<Mark> marks, boolean cutStart, boolean cutEnd) {
    static final int BEFORE = 60;
    static final int LENGTH = 240;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern BREAKS = Pattern.compile("[\\r\\n\\t]");

    /** The place of one found word in {@link #text}: start inclusive, end exclusive. */
    public record Mark(int start, int end) {
    }

    public SearchSnippet {
        marks = List.copyOf(marks);
    }

    /**
     * The excerpt around the first found word, or the start of the text when no word is found in it; {@code null} for an empty
     * text. Line breaks become spaces, so the offsets stay those of the text.
     */
    static SearchSnippet of(String body, List<String> words) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String text = BREAKS.matcher(body).replaceAll(" ");
        int first = -1;
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            if (found(matcher.group(), words)) {
                first = matcher.start();
                break;
            }
        }
        int start = first < 0 ? 0 : Math.max(0, first - BEFORE);
        if (start > 0) {
            int space = text.indexOf(' ', start);
            start = space >= 0 && space < first ? space + 1 : start;
        }
        int end = Math.min(text.length(), start + LENGTH);
        if (end < text.length()) {
            int space = text.lastIndexOf(' ', end);
            end = space > Math.max(start, first) ? space : end;
        }
        start = safe(text, start);
        end = safe(text, end);
        String excerpt = text.substring(start, end);
        return new SearchSnippet(excerpt, marks(excerpt, words), start > 0, end < text.length());
    }

    /** The places of the found words in a whole text such as a title. */
    static List<Mark> marks(String text, List<String> words) {
        List<Mark> marks = new ArrayList<>();
        if (text == null || words.isEmpty()) {
            return marks;
        }
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            if (found(matcher.group(), words)) {
                marks.add(new Mark(matcher.start(), matcher.end()));
            }
        }
        return marks;
    }

    private static boolean found(String word, List<String> words) {
        String normalized = Normalizer.normalize(word, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        for (String wanted : words) {
            if (normalized.startsWith(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Never between the two halves of a surrogate pair. */
    private static int safe(String text, int index) {
        return index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index)) ? index - 1 : index;
    }
}
