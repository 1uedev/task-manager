package taskmanager.wiki.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-127 (DEC-173, rule 2): the simple Markdown of the AI revision. Everything a section holds comes back unchanged when the
 * text is read again, images, links to wiki pages and blocks it cannot hold are placeholders that must return exactly once and in
 * their kind of place (prüffall A05), and nothing in the text of the model becomes markup the editor would run (prüffall A15).
 */
class WikiMarkdownTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String IMAGE = "{\"type\":\"image\",\"attrs\":{\"src\":\"/api/v1/wiki/images/11111111-1111-1111-1111-111111111111\",\"alt\":\"Formular\"}}";
    private static final String WIKI_LINK = "{\"type\":\"text\",\"text\":\"Anleitung\",\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":\"wiki:22222222-2222-2222-2222-222222222222\"}}]}";

    private static List<JsonNode> blocks(String json) {
        List<JsonNode> blocks = new ArrayList<>();
        JSON.readTree(json).forEach(blocks::add);
        return blocks;
    }

    private static JsonNode roundTrip(String json) {
        WikiMarkdown.Text text = WikiMarkdown.write(blocks(json));
        JsonNode read = WikiMarkdown.read(text.markdown(), text.placeholders());
        assertEquals(JSON.readTree(json), read, text.markdown());
        return read;
    }

    @Test
    void headingsParagraphsMarksListsQuotesAndRulesComeBackUnchanged() {
        roundTrip("""
            [{"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Angaben zu einer Störung"}]},
             {"type":"paragraph","content":[{"type":"text","text":"Keine Passwörter","marks":[{"type":"bold"}]},{"type":"text","text":" in eine "},
               {"type":"text","text":"Supportanfrage","marks":[{"type":"italic"}]},{"type":"text","text":" eintragen, "},
               {"type":"text","text":"DEMO-42","marks":[{"type":"code"}]},{"type":"hardBreak"},
               {"type":"text","text":"Hilfe","marks":[{"type":"link","attrs":{"href":"https://example.org/hilfe"}}]}]},
             {"type":"orderedList","attrs":{"start":1},"content":[
               {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Anfrage öffnen."}]}]},
               {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Zeitpunkt ergänzen."}]},
                 {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Datum"}]}]}]}]}]},
             {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"Zitat"}]}]},
             {"type":"horizontalRule"}]
            """);
    }

    @Test
    void charactersOfMarkdownInTheTextStayText() {
        JsonNode read = roundTrip("""
            [{"type":"paragraph","content":[{"type":"text","text":"# kein Titel, *kein Stern*, [kein](Link) und `kein Code` \\\\ <script>alert(1)</script>"}]},
             {"type":"paragraph","content":[{"type":"text","text":"1. keine Liste"}]},
             {"type":"paragraph","content":[{"type":"text","text":"- auch keine"}]}]
            """);
        assertTrue(read.toString().contains("<script>alert(1)</script>"), "a script stays plain text of a paragraph");
    }

    @Test
    void imagesWikiLinksAndOtherBlocksArePlaceholders() {
        String json = "[" + IMAGE + ",{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Siehe \"}," + WIKI_LINK + "]},"
            + "{\"type\":\"codeBlock\",\"attrs\":{\"language\":\"text\"},\"content\":[{\"type\":\"text\",\"text\":\"Fehlercode: DEMO-42\"}]},"
            + "{\"type\":\"table\",\"content\":[{\"type\":\"tableRow\",\"content\":[{\"type\":\"tableCell\",\"content\":[{\"type\":\"paragraph\","
            + "\"content\":[{\"type\":\"text\",\"text\":\"Zelle\"}]}]}]}]}]";
        WikiMarkdown.Text text = WikiMarkdown.write(blocks(json));
        assertEquals(List.of("[[IMAGE-1]]", "[[LINK-1]]", "[[BLOCK-1]]", "[[BLOCK-2]]"),
            text.placeholders().stream().map(WikiMarkdown.Placeholder::token).toList());
        assertTrue(text.markdown().contains("Siehe [[LINK-1]]"), text.markdown());
        assertTrue(!text.markdown().contains("wiki:") && !text.markdown().contains("/api/v1/wiki/images/"), "no identifier reaches the model");
        roundTrip(json);

        // The revised text may move a block placeholder between paragraphs and rewrite the text around a link.
        String revised = "## Neu\n\n[[BLOCK-1]]\n\nKurz: siehe **[[LINK-1]]**.\n\n[[IMAGE-1]]\n\n[[BLOCK-2]]\n";
        JsonNode read = WikiMarkdown.read(revised, text.placeholders());
        assertEquals("codeBlock", read.get(1).get("type").asString());
        assertTrue(read.toString().contains("wiki:22222222-2222-2222-2222-222222222222"), read.toString());
        assertTrue(read.toString().contains("/api/v1/wiki/images/11111111-1111-1111-1111-111111111111"), read.toString());
    }

    @Test
    void aLostDoubledOrMisplacedPlaceholderIsRefused() {
        String json = "[" + IMAGE + ",{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Siehe \"}," + WIKI_LINK + "]}]";
        WikiMarkdown.Text text = WikiMarkdown.write(blocks(json));
        List<String> tokens = text.placeholders().stream().map(WikiMarkdown.Placeholder::token).toList();
        assertNull(WikiMarkdown.problem(text.markdown(), tokens));
        for (String changed : List.of("Siehe [[LINK-1]]", "[[IMAGE-1]]\n\nSiehe", "[[IMAGE-1]]\n\n[[IMAGE-1]]\n\nSiehe [[LINK-1]]",
                "Bild [[IMAGE-1]] im Satz\n\nSiehe [[LINK-1]]", "[[IMAGE-1]]\n\nSiehe [[LINK-1]] und [[LINK-9]]")) {
            assertEquals(WikiMarkdown.PLACEHOLDERS_CHANGED, WikiMarkdown.problem(changed, tokens), changed);
            assertThrows(WikiMarkdown.Invalid.class, () -> WikiMarkdown.read(changed, text.placeholders()), changed);
        }
    }

    @Test
    void aNewLinkToAWikiPageIsRefused() {
        assertThrows(WikiMarkdown.Invalid.class,
            () -> WikiMarkdown.read("Siehe [hier](wiki:22222222-2222-2222-2222-222222222222)", List.of()));
        assertEquals("ai.invalid_output", WikiMarkdown.problem("Siehe [hier](wiki:22222222-2222-2222-2222-222222222222)", List.of()));
    }

    @Test
    void theRevisedMarkdownBecomesBlocksOfTheEditor() {
        JsonNode read = WikiMarkdown.read("# Titel\n\nErster **fett** und *kursiv* mit `code`.\nZweite Zeile\n\n- a\n- b\n  - c\n\n3. drei\n4. vier\n\n> Zitat\n\n---\n",
            List.of());
        List<String> types = new ArrayList<>();
        read.forEach(block -> types.add(block.get("type").asString()));
        assertEquals(List.of("heading", "paragraph", "bulletList", "orderedList", "blockquote", "horizontalRule"), types);
        assertEquals(1, read.get(0).get("attrs").get("level").asInt());
        assertEquals(3, read.get(3).get("attrs").get("start").asInt());
        assertTrue(read.get(1).toString().contains("hardBreak"), "a line break inside a paragraph stays a break");
        WikiDocument.check(JSON.readTree("{\"type\":\"doc\",\"content\":" + read + "}"));
    }
}
