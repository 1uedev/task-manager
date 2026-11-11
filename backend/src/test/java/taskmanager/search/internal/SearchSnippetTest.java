package taskmanager.search.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
                "\u0130stanbul \u1100\u1161\u11A8 x\u0303y", "\u2460 Stra\u00DFe")) {
            for (String term : SearchText.terms(text).split(" ")) {
                assertFalse(SearchSnippet.marks(text, List.of(term)).isEmpty(), term + " in " + text);
            }
        }
    }
}
