package taskmanager.search.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The search text of a request (TASK-029): words of letters and digits, each matched as the start of a word, all of them
 * required. A text of the form {@code KEY-12} also names a ticket directly. The text never reaches SQL as code: the words
 * consist of letters and digits only and travel as a bound parameter.
 */
record SearchText(String tsquery, String key, Long number, boolean given) {
    static final int MAX_LENGTH = 200;
    static final int MAX_WORDS = 10;
    static final int MAX_WORD_LENGTH = 50;
    /** The most bytes of terms one indexed text keeps (TASK-102). */
    static final int MAX_INDEX_BYTES = 200_000;
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern IDENTIFIER = Pattern.compile("([A-Z][A-Z0-9]{1,9})-([0-9]{1,18})");

    /** Not {@link #present()} for an empty text; {@code null} when the text is invalid. */
    static SearchText parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SearchText(null, null, null, false);
        }
        String text = Nfkc.of(raw).strip();
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            return null;
        }
        List<String> words = new ArrayList<>();
        for (String word : SEPARATORS.split(text.toLowerCase(Locale.ROOT))) {
            if (word.isEmpty()) {
                continue;
            }
            if (word.codePointCount(0, word.length()) > MAX_WORD_LENGTH || words.size() == MAX_WORDS) {
                return null;
            }
            words.add(word);
        }
        String key = null;
        Long number = null;
        Matcher identifier = IDENTIFIER.matcher(text.toUpperCase(Locale.ROOT));
        if (identifier.matches()) {
            key = identifier.group(1);
            number = Long.parseLong(identifier.group(2));
        }
        String tsquery = words.isEmpty() ? null : String.join(" & ", words.stream().map(word -> "'" + word + "':*").toList());
        return new SearchText(tsquery, key, number, true);
    }

    /**
     * The words of a stored text as the index keeps them, separated by spaces: the same normalization and splitting as a
     * search text, without its limits, so every word a search can ask for is a word of the index.
     */
    static String terms(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = Nfkc.of(text).toLowerCase(Locale.ROOT);
        StringBuilder terms = new StringBuilder();
        for (String word : SEPARATORS.split(normalized)) {
            if (!word.isEmpty()) {
                terms.append(terms.isEmpty() ? "" : " ").append(word);
            }
        }
        return terms.toString();
    }

    /**
     * The terms of a long text for the index (TASK-102): those of {@link #terms}, but at most {@value #MAX_INDEX_BYTES} bytes in
     * UTF-8, cut after a whole word, so that the vector PostgreSQL builds from them stays below its limit of 1 MB. Words after
     * the limit are not found; a wiki page reaches it only with far more than 20,000 words.
     */
    static String indexTerms(String text) {
        String terms = terms(text);
        if (terms.length() * 4L <= MAX_INDEX_BYTES || terms.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_INDEX_BYTES) {
            return terms;
        }
        int bytes = 0;
        int end = 0;
        for (int i = 0; i < terms.length(); ) {
            int codePoint = terms.codePointAt(i);
            int size = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (bytes + size > MAX_INDEX_BYTES) {
                break;
            }
            bytes += size;
            i += Character.charCount(codePoint);
            if (i == terms.length() || terms.charAt(i) == ' ') {
                end = i;
            }
        }
        return terms.substring(0, end);
    }

    /** The words of the text, each as a prefix query of its own; empty without words. */
    List<String> words() {
        return tsquery == null ? List.of() : List.of(tsquery.split(" & "));
    }

    /** The words of the text without the syntax of the query, as they start the words they find (TASK-102). */
    List<String> plainWords() {
        return words().stream().map(word -> word.substring(1, word.length() - 3)).toList();
    }

    /** A text was given; with neither words nor an identifier it matches nothing. */
    boolean present() {
        return given;
    }
}
