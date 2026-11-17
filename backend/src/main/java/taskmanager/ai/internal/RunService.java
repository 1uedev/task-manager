package taskmanager.ai.internal;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.ai.AiRequest;
import taskmanager.ai.AiRuns;
import taskmanager.ai.AiSource;
import taskmanager.ai.AiSuggestionGuard;
import taskmanager.ai.AiUseCase;
import taskmanager.identity.AccountDeactivated;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The executions of the AI foundation (TASK-125, rules 9 to 13). A start checks in one transaction the rights, the assignment
 * with its access and model, the skill that applies and the daily limits, builds the prompt in the fixed frame and writes the
 * record; the call itself runs afterwards on one of the places of this process, at most {@code running} at a time and
 * {@code waiting} more in line, so a full line answers 503 at once. Nothing is repeated on its own and nothing moves to
 * another provider. The answer is validated against the format of the use case and kept as a suggestion only for the person
 * who started it, and only if access, model and assignment are still usable when it arrives (prüffall A14). The person can
 * cancel; a cancelled or failed execution keeps no content. Prompts and answers never reach a log.
 */
@Service
public class RunService implements AiRuns {
    private static final Logger LOG = LoggerFactory.getLogger(RunService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int GOAL_MAX = 500;
    static final int SAMPLE_MAX = 20_000;

    private final AiStore store;
    private final AiAccess access;
    private final ConfigService config;
    private final SkillService skills;
    private final AiAdapters adapters;
    private final AiProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ObjectProvider<AiSuggestionGuard> guards;
    private final Semaphore places;
    private final ThreadPoolExecutor executor;
    private final Map<UUID, Future<?>> pending = new ConcurrentHashMap<>();

    RunService(AiStore store, AiAccess access, ConfigService config, SkillService skills, AiAdapters adapters, AiProperties properties, Clock clock,
            PlatformTransactionManager transactions, ObjectProvider<AiSuggestionGuard> guards) {
        this.store = store;
        this.access = access;
        this.config = config;
        this.skills = skills;
        this.adapters = adapters;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.guards = guards;
        this.places = new Semaphore(properties.running() + properties.waiting());
        AtomicInteger number = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(properties.running(), properties.running(), 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(Math.max(1, properties.waiting())), runnable -> {
                Thread thread = new Thread(runnable, "ai-run-" + number.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    /** What a person sees of an execution: the record, the suggestion while it is valid, otherwise why not. */
    public record View(Run run, JsonNode content, String invalid) {
    }

    /** One execution as it will be sent, prepared in the transaction of the start. */
    private record Plan(UUID runId, AiUseCase useCase, AiProvider provider, AiModel model, AiAdapters.Prompt prompt, List<String> sourceIds,
            String references) {
    }

    // ------------------------------------------------------------------------------------------------ start

    @Override
    public UUID start(AccessContext actor, AiRequest request) {
        AiAccess.internal(actor);
        validate(request);
        return begin(() -> {
            access.requireUse(actor, request.projectId());
            return prepare(actor.companyId(), actor.userId(), "use", request, true, null);
        });
    }

    /** The trial of a skill by the administration (rule 7): the draft or what applies, with a sample text as the only source. */
    public UUID trial(UUID accountId, AiUseCase useCase, String language, String sample, String goal) {
        String text = sample == null ? null : sample.strip();
        if (text == null || text.isEmpty() || text.length() > SAMPLE_MAX) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("sample.invalid"));
        }
        AiRequest request = new AiRequest(useCase, SkillService.language(language), null, null, null, null, goal,
            List.of(new AiSource("S1", "de".equals(language) ? "Beispiel" : "Sample", text, true)), null);
        validate(request);
        return begin(() -> {
            UUID companyId = access.requireManage();
            return prepare(companyId, accountId, "test", request, false, skills.forTrial(companyId, useCase, request.language()));
        });
    }

    private UUID begin(java.util.function.Supplier<Plan> prepare) {
        if (!places.tryAcquire()) {
            throw ApiProblemException.retryAfter(503, "ai.busy", 10);
        }
        boolean handed = false;
        try {
            Plan plan;
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                plan = prepare.get();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            submit(plan);
                        } else {
                            places.release();
                        }
                    }
                });
            } else {
                plan = transaction.execute(status -> prepare.get());
                submit(plan);
            }
            handed = true;
            return plan.runId();
        } finally {
            if (!handed) {
                places.release();
            }
        }
    }

    private Plan prepare(UUID companyId, UUID accountId, String kind, AiRequest request, boolean requireEnabled, SkillService.Applied skill) {
        Assignment assignment = store.assignments(companyId).get(request.useCase());
        ConfigService.Availability availability = config.availability(companyId, assignment, requireEnabled);
        if (!availability.available()) {
            throw ApiProblemException.withViolations(409, "ai.unavailable", List.of(availability.reason()));
        }
        store.lockLimits(companyId);
        Limits limits = store.limits(companyId);
        Instant now = clock.instant();
        Instant day = LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (store.countSince(companyId, accountId, day) >= limits.personDaily()) {
            throw ApiProblemException.of(429, "ai.daily_limit_person");
        }
        if (store.countSince(companyId, null, day) >= limits.companyDaily()) {
            throw ApiProblemException.of(429, "ai.daily_limit_company");
        }
        SkillService.Applied applied = skill != null ? skill : skills.applied(companyId, request.useCase(), request.language());
        AiFrame.Built built;
        try {
            built = AiFrame.build(request.useCase(), request.language(), applied.content(), request.goal(), request.sources(),
                properties.contextCharacters(), availability.model());
        } catch (AiFrame.TooLarge tooLarge) {
            throw ApiProblemException.of(400, "ai.context_too_large");
        }
        UUID runId = UUID.randomUUID();
        AiProvider provider = availability.provider();
        AiModel model = availability.model();
        store.insertRun(new Run(runId, companyId, accountId, kind, request.useCase(), request.language(), request.projectId(), request.objectKind(),
            request.objectId(), request.objectVersion(), applied.label(), provider.id(), model.id(), model.modelKey(), provider.dataPath().key(),
            "queued", null, built.truncated(), null, null, now, null, false));
        String references = request.references() == null ? "null" : JSON.writeValueAsString(request.references());
        return new Plan(runId, request.useCase(), provider, model, built.prompt(), built.sourceIds(), references);
    }

    private static void validate(AiRequest request) {
        List<String> violations = new java.util.ArrayList<>();
        if (request == null || request.useCase() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("useCase.invalid"));
        }
        if (!SkillService.LANGUAGES.contains(request.language())) {
            violations.add("language.invalid");
        }
        if (request.goal() != null && request.goal().length() > GOAL_MAX) {
            violations.add("goal.invalid");
        }
        if (request.sources() == null || request.sources().isEmpty() || request.sources().size() > 500) {
            violations.add("sources.invalid");
        } else {
            HashSet<String> ids = new HashSet<>();
            for (AiSource source : request.sources()) {
                if (source == null || source.id() == null || !source.id().matches("S[1-9][0-9]{0,3}") || !ids.add(source.id()) || source.text() == null) {
                    violations.add("sources.invalid");
                    break;
                }
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
    }

    // ------------------------------------------------------------------------------------------------ execution

    private void submit(Plan plan) {
        try {
            pending.put(plan.runId(), executor.submit(() -> {
                try {
                    execute(plan);
                } finally {
                    pending.remove(plan.runId());
                    places.release();
                }
            }));
        } catch (RuntimeException rejected) {
            places.release();
            finish(plan.runId(), "failed", "ai.busy", null);
        }
    }

    private void execute(Plan plan) {
        if (!transaction.execute(status -> store.markRunning(plan.runId()))) {
            finish(plan.runId(), "cancelled", null, null);
            return;
        }
        long started = System.nanoTime();
        try {
            String key = config.key(plan.provider());
            AiAdapters.Completion completion = adapters.complete(plan.provider(), key, plan.model(), plan.prompt(), properties.timeout());
            ObjectNode content;
            try {
                content = AiFormats.parse(plan.useCase(), completion.text(), new HashSet<>(plan.sourceIds()));
            } catch (AiFormats.Invalid invalid) {
                finish(plan.runId(), "failed", invalid.code(), completion);
                LOG.info("AI run {} answered outside the format: {}", plan.runId(), invalid.code());
                return;
            }
            transaction.executeWithoutResult(status -> deliver(plan, completion, content));
        } catch (AiHttp.Failure failure) {
            boolean cancelled = "ai.cancelled".equals(failure.code());
            finish(plan.runId(), cancelled ? "cancelled" : "failed", cancelled ? null : failure.code(), null);
            LOG.info("AI run {} ended with {} after {} ms", plan.runId(), failure.code(), Duration.ofNanos(System.nanoTime() - started).toMillis());
        } catch (ConfigService.Unavailable unavailable) {
            finish(plan.runId(), "failed", unavailable.getMessage(), null);
        } catch (RuntimeException unexpected) {
            LOG.warn("AI run {} failed unexpectedly: {}", plan.runId(), unexpected.getClass().getName());
            finish(plan.runId(), "failed", "ai.internal_error", null);
        }
    }

    /** Keeps the suggestion if the execution was not cancelled and what it used is still usable (prüffall A14). */
    private void deliver(Plan plan, AiAdapters.Completion completion, ObjectNode content) {
        Run run = store.lockRun(plan.runId()).orElseThrow();
        Instant now = clock.instant();
        if (!run.open()) {
            return;
        }
        if (run.cancelRequested()) {
            store.finish(run.id(), "cancelled", null, run.truncated(), completion.inputTokens(), completion.outputTokens(), now);
            return;
        }
        String changed = configurationChanged(run);
        if (changed != null) {
            store.finish(run.id(), "failed", changed, run.truncated(), completion.inputTokens(), completion.outputTokens(), now);
            return;
        }
        ObjectNode sources = JSON.createObjectNode();
        sources.set("ids", JSON.valueToTree(plan.sourceIds()));
        sources.set("references", JSON.readTree(plan.references()));
        store.insertSuggestion(run.id(), run.companyId(), run.accountId(), JSON.writeValueAsString(sources), JSON.writeValueAsString(content), now,
            now.plus(properties.keep()));
        store.finish(run.id(), "succeeded", null, run.truncated(), completion.inputTokens(), completion.outputTokens(), now);
    }

    /** {@code null} while access, model and, for a use, the assignment still allow the execution; otherwise the code. */
    private String configurationChanged(Run run) {
        Assignment assignment = store.assignments(run.companyId()).get(run.useCase());
        boolean use = "use".equals(run.kind());
        ConfigService.Availability now = config.availability(run.companyId(), assignment, use);
        if (!now.available() || !now.model().id().equals(run.modelId())) {
            return "ai.configuration_changed";
        }
        return null;
    }

    private void finish(UUID runId, String state, String code, AiAdapters.Completion completion) {
        transaction.executeWithoutResult(status -> {
            Run run = store.lockRun(runId).orElse(null);
            if (run == null || !run.open()) {
                return;
            }
            String finalState = run.cancelRequested() && !"succeeded".equals(state) ? "cancelled" : state;
            store.finish(runId, finalState, "cancelled".equals(finalState) ? null : code, run.truncated(),
                completion == null ? null : completion.inputTokens(), completion == null ? null : completion.outputTokens(), clock.instant());
        });
    }

    // ------------------------------------------------------------------------------------------------ reading and cancelling

    /** An execution of the person; another one answers 404 like an unknown one. */
    @Transactional
    public View read(AccessContext actor, UUID runId) {
        AiAccess.internal(actor);
        Run run = store.run(runId).filter(r -> r.accountId().equals(actor.userId()) && r.companyId().equals(actor.companyId()))
            .orElseThrow(() -> ApiProblemException.of(404, "ai.run_not_found"));
        if (!"succeeded".equals(run.state())) {
            return new View(run, null, null);
        }
        Optional<AiStore.Suggestion> suggestion = store.suggestion(runId).filter(s -> s.expiresAt().isAfter(clock.instant()));
        if (suggestion.isEmpty()) {
            return new View(run, null, "ai.suggestion_expired");
        }
        String changed = configurationChanged(run);
        if (changed != null) {
            return new View(run, null, changed);
        }
        if ("use".equals(run.kind())) {
            if (run.projectId() == null || !access.mayUse(actor, run.projectId())) {
                return new View(run, null, "ai.rights_changed");
            }
            JsonNode references = JSON.readTree(suggestion.get().sources()).path("references");
            for (AiSuggestionGuard guard : guards) {
                if (guard.useCase() == run.useCase() && !guard.stillValid(actor, run.projectId(), run.objectKind(), run.objectId(), run.objectVersion(),
                        JSON.writeValueAsString(references))) {
                    return new View(run, null, "ai.source_changed");
                }
            }
        } else if (!access.holdsManage()) {
            return new View(run, null, "ai.rights_changed");
        }
        return new View(run, JSON.readTree(suggestion.get().content()), null);
    }

    /** Cancels an execution of the person that is still waiting or running; a finished one stays as it is. */
    public View cancel(AccessContext actor, UUID runId) {
        AiAccess.internal(actor);
        transaction.executeWithoutResult(status -> {
            Run run = store.lockRun(runId).filter(r -> r.accountId().equals(actor.userId()) && r.companyId().equals(actor.companyId()))
                .orElseThrow(() -> ApiProblemException.of(404, "ai.run_not_found"));
            if (run.open()) {
                store.requestCancel(runId);
                if ("queued".equals(run.state())) {
                    store.finish(runId, "cancelled", null, run.truncated(), null, null, clock.instant());
                }
            }
        });
        Future<?> future = pending.get(runId);
        if (future != null) {
            future.cancel(true);
        }
        return transaction.execute(status -> new View(store.run(runId).orElseThrow(), null, null));
    }

    // ------------------------------------------------------------------------------------------------ administration

    /** The record of the company, newest first, with {@code ai.manage}. */
    @Transactional
    public List<Run> records(Instant beforeAt, UUID beforeId, int limit) {
        UUID companyId = access.requireManage();
        return store.runs(companyId, beforeAt, beforeId, Math.max(1, Math.min(limit, 200)));
    }

    /** Executions and reported tokens per day and use case of the last days, with {@code ai.manage}. */
    @Transactional
    public List<Usage> usage(int days) {
        UUID companyId = access.requireManage();
        Instant from = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(Math.max(0, Math.min(days, 90) - 1L))
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        return store.usage(companyId, from);
    }

    /** Executions of the person and of the company today (UTC), for the display of the limits. */
    @Transactional
    public long[] countToday(UUID accountId) {
        UUID companyId = access.company();
        Instant day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new long[] { store.countSince(companyId, accountId, day), store.countSince(companyId, null, day) };
    }

    /** Suggestions of a deactivated account go with the deactivation. */
    @EventListener
    void deactivated(AccountDeactivated event) {
        store.deleteSuggestionsOf(event.accountId());
    }

    /** The clean up of the recurring task: expired suggestions and executions no process finishes any more. */
    public void cleanUp() {
        Instant now = clock.instant();
        Duration longest = properties.timeout().multipliedBy(properties.waiting() + 2L).plusMinutes(5);
        transaction.executeWithoutResult(status -> {
            int deleted = store.deleteExpired(now);
            int stale = store.failStale(now.minus(longest), now);
            if (deleted > 0 || stale > 0) {
                LOG.info("AI clean up removed {} suggestions and ended {} interrupted runs", deleted, stale);
            }
        });
    }
}
