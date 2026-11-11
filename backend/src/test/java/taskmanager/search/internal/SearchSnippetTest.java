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
}
