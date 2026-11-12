package taskmanager.search.internal;

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
     * ({@link SearchText#terms}), and every character of the normal form remembers the places in the original text it came
     * from; a compatibility symbol such as Ⓐ, which becomes a letter, is found and marked like the index finds it (R3 of the
     * follow up review of TASK-102). A word is marked from the first to the last original character of its own.
     */
    static List<Mark> marks(String text, List<String> words) {
        List<Mark> marks = new ArrayList<>();
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return marks;
        }
        for (Word word : words(text)) {
            if (words.stream().noneMatch(word.text()::startsWith)) {
                continue;
            }
            Mark mark = word.place();
            if (!marks.isEmpty() && mark.start() < marks.getLast().end()) {
                Mark last = marks.removeLast();
                mark = new Mark(Math.min(last.start(), mark.start()), Math.max(last.end(), mark.end()));
            }
            marks.add(mark);
        }
        return marks;
    }

    /** One word of the index form of a text and the original characters it is made of. */
    record Word(String text, Mark place) {
    }

    /**
     * The words of a text exactly as the index splits them ({@link SearchText#terms}), each with the first and last original
     * character it is made of; empty when the form of the places is not that of the index (H2 of the Codex review of TASK-107).
     */
    static List<Word> words(String text) {
        List<Word> found = new ArrayList<>();
        Normal normal = text == null || text.isEmpty() ? null : Normal.of(text);
        if (normal == null) {
            return found;
        }
        String folded = normal.text();
        int i = 0;
        while (i < folded.length()) {
            int codePoint = folded.codePointAt(i);
            if (!wordPart(codePoint)) {
                i += Character.charCount(codePoint);
                continue;
            }
            int start = i;
            int markStart = Integer.MAX_VALUE;
            int markEnd = 0;
            while (i < folded.length() && wordPart(folded.codePointAt(i))) {
                int units = Character.charCount(folded.codePointAt(i));
                for (int k = i; k < i + units; k++) {
                    markStart = Math.min(markStart, normal.from()[k]);
                    markEnd = Math.max(markEnd, normal.to()[k]);
                }
                i += units;
            }
            found.add(new Word(folded.substring(start, i), new Mark(markStart, markEnd)));
        }
        return found;
    }

    /** The normal form the marks search in, for tests: the same words as {@link SearchText#terms}. */
    static String normalForm(String text) {
        Normal normal = Normal.of(text);
        return normal == null ? null : normal.text();
    }

    /** A letter or a number, as {@code [\\p{L}\\p{N}]} of the index. */
    private static boolean wordPart(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetter(codePoint) || type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER
            || type == Character.OTHER_NUMBER;
    }

    /**
     * A text in normal form (NFKC, lower case) with the original place of every one of its characters (F3 of the Codex review
     * of TASK-106, F2, F3 and H2 of the Codex review of TASK-107). {@link Nfkc#origins} gives NFKC of the whole text with the
     * original characters each character of the normal form was made of, so two words that come from one character and the
     * marks after it, such as ½ before a conjoining vowel, each keep their own characters, and a letter composed only at the end
     * of a long run of marks costs no more than the run. Lower case is taken over the whole text, so a final sigma stays ς as in
     * the index; per character it only gives the places. {@code null} when this form is not exactly that of the index: then
     * nothing is marked rather than words of another form.
     */
    private record Normal(String text, int[] from, int[] to) {
        static Normal of(String original) {
            List<int[]> characters = Nfkc.origins(original);
            if (characters == null) {
                return null;
            }
            List<int[]> places = new ArrayList<>();
            StringBuilder whole = new StringBuilder(original.length());
            for (int[] character : characters) {
                whole.appendCodePoint(character[0]);
                String lower = new String(Character.toChars(character[0])).toLowerCase(Locale.ROOT);
                for (int unit = 0; unit < lower.length(); unit++) {
                    places.add(character);
                }
            }
            String text = whole.toString().toLowerCase(Locale.ROOT);
            // In the root locale only the final sigma depends on its neighbours, and it keeps the length; should another rule
            // ever change the length, the places no longer fit and nothing is marked.
            if (text.length() != places.size()) {
                return null;
            }
            int[] from = new int[places.size()];
            int[] to = new int[places.size()];
            for (int k = 0; k < from.length; k++) {
                from[k] = places.get(k)[1];
                to[k] = places.get(k)[2];
            }
            return new Normal(text, from, to);
        }
    }

    /** Never between the two halves of a surrogate pair. */
    private static int safe(String text, int index) {
        return index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index)) ? index - 1 : index;
    }
}
