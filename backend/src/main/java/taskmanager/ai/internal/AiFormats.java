package taskmanager.ai.internal;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import taskmanager.ai.AiUseCase;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The answer format of each use case and its validation (TASK-125, rule 8; prüffall A15). The application, not the skill,
 * decides the format. An answer is accepted only as exactly one JSON object without duplicate keys, surrounded by nothing but
 * white space or one code block, with exactly the fields of its use case, each required, and their types, values and lengths
 * (TASK-129, F9); control characters other than line breaks and tabs are removed, and a reference to a source that was not
 * passed refuses the whole answer with its own code. The stored suggestion holds only the cleaned fields and is always shown
 * as plain text.
 */
final class AiFormats {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonMapper STRICT = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Pattern FENCE = Pattern.compile("```(?:json)?[ \\t]*\\r?\\n(.*)\\r?\\n[ \\t]*```", Pattern.DOTALL);
    static final List<String> FINDING_KINDS = List.of("unclear", "contradiction", "missing_criterion", "not_measurable", "other");

    private AiFormats() {
    }

    /** An answer that does not meet the format; the code says why. */
    static final class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;

        Invalid(String code) {
            super(code);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    static String description(AiUseCase useCase, String language) {
        boolean de = !"en".equals(language);
        return switch (useCase) {
            case TICKET_SUMMARY -> de
                ? "{\"state\": [{\"text\": \"belegter Sachstand\", \"sources\": [\"S1\"]}], \"open\": [{\"text\": \"offener Punkt\", \"sources\": [\"S2\"]}]}\n"
                    + "state: der belegte Sachstand in höchstens 20 Punkten; open: offene Punkte und Rückfragen in höchstens 20 Punkten; "
                    + "jeder Punkt höchstens 1000 Zeichen mit den Quellen, die ihn belegen."
                : "{\"state\": [{\"text\": \"established state\", \"sources\": [\"S1\"]}], \"open\": [{\"text\": \"open point\", \"sources\": [\"S2\"]}]}\n"
                    + "state: the established state in at most 20 points; open: open points and questions in at most 20 points; "
                    + "each point at most 1000 characters with the sources that support it.";
            case REPLY_DRAFT -> de
                ? "{\"body\": \"Text der Antwort\", \"questions\": [\"Rückfrage\"], \"notes\": [\"Hinweis für die prüfende Person\"], \"sources\": [\"S1\"]}\n"
                    + "body: der Antworttext an die Kundschaft ohne Anrede und Signatur, höchstens 20000 Zeichen; questions: offene Rückfragen; "
                    + "notes: Unsicherheiten für die prüfende Person, nicht für die Kundschaft; sources: die verwendeten Quellen."
                : "{\"body\": \"text of the reply\", \"questions\": [\"question\"], \"notes\": [\"note for the reviewer\"], \"sources\": [\"S1\"]}\n"
                    + "body: the reply to the customer without greeting and signature, at most 20000 characters; questions: open questions; "
                    + "notes: uncertainties for the reviewing person, not for the customer; sources: the sources used.";
            case REQUIREMENT_REVIEW -> de
                ? "{\"findings\": [{\"kind\": \"unclear\", \"location\": \"betroffene Textstelle\", \"reason\": \"Begründung\", \"suggestion\": \"Änderungsvorschlag oder null\", \"sources\": [\"S1\"]}]}\n"
                    + "kind: unclear, contradiction, missing_criterion, not_measurable oder other; höchstens 30 Befunde; location und reason "
                    + "höchstens je 2000 Zeichen, suggestion höchstens 5000 Zeichen oder null. Keine Freigabe und keine Statusänderung."
                : "{\"findings\": [{\"kind\": \"unclear\", \"location\": \"affected passage\", \"reason\": \"reason\", \"suggestion\": \"proposed change or null\", \"sources\": [\"S1\"]}]}\n"
                    + "kind: unclear, contradiction, missing_criterion, not_measurable or other; at most 30 findings; location and reason "
                    + "at most 2000 characters each, suggestion at most 5000 characters or null. No approval and no status change.";
            case WIKI_REVISION -> de
                ? "{\"text\": \"überarbeiteter Text\", \"notes\": [\"Hinweis zur Änderung\"]}\n"
                    + "text: der überarbeitete Text im selben einfachen Markdown wie die Quelle, höchstens 100000 Zeichen, alle Platzhalter "
                    + "der Quelle unverändert; notes: kurze Hinweise, was geändert wurde."
                : "{\"text\": \"revised text\", \"notes\": [\"note on the change\"]}\n"
                    + "text: the revised text in the same simple Markdown as the source, at most 100000 characters, every placeholder of "
                    + "the source unchanged; notes: short notes on what changed.";
        };
    }

    /** The cleaned answer, or {@link Invalid}. */
    static ObjectNode parse(AiUseCase useCase, String answer, Set<String> sourceIds) {
        JsonNode root = object(answer);
        ObjectNode out = JSON.createObjectNode();
        switch (useCase) {
            case TICKET_SUMMARY -> {
                fields(root, "state", "open");
                ArrayNode state = points(root.get("state"), sourceIds);
                if (state.isEmpty()) {
                    throw new Invalid("ai.invalid_output");
                }
                out.set("state", state);
                out.set("open", points(root.get("open"), sourceIds));
            }
            case REPLY_DRAFT -> {
                fields(root, "body", "questions", "notes", "sources");
                out.put("body", required(root.get("body"), 20_000));
                out.set("questions", texts(root.get("questions"), 20, 1000));
                out.set("notes", texts(root.get("notes"), 20, 1000));
                out.set("sources", sources(root.get("sources"), sourceIds));
            }
            case REQUIREMENT_REVIEW -> {
                fields(root, "findings");
                JsonNode findings = root.get("findings");
                if (!findings.isArray() || findings.size() > 30) {
                    throw new Invalid("ai.invalid_output");
                }
                ArrayNode list = out.putArray("findings");
                for (JsonNode finding : findings) {
                    fields(finding, "kind", "location", "reason", "suggestion", "sources");
                    String kind = required(finding.get("kind"), 40);
                    if (!FINDING_KINDS.contains(kind)) {
                        throw new Invalid("ai.invalid_output");
                    }
                    ObjectNode item = list.addObject();
                    item.put("kind", kind);
                    item.put("location", required(finding.get("location"), 2000));
                    item.put("reason", required(finding.get("reason"), 2000));
                    String suggestion = optional(finding.get("suggestion"), 5000);
                    if (suggestion == null) {
                        item.putNull("suggestion");
                    } else {
                        item.put("suggestion", suggestion);
                    }
                    item.set("sources", sources(finding.get("sources"), sourceIds));
                }
            }
            case WIKI_REVISION -> {
                fields(root, "text", "notes");
                out.put("text", required(root.get("text"), 100_000));
                out.set("notes", texts(root.get("notes"), 20, 1000));
            }
        }
        return out;
    }

    /**
     * The one JSON object of an answer: the whole text after white space, or the content of exactly one code block with the
     * optional language {@code json}. Duplicate keys, trailing content and anything else refuse the answer.
     */
    static JsonNode object(String answer) {
        if (answer == null) {
            throw new Invalid("ai.invalid_output");
        }
        String text = answer.strip();
        if (text.startsWith("```")) {
            Matcher fence = FENCE.matcher(text);
            // F5 (TASK-130): backticks inside a JSON string belong to the value; a second block fails as trailing content.
            if (!fence.matches()) {
                throw new Invalid("ai.invalid_output");
            }
            text = fence.group(1).strip();
        }
        if (!text.startsWith("{")) {
            throw new Invalid("ai.invalid_output");
        }
        try {
            JsonNode node = STRICT.readTree(text);
            if (node == null || !node.isObject()) {
                throw new Invalid("ai.invalid_output");
            }
            return node;
        } catch (RuntimeException invalid) {
            if (invalid instanceof Invalid known) {
                throw known;
            }
            throw new Invalid("ai.invalid_output");
        }
    }

    /**
     * Whether a text, a field name or any nested value of the cleaned answer holds the secret (TASK-130, F2). The check runs
     * on the decoded and cleaned values, so a key written with JSON escapes or joined by the cleaning is found as well.
     */
    static boolean mentions(JsonNode node, String secret) {
        if (node == null || secret == null || secret.isEmpty()) {
            return false;
        }
        if (node.isString()) {
            return node.asString().contains(secret);
        }
        if (node.isObject()) {
            for (java.util.Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getKey().contains(secret) || mentions(entry.getValue(), secret)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (mentions(element, secret)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An object with exactly these fields, each present; nothing else. */
    private static void fields(JsonNode node, String... names) {
        if (node == null || !node.isObject() || node.size() != names.length) {
            throw new Invalid("ai.invalid_output");
        }
        for (String name : names) {
            if (!node.has(name)) {
                throw new Invalid("ai.invalid_output");
            }
        }
    }

    private static ArrayNode points(JsonNode node, Set<String> sourceIds) {
        if (!node.isArray() || node.size() > 20) {
            throw new Invalid("ai.invalid_output");
        }
        ArrayNode list = JSON.createArrayNode();
        for (JsonNode point : node) {
            fields(point, "text", "sources");
            ObjectNode item = list.addObject();
            item.put("text", required(point.get("text"), 1000));
            item.set("sources", sources(point.get("sources"), sourceIds));
        }
        return list;
    }

    private static ArrayNode texts(JsonNode node, int max, int length) {
        if (!node.isArray() || node.size() > max) {
            throw new Invalid("ai.invalid_output");
        }
        ArrayNode list = JSON.createArrayNode();
        for (JsonNode entry : node) {
            list.add(required(entry, length));
        }
        return list;
    }

    private static ArrayNode sources(JsonNode node, Set<String> sourceIds) {
        if (!node.isArray() || node.size() > 50) {
            throw new Invalid("ai.invalid_output");
        }
        ArrayNode list = JSON.createArrayNode();
        for (JsonNode entry : node) {
            if (!entry.isString()) {
                throw new Invalid("ai.invalid_output");
            }
            if (!sourceIds.contains(entry.asString())) {
                throw new Invalid("ai.unknown_source");
            }
            boolean seen = false;
            for (JsonNode existing : list) {
                seen |= existing.asString().equals(entry.asString());
            }
            if (!seen) {
                list.add(entry.asString());
            }
        }
        return list;
    }

    private static String required(JsonNode node, int max) {
        String text = optional(node, max);
        if (text == null) {
            throw new Invalid("ai.invalid_output");
        }
        return text;
    }

    private static String optional(JsonNode node, int max) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new Invalid("ai.invalid_output");
        }
        String text = AiFrame.clean(node.asString(), "\u0000").strip();
        if (text.length() > max) {
            throw new Invalid("ai.invalid_output");
        }
        return text.isEmpty() ? null : text;
    }
}
