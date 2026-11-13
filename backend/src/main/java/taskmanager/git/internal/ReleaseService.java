package taskmanager.git.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;

/**
 * The repositories released for a project (TASK-111, rule 6). Only the stable identifier of the provider is stored. What a
 * person sees of a released repository comes live from the provider with the token of that person; without a connection or
 * without access it is shown as not available, without name or path. Releasing needs a repository the own connection may
 * read. At most 50 repositories per project; at most four calls to providers run at the same time for one list.
 */
@Service
public class ReleaseService implements DisposableBean {
    static final int MAX_RELEASES = 50;

    private final GitStore store;
    private final GitAccess access;
    private final ConnectionService connections;
    private final ProviderApi api;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ExecutorService pool = Executors.newFixedThreadPool(4, Thread.ofVirtual().name("git-read-", 0).factory());

    ReleaseService(GitStore store, GitAccess access, ConnectionService connections, ProviderApi api, Clock clock,
            PlatformTransactionManager transactions) {
        this.store = store;
        this.access = access;
        this.connections = connections;
        this.api = api;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public void destroy() {
        pool.shutdownNow();
    }

    /**
     * A released repository as the caller sees it. {@code repository} is empty when it is not available to the caller; then
     * {@code reason} says whether the own connection is missing ({@code git.not_connected}, {@code git.connection_renew}) or
     * the repository is not available ({@code git.unavailable}), never whether it exists.
     */
    public record View(Release release, Provider provider, Optional<ProviderApi.Repository> repository, String reason) {
    }

    public record Listing(List<View> repositories, boolean manageable) {
    }

    public Listing list(AccessContext actor, UUID projectId) {
        record Read(List<Release> releases, Map<UUID, Provider> providers, boolean manageable) {
        }
        Read read = transaction.execute(status -> {
            access.requireProject(actor, projectId, false);
            List<Release> releases = store.releases(actor.companyId(), projectId);
            Map<UUID, Provider> providers = new HashMap<>();
            for (Release release : releases) {
                providers.computeIfAbsent(release.providerId(), id -> store.provider(actor.companyId(), id).orElseThrow());
            }
            return new Read(releases, providers, access.mayChangeProject(actor, projectId));
        });
        List<CompletableFuture<View>> views = new ArrayList<>();
        for (Release release : read.releases()) {
            Provider provider = read.providers().get(release.providerId());
            views.add(CompletableFuture.supplyAsync(() -> view(actor, release, provider), pool));
        }
        return new Listing(views.stream().map(CompletableFuture::join).toList(), read.manageable());
    }

    private View view(AccessContext actor, Release release, Provider provider) {
        try {
            Optional<ProviderApi.Repository> repository = connections.call(actor.companyId(), actor.userId(), provider,
                token -> api.repository(provider, token, release.externalId()));
            return new View(release, provider, repository, repository.isPresent() ? null : "git.unavailable");
        } catch (ConnectionService.NotConnected missing) {
            return new View(release, provider, Optional.empty(), missing.code());
        } catch (GitHttp.Failure failure) {
            return new View(release, provider, Optional.empty(), "git.unavailable");
        }
    }

    /** The repositories of a provider that the own connection may read, for the choice of a release. */
    public List<ProviderApi.Repository> candidates(AccessContext actor, UUID projectId, UUID providerId, String query) {
        Provider provider = transaction.execute(status -> {
            access.requireProject(actor, projectId, true);
            return store.provider(actor.companyId(), providerId).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
        });
        if (query != null && query.length() > 100) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("q.invalid"));
        }
        return reading(() -> connections.call(actor.companyId(), actor.userId(), provider, token -> api.repositories(provider, token, query)));
    }

    public Release release(AccessContext actor, UUID projectId, UUID providerId, String externalId) {
        if (externalId == null || !externalId.matches("[0-9]{1,20}")) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("externalId.invalid"));
        }
        Provider provider = transaction.execute(status -> {
            access.requireProject(actor, projectId, true);
            return store.provider(actor.companyId(), providerId).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
        });
        Optional<ProviderApi.Repository> seen = reading(() -> connections.call(actor.companyId(), actor.userId(), provider,
            token -> api.repository(provider, token, externalId)));
        if (seen.isEmpty()) {
            throw ApiProblemException.of(404, "git.repository_not_found");
        }
        return transaction.execute(status -> {
            access.requireProject(actor, projectId, true);
            Provider still = store.lockProvider(actor.companyId(), providerId).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
            if (store.released(projectId, still.id(), externalId)) {
                throw ApiProblemException.of(409, "git.repository_released");
            }
            if (store.releaseCount(projectId) >= MAX_RELEASES) {
                throw ApiProblemException.of(409, "git.repositories_too_many");
            }
            Instant now = clock.instant();
            Release release = new Release(UUID.randomUUID(), actor.companyId(), projectId, still.id(), externalId, now, actor.userId());
            store.insertRelease(release);
            store.audit(actor.companyId(), "repository", release.id(), "repository.released",
                ProviderService.json(new Described(projectId, still.id(), externalId)), actor.userId(), now);
            return release;
        });
    }

    public void withdraw(AccessContext actor, UUID projectId, UUID releaseId) {
        transaction.executeWithoutResult(status -> {
            access.requireProject(actor, projectId, true);
            Release release = store.release(actor.companyId(), projectId, releaseId)
                .orElseThrow(() -> ApiProblemException.of(404, "git.repository_not_found"));
            store.deleteRelease(release.id());
            store.audit(actor.companyId(), "repository", release.id(), "repository.withdrawn",
                ProviderService.json(new Described(projectId, release.providerId(), release.externalId())), actor.userId(), clock.instant());
        });
    }

    private record Described(UUID projectId, UUID providerId, String externalId) {
    }

    /** Turns a missing connection or a failed call into an answer the person can act on. */
    private static <T> T reading(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (ConnectionService.NotConnected missing) {
            throw ApiProblemException.of(409, missing.code());
        } catch (GitHttp.Failure failure) {
            throw ApiProblemException.of(failure.code().equals("git.rate_limited") ? 503 : 502, failure.code());
        }
    }
}
