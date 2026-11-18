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
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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
    private final Map<UUID, Task> pending = new ConcurrentHashMap<>();
    private final TransactionTemplate separate;

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
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    /** One execution as it will be sent, prepared in the transaction of the start, with the versions it started with (F4). */
    private record Plan(UUID runId, UUID companyId, String kind, AiUseCase useCase, String language, AiProvider provider, AiModel model,
            AiAdapters.Prompt prompt, List<String> sourceIds, String references, String stamp) {
    }

    /** A start the rules refuse after the rights were checked (TASK-129, F12); it is recorded as refused, then answered. */
    private static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient UUID companyId;
        private final ApiProblemException problem;
        private final String reason;

        Refused(UUID companyId, ApiProblemException problem, String reason) {
            super(reason, null, false, false);
            this.companyId = companyId;
            this.problem = problem;
            this.reason = reason;
        }
    }

    private static final int WAITING = 0;
    private static final int RUNNING = 1;
    private static final int DROPPED = 2;

    /**
     * One execution and its place, managed together (TASK-129, F7). A waiting execution that is dropped leaves the line and frees
     * its place exactly once, by whoever wins the change of its state; a running one frees its place only when it really ended.
     */
    private final class Task implements Runnable {
        private final Plan plan;
        private final AtomicInteger state = new AtomicInteger(WAITING);
        private volatile FutureTask<Void> future;
        private volatile String abort;

        Task(Plan plan) {
            this.plan = plan;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(WAITING, RUNNING)) {
                return;
            }
            try {
                execute(this);
            } finally {
                pending.remove(plan.runId(), this);
                places.release();
            }
        }

        /** Takes a waiting execution out of the line; returns whether it did, and then its place is free again. */
        boolean drop() {
            if (!state.compareAndSet(WAITING, DROPPED)) {
                return false;
            }
            if (future != null) {
                executor.remove(future);
            }
            pending.remove(plan.runId(), this);
            places.release();
            return true;
        }

        /** Ends the execution with a code: a waiting one at once, a running one by cancelling its request. */
        void stop(String code) {
            abort = code;
            if (drop()) {
                finish(plan.runId(), "failed", code, null);
            } else if (future != null) {
                future.cancel(true);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ start

    @Override
    public UUID start(AccessContext actor, AiRequest request) {
        AiAccess.internal(actor);
        validate(request);
        return begin(actor.userId(), "use", request, () -> {
            access.requireUse(actor, request.projectId());
            return actor.companyId();
        }, companyId -> prepare(companyId, actor.userId(), "use", request, true, null));
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
        return begin(accountId, "test", request, access::requireManage,
            companyId -> prepare(companyId, accountId, "test", request, false, skills.forTrial(companyId, useCase, request.language())));
    }

    /**
     * Takes a place, checks the rights and prepares the execution. A start of an authorized person that the rules refuse (no
     * usable assignment, a daily limit, all places taken, too large a context) is recorded without content as {@code refused}
     * in a transaction of its own, so it stays after the rollback, and never counts against a daily limit (TASK-129, F12).
     */
    private UUID begin(UUID accountId, String kind, AiRequest request, Supplier<UUID> authorize, Function<UUID, Plan> prepare) {
        boolean outer = TransactionSynchronizationManager.isActualTransactionActive();
        if (!places.tryAcquire()) {
            UUID companyId = outer ? authorize.get() : transaction.execute(status -> authorize.get());
            recordRefused(companyId, accountId, kind, request, "ai.busy");
            throw ApiProblemException.retryAfter(503, "ai.busy", 10);
        }
        boolean handed = false;
        try {
            Plan plan;
            try {
                if (outer) {
                    plan = prepare.apply(authorize.get());
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
                    plan = transaction.execute(status -> prepare.apply(authorize.get()));
                    submit(plan);
                }
            } catch (Refused refused) {
                recordRefused(refused.companyId, accountId, kind, request, refused.reason);
                throw refused.problem;
            }
            handed = true;
            return plan.runId();
        } finally {
            if (!handed) {
                places.release();
            }
        }
    }

    private void recordRefused(UUID companyId, UUID accountId, String kind, AiRequest request, String reason) {
        Instant now = clock.instant();
        separate.executeWithoutResult(status -> store.insertRun(new Run(UUID.randomUUID(), companyId, accountId, kind, request.useCase(),
            request.language(), request.projectId(), request.objectKind(), request.objectId(), request.objectVersion(), null, null, null, null, null,
            null, "refused", reason, false, null, null, now, now, false)));
    }

    private Plan prepare(UUID companyId, UUID accountId, String kind, AiRequest request, boolean requireEnabled, SkillService.Applied skill) {
        Assignment assignment = store.assignments(companyId).get(request.useCase());
        ConfigService.Availability availability = config.availability(companyId, assignment, requireEnabled);
        if (!availability.available()) {
            throw new Refused(companyId, ApiProblemException.withViolations(409, "ai.unavailable", List.of(availability.reason())),
                availability.reason());
        }
        store.lockLimits(companyId);
        Limits limits = store.limits(companyId);
        Instant now = clock.instant();
        Instant day = LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (store.countSince(companyId, accountId, day) >= limits.personDaily()) {
            throw new Refused(companyId, ApiProblemException.of(429, "ai.daily_limit_person"), "ai.daily_limit_person");
        }
        if (store.countSince(companyId, null, day) >= limits.companyDaily()) {
            throw new Refused(companyId, ApiProblemException.of(429, "ai.daily_limit_company"), "ai.daily_limit_company");
        }
        SkillService.Applied applied = skill != null ? skill : skills.applied(companyId, request.useCase(), request.language());
        AiFrame.Built built;
        try {
            built = AiFrame.build(request.useCase(), request.language(), applied.content(), request.goal(), request.sources(),
                properties.contextCharacters(), availability.model());
        } catch (AiFrame.TooLarge tooLarge) {
            throw new Refused(companyId, ApiProblemException.of(400, "ai.context_too_large"), "ai.context_too_large");
        }
        UUID runId = UUID.randomUUID();
        AiProvider provider = availability.provider();
        AiModel model = availability.model();
        String stamp = stamp(assignment, provider, model, applied.stampLabel());
        store.insertRun(new Run(runId, companyId, accountId, kind, request.useCase(), request.language(), request.projectId(), request.objectKind(),
            request.objectId(), request.objectVersion(), applied.label(), provider.id(), model.id(), model.modelKey(), provider.dataPath().key(),
            stamp, "queued", null, built.truncated(), null, null, now, null, false));
        String references = request.references() == null ? "null" : JSON.writeValueAsString(request.references());
        return new Plan(runId, companyId, kind, request.useCase(), request.language(), provider, model, built.prompt(), built.sourceIds(),
            references, stamp);
    }

    /** The versions an execution depends on: assignment, access, model and the applied skill (TASK-129, F4). */
    static String stamp(Assignment assignment, AiProvider provider, AiModel model, String skillLabel) {
        return assignment.version() + "|" + provider.id() + ":" + provider.version() + "|" + model.id() + ":" + model.version() + "|" + skillLabel;
    }

    /**
     * {@code null} while the configuration of an execution is still the one it started with and still usable; otherwise
     * {@code ai.configuration_changed}. Turning an assignment off and on again counts as a change, because it raises the
     * version; so does a new version of the same model, another skill and a changed draft for a trial.
     */
    private String configurationChanged(UUID companyId, String kind, AiUseCase useCase, String language, String stamp) {
        if (stamp == null) {
            return "ai.configuration_changed";
        }
        boolean use = "use".equals(kind);
        Assignment assignment = store.assignments(companyId).get(useCase);
        ConfigService.Availability now = config.availability(companyId, assignment, use);
        if (!now.available()) {
            return "ai.configuration_changed";
        }
        SkillService.Applied skill = use ? skills.applied(companyId, useCase, language) : skills.forTrial(companyId, useCase, language);
        return stamp.equals(stamp(assignment, now.provider(), now.model(), skill.stampLabel())) ? null : "ai.configuration_changed";
    }

    private String configurationChanged(Run run) {
        return configurationChanged(run.companyId(), run.kind(), run.useCase(), run.language(), run.configStamp());
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
        Task task = new Task(plan);
        task.future = new FutureTask<>(task, null);
        pending.put(plan.runId(), task);
        try {
            executor.execute(task.future);
        } catch (RejectedExecutionException rejected) {
            if (task.drop()) {
                finish(plan.runId(), "failed", "ai.busy", null);
            }
        }
    }

    private void execute(Task task) {
        Plan plan = task.plan;
        if (!transaction.execute(status -> store.markRunning(plan.runId()))) {
            finish(plan.runId(), "cancelled", null, null);
            return;
        }
        String before = transaction.execute(status -> configurationChanged(plan.companyId(), plan.kind(), plan.useCase(), plan.language(),
            plan.stamp()));
        if (before != null) {
            finish(plan.runId(), "failed", before, null);
            return;
        }
        long started = System.nanoTime();
        try {
            String key = config.key(plan.provider());
            if (task.abort != null || Thread.currentThread().isInterrupted()) {
                // TASK-130, F4: a cancellation that was already taken in must stop the execution before anything is sent.
                throw new AiHttp.Failure("ai.cancelled", null);
            }
            AiAdapters.Completion completion = adapters.complete(plan.provider(), key, plan.model(), plan.prompt(), properties.timeout());
            ObjectNode content;
            try {
                if (key != null && completion.text().contains(key)) {
                    // F2: an answer that repeats the key is never kept.
                    throw new AiFormats.Invalid("ai.invalid_output");
                }
                content = AiFormats.parse(plan.useCase(), completion.text(), new HashSet<>(plan.sourceIds()));
                if (key != null && AiFormats.mentions(content, key)) {
                    // TASK-130, F2: also what only the decoding or the cleaning turns into the key is never kept.
                    throw new AiFormats.Invalid("ai.invalid_output");
                }
            } catch (AiFormats.Invalid invalid) {
                finish(plan.runId(), "failed", invalid.code(), completion);
                LOG.info("AI run {} answered outside the format: {}", plan.runId(), invalid.code());
                return;
            }
            transaction.executeWithoutResult(status -> deliver(plan, completion, content));
        } catch (AiHttp.Failure failure) {
            Thread.interrupted();
            String abort = task.abort;
            boolean cancelled = "ai.cancelled".equals(failure.code());
            if (cancelled && abort != null) {
                finish(plan.runId(), "failed", abort, null);
            } else {
                finish(plan.runId(), cancelled ? "cancelled" : "failed", cancelled ? null : failure.code(), null);
            }
            LOG.info("AI run {} ended with {} after {} ms", plan.runId(), cancelled && abort != null ? abort : failure.code(),
                Duration.ofNanos(System.nanoTime() - started).toMillis());
        } catch (ConfigService.Unavailable unavailable) {
            finish(plan.runId(), "failed", unavailable.getMessage(), null);
        } catch (RuntimeException unexpected) {
            Thread.interrupted();
            LOG.warn("AI run {} failed unexpectedly: {}", plan.runId(), unexpected.getClass().getName());
            finish(plan.runId(), "failed", task.abort != null ? task.abort : "ai.internal_error", null);
        }
    }

    /**
     * Keeps the suggestion if the execution is still open, was not cancelled and its configuration is unchanged (prüffall A14,
     * TASK-129, F4). The run row is locked first, which orders this against the deactivation of the account (F5).
     */
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

    /** Ends an open execution in a transaction of its own, also when called after the commit of another one. */
    private void finish(UUID runId, String state, String code, AiAdapters.Completion completion) {
        separate.executeWithoutResult(status -> {
            Run run = store.lockRun(runId).orElse(null);
            if (run == null || !run.open()) {
                return;
            }
            String finalState = run.cancelRequested() && !"succeeded".equals(state) ? "cancelled" : state;
            store.finish(runId, finalState, "cancelled".equals(finalState) ? null : code, run.truncated(),
                completion == null ? null : completion.inputTokens(), completion == null ? null : completion.outputTokens(), clock.instant());
        });
    }

    /**
     * After the commit of a change of the configuration the executions of the company in this process compare their versions; an
     * execution whose configuration changed ends with {@code ai.configuration_changed}, a running request is cancelled (F4).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void configurationChanged(AiConfigurationChanged event) {
        for (Task task : List.copyOf(pending.values())) {
            Plan plan = task.plan;
            if (!plan.companyId().equals(event.companyId())) {
                continue;
            }
            String changed = separate.execute(status -> configurationChanged(plan.companyId(), plan.kind(), plan.useCase(), plan.language(),
                plan.stamp()));
            if (changed != null) {
                task.stop(changed);
            }
        }
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
            boolean guarded = false;
            for (AiSuggestionGuard guard : guards) {
                if (guard.useCase() != run.useCase()) {
                    continue;
                }
                guarded = true;
                if (!guard.stillValid(actor, run.projectId(), run.objectKind(), run.objectId(), run.objectVersion(),
                        JSON.writeValueAsString(references))) {
                    return new View(run, null, "ai.source_changed");
                }
            }
            if (!guarded) {
                // F8: without a responsible guard of its use case no content leaves the foundation.
                return new View(run, null, "ai.source_unchecked");
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
        Task task = pending.get(runId);
        if (task != null && !task.drop() && task.future != null) {
            task.future.cancel(true);
        }
        return transaction.execute(status -> new View(store.run(runId).orElseThrow(), null, null));
    }

    // ------------------------------------------------------------------------------------------------ administration

    /** The record of the company, newest first, with {@code ai.manage}; one more than a page of 200 tells of a next page (H2). */
    @Transactional
    public List<Run> records(Instant beforeAt, UUID beforeId, int limit) {
        UUID companyId = access.requireManage();
        return store.runs(companyId, beforeAt, beforeId, Math.max(1, Math.min(limit, 201)));
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

    /**
     * The deactivation of an account, in its transaction (TASK-129, F5): it first ends the open executions of the person under
     * their row locks, so a late result finds them closed and is never kept, then removes the suggestions; after the commit the
     * requests of this process are cancelled.
     */
    @EventListener
    void deactivated(AccountDeactivated event) {
        List<UUID> open = store.failOpenOf(event.accountId(), "ai.account_deactivated", clock.instant());
        store.deleteSuggestionsOf(event.accountId());
        if (open.isEmpty()) {
            return;
        }
        Runnable stop = () -> open.forEach(id -> {
            Task task = pending.get(id);
            if (task != null) {
                task.stop("ai.account_deactivated");
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stop.run();
                }
            });
        } else {
            stop.run();
        }
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
