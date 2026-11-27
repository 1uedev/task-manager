package taskmanager.assist.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.ai.AiRequest;
import taskmanager.ai.AiRuns;
import taskmanager.ai.AiSource;
import taskmanager.ai.AiUseCase;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.wiki.WikiDrafts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The AI revision of a wiki draft (TASK-127, DEC-173, rule 2). A start passes one section of the current draft, or the whole
 * language version, as simple Markdown in which images, links to wiki pages and every block it cannot hold are placeholders,
 * with the goal of the person. An answer that loses, doubles or moves a placeholder is refused when the execution ends (prüffall
 * A05). The result is brought into its form on the server: the section before and after and the whole draft with the revised
 * section, checked like a save. The draft itself is not changed here; the person takes the result into the editor, saves it with
 * the normal save and publishes it, if at all, with the separate action. Every reading checks that the section is still the same
 * for the person, so a changed draft or a lost right makes the suggestion invalid (prüffall A09).
 */
@Service
public class WikiAssist {
    /** The kind of object of the revision in the AI foundation; its guard answers only for it. */
    public static final String OBJECT_KIND = "wiki_page";
    static final String PURPOSE = "wiki";
    private static final int GOAL_MAX = 500;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WikiDrafts wiki;
    private final AiRuns runs;
    private final TransactionTemplate read;
    private final TransactionTemplate separate;

    WikiAssist(WikiDrafts wiki, AiRuns runs, PlatformTransactionManager transactions) {
        this.wiki = wiki;
        this.runs = runs;
        this.read = new TransactionTemplate(transactions);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** The fixed goals of the interface; {@code custom} takes the own text of the person. */
    public enum Goal {
        SHORTER("shorter"), CLEARER("clearer"), FACTUAL("factual"), CUSTOM("custom");

        private final String key;

        Goal(String key) {
            this.key = key;
        }

        public static Goal of(String key) {
            for (Goal goal : values()) {
                if (goal.key.equals(key)) {
                    return goal;
                }
            }
            return null;
        }
    }

    /** A started revision of one section of the draft at {@code revision}. */
    public record Started(UUID runId, String language, int revision, Integer section, String heading) {
    }

    /** The result in place of its section, see {@link WikiDrafts.Revised}, with the notes of the model. */
    public record Result(int revision, Integer section, String heading, JsonNode original, JsonNode revised, JsonNode document,
            List<String> notes) {
    }

    /** The references kept with the suggestion; never sent to the model. */
    record References(String purpose, String language, Integer section, String heading, String fingerprint, List<String> placeholders) {
    }

    /** Whether the person may start a revision of the page now; a snapshot for the interface, never an authorization. */
    public boolean usable(AccessContext actor, UUID pageId, String language) {
        try {
            return Boolean.TRUE.equals(read.execute(status -> {
                WikiDrafts.Section section = wiki.section(actor, pageId, language, null, null);
                return runs.usable(actor, section.projectId(), AiUseCase.WIKI_REVISION);
            }));
        } catch (ApiProblemException refused) {
            if (refused.status() == 403 || refused.status() == 409) {
                return false;
            }
            throw refused;
        }
    }

    /**
     * Starts the revision of a section of the draft at exactly {@code revision}, or of the whole version without a section. The
     * language of the model is the language of the version.
     */
    public Started start(AccessContext actor, UUID pageId, String language, Integer revision, Integer section, String goalKey, String ownGoal) {
        Goal goal = Goal.of(goalKey);
        String custom = ownGoal == null ? null : ownGoal.strip();
        List<String> violations = new ArrayList<>();
        if (revision == null) {
            violations.add("revision.invalid");
        }
        if (goal == null || goal == Goal.CUSTOM && (custom == null || custom.isEmpty() || custom.length() > GOAL_MAX)) {
            violations.add("goal.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        WikiDrafts.Section part = read.execute(status -> wiki.section(actor, pageId, language, revision, section));
        if (part.text().isBlank()) {
            throw ApiProblemException.of(409, "assist.no_sources");
        }
        boolean de = !"en".equals(part.language());
        String label = part.section() == null ? (de ? "Ganze Seite" : "Whole page")
            : (de ? "Abschnitt „" + part.heading() + "“" : "Section \"" + part.heading() + "\"");
        AiSource source = new AiSource("S1", label, part.text(), true);
        References references = new References(PURPOSE, part.language(), part.section(), part.heading(), part.fingerprint(), part.placeholders());
        UUID run = runs.start(actor, new AiRequest(AiUseCase.WIKI_REVISION, de ? "de" : "en", part.projectId(), OBJECT_KIND, pageId,
            (long) part.revision(), goal(goal, custom, de), List.of(source), references, false));
        return new Started(run, part.language(), part.revision(), part.section(), part.heading());
    }

    /**
     * The result of an execution of the person for this page and language version: 404 for another execution, 409
     * {@code assist.not_ready} while it runs, 409 with the code of a failed, cancelled or invalid one.
     */
    public Result result(AccessContext actor, UUID pageId, String language, UUID runId) {
        AiRuns.Suggestion suggestion = runs.suggestion(actor, runId);
        if (suggestion.useCase() != AiUseCase.WIKI_REVISION || !OBJECT_KIND.equals(suggestion.objectKind()) || !pageId.equals(suggestion.objectId())) {
            throw ApiProblemException.of(404, "ai.run_not_found");
        }
        if (suggestion.content() == null) {
            if (suggestion.invalid() != null) {
                throw ApiProblemException.of(409, suggestion.invalid());
            }
            if (("failed".equals(suggestion.state()) || "refused".equals(suggestion.state())) && suggestion.code() != null) {
                throw ApiProblemException.of(409, suggestion.code());
            }
            throw ApiProblemException.of(409, "cancelled".equals(suggestion.state()) ? "ai.cancelled" : "assist.not_ready");
        }
        References references = references(suggestion.references());
        if (references == null || language != null && !language.equals(references.language())) {
            throw ApiProblemException.of(404, "ai.run_not_found");
        }
        WikiDrafts.Revised revised = read.execute(status -> wiki.revise(actor, pageId, references.language(), references.section(),
            references.fingerprint(), suggestion.content().path("text").asString()));
        List<String> notes = new ArrayList<>();
        suggestion.content().path("notes").forEach(note -> notes.add(note.asString()));
        return new Result(revised.revision(), references.section(), references.heading(), revised.original(), revised.revised(),
            revised.document(), List.copyOf(notes));
    }

    /** Whether the section is still the same for the person, checked in a transaction of its own. */
    boolean stillValid(AccessContext actor, UUID projectId, UUID objectId, String referencesJson) {
        try {
            References references = references(JSON.readTree(referencesJson));
            if (references == null) {
                return false;
            }
            WikiDrafts.Section section = separate.execute(status -> wiki.section(actor, objectId, references.language(), null,
                references.section()));
            return section.projectId().equals(projectId) && section.fingerprint().equals(references.fingerprint());
        } catch (RuntimeException refused) {
            return false;
        }
    }

    /** The check of the answer when the execution ends: every placeholder of the source once and in its kind of place (A05). */
    String contentProblem(String referencesJson, JsonNode content) {
        References references = references(JSON.readTree(referencesJson));
        if (references == null) {
            return "ai.invalid_output";
        }
        return WikiDrafts.problem(content.path("text").asString(), references.placeholders());
    }

    private static References references(JsonNode node) {
        if (node == null || !PURPOSE.equals(node.path("purpose").asString()) || !node.path("fingerprint").isString()) {
            return null;
        }
        List<String> placeholders = new ArrayList<>();
        node.path("placeholders").forEach(token -> placeholders.add(token.asString()));
        JsonNode section = node.path("section");
        JsonNode heading = node.path("heading");
        return new References(PURPOSE, node.path("language").asString(), section.isInt() ? section.asInt() : null,
            heading.isString() ? heading.asString() : null, node.path("fingerprint").asString(), List.copyOf(placeholders));
    }

    private static String goal(Goal goal, String custom, boolean de) {
        return switch (goal) {
            case SHORTER -> de ? "Fasse den Text kürzer." : "Make the text shorter.";
            case CLEARER -> de ? "Gliedere den Text klarer." : "Structure the text more clearly.";
            case FACTUAL -> de ? "Formuliere den Text sachlicher." : "Make the wording more factual.";
            case CUSTOM -> custom;
        };
    }
}
