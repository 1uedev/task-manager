package taskmanager.git.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.requirements.RequirementGitRecords;
import taskmanager.tickets.TicketAccess;

/**
 * Git objects linked to tickets and requirements (TASK-112, DEC-142). A link keeps only stable identifiers; what a person sees
 * of it is read live from the provider with the token of that person, in parallel with at most four calls per request, kept
 * at most 60 seconds per person in {@link GitCache}. Without a connection, without access, for an unknown object or after a
 * failure of the provider only the kind of the object is shown, never path, title, message, link or number, and never
 * whether the object exists. A link is never removed by itself.
 *
 * <p>The access of the person to the repository is read live at every request, also when the commit or change itself
 * comes from the cache (TASK-114, F1). A link the caller cannot see shows only its kind and, for a writer, an opaque key to
 * remove it, bound to the caller; no identifier, instance, time or author of the link (TASK-114, F6).
 *
 * <p>Every route needs the read action of the ticket or requirement first, otherwise 404 (TASK-114, F5). Rights then follow
 * the field {@code git} of the resource types {@code ticket} and {@code requirement}: its read rule shows the links, its
 * write rule links and removes them. Linking also needs the own connection with access to the object and a
 * repository released for the project of the ticket or requirement. Locks of a change, in this order: rights (held until the
 * commit), the project row, the provider {@code FOR SHARE}, the ticket or requirement row {@code FOR SHARE}, the release row
 * {@code FOR SHARE} (TASK-114, F9), the links of that subject. A link changes neither the version of the ticket nor that of the requirement.
 */
@Service
public class LinkService {
    public static final String FIELD = "git";
    static final int MAX_LINKS = 50;
    static final int PARALLEL = 4;
    static final Duration DEADLINE = Duration.ofSeconds(20);
    public static final String TICKET = "ticket";
    public static final String REQUIREMENT = "requirement";
    /** The key of the removal keys of this process; a key from before a restart no longer matches and the list is read again. */
    private static final byte[] KEY_SECRET = new byte[32];

    static {
        new SecureRandom().nextBytes(KEY_SECRET);
    }

    private final GitStore store;
    private final ConnectionService connections;
    private final ProviderApi api;
    private final GitCache cache;
    private final Permissions permissions;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final RequirementGitRecords requirements;
    private final Clock clock;
    private final TransactionTemplate transaction;

    LinkService(GitStore store, ConnectionService connections, ProviderApi api, GitCache cache, Permissions permissions, ProjectAccess projects,
            TicketAccess tickets, RequirementGitRecords requirements, Clock clock, PlatformTransactionManager transactions) {
        this.store = store;
        this.connections = connections;
        this.api = api;
        this.cache = cache;
        this.permissions = permissions;
        this.projects = projects;
        this.tickets = tickets;
        this.requirements = requirements;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** A ticket by project and number or a requirement by its identifier. */
    public sealed interface Target permits TicketTarget, RequirementTarget {
    }

    public record TicketTarget(UUID projectId, long number) implements Target {
    }

    public record RequirementTarget(UUID id) implements Target {
    }

    /** The choice of a released repository with the kind of object and its SHA or number ({@code ""} for the repository). */
    public record Selection(UUID releaseId, String kind, String key) {
    }

    /** What the provider says of a linked object, with the address built by this module. */
    public record Live(ProviderApi.Repository repository, ProviderApi.Commit commit, ProviderApi.Change change, String url) {
    }

    /**
     * A link as the caller sees it. {@code live} is {@code null} when the object is not available; then {@code reason} is
     * {@code git.not_connected}, {@code git.connection_renew}, {@code git.rate_limited} or {@code git.unavailable}.
     */
    public record LinkView(GitLink link, Provider provider, Live live, String reason, String key) {
    }

    public record Listing(boolean readable, boolean writable, List<LinkView> links) {
    }

    /** A released repository of the project for the choice that the caller can read now. */
    public record Choice(Release release, Provider provider, ProviderApi.Repository repository) {
    }

    private record Subject(String kind, UUID id, UUID projectId, boolean readable, boolean writable) {
    }

    // ------------------------------------------------------------------------------------------------ reading

    public Listing list(AccessContext actor, Target target, boolean refresh) {
        record Read(Subject subject, List<GitLink> links, Map<UUID, Provider> providers) {
        }
        Read read = inTransaction(() -> {
            Subject subject = locate(actor, target, false);
            List<GitLink> links = subject.readable() ? store.links(actor.companyId(), subject.kind(), subject.id()) : List.of();
            return new Read(subject, links, providers(actor.companyId(), links.stream().map(GitLink::providerId).toList()));
        });
        boolean writable = read.subject().writable();
        Map<String, CompletableFuture<Optional<ProviderApi.Repository>>> seen = new ConcurrentHashMap<>();
        List<LinkView> views = parallel(read.links(), link -> view(actor, link, read.providers().get(link.providerId()), refresh, seen, writable),
            link -> new LinkView(link, read.providers().get(link.providerId()), null, "git.unavailable", removalKey(actor, link, writable)));
        return new Listing(read.subject().readable(), writable, views);
    }

    /** The released repositories of the project of the subject, for linking; needs the write rule of {@code git}. */
    public List<Choice> choices(AccessContext actor, Target target) {
        record Read(List<Release> releases, Map<UUID, Provider> providers) {
        }
        Read read = inTransaction(() -> {
            Subject subject = locate(actor, target, false);
            if (!subject.writable()) {
                throw GitAccess.forbidden();
            }
            List<Release> releases = store.releases(actor.companyId(), subject.projectId());
            return new Read(releases, providers(actor.companyId(), releases.stream().map(Release::providerId).toList()));
        });
        Map<String, CompletableFuture<Optional<ProviderApi.Repository>>> seen = new ConcurrentHashMap<>();
        // Only repositories the own connection can read now are offered (TASK-114, F6).
        return parallel(read.releases(), release -> {
            Provider provider = read.providers().get(release.providerId());
            if (provider == null || provider.removed()) {
                return null;
            }
            try {
                return connections.callWith(actor.companyId(), actor.userId(), provider,
                    grant -> liveRepository(seen, provider, grant.token(), release.externalId()))
                    .map(repository -> new Choice(release, provider, repository)).orElse(null);
            } catch (ConnectionService.NotConnected | GitHttp.Failure unusable) {
                return null;
            }
        }, release -> null).stream().filter(java.util.Objects::nonNull).toList();
    }

    private LinkView view(AccessContext actor, GitLink link, Provider provider, boolean refresh,
            Map<String, CompletableFuture<Optional<ProviderApi.Repository>>> seen, boolean writable) {
        String key = removalKey(actor, link, writable);
        if (provider == null || provider.removed()) {
            return new LinkView(link, provider, null, "git.unavailable", key);
        }
        try {
            Live live = connections.callWith(actor.companyId(), actor.userId(), provider,
                grant -> load(actor.userId(), provider, grant, link, refresh, seen));
            return new LinkView(link, provider, live, live == null ? "git.unavailable" : null, key);
        } catch (ConnectionService.NotConnected missing) {
            return new LinkView(link, provider, null, reason(missing), key);
        } catch (GitHttp.Failure failure) {
            return new LinkView(link, provider, null, reason(failure), key);
        }
    }

    /**
     * The object of a link as the token reads it, or {@code null} when it is not available. The repository is always read live,
     * once per request and repository; only then a kept commit or change of the same generation of the connection is shown.
     */
    private Live load(UUID accountId, Provider provider, ConnectionService.Grant grant, GitLink link, boolean refresh,
            Map<String, CompletableFuture<Optional<ProviderApi.Repository>>> seen) {
        Optional<ProviderApi.Repository> found = liveRepository(seen, provider, grant.token(), link.repositoryId());
        if (found.isEmpty()) {
            return null;
        }
        ProviderApi.Repository repository = found.get();
        String object = link.repositoryId() + ":" + link.objectKey();
        String url = ProviderApi.objectUrl(provider, repository, link.objectKind(), link.objectKey());
        return switch (link.objectKind()) {
            case GitLink.COMMIT -> cache.read(accountId, provider.id(), grant.generation(), "commit:" + object, refresh,
                () -> api.commit(provider, grant.token(), link.repositoryId(), link.objectKey()))
                .map(commit -> new Live(repository, commit, null, url)).orElse(null);
            case GitLink.PULL_REQUEST, GitLink.MERGE_REQUEST -> cache.read(accountId, provider.id(), grant.generation(), "change:" + object, refresh,
                () -> api.change(provider, grant.token(), link.repositoryId(), Long.parseLong(link.objectKey())))
                .map(change -> new Live(repository, null, change, url)).orElse(null);
            default -> new Live(repository, null, null, url);
        };
    }

    /** The repository read live with the token, once per request even when several links or releases name it. */
    private Optional<ProviderApi.Repository> liveRepository(Map<String, CompletableFuture<Optional<ProviderApi.Repository>>> seen,
            Provider provider, String token, String repositoryId) {
        String key = provider.id() + ":" + repositoryId;
        CompletableFuture<Optional<ProviderApi.Repository>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<ProviderApi.Repository>> running = seen.putIfAbsent(key, mine);
        if (running == null) {
            try {
                Optional<ProviderApi.Repository> repository = api.repository(provider, token, repositoryId);
                mine.complete(repository);
                return repository;
            } catch (RuntimeException failed) {
                seen.remove(key, mine);
                mine.completeExceptionally(failed);
                throw failed;
            }
        }
        try {
            return running.join();
        } catch (CompletionException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failed;
        }
    }

    // ------------------------------------------------------------------------------------------------ changing

    /** Links the object of a pasted address or of a selection; exactly one of both is given. */
    public LinkView add(AccessContext actor, Target target, String address, Selection selection) {
        if ((address == null) == (selection == null)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("url.invalid"));
        }
        if (address != null && (address.isBlank() || address.length() > GitAddress.MAX_LENGTH)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("url.invalid"));
        }
        if (selection != null) {
            List<String> violations = new ArrayList<>();
            if (selection.releaseId() == null) {
                violations.add("releaseId.invalid");
            }
            if (selection.kind() == null || !GitAddress.validKey(selection.kind(), selection.key())) {
                violations.add(selection.kind() == null || !List.of(GitLink.REPOSITORY, GitLink.COMMIT, GitLink.PULL_REQUEST, GitLink.MERGE_REQUEST)
                    .contains(selection.kind()) ? "kind.invalid" : "key.invalid");
            }
            if (!violations.isEmpty()) {
                throw ApiProblemException.withViolations(400, "request.invalid", violations);
            }
        }
        // One reading per instance with its own path, kind and key (TASK-114, F7); for a selection exactly one.
        record Reading(Provider provider, String repositoryId, String path, String kind, String key) {
        }
        List<Reading> readings = inTransaction(() -> {
            Subject subject = locate(actor, target, true);
            if (address != null) {
                List<GitAddress.Parsed> parsed = GitAddress.parse(address, store.providers(actor.companyId())).stream()
                    .filter(p -> GitAddress.validKey(p.kind(), p.key())).toList();
                if (parsed.isEmpty()) {
                    throw ApiProblemException.of(400, "git.address_unknown");
                }
                // Only an instance with a repository released for the project can lead to a link.
                Set<UUID> released = store.releases(actor.companyId(), subject.projectId()).stream().map(Release::providerId)
                    .collect(java.util.stream.Collectors.toSet());
                List<Reading> candidates = parsed.stream().filter(p -> released.contains(p.provider().id()))
                    .map(p -> new Reading(p.provider(), null, p.repositoryPath(), p.kind(), p.key())).toList();
                if (candidates.isEmpty()) {
                    throw ApiProblemException.of(409, "git.repository_not_released");
                }
                return candidates;
            }
            Release release = store.release(actor.companyId(), subject.projectId(), selection.releaseId())
                .orElseThrow(() -> ApiProblemException.of(404, "git.repository_not_found"));
            Provider provider = store.provider(actor.companyId(), release.providerId()).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
            if (!GitAddress.fits(provider, selection.kind())) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("kind.invalid"));
            }
            String key = selection.key() == null ? "" : selection.key().toLowerCase(java.util.Locale.ROOT);
            return List.of(new Reading(provider, release.externalId(), null, selection.kind(), key));
        });
        // The check at the provider runs outside a transaction and always asks the provider again (rule 1). For each instance
        // the path is first resolved to the stable identifier of the repository, then the release of exactly that repository
        // is checked, and only then the object is fetched (TASK-114, F3): a repository that is not released tells nothing of
        // the object. Of several instances the first whose connection finds the object in a released repository is taken.
        Resolved resolved = null;
        String kind = null;
        RuntimeException failure = null;
        boolean notReleased = false;
        for (Reading reading : readings) {
            Provider candidate = reading.provider();
            try {
                Optional<ProviderApi.Repository> repository = connections.call(actor.companyId(), actor.userId(), candidate,
                    token -> reading.path() != null ? api.repositoryByPath(candidate, token, reading.path())
                        : api.repository(candidate, token, reading.repositoryId()));
                if (repository.isEmpty()) {
                    continue;
                }
                if (!released(actor, target, candidate.id(), repository.get().externalId())) {
                    notReleased = true;
                    continue;
                }
                Resolved attempt = connections.call(actor.companyId(), actor.userId(), candidate,
                    token -> resolve(candidate, token, repository.get(), reading.kind(), reading.key()));
                if (attempt == null) {
                    continue;
                }
                resolved = attempt;
                kind = reading.kind();
                break;
            } catch (ConnectionService.NotConnected | GitHttp.Failure problem) {
                failure = failure == null ? problem : failure;
            }
        }
        if (resolved == null) {
            if (notReleased) {
                throw ApiProblemException.of(409, "git.repository_not_released");
            }
            if (failure != null) {
                RuntimeException first = failure;
                reading(() -> {
                    throw first;
                });
            }
            throw ApiProblemException.of(404, "git.object_not_found");
        }
        Resolved chosen = resolved;
        String chosenKind = kind;
        Provider provider = chosen.provider();
        return inTransaction(() -> {
            Subject subject = locate(actor, target, true);
            require(actor, subject);
            Provider still = store.shareProvider(actor.companyId(), provider.id()).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
            // The object was read at exactly this address of the instance (TASK-114, F4).
            if (!still.baseUrl().equals(provider.baseUrl())) {
                throw ApiProblemException.of(409, "git.provider_changed");
            }
            lockSubject(actor, subject);
            String repositoryId = chosen.repository().externalId();
            if (!store.shareReleased(subject.projectId(), still.id(), repositoryId)) {
                throw ApiProblemException.of(409, "git.repository_not_released");
            }
            store.lockLinks(subject.kind(), subject.id());
            if (store.linked(subject.kind(), subject.id(), still.id(), repositoryId, chosenKind, chosen.key())) {
                throw ApiProblemException.of(409, "git.link_exists");
            }
            if (store.linkCount(subject.kind(), subject.id()) >= MAX_LINKS) {
                throw ApiProblemException.of(409, "git.links_too_many");
            }
            Instant now = clock.instant();
            GitLink link = new GitLink(UUID.randomUUID(), actor.companyId(), subject.kind(), subject.id(), subject.projectId(), still.id(),
                repositoryId, chosenKind, chosen.key(), now, actor.userId());
            store.insertLink(link);
            history(actor, subject, now, null, link.historyValue());
            // The answer shows what was just read only to a reader of the field; the link itself is no secret to its writer.
            return new LinkView(link, still, subject.readable() ? chosen.live() : null, subject.readable() ? null : "git.unavailable",
                removalKey(actor, link, true));
        });
    }

    private record Resolved(Provider provider, ProviderApi.Repository repository, String key, Live live) {
    }

    /** The object in the found repository with the full SHA or the number; {@code null} when it is not there or not readable. */
    private Resolved resolve(Provider provider, String token, ProviderApi.Repository repo, String kind, String key) {
        return switch (kind) {
            case GitLink.COMMIT -> api.commit(provider, token, repo.externalId(), key)
                .map(commit -> new Resolved(provider, repo, commit.sha(), new Live(repo, commit, null,
                    ProviderApi.objectUrl(provider, repo, GitLink.COMMIT, commit.sha()))))
                .orElse(null);
            case GitLink.PULL_REQUEST, GitLink.MERGE_REQUEST -> api.change(provider, token, repo.externalId(), Long.parseLong(key))
                .map(change -> new Resolved(provider, repo, Long.toString(change.number()), new Live(repo, null, change,
                    ProviderApi.objectUrl(provider, repo, kind, Long.toString(change.number())))))
                .orElse(null);
            default -> new Resolved(provider, repo, "", new Live(repo, null, null, ProviderApi.objectUrl(provider, repo, GitLink.REPOSITORY, "")));
        };
    }

    private boolean released(AccessContext actor, Target target, UUID providerId, String repositoryId) {
        return inTransaction(() -> store.released(locate(actor, target, true).projectId(), providerId, repositoryId));
    }

    /**
     * Removes a link by the opaque key the list gave the caller; needs only the write rule of {@code git}, neither a connection
     * nor access to the object (rule 2).
     */
    public void remove(AccessContext actor, Target target, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}")) {
            throw ApiProblemException.of(404, "git.link_not_found");
        }
        inTransaction(() -> {
            Subject subject = locate(actor, target, true);
            require(actor, subject);
            lockSubject(actor, subject);
            UUID linkId = store.links(actor.companyId(), subject.kind(), subject.id()).stream()
                .filter(link -> key.equals(removalKey(actor, link, true))).map(GitLink::id).findFirst()
                .orElseThrow(() -> ApiProblemException.of(404, "git.link_not_found"));
            GitLink removed = store.deleteLink(actor.companyId(), subject.kind(), subject.id(), linkId)
                .orElseThrow(() -> ApiProblemException.of(404, "git.link_not_found"));
            history(actor, subject, clock.instant(), removed.historyValue(), null);
            return null;
        });
    }

    /**
     * The key with which the caller removes a link: bound to the person and the link and of no use to anybody else, so it
     * relates no list to another one or to the history (TASK-114, F6). {@code null} for a caller who may not remove links.
     */
    static String removalKey(AccessContext actor, GitLink link, boolean writable) {
        if (!writable) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY_SECRET, "HmacSHA256"));
            byte[] digest = mac.doFinal((actor.userId() + "|" + link.id()).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(digest, 18));
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException(missing);
        }
    }

    // ------------------------------------------------------------------------------------------------ rights and subjects

    /**
     * The ticket or requirement with the rules of the caller for {@code git}. A caller who may not read it does not see it:
     * 404 like everywhere else, also with the right to edit it (TASK-114, F5). With {@code change} the write rule is needed,
     * otherwise 403.
     */
    private Subject locate(AccessContext actor, Target target, boolean change) {
        GitAccess.internal(actor);
        String kind;
        UUID projectId;
        if (target instanceof TicketTarget ticket) {
            kind = TICKET;
            projectId = ticket.projectId();
        } else {
            kind = REQUIREMENT;
            projectId = requirements.projectOf(actor.companyId(), ((RequirementTarget) target).id()).orElseThrow(LinkService::requirementNotFound);
        }
        Supplier<ResourceContext> resource = resource(actor, kind, projectId);
        Decision read = permissions.inspect(actor, resource, kind + ".read", Set.of(FIELD));
        Decision edit = permissions.inspect(actor, resource, kind + ".edit", Set.of(FIELD));
        if (!read.allowed()) {
            throw kind.equals(TICKET) ? GitAccess.projectNotFound() : requirementNotFound();
        }
        UUID id = target instanceof TicketTarget ticket
            ? tickets.find(actor.companyId(), projectId, ticket.number()).orElseThrow(() -> ApiProblemException.of(404, "ticket.not_found"))
            : ((RequirementTarget) target).id();
        boolean writable = edit.allowed() && edit.writableFields().contains(FIELD);
        if (change && !writable) {
            throw GitAccess.forbidden();
        }
        return new Subject(kind, id, projectId, read.allowed() && read.readableFields().contains(FIELD), writable);
    }

    private Supplier<ResourceContext> resource(AccessContext actor, String kind, UUID projectId) {
        return projects.resource(actor.companyId(), projectId, actor.userId(), kind, false);
    }

    /** The authoritative check of the write rule, held until the commit. */
    private void require(AccessContext actor, Subject subject) {
        permissions.require(actor, resource(actor, subject.kind(), subject.projectId()), subject.kind() + ".edit", Set.of(), Set.of(FIELD));
    }

    private void lockSubject(AccessContext actor, Subject subject) {
        if (subject.kind().equals(TICKET)) {
            tickets.lockForGitChange(subject.id());
        } else if (requirements.lockForGitChange(actor.companyId(), subject.id()).isEmpty()) {
            throw requirementNotFound();
        }
    }

    private void history(AccessContext actor, Subject subject, Instant at, String removed, String added) {
        if (subject.kind().equals(TICKET)) {
            tickets.recordGitChange(subject.id(), at, actor.userId(), removed, added);
        } else {
            requirements.recordGitChange(actor.companyId(), subject.id(), at, actor.userId(), removed, added);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private Map<UUID, Provider> providers(UUID companyId, List<UUID> ids) {
        Map<UUID, Provider> providers = new HashMap<>();
        for (UUID id : ids) {
            if (!providers.containsKey(id)) {
                providers.put(id, store.provider(companyId, id).orElse(null));
            }
        }
        return providers;
    }

    /**
     * Runs the reads of one request with at most {@link #PARALLEL} at the same time and an overall limit of
     * {@link #DEADLINE}; what is not done by then, or failed unexpectedly, is shown as not available.
     */
    private static <T, R> List<R> parallel(List<T> items, Function<T, R> work, Function<T, R> unavailable) {
        if (items.isEmpty()) {
            return List.of();
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLEL, items.size()), Thread.ofVirtual().name("git-link-", 0).factory());
        try {
            List<Future<R>> futures = items.stream().map(item -> pool.submit(() -> work.apply(item))).toList();
            long end = System.nanoTime() + DEADLINE.toNanos();
            List<R> results = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                try {
                    results.add(futures.get(i).get(Math.max(0, end - System.nanoTime()), TimeUnit.NANOSECONDS));
                } catch (TimeoutException | ExecutionException late) {
                    futures.get(i).cancel(true);
                    results.add(unavailable.apply(items.get(i)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    results.add(unavailable.apply(items.get(i)));
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        try {
            return transaction.execute(status -> work.get());
        } catch (PermissionDenied denied) {
            throw GitAccess.forbidden();
        }
    }

    private static String reason(ConnectionService.NotConnected missing) {
        return missing.code().equals("git.provider_not_found") ? "git.unavailable" : missing.code();
    }

    private static String reason(GitHttp.Failure failure) {
        return failure.code().equals("git.rate_limited") ? "git.rate_limited" : "git.unavailable";
    }

    private static ApiProblemException requirementNotFound() {
        return ApiProblemException.of(404, "requirement.not_found");
    }

    /** Turns a missing connection or a failed call into an answer the person can act on. */
    private static <T> T reading(Supplier<T> call) {
        try {
            return call.get();
        } catch (ConnectionService.NotConnected missing) {
            throw ApiProblemException.of(409, missing.code());
        } catch (GitHttp.Failure failure) {
            throw ApiProblemException.of(failure.code().equals("git.rate_limited") ? 503 : 502, failure.code());
        }
    }
}
