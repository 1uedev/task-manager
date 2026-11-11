package taskmanager.search.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A short excerpt of a found text with the places of the search words (TASK-102, DEC-132). The excerpt is plain text; the
 * marks are offsets in UTF-16 code units, as JavaScript counts them, so the interface highlights them without reading HTML.
 * A word counts as found when it starts with a search word after the normalization and splitting of the index (NFKC, lower
 * case), and its mark covers the characters of the original text it came from, a decomposed accent included (F3 of the review,
 * R3 of the follow up review); the marks are found in the whole text and then moved into the excerpt.
 */
public record SearchSnippet(String text, List<Mark> marks, boolean cutStart, boolean cutEnd) {
    static final int BEFORE = 60;
    static final int LENGTH = 240;
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
        List<Mark> all = marks(text, words);
        int first = all.isEmpty() ? -1 : all.getFirst().start();
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
        List<Mark> marks = new ArrayList<>();
        for (Mark mark : all) {
            if (mark.end() > start && mark.start() < end) {
                marks.add(new Mark(Math.max(mark.start(), start) - start, Math.min(mark.end(), end) - start));
            }
        }
        return new SearchSnippet(text.substring(start, end), marks, start > 0, end < text.length());
    }

    /**
     * The places of the found words in a whole text such as a title. The text is normalized and split exactly like the index
     * ({@link SearchText#terms}), one character with its combining marks at a time, and every character of the normal form
     * remembers the place in the original text it came from; a compatibility symbol such as Ⓐ, which becomes a letter, is
     * found and marked like the index finds it (R3 of the follow up review of TASK-102).
     */
    static List<Mark> marks(String text, List<String> words) {
        List<Mark> marks = new ArrayList<>();
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return marks;
        }
        Normal normal = Normal.of(text);
        String folded = normal.text();
        int i = 0;
        while (i < folded.length()) {
            int codePoint = folded.codePointAt(i);
            if (!wordPart(codePoint)) {
                i += Character.charCount(codePoint);
                continue;
            }
            int start = i;
            while (i < folded.length() && wordPart(folded.codePointAt(i))) {
                i += Character.charCount(folded.codePointAt(i));
            }
            String term = folded.substring(start, i);
            if (words.stream().anyMatch(term::startsWith)) {
                Mark mark = new Mark(normal.from()[start], normal.to()[i - 1]);
                if (!marks.isEmpty() && mark.start() < marks.getLast().end()) {
                    Mark last = marks.removeLast();
                    mark = new Mark(last.start(), Math.max(last.end(), mark.end()));
                }
                marks.add(mark);
            }
        }
        return marks;
    }

    /** A letter or a number, as {@code [\\p{L}\\p{N}]} of the index. */
    private static boolean wordPart(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetter(codePoint) || type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER
            || type == Character.OTHER_NUMBER;
    }

    /** A text in normal form (NFKC, lower case) with the original place of every one of its characters. */
    private record Normal(String text, int[] from, int[] to) {
        static Normal of(String original) {
            StringBuilder normalized = new StringBuilder();
            List<int[]> places = new ArrayList<>();
            int i = 0;
            while (i < original.length()) {
                int start = i;
                i += Character.charCount(original.codePointAt(i));
                while (i < original.length() && continues(original.codePointAt(i))) {
                    i += Character.charCount(original.codePointAt(i));
                }
                String part = Normalizer.normalize(original.substring(start, i), Normalizer.Form.NFKC);
                for (int k = 0; k < part.length(); k++) {
                    normalized.append(part.charAt(k));
                    places.add(new int[] {start, i});
                }
            }
            String plain = normalized.toString();
            String lower = plain.toLowerCase(Locale.ROOT);
            if (lower.length() != plain.length()) {
                // Rare letters such as İ grow in lower case: one code point at a time keeps the places.
                StringBuilder each = new StringBuilder();
                List<int[]> moved = new ArrayList<>();
                for (int k = 0; k < plain.length(); ) {
                    int size = Character.charCount(plain.codePointAt(k));
                    String piece = plain.substring(k, k + size).toLowerCase(Locale.ROOT);
                    for (int c = 0; c < piece.length(); c++) {
                        moved.add(places.get(k));
                    }
                    each.append(piece);
                    k += size;
                }
                lower = each.toString();
                places = moved;
            }
            int[] from = new int[places.size()];
            int[] to = new int[places.size()];
            for (int k = 0; k < from.length; k++) {
                from[k] = places.get(k)[0];
                to[k] = places.get(k)[1];
            }
            return new Normal(lower, from, to);
        }

        /** A combining mark or a following Hangul jamo belongs to the character before it. */
        private static boolean continues(int codePoint) {
            int type = Character.getType(codePoint);
            return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
                || (codePoint >= 0x1160 && codePoint <= 0x11FF);
        }
    }

    /** Never between the two halves of a surrogate pair. */
    private static int safe(String text, int index) {
        return index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index)) ? index - 1 : index;
    }
}
