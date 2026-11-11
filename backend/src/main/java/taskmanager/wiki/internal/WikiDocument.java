package taskmanager.wiki.internal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The stored document of a wiki page (TASK-093, DEC-123): the structured JSON of the editor, checked and normalized by the
 * server whatever the editor sent. Only the elements Holger confirmed are allowed: paragraphs, headings in three levels, bold,
 * italic, code in the text, ordered and unordered lists, tables with header cells, links, images with alternative text,
 * quotes, code blocks, horizontal rules and line breaks. Unknown attributes are dropped, an unknown element is refused. The
 * reading view is HTML the server writes from the normalized document, with every text escaped; there is no way to bring
 * HTML, scripts or foreign content into it.
 */
public final class WikiDocument {
    /** The version of the stored format; a later change of the format needs a migration of the stored documents. */
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_IMAGES = 50;
    static final int MAX_DEPTH = 30;
    static final int MAX_HREF = 2000;
    static final int MAX_ALT = 500;
    /** The path below which the images of pages are delivered; an image node refers to one of them. */
    public static final String IMAGE_PATH = "/api/v1/wiki/images/";
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z0-9+#-]{1,20}");
    private static final Pattern EXTERNAL = Pattern.compile("(?i)(https?://[^\\s<>\"]+|mailto:[^\\s<>\"]+)");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final Set<String> BLOCKS = Set.of("paragraph", "heading", "blockquote", "bulletList", "orderedList", "codeBlock",
        "horizontalRule", "table", "image");
    private static final Set<String> INLINE = Set.of("text", "hardBreak");
    private static final Set<String> MARKS = Set.of("bold", "italic", "code", "link");

    private final ObjectNode root;
    private final List<UUID> images;
    private final Set<UUID> pageLinks;
    private final String json;

    private WikiDocument(ObjectNode root, List<UUID> images, Set<UUID> pageLinks) {
        this.root = root;
        this.images = images;
        this.pageLinks = pageLinks;
        this.json = JSON.writeValueAsString(root);
    }

    /** A refusal with the codes of the violations, such as {@code content.unsupported} or {@code content.too_large}. */
    public static final class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient List<String> violations;

        Invalid(String violation) {
            super(violation, null, false, false);
            this.violations = List.of(violation);
        }

        public List<String> violations() {
            return violations;
        }
    }

    /** An empty document with one empty paragraph. */
    public static WikiDocument empty() {
        ObjectNode doc = NODES.objectNode().put("type", "doc");
        doc.putArray("content").addObject().put("type", "paragraph");
        return new WikiDocument(doc, List.of(), Set.of());
    }

    /** Checks and normalizes what an editor sent; {@code null} is an empty document. */
    public static WikiDocument check(JsonNode content) {
        if (content == null || content.isNull()) {
            return empty();
        }
        Checker checker = new Checker();
        ObjectNode doc = checker.doc(content);
        WikiDocument document = new WikiDocument(doc, List.copyOf(checker.images), Set.copyOf(checker.links));
        if (document.json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new Invalid("content.too_large");
        }
        if (checker.imageCount > MAX_IMAGES) {
            throw new Invalid("content.too_many_images");
        }
        return document;
    }

    /** Reads a stored document again; it was checked when it was saved. */
    public static WikiDocument stored(String json) {
        return check(JSON.readTree(json));
    }

    public String json() {
        return json;
    }

    public JsonNode tree() {
        return root.deepCopy();
    }

    /** The distinct images the document shows, in their order. */
    public List<UUID> images() {
        return images;
    }

    /** The wiki pages the document links to. */
    public Set<UUID> pageLinks() {
        return pageLinks;
    }

    /** What the reading view writes for a link to a wiki page: the address of a readable page, or {@code null}. */
    public interface Links extends Function<UUID, String> {
    }

    /**
     * The reading view as HTML. A link to a page the reader may not read becomes an empty marker
     * {@code <span class="wiki-unavailable" data-wiki-unavailable="true"></span>}, so neither its title nor the text of the
     * link reaches the reader; the interface names it "Seite nicht verfügbar".
     */
    public String html(Links links) {
        StringBuilder out = new StringBuilder();
        children(root, out, links);
        return out.toString();
    }

    /**
     * The visible text of the document for the search (TASK-102, DEC-132): the text of paragraphs, headings, lists, tables,
     * quotes and code blocks, each block on a line of its own, and the text of external links. Images and their alternative
     * text, addresses of links and the text of links to wiki pages are left out; such a link shows its text only to who may
     * read the target, so it must not make a page findable for others.
     */
    public String plainText() {
        StringBuilder out = new StringBuilder();
        plain(root, out);
        return out.toString().strip();
    }

    private static void plain(JsonNode node, StringBuilder out) {
        String type = node.get("type").asString();
        switch (type) {
            case "text" -> {
                if (!internalLink(node)) {
                    out.append(node.get("text").asString());
                }
            }
            case "hardBreak" -> out.append('\n');
            case "image", "horizontalRule" -> {
                // no text
            }
            default -> {
                JsonNode content = node.get("content");
                if (content != null) {
                    for (JsonNode child : content) {
                        plain(child, out);
                    }
                }
                if (!"doc".equals(type) && (BLOCKS.contains(type) || "listItem".equals(type) || "tableRow".equals(type)
                        || "tableHeader".equals(type) || "tableCell".equals(type))) {
                    if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
                        out.append('\n');
                    }
                }
            }
        }
    }

    private static boolean internalLink(JsonNode text) {
        JsonNode marks = text.get("marks");
        if (marks == null) {
            return false;
        }
        for (JsonNode mark : marks) {
            if ("link".equals(mark.get("type").asString()) && mark.get("attrs").get("href").asString().startsWith("wiki:")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ checking

    private static final class Checker {
        final LinkedHashSet<UUID> images = new LinkedHashSet<>();
        final Set<UUID> links = new LinkedHashSet<>();
        int imageCount;

        ObjectNode doc(JsonNode node) {
            if (!node.isObject() || !"doc".equals(text(node.get("type")))) {
                throw new Invalid("content.invalid");
            }
            ObjectNode out = NODES.objectNode().put("type", "doc");
            ArrayNode content = out.putArray("content");
            blocks(node.get("content"), content, 1, BLOCKS);
            if (content.isEmpty()) {
                content.addObject().put("type", "paragraph");
            }
            return out;
        }

        private void blocks(JsonNode list, ArrayNode out, int depth, Set<String> allowed) {
            if (list == null || list.isNull()) {
                return;
            }
            if (!list.isArray()) {
                throw new Invalid("content.invalid");
            }
            if (depth > MAX_DEPTH) {
                throw new Invalid("content.too_deep");
            }
            for (JsonNode child : list) {
                String type = child.isObject() ? text(child.get("type")) : null;
                if (type == null) {
                    throw new Invalid("content.invalid");
                }
                if (!allowed.contains(type)) {
                    throw new Invalid("content.unsupported");
                }
                out.add(block(type, child, depth));
            }
        }

        private ObjectNode block(String type, JsonNode node, int depth) {
            ObjectNode out = NODES.objectNode().put("type", type);
            JsonNode attrs = node.get("attrs");
            switch (type) {
                case "paragraph" -> inline(node.get("content"), out, true);
                case "heading" -> {
                    int level = integer(attrs, "level", 1);
                    if (level < 1 || level > 3) {
                        throw new Invalid("content.invalid");
                    }
                    out.putObject("attrs").put("level", level);
                    inline(node.get("content"), out, true);
                }
                case "blockquote" -> nonEmpty(node, out, depth, BLOCKS);
                case "bulletList" -> nonEmpty(node, out, depth, Set.of("listItem"));
                case "orderedList" -> {
                    int start = integer(attrs, "start", 1);
                    if (start < 1 || start > 1_000_000) {
                        throw new Invalid("content.invalid");
                    }
                    out.putObject("attrs").put("start", start);
                    nonEmpty(node, out, depth, Set.of("listItem"));
                }
                case "listItem" -> nonEmpty(node, out, depth, BLOCKS);
                case "codeBlock" -> {
                    String language = attrs == null ? null : text(attrs.get("language"));
                    ObjectNode a = out.putObject("attrs");
                    if (language != null && LANGUAGE.matcher(language).matches()) {
                        a.put("language", language);
                    } else {
                        a.putNull("language");
                    }
                    inline(node.get("content"), out, false);
                }
                case "horizontalRule" -> {
                    // no attributes, no content
                }
                case "table" -> nonEmpty(node, out, depth, Set.of("tableRow"));
                case "tableRow" -> nonEmpty(node, out, depth, Set.of("tableHeader", "tableCell"));
                case "tableHeader", "tableCell" -> {
                    int colspan = integer(attrs, "colspan", 1);
                    int rowspan = integer(attrs, "rowspan", 1);
                    if (colspan < 1 || colspan > 50 || rowspan < 1 || rowspan > 500) {
                        throw new Invalid("content.invalid");
                    }
                    ObjectNode a = out.putObject("attrs").put("colspan", colspan).put("rowspan", rowspan);
                    JsonNode widths = attrs == null ? null : attrs.get("colwidth");
                    if (widths != null && widths.isArray() && widths.size() == colspan) {
                        ArrayNode kept = a.putArray("colwidth");
                        for (JsonNode width : widths) {
                            if (!width.isInt() || width.asInt() < 1 || width.asInt() > 5000) {
                                throw new Invalid("content.invalid");
                            }
                            kept.add(width.asInt());
                        }
                    } else {
                        a.putNull("colwidth");
                    }
                    nonEmpty(node, out, depth, BLOCKS);
                }
                case "image" -> {
                    String src = attrs == null ? null : text(attrs.get("src"));
                    UUID id = imageId(src);
                    if (id == null) {
                        throw new Invalid("image.external");
                    }
                    String alt = attrs.get("alt") == null || attrs.get("alt").isNull() ? "" : text(attrs.get("alt"));
                    if (alt == null || alt.length() > MAX_ALT) {
                        throw new Invalid("content.invalid");
                    }
                    out.putObject("attrs").put("src", IMAGE_PATH + id).put("alt", alt.strip());
                    images.add(id);
                    imageCount++;
                }
                default -> throw new Invalid("content.unsupported");
            }
            return out;
        }

        private void nonEmpty(JsonNode node, ObjectNode out, int depth, Set<String> allowed) {
            ArrayNode content = NODES.arrayNode();
            blocks(node.get("content"), content, depth + 1, allowed);
            if (content.isEmpty()) {
                throw new Invalid("content.invalid");
            }
            out.set("content", content);
        }

        private void inline(JsonNode list, ObjectNode out, boolean marks) {
            if (list == null || list.isNull()) {
                return;
            }
            if (!list.isArray()) {
                throw new Invalid("content.invalid");
            }
            ArrayNode content = NODES.arrayNode();
            for (JsonNode child : list) {
                String type = child.isObject() ? text(child.get("type")) : null;
                if (type == null) {
                    throw new Invalid("content.invalid");
                }
                if (!INLINE.contains(type) || ("hardBreak".equals(type) && !marks)) {
                    throw new Invalid("content.unsupported");
                }
                if ("hardBreak".equals(type)) {
                    content.addObject().put("type", "hardBreak");
                    continue;
                }
                String value = text(child.get("text"));
                if (value == null || value.isEmpty()) {
                    throw new Invalid("content.invalid");
                }
                ObjectNode text = NODES.objectNode().put("type", "text").put("text", value);
                JsonNode given = child.get("marks");
                if (given != null && !given.isNull()) {
                    if (!marks || !given.isArray()) {
                        throw new Invalid(marks ? "content.invalid" : "content.unsupported");
                    }
                    ArrayNode kept = NODES.arrayNode();
                    Set<String> seen = new LinkedHashSet<>();
                    for (JsonNode mark : given) {
                        String name = mark.isObject() ? text(mark.get("type")) : null;
                        if (name == null) {
                            throw new Invalid("content.invalid");
                        }
                        if (!MARKS.contains(name)) {
                            throw new Invalid("content.unsupported");
                        }
                        if (!seen.add(name)) {
                            continue;
                        }
                        ObjectNode m = NODES.objectNode().put("type", name);
                        if ("link".equals(name)) {
                            m.putObject("attrs").put("href", href(mark.get("attrs") == null ? null : text(mark.get("attrs").get("href"))));
                        }
                        kept.add(m);
                    }
                    if (!kept.isEmpty()) {
                        text.set("marks", kept);
                    }
                }
                content.add(text);
            }
            if (!content.isEmpty()) {
                out.set("content", content);
            }
        }

        /** Links to the web, to mail addresses and to wiki pages as {@code wiki:<id>}; nothing else. */
        private String href(String href) {
            if (href == null || href.isBlank() || href.length() > MAX_HREF || href.chars().anyMatch(WikiDocument::control)) {
                throw new Invalid("link.invalid");
            }
            String value = href.strip();
            if (value.startsWith("wiki:")) {
                String id = value.substring(5);
                if (!UUID_TEXT.matcher(id).matches()) {
                    throw new Invalid("link.invalid");
                }
                links.add(UUID.fromString(id));
                return value;
            }
            if (!EXTERNAL.matcher(value).matches()) {
                throw new Invalid("link.invalid");
            }
            return value;
        }
    }

    /** Control characters of C0, DEL and C1 never belong into a link target, before or after any normalization. */
    private static boolean control(int c) {
        return c < 0x20 || (c >= 0x7F && c <= 0x9F);
    }

    static UUID imageId(String src) {
        if (src == null || !src.startsWith(IMAGE_PATH)) {
            return null;
        }
        String id = src.substring(IMAGE_PATH.length());
        return UUID_TEXT.matcher(id).matches() ? UUID.fromString(id) : null;
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    private static int integer(JsonNode attrs, String name, int fallback) {
        JsonNode value = attrs == null ? null : attrs.get(name);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new Invalid("content.invalid");
        }
        return value.asInt();
    }

    // ------------------------------------------------------------------------------------------------ the reading view

    private static final Map<String, String> TAGS = Map.of("paragraph", "p", "blockquote", "blockquote", "bulletList", "ul", "listItem", "li",
        "table", "table", "tableRow", "tr", "tableHeader", "th", "tableCell", "td");

    private static void children(JsonNode node, StringBuilder out, Links links) {
        JsonNode content = node.get("content");
        if (content == null) {
            return;
        }
        for (JsonNode child : content) {
            render(child, out, links);
        }
    }

    private static void render(JsonNode node, StringBuilder out, Links links) {
        String type = node.get("type").asString();
        JsonNode attrs = node.get("attrs");
        switch (type) {
            case "heading" -> {
                int level = attrs.get("level").asInt();
                out.append("<h").append(level).append('>');
                children(node, out, links);
                out.append("</h").append(level).append('>');
            }
            case "orderedList" -> {
                int start = attrs.get("start").asInt();
                out.append(start == 1 ? "<ol>" : "<ol start=\"" + start + "\">");
                children(node, out, links);
                out.append("</ol>");
            }
            case "codeBlock" -> {
                JsonNode language = attrs.get("language");
                out.append("<pre><code");
                if (language != null && language.isString()) {
                    out.append(" class=\"language-").append(escape(language.asString())).append('"');
                }
                out.append('>');
                children(node, out, links);
                out.append("</code></pre>");
            }
            case "horizontalRule" -> out.append("<hr>");
            case "hardBreak" -> out.append("<br>");
            case "image" -> out.append("<img src=\"").append(escape(attrs.get("src").asString())).append("\" alt=\"")
                .append(escape(attrs.get("alt").asString())).append("\" loading=\"lazy\">");
            case "table" -> {
                out.append("<table><tbody>");
                children(node, out, links);
                out.append("</tbody></table>");
            }
            case "tableHeader", "tableCell" -> {
                String tag = TAGS.get(type);
                out.append('<').append(tag);
                int colspan = attrs.get("colspan").asInt();
                int rowspan = attrs.get("rowspan").asInt();
                if (colspan > 1) {
                    out.append(" colspan=\"").append(colspan).append('"');
                }
                if (rowspan > 1) {
                    out.append(" rowspan=\"").append(rowspan).append('"');
                }
                out.append('>');
                children(node, out, links);
                out.append("</").append(tag).append('>');
            }
            case "text" -> text(node, out, links);
            default -> {
                String tag = TAGS.get(type);
                out.append('<').append(tag).append('>');
                children(node, out, links);
                out.append("</").append(tag).append('>');
            }
        }
    }

    private static void text(JsonNode node, StringBuilder out, Links links) {
        String value = escape(node.get("text").asString());
        JsonNode marks = node.get("marks");
        if (marks == null) {
            out.append(value);
            return;
        }
        String href = null;
        List<String> wrap = new ArrayList<>();
        for (JsonNode mark : marks) {
            switch (mark.get("type").asString()) {
                case "bold" -> wrap.add("strong");
                case "italic" -> wrap.add("em");
                case "code" -> wrap.add("code");
                case "link" -> href = mark.get("attrs").get("href").asString();
                default -> {
                    // checked when saved
                }
            }
        }
        StringBuilder inner = new StringBuilder();
        wrap.forEach(tag -> inner.append('<').append(tag).append('>'));
        inner.append(value);
        for (int i = wrap.size() - 1; i >= 0; i--) {
            inner.append("</").append(wrap.get(i)).append('>');
        }
        if (href == null) {
            out.append(inner);
        } else if (href.startsWith("wiki:")) {
            UUID target = UUID.fromString(href.substring(5));
            String address = links.apply(target);
            if (address == null) {
                out.append("<span class=\"wiki-unavailable\" data-wiki-unavailable=\"true\"></span>");
            } else {
                out.append("<a href=\"").append(escape(address)).append("\" data-wiki-page=\"").append(target).append("\">").append(inner).append("</a>");
            }
        } else {
            out.append("<a href=\"").append(escape(href)).append("\" rel=\"noopener noreferrer nofollow\" target=\"_blank\">").append(inner).append("</a>");
        }
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
