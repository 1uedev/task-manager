package taskmanager.ai.internal;

import java.util.List;
import java.util.Set;
import taskmanager.ai.AiUseCase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The answer format of each use case and its validation (TASK-125, rule 8; prüffall A15). The application, not the skill,
 * decides the format. An answer is accepted only as one JSON object with the expected fields, types and lengths; unknown
 * fields are dropped, control characters other than line breaks and tabs removed, and a reference to a source that was not
 * passed refuses the whole answer. The stored suggestion holds only the cleaned fields and is always shown as plain text.
 */
final class AiFormats {
    private static final JsonMapper JSON = JsonMapper.builder().build();
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
                out.set("state", points(root.path("state"), sourceIds));
                out.set("open", points(root.path("open"), sourceIds));
            }
            case REPLY_DRAFT -> {
                out.put("body", required(root.path("body"), 20_000));
                out.set("questions", texts(root.path("questions"), 20, 1000));
                out.set("notes", texts(root.path("notes"), 20, 1000));
                out.set("sources", sources(root.path("sources"), sourceIds));
            }
            case REQUIREMENT_REVIEW -> {
                JsonNode findings = root.path("findings");
                if (!findings.isArray() || findings.size() > 30) {
                    throw new Invalid("ai.invalid_output");
                }
                ArrayNode list = out.putArray("findings");
                for (JsonNode finding : findings) {
                    String kind = optional(finding.path("kind"), 40);
                    ObjectNode item = list.addObject();
                    item.put("kind", kind != null && FINDING_KINDS.contains(kind) ? kind : "other");
                    item.put("location", required(finding.path("location"), 2000));
                    item.put("reason", required(finding.path("reason"), 2000));
                    String suggestion = optional(finding.path("suggestion"), 5000);
                    if (suggestion == null) {
                        item.putNull("suggestion");
                    } else {
                        item.put("suggestion", suggestion);
                    }
                    item.set("sources", sources(finding.path("sources"), sourceIds));
                }
            }
            case WIKI_REVISION -> {
                out.put("text", required(root.path("text"), 100_000));
                out.set("notes", texts(root.path("notes"), 20, 1000));
            }
        }
        return out;
    }

    /** The one JSON object of an answer, also when a model wrapped it in a code fence or a sentence. */
    private static JsonNode object(String answer) {
        if (answer == null) {
            throw new Invalid("ai.invalid_output");
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new Invalid("ai.invalid_output");
        }
        try {
            JsonNode node = JSON.readTree(answer.substring(start, end + 1));
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

    private static ArrayNode points(JsonNode node, Set<String> sourceIds) {
        if (node.isMissingNode() || node.isNull()) {
            return JSON.createArrayNode();
        }
        if (!node.isArray() || node.size() > 20) {
            throw new Invalid("ai.invalid_output");
        }
        ArrayNode list = JSON.createArrayNode();
        for (JsonNode point : node) {
            ObjectNode item = list.addObject();
            item.put("text", required(point.path("text"), 1000));
            item.set("sources", sources(point.path("sources"), sourceIds));
        }
        return list;
    }

    private static ArrayNode texts(JsonNode node, int max, int length) {
        ArrayNode list = JSON.createArrayNode();
        if (node.isMissingNode() || node.isNull()) {
            return list;
        }
        if (!node.isArray() || node.size() > max) {
            throw new Invalid("ai.invalid_output");
        }
        for (JsonNode entry : node) {
            list.add(required(entry, length));
        }
        return list;
    }

    private static ArrayNode sources(JsonNode node, Set<String> sourceIds) {
        ArrayNode list = JSON.createArrayNode();
        if (node.isMissingNode() || node.isNull()) {
            return list;
        }
        if (!node.isArray() || node.size() > 50) {
            throw new Invalid("ai.invalid_output");
        }
        for (JsonNode entry : node) {
            if (!entry.isString() || !sourceIds.contains(entry.asString())) {
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
