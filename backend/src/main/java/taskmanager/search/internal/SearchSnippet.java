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

    /** The normal form the marks search in, for tests: the same words as {@link SearchText#terms}. */
    static String normalForm(String text) {
        return Normal.of(text).text();
    }

    /** A letter or a number, as {@code [\\p{L}\\p{N}]} of the index. */
    private static boolean wordPart(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetter(codePoint) || type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER
            || type == Character.OTHER_NUMBER;
    }

    /**
     * A text in normal form (NFKC, lower case) with the original place of every one of its characters (F2 and F3 of the Codex
     * review of TASK-106). The original is cut into segments at the boundaries of the normalization: a segment starts at a
     * character whose decomposition starts with a starter that never joins the one before it, so it is no combining mark and
     * no conjoining vowel or final Hangul jamo; everything else belongs to the segment before it. NFKC of the whole text is then
     * NFKC of the segments one after the other, which {@link #of} checks, and each segment is normalized once, so the time grows
     * with the length of the text. The letters and numbers of a segment come from its shortest beginning that gives them all,
     * found by halving, so a mark of a word covers only the original characters its word is made of and not a combining mark
     * after it that stays apart. Lower case is taken over the whole text, so a final sigma stays ς as in the index; per segment
     * it only gives the places.
     */
    private record Normal(String text, int[] from, int[] to) {
        static Normal of(String original) {
            List<int[]> segments = new ArrayList<>();
            int i = 0;
            while (i < original.length()) {
                int start = i;
                i += Character.charCount(original.codePointAt(i));
                while (i < original.length() && !starts(original.codePointAt(i))) {
                    i += Character.charCount(original.codePointAt(i));
                }
                segments.add(new int[] {start, i});
            }
            StringBuilder joined = new StringBuilder();
            StringBuilder lowered = new StringBuilder();
            List<int[]> places = new ArrayList<>();
            for (int[] segment : segments) {
                String normal = Nfkc.of(original.substring(segment[0], segment[1]));
                int[] word = {segment[0], wordEnd(original, segment, normal)};
                String piece = normal.toLowerCase(Locale.ROOT);
                piece.codePoints().forEach(codePoint -> {
                    for (int unit = 0; unit < Character.charCount(codePoint); unit++) {
                        places.add(wordPart(codePoint) ? word : segment);
                    }
                });
                joined.append(normal);
                lowered.append(piece);
            }
            String whole = Nfkc.of(original);
            if (!whole.contentEquals(joined)) {
                // Not expected for any text (SearchSnippetTest compares many): the marks then follow the normal form of the
                // segments, which can differ from the index only around the place where the two differ.
                whole = joined.toString();
            }
            String wholeLowered = whole.toLowerCase(Locale.ROOT);
            // In the root locale only the final sigma depends on its neighbours, and it keeps the length; should another rule
            // ever change it, the segments stay in place of the whole text.
            String text = wholeLowered.length() == lowered.length() ? wholeLowered : lowered.toString();
            int[] from = new int[places.size()];
            int[] to = new int[places.size()];
            for (int k = 0; k < from.length; k++) {
                from[k] = places.get(k)[0];
                to[k] = places.get(k)[1];
            }
            return new Normal(text, from, to);
        }

        /**
         * The end of the shortest beginning of a segment whose normal form has the same letters and numbers as the whole
         * segment; for a single character the segment end. Found by halving over the characters after the first, so a long run
         * of combining marks costs a few normalizations only.
         */
        private static int wordEnd(String original, int[] segment, String normal) {
            int first = segment[0] + Character.charCount(original.codePointAt(segment[0]));
            if (first >= segment[1]) {
                return segment[1];
            }
            String letters = letters(normal);
            if (letters.isEmpty()) {
                return segment[1];
            }
            int[] ends = original.substring(first, segment[1]).codePoints().map(Character::charCount).toArray();
            int[] positions = new int[ends.length + 1];
            positions[0] = first;
            for (int k = 0; k < ends.length; k++) {
                positions[k + 1] = positions[k] + ends[k];
            }
            int low = 0;
            int high = ends.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                String prefix = Nfkc.of(original.substring(segment[0], positions[middle]));
                if (letters(prefix).equals(letters)) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            return positions[low];
        }

        private static String letters(String normal) {
            StringBuilder letters = new StringBuilder();
            normal.codePoints().filter(SearchSnippet::wordPart).forEach(letters::appendCodePoint);
            return letters.toString();
        }

        /**
         * A character that starts a new segment: its decomposition starts with a character that is no combining mark and no
         * conjoining vowel or final Hangul jamo, the only starters that compose with the character before them.
         */
        static boolean starts(int codePoint) {
            if (codePoint < 0x300) {
                return true;
            }
            int first = Normalizer.normalize(new String(Character.toChars(codePoint)), Normalizer.Form.NFKD).codePointAt(0);
            int type = Character.getType(first);
            boolean mark = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK;
            boolean jamo = (first >= 0x1160 && first <= 0x11FF) || (first >= 0xD7B0 && first <= 0xD7FF);
            return !mark && !jamo;
        }
    }

    /** Never between the two halves of a surrogate pair. */
    private static int safe(String text, int index) {
        return index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index)) ? index - 1 : index;
    }
}
