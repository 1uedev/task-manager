package taskmanager.wiki;

import java.util.ArrayList;
import java.util.List;
import taskmanager.wiki.internal.WikiDocument;
import tools.jackson.databind.JsonNode;

/**
 * The checked document of the wiki editor for other modules (TASK-096, DEC-126): the description of a requirement uses the
 * same format, check and reading view as a wiki page, without images and without links to wiki pages. Only external links
 * with https, http and mailto remain.
 */
public final class RichText {
    private final WikiDocument document;

    private RichText(WikiDocument document) {
        this.document = document;
    }

    /** A refusal with the codes of the violations, for example {@code content.unsupported} or {@code content.image_not_allowed}. */
    public static final class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient List<String> violations;

        Invalid(List<String> violations) {
            super(String.join(",", violations), null, false, false);
            this.violations = List.copyOf(violations);
        }

        public List<String> violations() {
            return violations;
        }
    }

    /** Checks and normalizes what an editor sent; {@code null} is an empty document. */
    public static RichText check(JsonNode content) {
        WikiDocument document;
        try {
            document = WikiDocument.check(content);
        } catch (WikiDocument.Invalid invalid) {
            throw new Invalid(invalid.violations());
        }
        List<String> violations = new ArrayList<>();
        if (!document.images().isEmpty()) {
            violations.add("content.image_not_allowed");
        }
        if (!document.pageLinks().isEmpty()) {
            violations.add("content.page_link_not_allowed");
        }
        if (!violations.isEmpty()) {
            throw new Invalid(violations);
        }
        return new RichText(document);
    }

    /** Reads a stored document again; it was checked when it was saved. */
    public static RichText stored(String json) {
        return new RichText(WikiDocument.stored(json));
    }

    /** The normalized document as stored. */
    public String json() {
        return document.json();
    }

    /** The normalized document for the editor. */
    public JsonNode tree() {
        return document.tree();
    }

    /** The reading view as HTML written by the server with every text escaped. */
    public String html() {
        return document.html(target -> null);
    }

    /** Whether the document holds nothing but empty paragraphs. */
    public boolean blank() {
        return document.html(target -> null).replaceAll("<p></p>", "").isEmpty();
    }
}
