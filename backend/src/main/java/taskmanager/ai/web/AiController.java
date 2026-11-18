package taskmanager.ai.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.ai.AiUseCase;
import taskmanager.ai.internal.AiModel;
import taskmanager.ai.internal.AiProvider;
import taskmanager.ai.internal.Assignment;
import taskmanager.ai.internal.ConfigService;
import taskmanager.ai.internal.Limits;
import taskmanager.ai.internal.Run;
import taskmanager.ai.internal.RunService;
import taskmanager.ai.internal.SkillService;
import taskmanager.ai.internal.SkillVersion;
import taskmanager.platform.ApiProblemException;
import tools.jackson.databind.JsonNode;

/**
 * The AI foundation (TASK-125): the availability of the use cases and the own executions for every internal person, and for
 * {@code ai.manage} the accesses, models, skills, assignments, limits, usage and the record. API keys are write only and never
 * part of an answer; the record never holds content.
 */
@RestController
class AiController {
    private final ConfigService config;
    private final SkillService skills;
    private final RunService runs;
    private final AiRequests requests;

    AiController(ConfigService config, SkillService skills, RunService runs, AiRequests requests) {
        this.config = config;
        this.skills = skills;
        this.runs = runs;
        this.requests = requests;
    }

    // ------------------------------------------------------------------------------------------------ bodies

    record NamedBody(UUID id, String name, String kind, String dataPath) {
    }

    record UseCaseBody(String useCase, boolean available, String reason, NamedBody provider, NamedBody model) {
    }

    record TodayBody(long person, long company, int personLimit, int companyLimit) {
    }

    record StatusBody(boolean secretKey, boolean manage, List<UseCaseBody> useCases, TodayBody today) {
    }

    record RunBody(UUID id, String kind, String useCase, String language, String state, String code, boolean truncated, UUID projectId,
            String objectKind, UUID objectId, Long objectVersion, String skillVersion, UUID providerId, UUID modelId, String modelKey,
            String dataPath, Integer inputTokens, Integer outputTokens, Instant startedAt, Instant finishedAt, UUID accountId,
            JsonNode content, String invalid) {
        static RunBody of(Run r, JsonNode content, String invalid) {
            return new RunBody(r.id(), r.kind(), r.useCase().key(), r.language(), r.state(), r.code(), r.truncated(), r.projectId(),
                r.objectKind(), r.objectId(), r.objectVersion(), r.skillVersion(), r.providerId(), r.modelId(), r.modelKey(), r.dataPath(),
                r.inputTokens(), r.outputTokens(), r.startedAt(), r.finishedAt(), r.accountId(), content, invalid);
        }
    }

    record ProviderBody(UUID id, String kind, String name, String baseUrl, boolean keySet, String caCertificate, String dataPath,
            boolean enabled, long version, Instant updatedAt, UUID updatedBy) {
        static ProviderBody of(AiProvider p) {
            return new ProviderBody(p.id(), p.kind().key(), p.name(), p.baseUrl(), p.keySet(), p.caCertificate(), p.dataPath().key(), p.enabled(),
                p.version(), p.updatedAt(), p.updatedBy());
        }
    }

    record ProviderRequest(String kind, String name, String baseUrl, String apiKey, String caCertificate, String dataPath, Boolean enabled) {
        ConfigService.ProviderSettings settings() {
            return new ConfigService.ProviderSettings(kind, name, baseUrl, apiKey, caCertificate, dataPath, enabled);
        }
    }

    record ModelBody(UUID id, UUID providerId, String name, String modelKey, int contextTokens, int outputTokens, boolean enabled, long version,
            Instant updatedAt) {
        static ModelBody of(AiModel m) {
            return new ModelBody(m.id(), m.providerId(), m.name(), m.modelKey(), m.contextTokens(), m.outputTokens(), m.enabled(), m.version(),
                m.updatedAt());
        }
    }

    record ModelRequest(UUID providerId, String name, String modelKey, Integer contextTokens, Integer outputTokens, Boolean enabled) {
        ConfigService.ModelSettings settings() {
            return new ConfigService.ModelSettings(providerId, name, modelKey, contextTokens, outputTokens, enabled);
        }
    }

    record CheckBody(boolean ok, String code, Integer models) {
    }

    record AssignmentBody(String useCase, UUID modelId, boolean enabled, long version, Instant updatedAt) {
        static AssignmentBody of(Assignment a) {
            return new AssignmentBody(a.useCase().key(), a.modelId(), a.enabled(), a.version(), a.updatedAt());
        }
    }

    record AssignmentRequest(UUID modelId, Boolean enabled) {
    }

    record LimitsBody(int personDaily, int companyDaily, long version, Instant updatedAt) {
        static LimitsBody of(Limits l) {
            return new LimitsBody(l.personDaily(), l.companyDaily(), l.version(), l.updatedAt());
        }
    }

    record LimitsRequest(Integer personDaily, Integer companyDaily) {
    }

    record AppliedBody(String label, String name, String content, boolean builtin, Instant activatedAt) {
    }

    record VersionBody(int number, String label, String name, String state, Instant updatedAt, UUID updatedBy, Instant activatedAt,
            UUID activatedBy, Instant archivedAt) {
        static VersionBody of(SkillVersion v) {
            return new VersionBody(v.number(), v.label(), v.name(),
                v.state().key(), v.updatedAt(), v.updatedBy(), v.activatedAt(), v.activatedBy(), v.archivedAt());
        }
    }

    record DraftBody(int number, int revision, String name, String content, String tag, Instant updatedAt, UUID updatedBy) {
    }

    record SkillBody(String useCase, String language, AppliedBody applied, DraftBody draft, String draftTag, List<VersionBody> versions) {
        static SkillBody of(SkillService.Slot slot, boolean full) {
            SkillVersion d = slot.draft();
            SkillService.Applied a = slot.applied();
            return new SkillBody(slot.useCase().key(), slot.language(),
                new AppliedBody(a.label(), a.name(), full ? a.content() : null, a.builtin(), a.activatedAt()),
                d == null ? null : new DraftBody(d.number(), d.revision(), d.name(), full ? d.content() : null, slot.draftTag(), d.updatedAt(), d.updatedBy()),
                slot.draftTag(), full ? slot.versions().stream().map(VersionBody::of).toList() : List.of());
        }
    }

    record DraftRequest(String name, String content) {
    }

    record TrialRequest(String sample, String goal) {
    }

    record StartedBody(UUID runId) {
    }

    record UsageBody(LocalDate day, String useCase, long runs, long succeeded, long inputTokens, long outputTokens) {
    }

    record RecordsBody(List<RunBody> runs, String next) {
    }

    // ------------------------------------------------------------------------------------------------ every internal person

    @GetMapping("/api/v1/ai/status")
    StatusBody status() {
        UUID account = requests.person().accountId();
        List<UseCaseBody> useCases = new ArrayList<>();
        for (ConfigService.Availability a : AiRequests.retrying(config::availability)) {
            useCases.add(new UseCaseBody(a.useCase().key(), a.available(), a.reason(),
                a.provider() == null ? null : new NamedBody(a.provider().id(), a.provider().name(), a.provider().kind().key(), a.provider().dataPath().key()),
                a.model() == null ? null : new NamedBody(a.model().id(), a.model().name(), null, null)));
        }
        long[] today = AiRequests.retrying(() -> runs.countToday(account));
        Limits limits = AiRequests.retrying(config::limitsForStatus);
        return new StatusBody(config.secretKeyAvailable(), config.holdsManage(), useCases,
            new TodayBody(today[0], today[1], limits.personDaily(), limits.companyDaily()));
    }

    @GetMapping("/api/v1/ai/runs/{id}")
    ResponseEntity<RunBody> run(@PathVariable UUID id) {
        var actor = requests.actor();
        RunService.View view = AiRequests.retrying(() -> runs.read(actor, id));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(RunBody.of(view.run(), view.content(), view.invalid()));
    }

    @DeleteMapping("/api/v1/ai/runs/{id}")
    RunBody cancel(@PathVariable UUID id) {
        var actor = requests.actor();
        RunService.View view = AiRequests.retrying(() -> runs.cancel(actor, id));
        return RunBody.of(view.run(), null, null);
    }

    // ------------------------------------------------------------------------------------------------ accesses

    @GetMapping("/api/v1/ai/admin/providers")
    List<ProviderBody> providers() {
        requests.administrator();
        return AiRequests.retrying(config::providers).stream().map(ProviderBody::of).toList();
    }

    @PostMapping("/api/v1/ai/admin/providers")
    ResponseEntity<ProviderBody> createProvider(@RequestBody ProviderRequest body) {
        UUID actor = requests.administrator().accountId();
        AiProvider provider = AiRequests.retrying(() -> config.createProvider(actor, required(body).settings()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(AiRequests.etag(provider.version())).body(ProviderBody.of(provider));
    }

    @PutMapping("/api/v1/ai/admin/providers/{id}")
    ResponseEntity<ProviderBody> updateProvider(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody ProviderRequest body) {
        UUID actor = requests.administrator().accountId();
        long version = AiRequests.expectedVersion(ifMatch);
        AiProvider provider = AiRequests.retrying(() -> config.updateProvider(actor, id, version, required(body).settings()));
        return ResponseEntity.ok().eTag(AiRequests.etag(provider.version())).body(ProviderBody.of(provider));
    }

    @DeleteMapping("/api/v1/ai/admin/providers/{id}")
    ResponseEntity<Void> removeProvider(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        UUID actor = requests.administrator().accountId();
        long version = AiRequests.expectedVersion(ifMatch);
        AiRequests.retrying(() -> {
            config.removeProvider(actor, id, version);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/ai/admin/providers/{id}/check")
    CheckBody checkProvider(@PathVariable UUID id) {
        requests.administrator();
        ConfigService.CheckResult result = AiRequests.retrying(() -> config.checkProvider(id));
        return new CheckBody(result.ok(), result.code(), result.models());
    }

    // ------------------------------------------------------------------------------------------------ models

    @GetMapping("/api/v1/ai/admin/models")
    List<ModelBody> models() {
        requests.administrator();
        return AiRequests.retrying(config::models).stream().map(ModelBody::of).toList();
    }

    @PostMapping("/api/v1/ai/admin/models")
    ResponseEntity<ModelBody> createModel(@RequestBody ModelRequest body) {
        UUID actor = requests.administrator().accountId();
        AiModel model = AiRequests.retrying(() -> config.createModel(actor, required(body).settings()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(AiRequests.etag(model.version())).body(ModelBody.of(model));
    }

    @PutMapping("/api/v1/ai/admin/models/{id}")
    ResponseEntity<ModelBody> updateModel(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody ModelRequest body) {
        UUID actor = requests.administrator().accountId();
        long version = AiRequests.expectedVersion(ifMatch);
        AiModel model = AiRequests.retrying(() -> config.updateModel(actor, id, version, required(body).settings()));
        return ResponseEntity.ok().eTag(AiRequests.etag(model.version())).body(ModelBody.of(model));
    }

    @DeleteMapping("/api/v1/ai/admin/models/{id}")
    ResponseEntity<Void> removeModel(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        UUID actor = requests.administrator().accountId();
        long version = AiRequests.expectedVersion(ifMatch);
        AiRequests.retrying(() -> {
            config.removeModel(actor, id, version);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/ai/admin/models/{id}/check")
    CheckBody checkModel(@PathVariable UUID id) {
        requests.administrator();
        ConfigService.CheckResult result = AiRequests.retrying(() -> config.checkModel(id));
        return new CheckBody(result.ok(), result.code(), result.models());
    }

    // ------------------------------------------------------------------------------------------------ assignments and limits

    @GetMapping("/api/v1/ai/admin/assignments")
    List<AssignmentBody> assignments() {
        requests.administrator();
        Map<AiUseCase, Assignment> all = AiRequests.retrying(config::assignments);
        return all.values().stream().map(AssignmentBody::of).toList();
    }

    @PutMapping("/api/v1/ai/admin/assignments/{useCase}")
    ResponseEntity<AssignmentBody> assign(@PathVariable String useCase, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody AssignmentRequest body) {
        UUID actor = requests.administrator().accountId();
        AiUseCase target = useCase(useCase);
        long version = AiRequests.expectedVersion(ifMatch);
        AssignmentRequest request = required(body);
        Assignment saved = AiRequests.retrying(() -> config.assign(actor, target, version, request.modelId(), Boolean.TRUE.equals(request.enabled())));
        return ResponseEntity.ok().eTag(AiRequests.etag(saved.version())).body(AssignmentBody.of(saved));
    }

    @GetMapping("/api/v1/ai/admin/limits")
    ResponseEntity<LimitsBody> limits() {
        requests.administrator();
        Limits limits = AiRequests.retrying(config::limits);
        return ResponseEntity.ok().eTag(AiRequests.etag(limits.version())).body(LimitsBody.of(limits));
    }

    @PutMapping("/api/v1/ai/admin/limits")
    ResponseEntity<LimitsBody> saveLimits(@RequestHeader(value = "If-Match", required = false) String ifMatch, @RequestBody LimitsRequest body) {
        UUID actor = requests.administrator().accountId();
        long version = AiRequests.expectedVersion(ifMatch);
        LimitsRequest request = required(body);
        Limits limits = AiRequests.retrying(() -> config.saveLimits(actor, version, request.personDaily(), request.companyDaily()));
        return ResponseEntity.ok().eTag(AiRequests.etag(limits.version())).body(LimitsBody.of(limits));
    }

    // ------------------------------------------------------------------------------------------------ skills

    @GetMapping("/api/v1/ai/admin/skills")
    List<SkillBody> skills() {
        requests.administrator();
        return AiRequests.retrying(skills::slots).stream().map(slot -> SkillBody.of(slot, false)).toList();
    }

    @GetMapping("/api/v1/ai/admin/skills/{useCase}/{language}")
    ResponseEntity<SkillBody> skill(@PathVariable String useCase, @PathVariable String language) {
        requests.administrator();
        SkillService.Slot slot = AiRequests.retrying(() -> skills.slot(useCase(useCase), language));
        return ResponseEntity.ok().eTag(AiRequests.etag(slot.draftTag())).body(SkillBody.of(slot, true));
    }

    @PutMapping("/api/v1/ai/admin/skills/{useCase}/{language}/draft")
    ResponseEntity<SkillBody> saveDraft(@PathVariable String useCase, @PathVariable String language,
            @RequestHeader(value = "If-Match", required = false) String ifMatch, @RequestBody DraftRequest body) {
        UUID actor = requests.administrator().accountId();
        AiUseCase target = useCase(useCase);
        String tag = AiRequests.expectedTag(ifMatch);
        DraftRequest request = required(body);
        SkillService.Slot slot = AiRequests.retrying(() -> skills.saveDraft(actor, target, language, tag, request.name(), request.content()));
        return ResponseEntity.ok().eTag(AiRequests.etag(slot.draftTag())).body(SkillBody.of(slot, true));
    }

    @DeleteMapping("/api/v1/ai/admin/skills/{useCase}/{language}/draft")
    ResponseEntity<SkillBody> discardDraft(@PathVariable String useCase, @PathVariable String language,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        UUID actor = requests.administrator().accountId();
        AiUseCase target = useCase(useCase);
        String tag = AiRequests.expectedTag(ifMatch);
        SkillService.Slot slot = AiRequests.retrying(() -> skills.discardDraft(actor, target, language, tag));
        return ResponseEntity.ok().eTag(AiRequests.etag(slot.draftTag())).body(SkillBody.of(slot, true));
    }

    @PostMapping("/api/v1/ai/admin/skills/{useCase}/{language}/activate")
    ResponseEntity<SkillBody> activate(@PathVariable String useCase, @PathVariable String language,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        UUID actor = requests.administrator().accountId();
        AiUseCase target = useCase(useCase);
        String tag = AiRequests.expectedTag(ifMatch);
        SkillService.Slot slot = AiRequests.retrying(() -> skills.activate(actor, target, language, tag));
        return ResponseEntity.ok().eTag(AiRequests.etag(slot.draftTag())).body(SkillBody.of(slot, true));
    }

    @PostMapping("/api/v1/ai/admin/skills/{useCase}/{language}/restore")
    ResponseEntity<SkillBody> restore(@PathVariable String useCase, @PathVariable String language) {
        UUID actor = requests.administrator().accountId();
        AiUseCase target = useCase(useCase);
        SkillService.Slot slot = AiRequests.retrying(() -> skills.restore(actor, target, language));
        return ResponseEntity.ok().eTag(AiRequests.etag(slot.draftTag())).body(SkillBody.of(slot, true));
    }

    @PostMapping("/api/v1/ai/admin/skills/{useCase}/{language}/trial")
    ResponseEntity<StartedBody> trial(@PathVariable String useCase, @PathVariable String language, @RequestBody TrialRequest body) {
        UUID actor = requests.administrator().accountId();
        AiUseCase target = useCase(useCase);
        TrialRequest request = required(body);
        UUID runId = AiRequests.retrying(() -> runs.trial(actor, target, language, request.sample(), request.goal()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new StartedBody(runId));
    }

    // ------------------------------------------------------------------------------------------------ usage and record

    @GetMapping("/api/v1/ai/admin/usage")
    List<UsageBody> usage(@RequestParam(defaultValue = "30") int days) {
        requests.administrator();
        return AiRequests.retrying(() -> runs.usage(days)).stream()
            .map(u -> new UsageBody(u.day(), u.useCase(), u.runs(), u.succeeded(), u.inputTokens(), u.outputTokens())).toList();
    }

    @GetMapping("/api/v1/ai/admin/runs")
    RecordsBody records(@RequestParam(required = false) String before, @RequestParam(defaultValue = "50") int limit) {
        requests.administrator();
        Instant beforeAt = null;
        UUID beforeId = null;
        if (before != null && !before.isBlank()) {
            int split = before.indexOf('_');
            try {
                beforeAt = Instant.parse(before.substring(0, split));
                beforeId = UUID.fromString(before.substring(split + 1));
            } catch (RuntimeException invalid) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("before.invalid"));
            }
        }
        int size = Math.max(1, Math.min(limit, 200));
        Instant at = beforeAt;
        UUID id = beforeId;
        List<Run> page = AiRequests.retrying(() -> runs.records(at, id, size + 1));
        String next = null;
        if (page.size() > size) {
            Run last = page.get(size - 1);
            next = last.startedAt() + "_" + last.id();
            page = page.subList(0, size);
        }
        return new RecordsBody(page.stream().map(run -> RunBody.of(run, null, null)).toList(), next);
    }

    private static AiUseCase useCase(String key) {
        return AiUseCase.of(key).orElseThrow(() -> ApiProblemException.of(404, "ai.use_case_not_found"));
    }

    private static <T> T required(T body) {
        if (body == null) {
            throw ApiProblemException.of(400, "request.invalid");
        }
        return body;
    }
}
