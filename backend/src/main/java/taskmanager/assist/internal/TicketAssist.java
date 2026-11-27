package taskmanager.assist.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import taskmanager.helpdesk.TicketConversation;
import taskmanager.identity.AccountNames;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.sla.TicketPriorities;
import taskmanager.tickets.TicketReader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two use cases at the ticket (TASK-126). Each start assembles its sources with the read rules of the person at that moment,
 * oldest first, and keeps a digest of exactly these sources with the suggestion. Every reading of the suggestion assembles them
 * again for the person who reads: a changed source, a lost right or a lost ticket makes the suggestion invalid (prüffall A09).
 *
 * <p>The summary passes what the person may read in the ticket: title, description, type, status, priority and the readable
 * custom fields as one required source, then the mails, sent replies and posts of the portal. The draft passes only what the
 * customer of the ticket already saw: the title, the mails of the customer, the replies sent to it and the posts written for it,
 * never a description, custom field, priority or internal entry (prüffall A02). Its language is the language of the contact.
 */
@Service
public class TicketAssist {
    /** The kind of object of both use cases in the AI foundation; the guards answer only for it. */
    public static final String OBJECT_KIND = "ticket";
    static final String SUMMARY = "summary";
    private static final int SOURCES_MAX = 500;
    private static final int GOAL_MAX = 500;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final TicketReader tickets;
    private final TicketPriorities priorities;
    private final TicketConversation conversation;
    private final AiRuns runs;
    private final ObjectProvider<AccountNames> names;
    private final TransactionTemplate read;
    private final TransactionTemplate separate;

    TicketAssist(TicketReader tickets, TicketPriorities priorities, TicketConversation conversation, AiRuns runs,
            ObjectProvider<AccountNames> names, PlatformTransactionManager transactions) {
        this.tickets = tickets;
        this.priorities = priorities;
        this.conversation = conversation;
        this.runs = runs;
        this.names = names;
        this.read = new TransactionTemplate(transactions);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What a source of a suggestion refers to, for the display of the sources: the ticket itself or one entry. */
    public record SourceRef(String id, String kind, UUID entryId, Instant at, String author) {
    }

    /** A started execution with the language it writes in and the sources it was given. */
    public record Started(UUID runId, String language, List<SourceRef> sources) {
    }

    /** Which use case the person may start at the ticket now; a snapshot for the interface, never an authorization. */
    public record Options(boolean summary, boolean replyDraft) {
    }

    /**
     * The assembled sources of one start or check. {@code binding} holds what the suggestion depends on beyond the texts of its
     * sources and never reaches the model: for a draft the customer, the contact and the recipient data of the answer. With
     * {@code truncated} older entries were left out because of the limit of sources (TASK-141, H3).
     */
    record Context(UUID ticketId, long version, String language, List<AiSource> sources, List<SourceRef> refs, List<String> binding,
            boolean truncated) {
        /**
         * The digest of exactly what the suggestion was made from: purpose, language, ticket, every source with the kind and the
         * real identifier of its entry, and the binding (TASK-141, F1). The running source names alone would let another entry with
         * the same text or another recipient pass as unchanged.
         */
        String digest(String purpose) {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                update(sha, purpose);
                update(sha, language);
                update(sha, ticketId.toString());
                for (int i = 0; i < sources.size(); i++) {
                    AiSource source = sources.get(i);
                    SourceRef ref = refs.get(i);
                    update(sha, source.id());
                    update(sha, source.label());
                    update(sha, source.text());
                    update(sha, Boolean.toString(source.required()));
                    update(sha, ref.kind());
                    update(sha, ref.entryId() == null ? "" : ref.entryId().toString());
                }
                update(sha, Integer.toString(binding.size()));
                for (String value : binding) {
                    update(sha, value == null ? "" : value);
                }
                return HexFormat.of().formatHex(sha.digest());
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        private static void update(MessageDigest sha, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            sha.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
            sha.update((byte) ':');
            sha.update(bytes);
        }
    }

    /** The references kept with the suggestion; never sent to the model. */
    record References(String purpose, long number, String language, String digest) {
    }

    public Options options(AccessContext actor, UUID projectId, long number) {
        return read.execute(status -> {
            tickets.read(actor, projectId, number);
            return new Options(runs.usable(actor, projectId, AiUseCase.TICKET_SUMMARY), runs.usable(actor, projectId, AiUseCase.REPLY_DRAFT));
        });
    }

    /** Starts the summary in the language of the person ({@code de} or {@code en}). */
    public Started summary(AccessContext actor, UUID projectId, long number, String requestedLanguage) {
        String language = language(requestedLanguage);
        Context context = read.execute(status -> summaryContext(actor, projectId, number, language));
        return start(actor, projectId, number, AiUseCase.TICKET_SUMMARY, SUMMARY, context, null);
    }

    /** Starts the draft of an answer by mail or in the portal with an optional short goal of the person. */
    public Started draft(AccessContext actor, UUID projectId, long number, TicketConversation.Channel channel, String rawGoal) {
        String goal = rawGoal == null ? null : rawGoal.strip();
        if (goal != null && goal.isEmpty()) {
            goal = null;
        }
        if (channel == null || goal != null && goal.length() > GOAL_MAX) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of(channel == null ? "channel.invalid" : "goal.invalid"));
        }
        Context context = read.execute(status -> draftContext(actor, projectId, number, channel));
        return start(actor, projectId, number, AiUseCase.REPLY_DRAFT, purpose(channel), context, goal);
    }

    private Started start(AccessContext actor, UUID projectId, long number, AiUseCase useCase, String purpose, Context context, String goal) {
        if (context.sources().isEmpty()) {
            throw ApiProblemException.of(409, "assist.no_sources");
        }
        References references = new References(purpose, number, context.language(), context.digest(purpose));
        UUID run = runs.start(actor, new AiRequest(useCase, context.language(), projectId, OBJECT_KIND, context.ticketId(), context.version(), goal,
            context.sources(), references, context.truncated()));
        return new Started(run, context.language(), context.refs());
    }

    /**
     * Whether a suggestion of the use case still matches what the person may read now. The sources are assembled again in a
     * transaction of their own, so a refusal there never touches the reading of the suggestion; any refusal makes it invalid.
     */
    boolean stillValid(AiUseCase useCase, AccessContext actor, UUID projectId, UUID objectId, String referencesJson) {
        try {
            JsonNode node = JSON.readTree(referencesJson);
            References references = new References(node.path("purpose").asString(), node.path("number").asLong(-1),
                node.path("language").asString(), node.path("digest").asString());
            Context context = separate.execute(status -> switch (references.purpose()) {
                case SUMMARY -> useCase == AiUseCase.TICKET_SUMMARY
                    ? summaryContext(actor, projectId, references.number(), language(references.language())) : null;
                case "mail" -> useCase == AiUseCase.REPLY_DRAFT
                    ? draftContext(actor, projectId, references.number(), TicketConversation.Channel.MAIL) : null;
                case "portal" -> useCase == AiUseCase.REPLY_DRAFT
                    ? draftContext(actor, projectId, references.number(), TicketConversation.Channel.PORTAL) : null;
                default -> null;
            });
            return context != null && context.ticketId().equals(objectId) && context.digest(references.purpose()).equals(references.digest());
        } catch (RuntimeException refused) {
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------ the sources

    private Context summaryContext(AccessContext actor, UUID projectId, long number, String language) {
        TicketReader.Readable ticket = tickets.read(actor, projectId, number);
        Optional<String> priority = priorities.readable(actor, projectId, number);
        List<TicketConversation.Entry> entries = conversation.readable(actor, projectId, number);
        boolean de = language.equals("de");
        StringBuilder fields = new StringBuilder();
        Map<String, String> values = ticket.values();
        ProjectConfiguration configuration = ticket.configuration();
        line(fields, de ? "Titel" : "Title", values.get("title"));
        line(fields, de ? "Typ" : "Type", Optional.ofNullable(values.get("type")).map(key -> typeName(configuration, key)).orElse(null));
        line(fields, "Status", Optional.ofNullable(values.get("status")).map(key -> statusName(configuration, key)).orElse(null));
        line(fields, de ? "Priorität" : "Priority", priority.map(p -> priorityName(p, de)).orElse(null));
        for (ProjectConfiguration.CustomField field : configuration.customFields()) {
            String value = values.get(field.id());
            if (value != null) {
                line(fields, field.name(), customValue(field, value, de));
            }
        }
        String description = values.get("description");
        if (description != null && !description.isBlank()) {
            fields.append(de ? "Beschreibung:\n" : "Description:\n").append(description.strip()).append('\n');
        }
        List<AiSource> sources = new ArrayList<>();
        List<SourceRef> refs = new ArrayList<>();
        String label = (de ? "Ticket " : "Ticket ") + ticket.projectKey() + "-" + ticket.number();
        sources.add(new AiSource("S1", label, fields.toString().strip(), true));
        refs.add(new SourceRef("S1", "ticket", ticket.id(), null, null));
        boolean truncated = addEntries(sources, refs, entries, de, false);
        return new Context(ticket.id(), ticket.version(), language, List.copyOf(sources), List.copyOf(refs), List.of(), truncated);
    }

    private Context draftContext(AccessContext actor, UUID projectId, long number, TicketConversation.Channel channel) {
        TicketConversation.CustomerView view = conversation.forAnswer(actor, projectId, number, channel);
        TicketReader.Readable ticket = tickets.read(actor, projectId, number);
        boolean de = view.language().equals("de");
        List<AiSource> sources = new ArrayList<>();
        List<SourceRef> refs = new ArrayList<>();
        String title = ticket.values().get("title");
        if (title != null && !title.isBlank()) {
            sources.add(new AiSource("S1", de ? "Titel der Anfrage" : "Title of the request", title.strip(), false));
            refs.add(new SourceRef("S1", "ticket", ticket.id(), null, null));
        }
        boolean truncated = addEntries(sources, refs, view.entries(), de, true);
        List<String> binding = new ArrayList<>();
        binding.add("customer=" + view.customerId());
        binding.add("contact=" + (view.contactId() == null ? "" : view.contactId()));
        view.recipient().forEach(value -> binding.add("recipient=" + value));
        return new Context(ticket.id(), ticket.version(), view.language(), List.copyOf(sources), List.copyOf(refs), List.copyOf(binding),
            truncated);
    }

    /**
     * The entries as sources after the ones already given, oldest first; only the newest that fit into the limit of sources.
     * With {@code newestRequired} the newest entry must fit, else the start is refused as too large. Answers whether older entries
     * were left out because of the limit.
     */
    private static boolean addEntries(List<AiSource> sources, List<SourceRef> refs, List<TicketConversation.Entry> entries, boolean de,
            boolean newestRequired) {
        int room = SOURCES_MAX - sources.size();
        List<TicketConversation.Entry> kept = entries.size() > room ? entries.subList(entries.size() - room, entries.size()) : entries;
        int next = sources.size() + 1;
        for (int i = 0; i < kept.size(); i++) {
            TicketConversation.Entry entry = kept.get(i);
            String id = "S" + next++;
            boolean newest = i == kept.size() - 1;
            sources.add(new AiSource(id, label(entry, de), text(entry, de), newestRequired && newest));
            refs.add(new SourceRef(id, entry.kind(), entry.id(), entry.at(), entry.author()));
        }
        return kept.size() < entries.size();
    }

    private static String label(TicketConversation.Entry entry, boolean de) {
        boolean customer = "customer".equals(entry.author());
        String kind = switch (entry.kind()) {
            case "mail" -> de ? "Mail der Kundschaft" : "Mail of the customer";
            case "reply" -> de ? "Gesendete Antwort des Supports" : "Sent reply of the support";
            default -> customer ? (de ? "Portalbeitrag der Kundschaft" : "Portal post of the customer")
                : (de ? "Portalantwort des Supports" : "Portal answer of the support");
        };
        return kind + ", " + TIME.format(entry.at());
    }

    private static String text(TicketConversation.Entry entry, boolean de) {
        StringBuilder text = new StringBuilder();
        if (entry.name() != null && !entry.name().isBlank()) {
            text.append(de ? "Von: " : "From: ").append(entry.name().strip()).append('\n');
        }
        if (entry.subject() != null && !entry.subject().isBlank()) {
            text.append(de ? "Betreff: " : "Subject: ").append(entry.subject().strip()).append('\n');
        }
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(entry.body() == null ? "" : entry.body().strip());
        return text.toString();
    }

    private static void line(StringBuilder out, String name, String value) {
        if (value != null && !value.isBlank()) {
            out.append(name).append(": ").append(value.strip().replace('\n', ' ')).append('\n');
        }
    }

    private static String typeName(ProjectConfiguration configuration, String key) {
        return configuration.ticketTypes() == null ? key
            : configuration.ticketTypes().stream().filter(t -> t.key().equals(key)).map(ProjectConfiguration.TicketType::name).findFirst().orElse(key);
    }

    private static String statusName(ProjectConfiguration configuration, String key) {
        return configuration.statuses() == null ? key
            : configuration.statuses().stream().filter(s -> s.key().equals(key)).map(ProjectConfiguration.Status::name).findFirst().orElse(key);
    }

    private static String priorityName(String priority, boolean de) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("low", de ? "niedrig" : "low");
        names.put("normal", de ? "normal" : "normal");
        names.put("high", de ? "hoch" : "high");
        names.put("critical", de ? "kritisch" : "critical");
        return names.getOrDefault(priority, priority);
    }

    private String customValue(ProjectConfiguration.CustomField field, String value, boolean de) {
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

    private static String purpose(TicketConversation.Channel channel) {
        return channel == TicketConversation.Channel.MAIL ? "mail" : "portal";
    }

    private static String language(String requested) {
        return "en".equals(requested) ? "en" : "de";
    }
}
