package taskmanager.wiki;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import taskmanager.permissions.AccessContext;
import taskmanager.wiki.internal.WikiMarkdown;
import tools.jackson.databind.JsonNode;

/**
 * What the AI revision reads of a wiki draft and how its result comes back (TASK-127, DEC-173). Both need {@code wiki.edit} in
 * the project of the page, which must not be archived; a page the caller may not see answers 404 like the routes of the wiki.
 * The text is the simple Markdown of {@link WikiMarkdown}: images, links to wiki pages and every block it cannot hold are
 * placeholders that the result has to carry back unchanged. Nothing here changes the page; the revised document goes into the
 * editor and is saved, as every draft, with the normal save.
 */
public interface WikiDrafts {
    /**
     * One section of the current draft of a language version, or the whole version when {@code section} is {@code null}. A
     * section is the {@code section}th heading at the top of the document up to the next heading at the top. {@code fingerprint}
     * covers page, language, section, text and the original of every placeholder, so it changes whenever the section does.
     */
    record Section(UUID pageId, UUID projectId, String language, int revision, Integer section, String heading, String text,
            List<String> placeholders, String fingerprint) {
    }

    /**
     * The result put in place of its section in the current draft: {@code original} and {@code revised} are the section before
     * and after as documents of their own, {@code document} the whole draft with the revised section, all checked like a save.
     */
    record Revised(int revision, JsonNode original, JsonNode revised, JsonNode document) {
    }

    /**
     * The section of the current draft. With {@code basedOn} the draft has to be at exactly that revision, else 412
     * {@code wiki.version_conflict}; an unknown section is 400 {@code section.invalid}; without {@code wiki.edit} 403.
     */
    Section section(AccessContext actor, UUID pageId, String language, Integer basedOn, Integer section);

    /**
     * The revised text in place of the section of the current draft. The section must still have the {@code fingerprint} of the
     * start, else 409 {@code wiki.section_changed}; a text whose placeholders differ is 409 {@code ai.placeholders_changed}, one
     * that does not give a valid document 409 {@code ai.invalid_output}.
     */
    Revised revise(AccessContext actor, UUID pageId, String language, Integer section, String fingerprint, String text);

    /**
     * Whether a result keeps exactly the given placeholders, each once and in its kind of place: {@code null} when it does, else
     * {@code ai.placeholders_changed} or {@code ai.invalid_output}. It needs no draft and runs when the execution ends.
     */
    static String problem(String text, Collection<String> placeholders) {
        return WikiMarkdown.problem(text, placeholders);
    }
}
