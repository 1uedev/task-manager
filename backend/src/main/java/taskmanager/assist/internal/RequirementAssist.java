package taskmanager.assist.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.ai.AiRequest;
import taskmanager.ai.AiRuns;
import taskmanager.ai.AiSource;
import taskmanager.ai.AiUseCase;
import taskmanager.identity.AccountNames;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.requirements.RequirementReviews;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The AI review of a requirement (TASK-127, DEC-173, rule 1). A start passes exactly the revision the person sees, with what the
 * person may read of it: the title, the description, every acceptance criterion with its number and the readable custom fields,
 * each as a required source. The suggestion keeps a digest of these sources and of whether the revision is the current one;
 * every reading assembles them again for the person who reads, so a changed revision, a newer revision or a lost right makes it
 * invalid (prüffall A09). Nothing here changes the requirement: a proposal goes only into the editor and is saved, with If-Match,
 * by the normal save, which follows the rules of the revisions (prüffall A04). The review never approves and never changes the
 * status.
 */
@Service
public class RequirementAssist {
    /** The kind of object of the review in the AI foundation; its guard answers only for it. */
    public static final String OBJECT_KIND = "requirement";
    static final String PURPOSE = "review";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RequirementReviews requirements;
    private final AiRuns runs;
    private final ObjectProvider<AccountNames> names;
    private final TransactionTemplate read;
    private final TransactionTemplate separate;

    RequirementAssist(RequirementReviews requirements, AiRuns runs, ObjectProvider<AccountNames> names, PlatformTransactionManager transactions) {
        this.requirements = requirements;
        this.runs = runs;
        this.names = names;
        this.read = new TransactionTemplate(transactions);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What a source refers to: the title, the description, one criterion with its number or the custom fields. */
    public record SourceRef(String id, String part, Integer criterion) {
    }

    /** A started review of one revision with the language it writes in and the sources it was given. */
    public record Started(UUID runId, String language, int revision, List<SourceRef> sources) {
    }

    /** The assembled sources of one start or check. */
    record Context(UUID requirementId, UUID projectId, int revision, boolean current, String language, List<AiSource> sources,
            List<SourceRef> refs) {
        /** Purpose, language, requirement, revision, whether it is current, and every source with what it refers to. */
        String digest() {
            List<String> parts = new ArrayList<>();
            parts.add(PURPOSE);
            parts.add(language);
            parts.add(requirementId.toString());
            parts.add(Integer.toString(revision));
            parts.add(Boolean.toString(current));
            for (int i = 0; i < sources.size(); i++) {
                parts.add(sources.get(i).id());
                parts.add(sources.get(i).label());
                parts.add(sources.get(i).text());
                parts.add(refs.get(i).part());
                parts.add(refs.get(i).criterion() == null ? "" : refs.get(i).criterion().toString());
            }
            return Digests.of(parts);
        }
    }

    /** The references kept with the suggestion; never sent to the model. */
    record References(String purpose, int revision, String language, String digest) {
    }

    /** Whether the person may start the review at the requirement now; a snapshot for the interface, never an authorization. */
    public boolean usable(AccessContext actor, UUID requirementId, int revision) {
        return Boolean.TRUE.equals(read.execute(status -> {
            RequirementReviews.Review review = requirements.review(actor, requirementId, revision);
            return runs.usable(actor, review.projectId(), AiUseCase.REQUIREMENT_REVIEW);
        }));
    }

    /** Starts the review of the revision in {@code de} or {@code en}. */
    public Started review(AccessContext actor, UUID requirementId, int revision, String requestedLanguage) {
        String language = "en".equals(requestedLanguage) ? "en" : "de";
        Context context = read.execute(status -> context(actor, requirementId, revision, language));
        if (context.sources().isEmpty()) {
            throw ApiProblemException.of(409, "assist.no_sources");
        }
        References references = new References(PURPOSE, revision, language, context.digest());
        UUID run = runs.start(actor, new AiRequest(AiUseCase.REQUIREMENT_REVIEW, language, context.projectId(), OBJECT_KIND, requirementId,
            (long) revision, null, context.sources(), references, false));
        return new Started(run, language, revision, context.refs());
    }

    /** Whether a review still matches what the person may read of the revision now, checked in a transaction of its own. */
    boolean stillValid(AccessContext actor, UUID projectId, UUID objectId, String referencesJson) {
        try {
            JsonNode node = JSON.readTree(referencesJson);
            References references = new References(node.path("purpose").asString(), node.path("revision").asInt(-1),
                node.path("language").asString(), node.path("digest").asString());
            if (!PURPOSE.equals(references.purpose())) {
                return false;
            }
            Context context = separate.execute(status -> context(actor, objectId, references.revision(), references.language()));
            return context.projectId().equals(projectId) && context.digest().equals(references.digest());
        } catch (RuntimeException refused) {
            return false;
        }
    }

    private Context context(AccessContext actor, UUID requirementId, int revision, String language) {
        RequirementReviews.Review review = requirements.review(actor, requirementId, revision);
        boolean de = !"en".equals(language);
        List<AiSource> sources = new ArrayList<>();
        List<SourceRef> refs = new ArrayList<>();
        if (review.title() != null && !review.title().isBlank()) {
            add(sources, refs, de ? "Titel von " + review.key() : "Title of " + review.key(), review.title().strip(), "title", null);
        }
        if (review.description() != null && !review.description().isBlank()) {
            add(sources, refs, de ? "Beschreibung" : "Description", review.description().strip(), "description", null);
        }
        if (review.criteria() != null) {
            if (review.criteria().stream().allMatch(c -> c.text() == null || c.text().isBlank())) {
                // Without criteria the model still sees that there are none, a finding of the kind missing_criterion.
                add(sources, refs, de ? "Abnahmekriterien" : "Acceptance criteria",
                    de ? "Es sind keine Abnahmekriterien erfasst." : "No acceptance criteria are recorded.", "criteria", null);
            }
            for (RequirementReviews.Criterion criterion : review.criteria()) {
                if (criterion.text() != null && !criterion.text().isBlank()) {
                    add(sources, refs, (de ? "Abnahmekriterium K" : "Acceptance criterion K") + criterion.id(), criterion.text().strip(),
                        "criterion", criterion.id());
                }
            }
        }
        StringBuilder fields = new StringBuilder();
        for (RequirementReviews.Field field : review.fields()) {
            String value = value(field.definition(), field.value(), de);
            if (value != null && !value.isBlank()) {
                fields.append(field.definition().name()).append(": ").append(value.strip().replace('\n', ' ')).append('\n');
            }
        }
        if (!fields.isEmpty()) {
            add(sources, refs, de ? "Eigene Felder" : "Custom fields", fields.toString().strip(), "fields", null);
        }
        return new Context(review.id(), review.projectId(), review.revision(), review.current(), de ? "de" : "en", List.copyOf(sources),
            List.copyOf(refs));
    }

    private static void add(List<AiSource> sources, List<SourceRef> refs, String label, String text, String part, Integer criterion) {
        String id = "S" + (sources.size() + 1);
        sources.add(new AiSource(id, label, text, true));
        refs.add(new SourceRef(id, part, criterion));
    }

    private String value(ProjectConfiguration.CustomField field, String value, boolean de) {
        return switch (field.type()) {
            case ProjectConfiguration.BOOLEAN -> "true".equals(value) ? (de ? "ja" : "yes") : (de ? "nein" : "no");
            case ProjectConfiguration.SELECT -> field.option(value).map(ProjectConfiguration.Option::name).orElse(value);
            case ProjectConfiguration.USER -> person(value);
            default -> value;
        };
    }

    /** The shown name of a person; an unknown or unreadable one is left out rather than passed as an identifier. */
    private String person(String value) {
        AccountNames accounts = names.getIfUnique();
        if (accounts == null) {
            return null;
        }
        try {
            return accounts.shownName(UUID.fromString(value)).orElse(null);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** SHA-256 over values with their lengths, so that no two different lists give the same digest. */
    static final class Digests {
        private Digests() {
        }

        static String of(List<String> values) {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                for (String value : values) {
                    byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
                    sha.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                    sha.update((byte) ':');
                    sha.update(bytes);
                }
                return HexFormat.of().formatHex(sha.digest());
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }
}
