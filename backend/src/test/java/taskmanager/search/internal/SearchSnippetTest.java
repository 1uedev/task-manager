package taskmanager.search.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** TASK-102: the excerpt of a found text, its marks in UTF-16 offsets, and the limit of the indexed terms. */
class SearchSnippetTest {

    @Test
    void theExcerptStartsBeforeTheFirstFoundWordAndMarksEveryFoundWord() {
        String body = "Einleitung ".repeat(20) + "Der Drucker druckt.\nDrucker neu starten. " + "Schluss ".repeat(60);
        SearchSnippet snippet = SearchSnippet.of(body, List.of("druck"));
        assertTrue(snippet.cutStart());
        assertTrue(snippet.cutEnd());
        assertTrue(snippet.text().length() <= SearchSnippet.LENGTH);
        assertFalse(snippet.text().contains("\n"), "line breaks become spaces");
        assertEquals(3, snippet.marks().size(), "Drucker, druckt and Drucker start with the word");
        for (SearchSnippet.Mark mark : snippet.marks()) {
            assertTrue(snippet.text().substring(mark.start(), mark.end()).toLowerCase().startsWith("druck"));
        }
    }

    @Test
    void withoutAFoundWordTheExcerptIsTheStartAndAnEmptyTextHasNone() {
        SearchSnippet snippet = SearchSnippet.of("Kurzer Text", List.of("anderes"));
        assertEquals("Kurzer Text", snippet.text());
        assertFalse(snippet.cutStart());
        assertFalse(snippet.cutEnd());
        assertTrue(snippet.marks().isEmpty());
        assertNull(SearchSnippet.of("  ", List.of("x")));
    }

    @Test
    void marksCountUtf16UnitsAndFollowTheNormalizationOfTheIndex() {
        // A surrogate pair before the word moves its offset by two units, as JavaScript counts them.
        String title = "😀 ＧＲＯSS Straße";
        List<SearchSnippet.Mark> marks = SearchSnippet.marks(title, List.of("gross", "stra"));
        assertEquals(2, marks.size());
        assertEquals(3, marks.get(0).start());
        assertEquals("Straße", title.substring(marks.get(1).start(), marks.get(1).end()));
    }

    @Test
    void indexedTermsStopAtTheByteLimitAfterAWholeWord() {
        String text = "wort ".repeat(60_000);
        String terms = SearchText.indexTerms(text);
        assertTrue(terms.getBytes(StandardCharsets.UTF_8).length <= SearchText.MAX_INDEX_BYTES);
        assertTrue(terms.endsWith("wort"));
        assertEquals("kurz und gut", SearchText.indexTerms("Kurz, und GUT!"));
        String wide = "äöü ".repeat(40_000);
        String cut = SearchText.indexTerms(wide);
        assertTrue(cut.getBytes(StandardCharsets.UTF_8).length <= SearchText.MAX_INDEX_BYTES);
        assertTrue(cut.endsWith("äöü"));
    }

    /** F3 of the review: the same words as the index, also for decomposed accents, compatibility forms and astral letters. */
    @Test
    void marksFollowTheIndexForDecomposedAccentsCompatibilityFormsAndAstralLetters() {
        String decomposed = "Cafe\u0301 offen";
        assertEquals(List.of(new SearchSnippet.Mark(0, 5)), SearchSnippet.marks(decomposed, List.of("café")));
        assertEquals(SearchText.terms(decomposed), "café offen");
        // The ligature fi is one letter that the index reads as two.
        assertEquals(List.of(new SearchSnippet.Mark(0, 5)), SearchSnippet.marks("\uFB01nden", List.of("find")));
        // A letter outside the BMP counts two units; the word after it is marked at its real place.
        String astral = "\uD835\uDC00bc wort";
        assertEquals(List.of(new SearchSnippet.Mark(5, 9)), SearchSnippet.marks(astral, List.of("wort")));
        // A found word far back leads the excerpt, and its mark is moved into it.
        String body = "x ".repeat(300) + "Cafe\u0301 am Ende";
        SearchSnippet snippet = SearchSnippet.of(body, List.of("café"));
        assertTrue(snippet.cutStart());
        SearchSnippet.Mark mark = snippet.marks().getFirst();
        assertEquals("Cafe\u0301", snippet.text().substring(mark.start(), mark.end()));
    }

    /** A mark cut by the edge of the excerpt is cut with it and never points outside. */
    @Test
    void aMarkAtTheEdgeOfTheExcerptStaysInside() {
        String body = "druck".repeat(100);
        SearchSnippet snippet = SearchSnippet.of(body, List.of("druck"));
        assertEquals(1, snippet.marks().size());
        assertEquals(0, snippet.marks().getFirst().start());
        assertEquals(snippet.text().length(), snippet.marks().getFirst().end());
    }

    /** R3 of the follow up review: a compatibility symbol that becomes letters is found and marked like the index finds it. */
    @Test
    void compatibilitySymbolsThatBecomeLettersAreMarked() {
        assertEquals("alpha", SearchText.terms("\u24B6lpha"));
        assertEquals(List.of(new SearchSnippet.Mark(0, 5)), SearchSnippet.marks("\u24B6lpha", List.of("alpha")));
        assertEquals("telecall", SearchText.terms("\u2121ecall"));
        assertEquals(List.of(new SearchSnippet.Mark(3, 9)), SearchSnippet.marks("Zu \u2121ecall", List.of("telec")));
        // A symbol that splits into two words is marked once.
        assertEquals(List.of(new SearchSnippet.Mark(0, 1)), SearchSnippet.marks("\u00BD", List.of("1", "2")));
        // The İ grows in lower case; the places after it stay right.
        String dotted = "\u0130stanbul Wort";
        assertEquals(List.of(new SearchSnippet.Mark(9, 13)), SearchSnippet.marks(dotted, List.of("wort")));
        // Far back in a text, the excerpt starts near the symbol and marks it.
        String body = "Vorwort ".repeat(40) + "\u24B6lpha Ende";
        SearchSnippet snippet = SearchSnippet.of(body, List.of("alpha"));
        assertTrue(snippet.cutStart());
        SearchSnippet.Mark mark = snippet.marks().getFirst();
        assertEquals("\u24B6lpha", snippet.text().substring(mark.start(), mark.end()));
    }

    /** Every word the index keeps for a text is found with its marks, for texts with every kind of special character. */
    @Test
    void everyIndexedWordIsMarked() {
        for (String text : List.of("\u24B6lpha \u2121ecall", "Cafe\u0301 \uFB01nden \u00BD", "\uD835\uDC00bc \uFF27\uFF32\uFF2F\uFF33\uFF33",
                "\u0130stanbul \u1100\u1161\u11A8 x\u0303y", "\u2460 Stra\u00DFe", "\u3131\u314F \u3131\u314F\u3131",
                "\u0130 \u039F\u03A3", "\u0130 \u039F\u03A3 \u03A3\u039F\u03A3")) {
            for (String term : SearchText.terms(text).split(" ")) {
                assertFalse(SearchSnippet.marks(text, List.of(term)).isEmpty(), term + " in " + text);
            }
        }
    }

    /**
     * F2 of the Codex review of TASK-105: compatibility jamo that NFKC composes only together, and a final sigma whose lower case
     * depends on its neighbours, are marked exactly where the index finds them.
     */
    @Test
    void composedJamoAndFinalSigmaAreMarkedLikeTheIndex() {
        assertEquals("\uAC00", SearchText.terms("\u3131\u314F"));
        assertEquals(List.of(new SearchSnippet.Mark(0, 2)), SearchSnippet.marks("\u3131\u314F", List.of("\uAC00")));
        assertEquals("i \u03BF\u03C2", SearchText.terms("\u0130 \u039F\u03A3"));
        assertEquals(List.of(new SearchSnippet.Mark(2, 4)), SearchSnippet.marks("\u0130 \u039F\u03A3", List.of("\u03BF\u03C2")));
        SearchSnippet snippet = SearchSnippet.of("Vorwort \u3131\u314F Ende", List.of("\uAC00"));
        assertEquals(List.of(new SearchSnippet.Mark(8, 10)), snippet.marks());
    }

    /**
     * F3 of the Codex review of TASK-106: combining marks that stay apart after NFKC are not part of the word before them, and a
     * mark covers only the original characters its word is made of.
     */
    @Test
    void aMarkCoversOnlyTheCharactersOfItsWord() {
        String text = "\u30AB\u3099\u3099\uFF9E\u0334\uFF9E\u0334 end";
        assertEquals("\u30AC end", SearchText.terms(text));
        assertEquals(List.of(new SearchSnippet.Mark(0, 2)), SearchSnippet.marks(text, List.of("\u30AC")));
        assertEquals(List.of(new SearchSnippet.Mark(8, 11)), SearchSnippet.marks(text, List.of("end")));
        // A composed letter keeps its accent, a mark that does not compose stays outside.
        assertEquals(List.of(new SearchSnippet.Mark(0, 2)), SearchSnippet.marks("e\u0301\u0334x", List.of("\u00E9")));
        // Where the normal form of a whole text differs from pieces joined pairwise, the words still follow the index.
        assertEquals(SearchText.terms("=\uFF9E\uFF9E\u0338 wort"), String.join(" ", words(SearchSnippet.normalForm("=\uFF9E\uFF9E\u0338 wort"))));
    }

    /**
     * F3 of the Codex review of TASK-106: for many random texts of characters that compose, reorder, decompose, grow in lower
     * case or lie outside the BMP, the normal form of the marks has exactly the words of the index, and every word is marked
     * at original characters whose normal form holds it.
     */
    @Test
    void theWordsOfTheMarksAreThoseOfTheIndexForRandomTexts() {
        String[] alphabet = {"a", "E", " ", "-", "=", "1", "\u0301", "\u0334", "\u0338", "\u3099", "\uFF9E", "\uFF9F", "\u30AB",
            "\uFF76", "\u3131", "\u314F", "\u1100", "\u1161", "\u11A8", "\uAC00", "\uFFA1", "\uFFC2", "\u0130", "\u03A3", "\u039F",
            "\uFB01", "\u00BD", "\u24B6", "\uD835\uDC00", "\u0F73", "\u0344", "\u0915", "\u093C", "\u0B47", "\u0B3E", "\u09C7",
            "\u09BE", "\u0F40", "\u0FB5", "\u1E9B", "\u0323", "\u200B", "\u00A8", "\u2126", "\u212B", "\u0627", "\u0653"};
        Random random = new Random(106);
        for (int round = 0; round < 3000; round++) {
            StringBuilder text = new StringBuilder();
            for (int k = random.nextInt(12) + 1; k > 0; k--) {
                text.append(alphabet[random.nextInt(alphabet.length)]);
            }
            String original = text.toString();
            List<String> terms = SearchText.terms(original).isEmpty() ? List.of() : List.of(SearchText.terms(original).split(" "));
            assertEquals(terms, words(SearchSnippet.normalForm(original)), escaped(original));
            for (String term : terms) {
                List<SearchSnippet.Mark> marks = SearchSnippet.marks(original, List.of(term));
                assertFalse(marks.isEmpty(), term + " in " + escaped(original));
                boolean holds = marks.stream().anyMatch(mark -> sigma(Normalizer.normalize(original.substring(mark.start(), mark.end()),
                    Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT)).contains(sigma(term)));
                assertTrue(holds, term + " in " + escaped(original) + " " + marks);
            }
        }
    }

    /** F2 of the Codex review of TASK-106: a long run of marks that NFKC reorders is marked in a time that grows with its length. */
    @Test
    void aLongRunOfReorderedMarksIsMarkedQuickly() {
        String text = "wort " + "\uFF9E\u0334".repeat(50_000) + " ende";
        long started = System.nanoTime();
        List<SearchSnippet.Mark> marks = SearchSnippet.marks(text, List.of("wort", "ende"));
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertEquals(List.of(new SearchSnippet.Mark(0, 4), new SearchSnippet.Mark(text.length() - 4, text.length())), marks);
        assertTrue(millis < 3_000, millis + " ms for " + text.length() + " units");
        String letters = "\u30AB" + "\uFF9E\u0334".repeat(50_000) + " ende";
        started = System.nanoTime();
        assertEquals(List.of(new SearchSnippet.Mark(0, 2)), SearchSnippet.marks(letters, List.of("\u30AC")));
        millis = (System.nanoTime() - started) / 1_000_000;
        assertTrue(millis < 3_000, millis + " ms for a letter with " + (letters.length() - 6) + " marks");
    }

    /** F2 of the Codex review of TASK-106: the ordered way of {@link Nfkc} gives exactly NFKC, for random texts and long runs. */
    @Test
    void theLinearNormalFormIsNfkc() {
        String[] alphabet = {"a", " ", "\u0301", "\u0334", "\u0338", "\u3099", "\uFF9E", "\uFF9F", "\u30AB", "\u0345", "\u03B1",
            "\u0F73", "\u0F71", "\u0F72", "\u0344", "\u0323", "\u0302", "\u1100", "\u1161", "\u11A8", "\u0B47", "\u0B3E", "\u05B0",
            "\u05BC", "\u0591", "\u093C", "\u094D", "\u20D2", "\u0E38", "\u0E48", "\uFB01", "\u00A8", "\u1E9B", "\u0130", "="};
        Random random = new Random(1062);
        for (int round = 0; round < 3000; round++) {
            StringBuilder text = new StringBuilder();
            for (int k = random.nextInt(40) + 1; k > 0; k--) {
                text.append(alphabet[random.nextInt(alphabet.length)]);
            }
            String original = text.toString();
            assertEquals(Normalizer.normalize(original, Normalizer.Form.NFKC), Nfkc.ordered(original), escaped(original));
        }
        String run = "\u30AB" + "\uFF9E\u0334\u0301\u0323".repeat(20) + " x";
        assertEquals(Normalizer.normalize(run, Normalizer.Form.NFKC), Nfkc.of(run));
        long started = System.nanoTime();
        String terms = SearchText.terms("wort " + "\uFF9E\u0334".repeat(100_000) + " ende");
        assertEquals("wort ende", terms);
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000, "the index words of a long run of marks");
    }

    private static List<String> words(String normal) {
        return Arrays.stream(normal.split("[^\\p{L}\\p{N}]+")).filter(word -> !word.isEmpty()).toList();
    }

    private static String sigma(String text) {
        return text.replace('\u03C2', '\u03C3');
    }

    private static String escaped(String text) {
        StringBuilder escaped = new StringBuilder();
        text.chars().forEach(unit -> escaped.append(unit < 0x80 ? String.valueOf((char) unit) : String.format("\\u%04X", unit)));
        return escaped.toString();
    }
}
