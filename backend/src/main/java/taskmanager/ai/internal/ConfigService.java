package taskmanager.ai.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.ai.AiUseCase;
import taskmanager.platform.ApiProblemException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Provider accesses, models, the assignment of a model per use case and the daily limits (TASK-125, rules 2 to 5 and 11).
 * Everything that changes needs the company right {@code ai.manage}. Saving never calls a provider; only the explicit check
 * does, and it reads no more than the list of models (rule 3). The API key is accepted only when the key of the operation is
 * set, encrypted at once and never given back. A removed access or model stays for the record and leaves its assignments
 * without a model, so a use case never moves to another model by itself (rule 4).
 */
@Service
public class ConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int NAME_MAX = 80;

    private final AiStore store;
    private final AiAccess access;
    private final AiSecrets secrets;
    private final AiProperties properties;
    private final AiAdapters adapters;
    private final Clock clock;
    private final TransactionTemplate transaction;

    ConfigService(AiStore store, AiAccess access, AiSecrets secrets, AiProperties properties, AiAdapters adapters, Clock clock,
            PlatformTransactionManager transactions) {
        this.store = store;
        this.access = access;
        this.secrets = secrets;
        this.properties = properties;
        this.adapters = adapters;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** What an administrator sets for an access. {@code apiKey} {@code null} keeps the stored one, an empty text removes it. */
    public record ProviderSettings(String kind, String name, String baseUrl, String apiKey, String caCertificate, String dataPath,
            Boolean enabled) {
    }

    public record ModelSettings(UUID providerId, String name, String modelKey, Integer contextTokens, Integer outputTokens, Boolean enabled) {
    }

    /** The outcome of a check: {@code null} code means the provider answered (and knows the model). */
    public record CheckResult(boolean ok, String code, Integer models) {
    }

    /** Whether a use case can run now, and if not why; with the access and model it would use. */
    public record Availability(AiUseCase useCase, boolean available, String reason, AiProvider provider, AiModel model) {
    }

    public boolean secretKeyAvailable() {
        return secrets.available();
    }

    public boolean holdsManage() {
        return access.holdsManage();
    }

    // ------------------------------------------------------------------------------------------------ accesses

    @Transactional
    public List<AiProvider> providers() {
        return store.providers(access.requireManage());
    }

    @Transactional
    public AiProvider createProvider(UUID actor, ProviderSettings settings) {
        UUID companyId = access.requireManage();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        AiProvider provider = validated(companyId, id, settings, null, 1, now, actor, now, actor);
        if (store.providerNameTaken(companyId, provider.name(), null)) {
            throw ApiProblemException.of(409, "ai.provider_name_taken");
        }
        store.insertProvider(provider);
        store.audit(companyId, "provider", id.toString(), "provider.created", describe(provider), actor, now);
        return store.provider(companyId, id).orElseThrow();
    }

    @Transactional
    public AiProvider updateProvider(UUID actor, UUID id, long expectedVersion, ProviderSettings settings) {
        UUID companyId = access.requireManage();
        AiProvider current = store.lockProvider(companyId, id).filter(p -> !p.removed())
            .orElseThrow(() -> ApiProblemException.of(404, "ai.provider_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        Instant now = clock.instant();
        AiProvider changed = validated(companyId, id, settings, current, current.version() + 1, current.createdAt(), current.createdBy(), now,
            actor);
        if (store.providerNameTaken(companyId, changed.name(), id)) {
            throw ApiProblemException.of(409, "ai.provider_name_taken");
        }
        store.updateProvider(changed);
        String operation = settings.apiKey() == null ? "provider.changed" : settings.apiKey().isEmpty() ? "provider.key_removed" : "provider.key_changed";
        store.audit(companyId, "provider", id.toString(), operation, describe(changed), actor, now);
        return store.provider(companyId, id).orElseThrow();
    }

    @Transactional
    public void removeProvider(UUID actor, UUID id, long expectedVersion) {
        UUID companyId = access.requireManage();
        AiProvider current = store.lockProvider(companyId, id).filter(p -> !p.removed())
            .orElseThrow(() -> ApiProblemException.of(404, "ai.provider_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        Instant now = clock.instant();
        store.removeProvider(id, current.version() + 1, now, actor);
        store.audit(companyId, "provider", id.toString(), "provider.removed", null, actor, now);
        List<UUID> models = store.removeModelsOfProvider(id, now, actor);
        for (UUID model : models) {
            store.audit(companyId, "model", model.toString(), "model.removed", null, actor, now);
        }
        for (String useCase : store.clearAssignments(models, now, actor)) {
            store.audit(companyId, "assignment", useCase, "assignment.cleared", null, actor, now);
        }
    }

    /** Asks the access for its models; changes nothing. */
    public CheckResult checkProvider(UUID id) {
        AiProvider provider = transaction.execute(status -> {
            UUID companyId = access.requireManage();
            return store.provider(companyId, id).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "ai.provider_not_found"));
        });
        try {
            List<String> models = adapters.models(provider, key(provider), properties.checkTimeout());
            return new CheckResult(true, null, models.size());
        } catch (AiHttp.Failure failure) {
            LOG.info("The check of AI provider {} failed with {}", id, failure.code());
            return new CheckResult(false, failure.code(), null);
        } catch (Unavailable unavailable) {
            return new CheckResult(false, unavailable.getMessage(), null);
        }
    }

    // ------------------------------------------------------------------------------------------------ models

    @Transactional
    public List<AiModel> models() {
        return store.models(access.requireManage());
    }

    @Transactional
    public AiModel createModel(UUID actor, ModelSettings settings) {
        UUID companyId = access.requireManage();
        if (settings.providerId() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("providerId.invalid"));
        }
        AiProvider provider = store.lockProvider(companyId, settings.providerId()).filter(p -> !p.removed())
            .orElseThrow(() -> ApiProblemException.of(404, "ai.provider_not_found"));
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        AiModel model = validated(companyId, id, provider.id(), settings, null, 1, now, actor, now, actor);
        if (store.modelNameTaken(provider.id(), model.name(), null)) {
            throw ApiProblemException.of(409, "ai.model_name_taken");
        }
        store.insertModel(model);
        store.audit(companyId, "model", id.toString(), "model.created", describe(model), actor, now);
        return store.model(companyId, id).orElseThrow();
    }

    @Transactional
    public AiModel updateModel(UUID actor, UUID id, long expectedVersion, ModelSettings settings) {
        UUID companyId = access.requireManage();
        AiModel current = store.lockModel(companyId, id).filter(m -> !m.removed())
            .orElseThrow(() -> ApiProblemException.of(404, "ai.model_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        if (settings.providerId() != null && !settings.providerId().equals(current.providerId())) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("providerId.unchangeable"));
        }
        Instant now = clock.instant();
        AiModel changed = validated(companyId, id, current.providerId(), settings, current, current.version() + 1, current.createdAt(),
            current.createdBy(), now, actor);
        if (store.modelNameTaken(current.providerId(), changed.name(), id)) {
            throw ApiProblemException.of(409, "ai.model_name_taken");
        }
        store.updateModel(changed);
        store.audit(companyId, "model", id.toString(), "model.changed", describe(changed), actor, now);
        return store.model(companyId, id).orElseThrow();
    }

    @Transactional
    public void removeModel(UUID actor, UUID id, long expectedVersion) {
        UUID companyId = access.requireManage();
        AiModel current = store.lockModel(companyId, id).filter(m -> !m.removed())
            .orElseThrow(() -> ApiProblemException.of(404, "ai.model_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        Instant now = clock.instant();
        store.removeModel(id, current.version() + 1, now, actor);
        store.audit(companyId, "model", id.toString(), "model.removed", null, actor, now);
        for (String useCase : store.clearAssignments(List.of(id), now, actor)) {
            store.audit(companyId, "assignment", useCase, "assignment.cleared", null, actor, now);
        }
    }

    /** Asks the access whether it offers the model; changes nothing. */
    public CheckResult checkModel(UUID id) {
        record Target(AiProvider provider, AiModel model) {
        }
        Target target = transaction.execute(status -> {
            UUID companyId = access.requireManage();
            AiModel model = store.model(companyId, id).filter(m -> !m.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "ai.model_not_found"));
            return new Target(store.provider(companyId, model.providerId()).orElseThrow(), model);
        });
        try {
            List<String> names = adapters.models(target.provider(), key(target.provider()), properties.checkTimeout());
            boolean known = names.stream().anyMatch(name -> AiAdapters.matches(target.provider().kind(), name, target.model().modelKey()));
            return new CheckResult(known, known ? null : "ai.model_unknown", names.size());
        } catch (AiHttp.Failure failure) {
            LOG.info("The check of AI model {} failed with {}", id, failure.code());
            return new CheckResult(false, failure.code(), null);
        } catch (Unavailable unavailable) {
            return new CheckResult(false, unavailable.getMessage(), null);
        }
    }

    // ------------------------------------------------------------------------------------------------ assignments and limits

    @Transactional
    public Map<AiUseCase, Assignment> assignments() {
        return store.assignments(access.requireManage());
    }

    @Transactional
    public Assignment assign(UUID actor, AiUseCase useCase, long expectedVersion, UUID modelId, boolean enabled) {
        UUID companyId = access.requireManage();
        long current = store.lockAssignment(companyId, useCase).map(Assignment::version).orElse(0L);
        if (current != expectedVersion) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        if (modelId != null) {
            AiModel model = store.lockModel(companyId, modelId).filter(m -> !m.removed())
                .orElseThrow(() -> ApiProblemException.withViolations(400, "request.invalid", List.of("modelId.invalid")));
            store.lockProvider(companyId, model.providerId()).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.withViolations(400, "request.invalid", List.of("modelId.invalid")));
        } else if (enabled) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("modelId.required"));
        }
        Instant now = clock.instant();
        Assignment saved = new Assignment(useCase, modelId, enabled, current + 1, now, actor);
        store.saveAssignment(companyId, saved);
        store.audit(companyId, "assignment", useCase.key(), "assignment.changed", json(new AssignmentDescription(modelId, enabled)), actor, now);
        return saved;
    }

    @Transactional
    public Limits limits() {
        return store.limits(access.requireManage());
    }

    /** The limits for the status every internal person sees; needs no right. */
    @Transactional
    public Limits limitsForStatus() {
        return store.limits(access.company());
    }

    @Transactional
    public Limits saveLimits(UUID actor, long expectedVersion, Integer personDaily, Integer companyDaily) {
        UUID companyId = access.requireManage();
        store.lockLimits(companyId);
        Limits current = store.limits(companyId);
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "ai.version_conflict");
        }
        List<String> violations = new ArrayList<>();
        if (personDaily == null || personDaily < 0 || personDaily > 100_000) {
            violations.add("personDaily.invalid");
        }
        if (companyDaily == null || companyDaily < 0 || companyDaily > 1_000_000) {
            violations.add("companyDaily.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        Limits saved = new Limits(personDaily, companyDaily, current.version() + 1, now, actor);
        store.saveLimits(companyId, saved);
        store.audit(companyId, "limits", companyId.toString(), "limits.changed", json(new LimitsDescription(personDaily, companyDaily)), actor, now);
        return saved;
    }

    // ------------------------------------------------------------------------------------------------ availability

    /** Whether each use case can run, for every internal person (the interface greys out what cannot, rule 4). */
    @Transactional
    public List<Availability> availability() {
        UUID companyId = access.company();
        Map<AiUseCase, Assignment> assignments = store.assignments(companyId);
        List<Availability> result = new ArrayList<>();
        for (AiUseCase useCase : AiUseCase.values()) {
            result.add(availability(companyId, assignments.get(useCase), true));
        }
        return result;
    }

    /** The availability of one assignment; {@code requireEnabled} false is the trial of a skill, which may run before. */
    Availability availability(UUID companyId, Assignment assignment, boolean requireEnabled) {
        AiUseCase useCase = assignment.useCase();
        if (assignment.modelId() == null) {
            return new Availability(useCase, false, "ai.not_assigned", null, null);
        }
        AiModel model = store.model(companyId, assignment.modelId()).orElse(null);
        AiProvider provider = model == null ? null : store.provider(companyId, model.providerId()).orElse(null);
        if (model == null || model.removed() || provider == null || provider.removed()) {
            return new Availability(useCase, false, "ai.not_assigned", null, null);
        }
        String reason = null;
        if (requireEnabled && !assignment.enabled()) {
            reason = "ai.disabled";
        } else if (!provider.enabled()) {
            reason = "ai.provider_disabled";
        } else if (!model.enabled()) {
            reason = "ai.model_disabled";
        } else if (provider.keyRequired() && !provider.keySet()) {
            reason = "ai.key_missing";
        } else if (provider.keySet() && !secrets.available()) {
            reason = "ai.secret_key_missing";
        }
        return new Availability(useCase, reason == null, reason, provider, model);
    }

    /** No call is possible: a key is missing or does not open. */
    static final class Unavailable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unavailable(String code) {
            super(code);
        }
    }

    /** The API key of an access in clear, only for the call itself; {@code null} for Ollama without key. */
    String key(AiProvider provider) {
        if (!provider.keySet()) {
            if (provider.keyRequired()) {
                throw new Unavailable("ai.key_missing");
            }
            return null;
        }
        try {
            return secrets.open(AiProvider.keyPurpose(provider.id()), provider.keyCipher());
        } catch (AiSecrets.KeyMissing missing) {
            throw new Unavailable("ai.secret_key_missing");
        } catch (AiSecrets.Unreadable unreadable) {
            throw new Unavailable("ai.key_unreadable");
        }
    }

    // ------------------------------------------------------------------------------------------------ rules

    private AiProvider validated(UUID companyId, UUID id, ProviderSettings s, AiProvider current, long version, Instant createdAt,
            UUID createdBy, Instant now, UUID actor) {
        List<String> violations = new ArrayList<>();
        AiProvider.Kind kind = current != null ? current.kind() : AiProvider.Kind.of(s.kind());
        if (kind == null) {
            violations.add("kind.invalid");
        } else if (current != null && s.kind() != null && AiProvider.Kind.of(s.kind()) != current.kind()) {
            violations.add("kind.unchangeable");
        }
        String name = clean(s.name());
        if (name == null || name.length() > NAME_MAX) {
            violations.add("name.invalid");
        }
        AiProvider.DataPath path = AiProvider.DataPath.of(s.dataPath());
        String baseUrl = null;
        if (kind == AiProvider.Kind.ANTHROPIC) {
            baseUrl = trimmed(properties.anthropicUrl());
            path = AiProvider.DataPath.EXTERNAL;
        } else if (kind == AiProvider.Kind.OPENAI && (s.baseUrl() == null || s.baseUrl().isBlank()
                || trimmed(s.baseUrl().strip()).equals(trimmed(properties.openaiUrl())))) {
            baseUrl = trimmed(properties.openaiUrl());
            path = AiProvider.DataPath.EXTERNAL;
        } else if (kind != null) {
            if (path == null) {
                violations.add("dataPath.invalid");
            }
            baseUrl = baseUrl(s.baseUrl(), path == AiProvider.DataPath.LOCAL);
            if (baseUrl == null) {
                violations.add("baseUrl.invalid");
            }
        }
        String ca = s.caCertificate() == null || s.caCertificate().isBlank() ? null : s.caCertificate().strip();
        if (ca != null && (ca.length() > 20000 || AiHttp.certificates(ca).isEmpty())) {
            violations.add("caCertificate.invalid");
        }
        if (s.apiKey() != null && (s.apiKey().length() > 1000 || s.apiKey().chars().anyMatch(Character::isISOControl))) {
            violations.add("apiKey.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        byte[] cipher = current == null ? null : current.keyCipher();
        if (s.apiKey() != null) {
            if (s.apiKey().isEmpty()) {
                cipher = null;
            } else if (!secrets.available()) {
                throw ApiProblemException.of(409, "ai.secret_key_missing");
            } else {
                cipher = secrets.seal(AiProvider.keyPurpose(id), s.apiKey().strip());
            }
        }
        boolean enabled = s.enabled() != null ? s.enabled() : current == null || current.enabled();
        return new AiProvider(id, companyId, kind, name, baseUrl, cipher, ca, path, enabled, version, createdAt, createdBy, now, actor, null);
    }

    private AiModel validated(UUID companyId, UUID id, UUID providerId, ModelSettings s, AiModel current, long version, Instant createdAt,
            UUID createdBy, Instant now, UUID actor) {
        List<String> violations = new ArrayList<>();
        String name = clean(s.name());
        if (name == null || name.length() > NAME_MAX) {
            violations.add("name.invalid");
        }
        String key = s.modelKey() == null ? null : s.modelKey().strip();
        if (key == null || key.isEmpty() || key.length() > 200 || !key.matches("[\\x21-\\x7e]+")) {
            violations.add("modelKey.invalid");
        }
        Integer context = s.contextTokens();
        if (context == null || context < 1000 || context > 10_000_000) {
            violations.add("contextTokens.invalid");
        }
        Integer output = s.outputTokens();
        if (output == null || output < 100 || output > 100_000 || (context != null && output >= context)) {
            violations.add("outputTokens.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        boolean enabled = s.enabled() != null ? s.enabled() : current == null || current.enabled();
        return new AiModel(id, companyId, providerId, name, key, context, output, enabled, version, createdAt, createdBy, now, actor, null);
    }

    /** An address of an access: https, or http for a local data path; host, optional port and path, nothing else. */
    private String baseUrl(String raw, boolean local) {
        if (raw == null) {
            return null;
        }
        String text = trimmed(raw.strip());
        if (text.length() < 8 || text.length() > 200) {
            return null;
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean allowed = scheme.equals("https") || (scheme.equals("http") && (local || properties.allowHttp()));
            if (!allowed || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !text.matches("(?i)https?://[A-Za-z0-9.\\-\\[\\]:]+(/[A-Za-z0-9._~\\-/]*)?")) {
                return null;
            }
            return scheme + text.substring(scheme.length());
        } catch (URISyntaxException invalid) {
            return null;
        }
    }

    private static String trimmed(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    static String clean(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(point -> !Character.isISOControl(point) && Character.getType(point) != Character.FORMAT).forEach(out::appendCodePoint);
        String result = out.toString().strip();
        return result.isEmpty() ? null : result;
    }

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    private static String describe(AiProvider p) {
        return json(new ProviderDescription(p.kind().key(), p.name(), p.baseUrl(), p.dataPath().key(), p.enabled(), p.keySet(),
            p.caCertificate() != null));
    }

    private static String describe(AiModel m) {
        return json(new ModelDescription(m.providerId(), m.name(), m.modelKey(), m.contextTokens(), m.outputTokens(), m.enabled()));
    }

    Optional<AiProvider> provider(UUID companyId, UUID id) {
        return store.provider(companyId, id);
    }

    /** What the audit trail keeps of an access: everything but the key, of which only its presence. */
    private record ProviderDescription(String kind, String name, String baseUrl, String dataPath, boolean enabled, boolean keySet,
            boolean caCertificate) {
    }

    private record ModelDescription(UUID providerId, String name, String modelKey, int contextTokens, int outputTokens, boolean enabled) {
    }

    private record AssignmentDescription(UUID modelId, boolean enabled) {
    }

    private record LimitsDescription(int personDaily, int companyDaily) {
    }
}
