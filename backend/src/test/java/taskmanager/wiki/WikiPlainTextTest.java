package taskmanager.wiki;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import taskmanager.wiki.internal.WikiDocument;
import tools.jackson.databind.json.JsonMapper;

/** TASK-102: the visible text of a wiki document for the search. */
class WikiPlainTextTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void blocksBecomeLinesWithoutImagesAndTextsOfLinksToWikiPages() {
        String page = "11111111-2222-3333-4444-555555555555";
        String document = """
            {"type":"doc","content":[
              {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Titel"}]},
              {"type":"paragraph","content":[{"type":"text","text":"Siehe "},
                {"type":"text","text":"Geheim","marks":[{"type":"link","attrs":{"href":"wiki:%s"}}]},
                {"type":"text","text":" und "},
                {"type":"text","text":"Hilfe","marks":[{"type":"link","attrs":{"href":"https://example.org/hilfe"}}]},
                {"type":"hardBreak"},{"type":"text","text":"Zeile"}]},
              {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Punkt"}]}]}]},
              {"type":"codeBlock","attrs":{"language":"text"},"content":[{"type":"text","text":"Code 42"}]},
              {"type":"horizontalRule"}
            ]}
            """.formatted(page);
        String text = WikiDocument.check(JSON.readTree(document)).plainText();
        assertEquals("Titel\nSiehe  und Hilfe\nZeile\nPunkt\nCode 42", text);
    }
}
