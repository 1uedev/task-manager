package taskmanager.git.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.platform.ApiProblemException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The provider instances of the company (TASK-111, rules 2, 7 and 8). Everything here needs the company right
 * {@code git.manage}. The secret of the application is accepted only when the key of the operation is set, encrypted at once
 * and never given back. A provider is never deleted, because released repositories refer to it; removing it hides it and
 * ends every connection at once. Changing the address or the application ends the connections too, because their tokens
 * belong to the old one.
 */
@Service
public class ProviderService {
    private static final Logger LOG = LoggerFactory.getLogger(ProviderService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int NAME_MAX = 80;

    private final GitStore store;
    private final GitAccess access;
    private final GitSecrets secrets;
    private final GitProperties properties;
    private final ProviderApi api;
    private final Clock clock;
    private final TransactionTemplate transaction;

    ProviderService(GitStore store, GitAccess access, GitSecrets secrets, GitProperties properties, ProviderApi api, Clock clock,
            PlatformTransactionManager transactions) {
        this.store = store;
        this.access = access;
        this.secrets = secrets;
        this.properties = properties;
        this.api = api;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /**
     * What an administrator sets. {@code clientSecret} {@code null} keeps the stored one, an empty text removes it.
     * {@code caCertificate} {@code null} or empty means none. {@code baseUrl} counts only for a GitLab of its own.
     */
    public record Settings(String kind, String name, String baseUrl, String clientId, String clientSecret, String caCertificate) {
    }

    /** The outcome of a check: {@code null} code means the provider answered; the version when a GitLab told it. */
    public record CheckResult(boolean ok, String code, String version) {
    }

    public boolean secretKeyAvailable() {
        return secrets.available();
    }

    public boolean holdsManage() {
        return access.holdsManage();
    }

    @Transactional
    public List<Provider> list() {
        return store.providers(access.requireManage());
    }

    @Transactional
    public Provider create(UUID actor, Settings settings) {
        UUID companyId = access.requireManage();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Provider provider = validated(companyId, id, settings, null, 1, now, actor, now, actor);
        if (store.providerNameTaken(companyId, provider.name(), null)) {
            throw ApiProblemException.of(409, "git.provider_name_taken");
        }
        store.insertProvider(provider);
        store.audit(companyId, "provider", id, "provider.created", describe(provider), actor, now);
        return store.provider(companyId, id).orElseThrow();
    }

    @Transactional
    public Provider update(UUID actor, UUID id, long expectedVersion, Settings settings) {
        UUID companyId = access.requireManage();
        Provider current = store.lockProvider(companyId, id).filter(p -> !p.removed())
            .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "git.version_conflict");
        }
        Instant now = clock.instant();
        Provider changed = validated(companyId, id, settings, current, current.version() + 1, current.createdAt(), current.createdBy(), now, actor);
        if (store.providerNameTaken(companyId, changed.name(), id)) {
            throw ApiProblemException.of(409, "git.provider_name_taken");
        }
        store.updateProvider(changed);
        String operation = settings.clientSecret() == null ? "provider.changed"
            : settings.clientSecret().isEmpty() ? "provider.secret_removed" : "provider.secret_changed";
        store.audit(companyId, "provider", id, operation, describe(changed), actor, now);
        if (!changed.baseUrl().equals(current.baseUrl()) || !changed.clientId().equals(current.clientId())) {
            endConnections(companyId, id, actor, now);
        }
        return store.provider(companyId, id).orElseThrow();
    }

    @Transactional
    public void remove(UUID actor, UUID id, long expectedVersion) {
        UUID companyId = access.requireManage();
        Provider current = store.lockProvider(companyId, id).filter(p -> !p.removed())
            .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "git.version_conflict");
        }
        Instant now = clock.instant();
        store.removeProvider(id, current.version() + 1, now, actor);
        store.audit(companyId, "provider", id, "provider.removed", null, actor, now);
        endConnections(companyId, id, actor, now);
    }

    /** Asks the provider whether it answers, without a token, and reports the first failure; changes nothing. */
    public CheckResult check(UUID id) {
        Provider provider = transaction.execute(status -> {
            UUID companyId = access.requireManage();
            return store.provider(companyId, id).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
        });
        try {
            Optional<int[]> version = api.reach(provider);
            if (version.isPresent() && provider.kind() == Provider.Kind.GITLAB && version.get()[0] < ProviderApi.GITLAB_MIN_MAJOR) {
                return new CheckResult(false, "git.version_unsupported", version.get()[0] + "." + version.get()[1]);
            }
            return new CheckResult(true, null, version.map(v -> v[0] + "." + v[1]).orElse(null));
        } catch (GitHttp.Failure failure) {
            LOG.info("The check of Git provider {} failed with {}", id, failure.code());
            return new CheckResult(false, failure.code(), null);
        }
    }

    private void endConnections(UUID companyId, UUID providerId, UUID actor, Instant now) {
        for (UUID account : store.deleteConnectionsOfProvider(providerId)) {
            store.audit(companyId, "connection", account, "connection.ended", json(new Ended(providerId)), actor, now);
        }
    }

    private record Ended(UUID providerId) {
    }

    // ------------------------------------------------------------------------------------------------ rules

    private Provider validated(UUID companyId, UUID id, Settings s, Provider current, long version, Instant createdAt, UUID createdBy,
            Instant now, UUID actor) {
        List<String> violations = new ArrayList<>();
        Provider.Kind kind = current != null ? current.kind() : Provider.Kind.of(s.kind());
        if (kind == null) {
            violations.add("kind.invalid");
        } else if (current != null && s.kind() != null && Provider.Kind.of(s.kind()) != current.kind()) {
            violations.add("kind.unchangeable");
        }
        String name = clean(s.name());
        if (name == null || name.length() > NAME_MAX) {
            violations.add("name.invalid");
        }
        String baseUrl = null;
        if (kind == Provider.Kind.GITHUB) {
            baseUrl = trimmed(properties.githubUrl());
        } else if (kind == Provider.Kind.GITLAB_COM) {
            baseUrl = trimmed(properties.gitlabComUrl());
        } else if (kind == Provider.Kind.GITLAB) {
            baseUrl = baseUrl(s.baseUrl());
            if (baseUrl == null) {
                violations.add("baseUrl.invalid");
            }
        }
        String clientId = s.clientId() == null ? null : s.clientId().strip();
        if (clientId == null || clientId.isEmpty() || clientId.length() > 200 || !clientId.matches("[\\x21-\\x7e]+")) {
            violations.add("clientId.invalid");
        }
        String ca = s.caCertificate() == null || s.caCertificate().isBlank() ? null : s.caCertificate().strip();
        if (ca != null && (ca.length() > 20000 || GitHttp.certificates(ca).isEmpty())) {
            violations.add("caCertificate.invalid");
        }
        if (s.clientSecret() != null && (s.clientSecret().length() > 1000 || s.clientSecret().chars().anyMatch(Character::isISOControl))) {
            violations.add("clientSecret.invalid");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        byte[] cipher = current == null ? null : current.secretCipher();
        if (s.clientSecret() != null) {
            if (s.clientSecret().isEmpty()) {
                cipher = null;
            } else if (!secrets.available()) {
                throw ApiProblemException.of(409, "git.secret_key_missing");
            } else {
                cipher = secrets.seal(Provider.secretPurpose(id), s.clientSecret());
            }
        }
        return new Provider(id, companyId, kind, name, baseUrl, clientId, cipher, ca, version, createdAt, createdBy, now, actor, null);
    }

    /** An address of a GitLab of its own: https (http only for the tests), host, optional port and path, nothing else. */
    private String baseUrl(String raw) {
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
            boolean allowed = scheme.equals("https") || (scheme.equals("http") && properties.allowHttp());
            if (!allowed || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !text.matches("https?://[A-Za-z0-9.\\-\\[\\]:]+(/[A-Za-z0-9._~\\-/]*)?")) {
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

    private static String describe(Provider p) {
        return json(new Description(p.kind().key(), p.name(), p.baseUrl(), p.clientId(), p.secretSet(), p.caCertificate() != null));
    }

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    /** What the audit trail keeps of a provider: everything but the secret, of which only its presence. */
    private record Description(String kind, String name, String baseUrl, String clientId, boolean secretSet, boolean caCertificate) {
    }
}
