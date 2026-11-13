package taskmanager.git.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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
import taskmanager.git.internal.ConnectionService;
import taskmanager.git.internal.GitProperties;
import taskmanager.git.internal.Provider;
import taskmanager.git.internal.ProviderService;
import taskmanager.git.internal.ReleaseService;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.platform.ApiProblemException;

/**
 * The Git link (TASK-111): provider instances for {@code git.manage}, the own connections of every internal person with the
 * OAuth callback, and the repositories released per project. Secrets of the applications and tokens are write only and
 * never part of an answer.
 */
@RestController
class GitController {
    private final ProviderService providers;
    private final ConnectionService connections;
    private final ReleaseService releases;
    private final GitProperties properties;
    private final GitRequests requests;
    private final AccessContexts identity;

    GitController(ProviderService providers, ConnectionService connections, ReleaseService releases, GitProperties properties,
            GitRequests requests, AccessContexts identity) {
        this.providers = providers;
        this.connections = connections;
        this.releases = releases;
        this.properties = properties;
        this.requests = requests;
        this.identity = identity;
    }

    record StatusBody(boolean secretKey, String callbackUrl, boolean manage) {
    }

    record ProviderBody(UUID id, String kind, String name, String baseUrl, String connection, Instant connectedAt) {
    }

    record AdminProviderBody(UUID id, String kind, String name, String baseUrl, String clientId, boolean secretSet, String caCertificate,
            long version, Instant updatedAt, UUID updatedBy) {
        static AdminProviderBody of(Provider p) {
            return new AdminProviderBody(p.id(), p.kind().key(), p.name(), p.baseUrl(), p.clientId(), p.secretSet(), p.caCertificate(),
                p.version(), p.updatedAt(), p.updatedBy());
        }
    }

    record ProviderRequest(String kind, String name, String baseUrl, String clientId, String clientSecret, String caCertificate) {
        ProviderService.Settings settings() {
            return new ProviderService.Settings(kind, name, baseUrl, clientId, clientSecret, caCertificate);
        }
    }

    record CheckBody(boolean ok, String code, String version) {
    }

    record ConnectBody(String authorizeUrl) {
    }

    record RepositoryBody(String externalId, String name, String path, String url) {
    }

    record ReleaseBody(UUID id, UUID providerId, String providerName, String providerKind, String externalId, boolean available,
            String reason, RepositoryBody repository, Instant releasedAt) {
    }

    record ReleasesBody(List<ReleaseBody> repositories, boolean manageable) {
    }

    record ReleaseRequest(UUID providerId, String externalId) {
    }

    @GetMapping("/api/v1/git/status")
    StatusBody status() {
        requests.person();
        return new StatusBody(providers.secretKeyAvailable(), properties.callbackUrl(), providers.holdsManage());
    }

    // ------------------------------------------------------------------------------------------------ own connections

    @GetMapping("/api/v1/git/providers")
    List<ProviderBody> providers() {
        UUID account = requests.person().accountId();
        return GitRequests.retrying(() -> connections.providers(account)).stream()
            .map(view -> new ProviderBody(view.provider().id(), view.provider().kind().key(), view.provider().name(), view.provider().baseUrl(),
                view.connection(), view.connectedAt()))
            .toList();
    }

    @PostMapping("/api/v1/git/providers/{id}/connect")
    ConnectBody connect(@PathVariable UUID id) {
        UUID account = requests.person().accountId();
        return new ConnectBody(GitRequests.retrying(() -> connections.start(account, id)));
    }

    @DeleteMapping("/api/v1/git/providers/{id}/connection")
    ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        UUID account = requests.person().accountId();
        GitRequests.retrying(() -> {
            connections.disconnect(account, id);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * Where the provider sends the browser back. Whatever happens, the browser goes on to the page "Git" of the application
     * with the outcome; the provider and the browser decide nothing else here.
     */
    @GetMapping("/api/v1/git/oauth/callback")
    ResponseEntity<Void> callback(@RequestParam(required = false) String state, @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        Optional<UUID> account = identity.current().filter(c -> c.level() == AuthenticationLevel.FULL && !c.portal())
            .map(taskmanager.identity.AccessContext::accountId);
        ConnectionService.Finished finished = connections.finish(account, state, code, error);
        String target = (properties.publicUrl().isEmpty() ? "" : properties.publicUrl()) + "/#/git?"
            + (finished.code() == null ? "connected=" + finished.providerId() : "error=" + finished.code());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, URI.create(target).toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    // ------------------------------------------------------------------------------------------------ administration

    @GetMapping("/api/v1/git/admin/providers")
    List<AdminProviderBody> adminProviders() {
        requests.administrator();
        return GitRequests.retrying(providers::list).stream().map(AdminProviderBody::of).toList();
    }

    @PostMapping("/api/v1/git/admin/providers")
    ResponseEntity<AdminProviderBody> create(@RequestBody ProviderRequest body) {
        UUID actor = requests.administrator().accountId();
        Provider provider = GitRequests.retrying(() -> providers.create(actor, required(body).settings()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(GitRequests.etag(provider.version())).body(AdminProviderBody.of(provider));
    }

    @PutMapping("/api/v1/git/admin/providers/{id}")
    ResponseEntity<AdminProviderBody> update(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody ProviderRequest body) {
        UUID actor = requests.administrator().accountId();
        long version = GitRequests.expectedVersion(ifMatch);
        Provider provider = GitRequests.retrying(() -> providers.update(actor, id, version, required(body).settings()));
        return ResponseEntity.ok().eTag(GitRequests.etag(provider.version())).body(AdminProviderBody.of(provider));
    }

    @DeleteMapping("/api/v1/git/admin/providers/{id}")
    ResponseEntity<Void> remove(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        UUID actor = requests.administrator().accountId();
        long version = GitRequests.expectedVersion(ifMatch);
        GitRequests.retrying(() -> {
            providers.remove(actor, id, version);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/git/admin/providers/{id}/check")
    CheckBody check(@PathVariable UUID id) {
        requests.administrator();
        ProviderService.CheckResult result = GitRequests.retrying(() -> providers.check(id));
        return new CheckBody(result.ok(), result.code(), result.version());
    }

    // ------------------------------------------------------------------------------------------------ released repositories

    @GetMapping("/api/v1/projects/{projectId}/git/repositories")
    ReleasesBody releases(@PathVariable UUID projectId) {
        var actor = requests.actor();
        ReleaseService.Listing listing = GitRequests.retrying(() -> releases.list(actor, projectId));
        return new ReleasesBody(listing.repositories().stream().map(view -> new ReleaseBody(view.release().id(), view.provider().id(),
            view.provider().name(), view.provider().kind().key(), view.release().externalId(), view.repository().isPresent(), view.reason(),
            view.repository().map(GitController::repository).orElse(null), view.release().releasedAt())).toList(), listing.manageable());
    }

    @GetMapping("/api/v1/projects/{projectId}/git/candidates")
    List<RepositoryBody> candidates(@PathVariable UUID projectId, @RequestParam UUID provider, @RequestParam(required = false) String q) {
        var actor = requests.actor();
        return GitRequests.retrying(() -> releases.candidates(actor, projectId, provider, q)).stream().map(GitController::repository).toList();
    }

    @PostMapping("/api/v1/projects/{projectId}/git/repositories")
    ResponseEntity<ReleaseBody> release(@PathVariable UUID projectId, @RequestBody ReleaseRequest body) {
        var actor = requests.actor();
        if (body == null || body.providerId() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("providerId.invalid"));
        }
        var release = GitRequests.retrying(() -> releases.release(actor, projectId, body.providerId(), body.externalId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReleaseBody(release.id(), release.providerId(), null, null,
            release.externalId(), true, null, null, release.releasedAt()));
    }

    @DeleteMapping("/api/v1/projects/{projectId}/git/repositories/{releaseId}")
    ResponseEntity<Void> withdraw(@PathVariable UUID projectId, @PathVariable UUID releaseId) {
        var actor = requests.actor();
        GitRequests.retrying(() -> {
            releases.withdraw(actor, projectId, releaseId);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private static RepositoryBody repository(taskmanager.git.internal.ProviderApi.Repository r) {
        return new RepositoryBody(r.externalId(), r.name(), r.path(), r.url());
    }

    private static <T> T required(T body) {
        if (body == null) {
            throw ApiProblemException.of(400, "request.invalid");
        }
        return body;
    }
}
