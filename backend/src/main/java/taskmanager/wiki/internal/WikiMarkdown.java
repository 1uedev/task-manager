package taskmanager.wiki.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The simple Markdown of a part of a wiki document for the AI revision (TASK-127, DEC-173) and the way back. Headings, paragraphs,
 * simple lists, quotes of paragraphs, horizontal rules and the marks bold, italic, code and external links are written as text.
 * Everything else is a placeholder the result must carry back unchanged: {@code [[IMAGE-n]]} for an image, {@code [[LINK-n]]} for
 * the text of a link to a wiki page, {@code [[BLOCK-n]]} for a table, a code block or a list or quote of another structure, and
 * {@code [[TEXT-n]]} for a piece of text Markdown cannot hold. Reading the result back puts the original nodes in place of the
 * placeholders; a placeholder that is missing, repeated, unknown or moved from a block into a line refuses the result.
 */
public final class WikiMarkdown {
    /** Every placeholder the writer can produce; anything else in this shape in a result is unknown. */
    public static final Pattern PLACEHOLDER = Pattern.compile("\\[\\[(IMAGE|LINK|BLOCK|TEXT)-([0-9]{1,6})\\]\\]");
    /** A result whose placeholders differ from those of its source. */
    public static final String PLACEHOLDERS_CHANGED = "ai.placeholders_changed";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String ESCAPABLE = "\\*`[]#>-+.)_!~";
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.*?)(?:[ \\t]+#+)?[ \\t]*$");
    private static final Pattern RULE = Pattern.compile("^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$");
    private static final Pattern ITEM = Pattern.compile("^([ \\t]*)([-*+]|([0-9]{1,9})[.)])(?:[ \\t]+(.*))?$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(```+|~~~+)[ \\t]*([^`\\s]*)[ \\t]*$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>[ ]?(.*)$");
    private static final Pattern BLOCK_LINE = Pattern.compile("^[ \\t]*\\[\\[(IMAGE|BLOCK)-([0-9]{1,6})\\]\\][ \\t]*$");
    private static final Pattern CODE_LANGUAGE = Pattern.compile("[a-z0-9+#-]{1,20}");

    private WikiMarkdown() {
    }

    /** A placeholder with the original block node, or the original inline nodes as an array. */
    public record Placeholder(String token, boolean block, JsonNode node) {
    }

    /** The written text with its placeholders in their order. */
    public record Text(String markdown, List<Placeholder> placeholders) {
    }

    /** A result that cannot be read back; the code is {@link #PLACEHOLDERS_CHANGED} or {@code ai.invalid_output}. */
    public static final class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;

        Invalid(String code) {
            super(code, null, false, false);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    // ------------------------------------------------------------------------------------------------ writing

    /** The Markdown of the blocks of a normalized document. */
    public static Text write(List<JsonNode> blocks) {
        Writer writer = new Writer();
        List<String> parts = new ArrayList<>();
        for (JsonNode block : blocks) {
            String part = writer.block(block);
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        return new Text(String.join("\n\n", parts), List.copyOf(writer.placeholders));
    }

    private static final class Writer {
        final List<Placeholder> placeholders = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();

        String placeholder(String kind, boolean block, JsonNode node) {
            int number = counts.merge(kind, 1, Integer::sum);
            String token = "[[" + kind + "-" + number + "]]";
            placeholders.add(new Placeholder(token, block, node.deepCopy()));
            return token;
        }

        String block(JsonNode node) {
            String type = node.get("type").asString();
            return switch (type) {
                case "heading" -> {
                    if (hasBreak(node)) {
                        yield placeholder("BLOCK", true, node);
                    }
                    yield "#".repeat(node.get("attrs").get("level").asInt()) + " " + inline(node.get("content"), "");
                }
                case "paragraph" -> escapeLineStarts(inline(node.get("content"), ""), "");
                case "bulletList", "orderedList" -> simpleList(node) ? list(node, 0) : placeholder("BLOCK", true, node);
                case "blockquote" -> simpleQuote(node) ? quote(node) : placeholder("BLOCK", true, node);
                case "horizontalRule" -> "---";
                case "image" -> placeholder("IMAGE", true, node);
                default -> placeholder("BLOCK", true, node);
            };
        }

        private String list(JsonNode list, int depth) {
            boolean ordered = "orderedList".equals(list.get("type").asString());
            int number = ordered ? list.get("attrs").get("start").asInt() : 1;
            String indent = "  ".repeat(depth);
            List<String> lines = new ArrayList<>();
            for (JsonNode item : list.get("content")) {
                String marker = ordered ? number++ + ". " : "- ";
                JsonNode content = item.get("content");
                String continuation = indent + " ".repeat(marker.length());
                lines.add(indent + marker + escapeLineStarts(inline(content.get(0).get("content"), continuation), continuation));
                for (int i = 1; i < content.size(); i++) {
                    lines.add(list(content.get(i), depth + 1));
                }
            }
            return String.join("\n", lines);
        }

        private String quote(JsonNode quote) {
            List<String> paragraphs = new ArrayList<>();
            for (JsonNode paragraph : quote.get("content")) {
                paragraphs.add("> " + escapeLineStarts(inline(paragraph.get("content"), "> "), "> "));
            }
            return String.join("\n>\n", paragraphs);
        }

        /** The inline content; a line break continues on a new line with {@code prefix}. */
        String inline(JsonNode content, String prefix) {
            if (content == null) {
                return "";
            }
            List<JsonNode> nodes = merged(content);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < nodes.size(); i++) {
                JsonNode node = nodes.get(i);
                if ("hardBreak".equals(node.get("type").asString())) {
                    out.append('\n').append(prefix);
                    continue;
                }
                String wiki = wikiHref(node);
                if (wiki != null) {
                    ArrayNode run = NODES.arrayNode();
                    while (i < nodes.size() && wiki.equals(wikiHref(nodes.get(i)))) {
                        run.add(nodes.get(i));
                        i++;
                    }
                    i--;
                    out.append(placeholder("LINK", false, run));
                    continue;
                }
                String written = text(node);
                if (written == null) {
                    ArrayNode one = NODES.arrayNode();
                    one.add(node);
                    out.append(placeholder("TEXT", false, one));
                } else {
                    out.append(written);
                }
            }
            return out.toString();
        }

        /** The text of one node with its marks, or {@code null} when Markdown cannot hold it. */
        private static String text(JsonNode node) {
            String value = node.get("text").asString();
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                return null;
            }
            boolean bold = false;
            boolean italic = false;
            boolean code = false;
            String href = null;
            JsonNode marks = node.get("marks");
            if (marks != null) {
                for (JsonNode mark : marks) {
                    switch (mark.get("type").asString()) {
                        case "bold" -> bold = true;
                        case "italic" -> italic = true;
                        case "code" -> code = true;
                        case "link" -> href = mark.get("attrs").get("href").asString();
                        default -> {
                            return null;
                        }
                    }
                }
            }
            if (code && value.indexOf('`') >= 0) {
                return null;
            }
            if (href != null && (href.indexOf(')') >= 0 || href.indexOf('(') >= 0 || href.chars().anyMatch(Character::isWhitespace))) {
                return null;
            }
            // Emphasis around white space at its edges would not read back as emphasis: the white space goes outside.
            String lead = "";
            String trail = "";
            String core = value;
            if (bold || italic) {
                int from = 0;
                int to = value.length();
                while (from < to && Character.isWhitespace(value.charAt(from))) {
                    from++;
                }
                while (to > from && Character.isWhitespace(value.charAt(to - 1))) {
                    to--;
                }
                lead = value.substring(0, from);
                trail = value.substring(to);
                core = value.substring(from, to);
            }
            String inner;
            if (core.isEmpty()) {
                inner = escape(value);
            } else {
                inner = code ? "`" + core + "`" : escape(core);
                if (italic) {
                    inner = "*" + inner + "*";
                }
                if (bold) {
                    inner = "**" + inner + "**";
                }
                inner = escape(lead) + inner + escape(trail);
            }
            return href == null ? inner : "[" + inner + "](" + href + ")";
        }

        private static boolean simpleList(JsonNode list) {
            for (JsonNode item : list.get("content")) {
                JsonNode content = item.get("content");
                if (content.isEmpty() || !"paragraph".equals(content.get(0).get("type").asString())) {
                    return false;
                }
                for (int i = 1; i < content.size(); i++) {
                    String type = content.get(i).get("type").asString();
                    if (!("bulletList".equals(type) || "orderedList".equals(type)) || !simpleList(content.get(i))) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static boolean simpleQuote(JsonNode quote) {
            for (JsonNode child : quote.get("content")) {
                if (!"paragraph".equals(child.get("type").asString())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean hasBreak(JsonNode node) {
            JsonNode content = node.get("content");
            if (content != null) {
                for (JsonNode child : content) {
                    if ("hardBreak".equals(child.get("type").asString())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** Neighbouring text nodes with the same marks as one node. */
    private static List<JsonNode> merged(JsonNode content) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode node : content) {
            JsonNode last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && "text".equals(last.get("type").asString()) && "text".equals(node.get("type").asString())
                    && String.valueOf(last.get("marks")).equals(String.valueOf(node.get("marks")))) {
                ObjectNode joined = (ObjectNode) last.deepCopy();
                joined.put("text", last.get("text").asString() + node.get("text").asString());
                out.set(out.size() - 1, joined);
            } else {
                out.add(node);
            }
        }
        return out;
    }

    private static String wikiHref(JsonNode node) {
        JsonNode marks = node.get("marks");
        if (!"text".equals(node.get("type").asString()) || marks == null) {
            return null;
        }
        for (JsonNode mark : marks) {
            if ("link".equals(mark.get("type").asString())) {
                String href = mark.get("attrs").get("href").asString();
                return href.startsWith("wiki:") ? href : null;
            }
        }
        return null;
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '*' || c == '`' || c == '[' || c == ']') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    private static final Pattern NUMBERED = Pattern.compile("^([0-9]{1,9})([.)])(\\s.*)?$");

    /**
     * A line of text that would read as the start of another block keeps its first character as text. The lines after the first
     * begin with {@code prefix}, the continuation of a quote or a list item, which stays as it is.
     */
    private static String escapeLineStarts(String text, String prefix) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String head = i == 0 || !lines[i].startsWith(prefix) ? "" : prefix;
            String rest = lines[i].substring(head.length());
            Matcher numbered = NUMBERED.matcher(rest);
            if (numbered.matches()) {
                rest = numbered.group(1) + "\\" + rest.substring(numbered.group(1).length());
            } else if (rest.startsWith("#") || rest.startsWith(">") || rest.startsWith("-") || rest.startsWith("+") || rest.startsWith("~")
                    || rest.startsWith("_")) {
                rest = "\\" + rest;
            }
            lines[i] = head + rest;
        }
        return String.join("\n", lines);
    }

    // ------------------------------------------------------------------------------------------------ reading

    /**
     * The blocks of a result with the original nodes in place of the placeholders. Every placeholder of the source must appear
     * exactly once and in its kind of place (a block one on a line of its own, an inline one inside a line); anything else is
     * {@link #PLACEHOLDERS_CHANGED}.
     */
    public static ArrayNode read(String markdown, Collection<Placeholder> placeholders) {
        Map<String, Placeholder> known = new LinkedHashMap<>();
        for (Placeholder placeholder : placeholders) {
            known.put(placeholder.token(), placeholder);
        }
        Reader reader = new Reader(known);
        ArrayNode blocks = reader.blocks(markdown);
        if (!reader.used.equals(known.keySet())) {
            throw new Invalid(PLACEHOLDERS_CHANGED);
        }
        return blocks;
    }

    /**
     * Whether a result keeps exactly the placeholders of its source; {@code null} when it does, else the code. Used when the
     * execution ends, before the original nodes are at hand: they are replaced by stand ins of the same kind.
     */
    public static String problem(String markdown, Collection<String> tokens) {
        List<Placeholder> standIns = new ArrayList<>();
        for (String token : new TreeSet<>(tokens)) {
            Matcher matcher = PLACEHOLDER.matcher(token);
            if (!matcher.matches()) {
                return PLACEHOLDERS_CHANGED;
            }
            boolean block = "IMAGE".equals(matcher.group(1)) || "BLOCK".equals(matcher.group(1));
            ObjectNode text = NODES.objectNode().put("type", "text").put("text", "x");
            if (block) {
                ObjectNode paragraph = NODES.objectNode().put("type", "paragraph");
                paragraph.putArray("content").add(text);
                standIns.add(new Placeholder(token, true, paragraph));
            } else {
                ArrayNode run = NODES.arrayNode();
                run.add(text);
                standIns.add(new Placeholder(token, false, run));
            }
        }
        try {
            read(markdown == null ? "" : markdown, standIns);
            return null;
        } catch (Invalid invalid) {
            return invalid.code();
        }
    }

    private static final class Reader {
        final Map<String, Placeholder> known;
        final Set<String> used = new HashSet<>();

        Reader(Map<String, Placeholder> known) {
            this.known = known;
        }

        ArrayNode blocks(String markdown) {
            String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            ArrayNode out = NODES.arrayNode();
            int i = 0;
            while (i < lines.length) {
                String line = lines[i];
                if (line.isBlank()) {
                    i++;
                    continue;
                }
                Matcher fence = FENCE.matcher(line);
                if (fence.matches()) {
                    i = code(lines, i, fence, out);
                    continue;
                }
                Matcher heading = HEADING.matcher(line);
                if (heading.matches()) {
                    ObjectNode node = out.addObject().put("type", "heading");
                    node.putObject("attrs").put("level", Math.min(3, heading.group(1).length()));
                    inline(heading.group(2), node);
                    i++;
                    continue;
                }
                if (RULE.matcher(line).matches()) {
                    out.addObject().put("type", "horizontalRule");
                    i++;
                    continue;
                }
                Matcher placeholder = BLOCK_LINE.matcher(line);
                if (placeholder.matches()) {
                    out.add(block("[[" + placeholder.group(1) + "-" + placeholder.group(2) + "]]"));
                    i++;
                    continue;
                }
                if (QUOTE.matcher(line).matches()) {
                    i = quote(lines, i, out);
                    continue;
                }
                if (ITEM.matcher(line).matches()) {
                    i = list(lines, i, out);
                    continue;
                }
                List<String> paragraph = new ArrayList<>();
                while (i < lines.length && !lines[i].isBlank() && (paragraph.isEmpty() || !startsBlock(lines[i]))) {
                    paragraph.add(lines[i].strip());
                    i++;
                }
                ObjectNode node = out.addObject().put("type", "paragraph");
                inline(String.join("\n", paragraph), node);
            }
            return out;
        }

        private static boolean startsBlock(String line) {
            return FENCE.matcher(line).matches() || HEADING.matcher(line).matches() || RULE.matcher(line).matches()
                || BLOCK_LINE.matcher(line).matches() || QUOTE.matcher(line).matches() || ITEM.matcher(line).matches();
        }

        private JsonNode block(String token) {
            Placeholder placeholder = known.get(token);
            if (placeholder == null || !placeholder.block() || !used.add(token)) {
                throw new Invalid(PLACEHOLDERS_CHANGED);
            }
            return placeholder.node().deepCopy();
        }

        private int code(String[] lines, int start, Matcher fence, ArrayNode out) {
            String marker = fence.group(1);
            String language = fence.group(2).toLowerCase(java.util.Locale.ROOT);
            List<String> body = new ArrayList<>();
            int i = start + 1;
            while (i < lines.length && !lines[i].strip().startsWith(marker)) {
                body.add(lines[i]);
                i++;
            }
            ObjectNode node = out.addObject().put("type", "codeBlock");
            ObjectNode attrs = node.putObject("attrs");
            if (CODE_LANGUAGE.matcher(language).matches()) {
                attrs.put("language", language);
            } else {
                attrs.putNull("language");
            }
            String text = String.join("\n", body);
            if (!text.isEmpty()) {
                node.putArray("content").addObject().put("type", "text").put("text", text);
            }
            return Math.min(lines.length, i + 1);
        }

        private int quote(String[] lines, int start, ArrayNode out) {
            List<String> inner = new ArrayList<>();
            int i = start;
            Matcher matcher;
            while (i < lines.length && (matcher = QUOTE.matcher(lines[i])).matches()) {
                inner.add(matcher.group(1));
                i++;
            }
            ObjectNode node = NODES.objectNode().put("type", "blockquote");
            ArrayNode content = node.putArray("content");
            List<String> paragraph = new ArrayList<>();
            for (String line : inner) {
                if (line.isBlank()) {
                    paragraphInto(paragraph, content);
                } else {
                    paragraph.add(line.strip());
                }
            }
            paragraphInto(paragraph, content);
            if (!content.isEmpty()) {
                out.add(node);
            }
            return i;
        }

        private void paragraphInto(List<String> lines, ArrayNode content) {
            if (lines.isEmpty()) {
                return;
            }
            ObjectNode paragraph = content.addObject().put("type", "paragraph");
            inline(String.join("\n", lines), paragraph);
            lines.clear();
        }

        private record Item(int indent, boolean ordered, int number, List<String> lines) {
        }

        private record Level(int indent, boolean ordered, ObjectNode list) {
        }

        private int list(String[] lines, int start, ArrayNode out) {
            List<Item> items = new ArrayList<>();
            int i = start;
            while (i < lines.length) {
                String line = lines[i];
                Matcher item = ITEM.matcher(line);
                if (item.matches() && !RULE.matcher(line).matches()) {
                    String number = item.group(3);
                    List<String> text = new ArrayList<>();
                    text.add(item.group(4) == null ? "" : item.group(4).strip());
                    items.add(new Item(width(item.group(1)), number != null, number == null ? 1 : Integer.parseInt(number), text));
                    i++;
                    continue;
                }
                if (!line.isBlank() && !items.isEmpty() && width(line) > items.get(items.size() - 1).indent() && !startsBlock(line.strip())) {
                    items.get(items.size() - 1).lines().add(line.strip());
                    i++;
                    continue;
                }
                if (line.isBlank()) {
                    int next = i + 1;
                    while (next < lines.length && lines[next].isBlank()) {
                        next++;
                    }
                    if (next < lines.length && ITEM.matcher(lines[next]).matches() && !RULE.matcher(lines[next]).matches()) {
                        i = next;
                        continue;
                    }
                }
                break;
            }
            List<Level> stack = new ArrayList<>();
            for (Item item : items) {
                while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() > item.indent()) {
                    stack.remove(stack.size() - 1);
                }
                Level top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                if (top != null && top.indent() == item.indent() && top.ordered() != item.ordered()) {
                    stack.remove(stack.size() - 1);
                    top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                }
                ObjectNode list;
                if (top != null && top.indent() == item.indent()) {
                    list = top.list();
                } else {
                    list = NODES.objectNode().put("type", item.ordered() ? "orderedList" : "bulletList");
                    if (item.ordered()) {
                        list.putObject("attrs").put("start", Math.max(1, Math.min(1_000_000, item.number())));
                    }
                    list.putArray("content");
                    if (top == null) {
                        out.add(list);
                    } else {
                        ArrayNode parentItems = (ArrayNode) top.list().get("content");
                        ((ArrayNode) parentItems.get(parentItems.size() - 1).get("content")).add(list);
                    }
                    stack.add(new Level(item.indent(), item.ordered(), list));
                }
                ObjectNode listItem = ((ArrayNode) list.get("content")).addObject().put("type", "listItem");
                ObjectNode paragraph = listItem.putArray("content").addObject().put("type", "paragraph");
                inline(String.join("\n", item.lines()), paragraph);
            }
            return i;
        }

        private static int width(String text) {
            int width = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') {
                    width++;
                } else if (c == '\t') {
                    width += 4;
                } else {
                    break;
                }
            }
            return width;
        }

        // -------------------------------------------------------------------------------------------- lines

        private enum Kind { TEXT, CODE, BREAK, STAR, LINK, PLACEHOLDER }

        private static final class Token {
            final Kind kind;
            final String text;
            final String href;
            final List<Token> inner;
            int count;
            boolean canOpen;
            boolean canClose;
            final List<Boolean> opens = new ArrayList<>();
            final List<Boolean> closes = new ArrayList<>();

            Token(Kind kind, String text, String href, List<Token> inner) {
                this.kind = kind;
                this.text = text;
                this.href = href;
                this.inner = inner;
            }
        }

        /** The inline content of a line or lines into {@code node}; a line break becomes {@code hardBreak}. */
        void inline(String text, ObjectNode node) {
            ArrayNode content = NODES.arrayNode();
            emit(tokens(text, true), content, false, false, null);
            ArrayNode merged = NODES.arrayNode();
            merged.addAll(merged(content));
            // A line break at the very start or end of a paragraph carries nothing.
            while (!merged.isEmpty() && "hardBreak".equals(merged.get(0).get("type").asString())) {
                merged.remove(0);
            }
            while (!merged.isEmpty() && "hardBreak".equals(merged.get(merged.size() - 1).get("type").asString())) {
                merged.remove(merged.size() - 1);
            }
            if (!merged.isEmpty()) {
                node.set("content", merged);
            }
        }

        private List<Token> tokens(String text, boolean links) {
            List<Token> tokens = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\' && i + 1 < text.length() && ESCAPABLE.indexOf(text.charAt(i + 1)) >= 0) {
                    plain.append(text.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '\n') {
                    flush(plain, tokens);
                    tokens.add(new Token(Kind.BREAK, null, null, null));
                    i++;
                    continue;
                }
                if (c == '`') {
                    int end = text.indexOf('`', i + 1);
                    if (end > i + 1) {
                        flush(plain, tokens);
                        tokens.add(new Token(Kind.CODE, text.substring(i + 1, end), null, null));
                        i = end + 1;
                        continue;
                    }
                }
                if (c == '[') {
                    Matcher placeholder = PLACEHOLDER.matcher(text).region(i, text.length());
                    if (placeholder.lookingAt()) {
                        flush(plain, tokens);
                        tokens.add(new Token(Kind.PLACEHOLDER, placeholder.group(), null, null));
                        i = placeholder.end();
                        continue;
                    }
                    int close = links ? closing(text, i) : -1;
                    if (close > 0 && close + 1 < text.length() && text.charAt(close + 1) == '(') {
                        int end = text.indexOf(')', close + 2);
                        if (end > close + 2) {
                            if (text.substring(close + 2, end).strip().toLowerCase(java.util.Locale.ROOT).startsWith("wiki:")) {
                                // A link to a wiki page comes back only as its placeholder, never as a new address.
                                throw new Invalid("ai.invalid_output");
                            }
                            flush(plain, tokens);
                            tokens.add(new Token(Kind.LINK, null, text.substring(close + 2, end).strip(), tokens(text.substring(i + 1, close), false)));
                            i = end + 1;
                            continue;
                        }
                    }
                }
                if (c == '*') {
                    flush(plain, tokens);
                    int end = i;
                    while (end < text.length() && text.charAt(end) == '*') {
                        end++;
                    }
                    Token star = new Token(Kind.STAR, null, null, null);
                    star.count = end - i;
                    star.canOpen = end < text.length() && !Character.isWhitespace(text.charAt(end));
                    star.canClose = i > 0 && !Character.isWhitespace(text.charAt(i - 1));
                    tokens.add(star);
                    i = end;
                    continue;
                }
                plain.append(c);
                i++;
            }
            flush(plain, tokens);
            pair(tokens);
            return tokens;
        }

        private static int closing(String text, int open) {
            for (int i = open + 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == ']') {
                    return i;
                } else if (c == '\n') {
                    return -1;
                }
            }
            return -1;
        }

        private static void flush(StringBuilder plain, List<Token> tokens) {
            if (!plain.isEmpty()) {
                tokens.add(new Token(Kind.TEXT, plain.toString(), null, null));
                plain.setLength(0);
            }
        }

        /** Pairs the runs of stars into bold and italic; what stays unpaired is text. */
        private static void pair(List<Token> tokens) {
            List<Token> openers = new ArrayList<>();
            for (Token token : tokens) {
                if (token.kind == Kind.BREAK) {
                    openers.clear();
                    continue;
                }
                if (token.kind != Kind.STAR) {
                    continue;
                }
                if (token.canClose) {
                    while (token.count > 0 && !openers.isEmpty()) {
                        Token opener = openers.get(openers.size() - 1);
                        boolean bold = opener.count >= 2 && token.count >= 2;
                        int use = bold ? 2 : 1;
                        opener.opens.add(bold);
                        token.closes.add(bold);
                        opener.count -= use;
                        token.count -= use;
                        if (opener.count == 0) {
                            openers.remove(openers.size() - 1);
                        }
                    }
                }
                if (token.count > 0 && token.canOpen) {
                    openers.add(token);
                }
            }
        }

        private void emit(List<Token> tokens, ArrayNode out, boolean bold0, boolean italic0, String href) {
            int bold = bold0 ? 1 : 0;
            int italic = italic0 ? 1 : 0;
            for (Token token : tokens) {
                switch (token.kind) {
                    case TEXT -> out.add(text(token.text, bold > 0, italic > 0, false, href));
                    case CODE -> out.add(text(token.text, bold > 0, italic > 0, true, href));
                    case BREAK -> out.addObject().put("type", "hardBreak");
                    case LINK -> emit(token.inner, out, bold > 0, italic > 0, token.href);
                    case PLACEHOLDER -> {
                        Placeholder placeholder = known.get(token.text);
                        if (placeholder == null || placeholder.block() || !used.add(token.text)) {
                            throw new Invalid(PLACEHOLDERS_CHANGED);
                        }
                        for (JsonNode node : placeholder.node()) {
                            out.add(node.deepCopy());
                        }
                    }
                    case STAR -> {
                        for (Boolean closed : token.closes) {
                            if (closed) {
                                bold--;
                            } else {
                                italic--;
                            }
                        }
                        if (token.count > 0) {
                            out.add(text("*".repeat(token.count), bold > 0, italic > 0, false, href));
                        }
                        for (Boolean opened : token.opens) {
                            if (opened) {
                                bold++;
                            } else {
                                italic++;
                            }
                        }
                    }
                }
            }
        }

        private static ObjectNode text(String value, boolean bold, boolean italic, boolean code, String href) {
            ObjectNode node = NODES.objectNode().put("type", "text").put("text", value);
            if (value.isEmpty()) {
                return node;
            }
            ArrayNode marks = NODES.arrayNode();
            if (bold) {
                marks.addObject().put("type", "bold");
            }
            if (italic) {
                marks.addObject().put("type", "italic");
            }
            if (code) {
                marks.addObject().put("type", "code");
            }
            if (href != null) {
                marks.addObject().put("type", "link").putObject("attrs").put("href", href);
            }
            if (!marks.isEmpty()) {
                node.set("marks", marks);
            }
            return node;
        }
    }
}
