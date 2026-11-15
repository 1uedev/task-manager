package taskmanager.git.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.identity.AccountDeactivated;
import taskmanager.platform.ApiProblemException;

/**
 * The connection of the own Git account (TASK-111, rules 3 to 5). A person starts the OAuth flow of a provider with PKCE and
 * a state of its own that is stored only as a hash, used once and valid for ten minutes. The callback checks that the same
 * person is still logged in, exchanges the code, asks the provider for the account and, for a GitLab of its own, for its
 * version, and stores the tokens encrypted. A token is renewed with its refresh token shortly before it ends or after the
 * provider refused it; if that fails too the connection asks to be renewed and the tokens are gone. Disconnecting, removing
 * the provider and deactivating the account delete the tokens at once.
 */
@Service
public class ConnectionService {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionService.class);
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RENEW_BEFORE = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GitStore store;
    private final GitAccess access;
    private final GitSecrets secrets;
    private final GitProperties properties;
    private final ProviderApi api;
    private final Clock clock;
    private final GitCache cache;
    private final TransactionTemplate transaction;

    ConnectionService(GitStore store, GitAccess access, GitSecrets secrets, GitProperties properties, ProviderApi api, Clock clock,
            PlatformTransactionManager transactions, GitCache cache) {
        this.cache = cache;
        this.store = store;
        this.access = access;
        this.secrets = secrets;
        this.properties = properties;
        this.api = api;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** A provider as a person sees it, with the state of the own connection: none, active or renew. */
    public record ProviderView(Provider provider, String connection, Instant connectedAt) {
    }

    /** The person cannot use the provider now: not connected, connection to renew, or the provider is gone. */
    public static final class NotConnected extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;

        NotConnected(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public List<ProviderView> providers(UUID accountId) {
        return transaction.execute(status -> {
            UUID companyId = access.company();
            Map<UUID, Connection> connections = store.connections(companyId, accountId).stream()
                .collect(Collectors.toMap(Connection::providerId, c -> c));
            return store.providers(companyId).stream().map(provider -> {
                Connection connection = connections.get(provider.id());
                return new ProviderView(provider, connection == null ? "none" : connection.state(), connection == null ? null : connection.connectedAt());
            }).toList();
        });
    }

    /** Starts the OAuth flow; returns the address of the provider the browser goes to. */
    public String start(UUID accountId, UUID providerId) {
        if (properties.callbackUrl() == null) {
            throw ApiProblemException.of(409, "git.public_url_missing");
        }
        if (!secrets.available()) {
            throw ApiProblemException.of(409, "git.secret_key_missing");
        }
        return transaction.execute(status -> {
            UUID companyId = access.company();
            Provider provider = store.provider(companyId, providerId).filter(p -> !p.removed())
                .orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
            if (!provider.secretSet()) {
                throw ApiProblemException.of(409, "git.provider_incomplete");
            }
            String state = random(32);
            String verifier = random(48);
            Instant now = clock.instant();
            store.insertState(hash(state), companyId, providerId, accountId, verifier, now, now.plus(STATE_LIFETIME));
            return api.authorizeUrl(provider, properties.callbackUrl(), state, verifier);
        });
    }

    /**
     * Finishes the OAuth flow for the person logged in now, or for nobody. Returns the provider and {@code null}, or a code
     * that names why it failed; never throws for what the provider or the browser sent.
     */
    public Finished finish(Optional<UUID> accountId, String state, String code, String error) {
        if (state == null || state.isEmpty() || state.length() > 200) {
            return new Finished(null, "git.state_invalid");
        }
        GitStore.OAuthState started = transaction.execute(status -> store.takeState(hash(state)).orElse(null));
        Instant now = clock.instant();
        if (started == null || started.expiresAt().isBefore(now) || accountId.isEmpty() || !accountId.get().equals(started.accountId())) {
            return new Finished(null, "git.state_invalid");
        }
        UUID providerId = started.providerId();
        if (error != null || code == null || code.isEmpty() || code.length() > 2000) {
            return new Finished(providerId, "git.denied");
        }
        Provider provider = transaction.execute(status -> store.provider(started.companyId(), providerId).filter(p -> !p.removed()).orElse(null));
        if (provider == null || !provider.secretSet()) {
            return new Finished(providerId, "git.provider_not_found");
        }
        try {
            String secret = secrets.open(Provider.secretPurpose(providerId), provider.secretCipher());
            ProviderApi.Tokens tokens = api.exchange(provider, secret, code, started.verifier(), properties.callbackUrl(), now);
            ProviderApi.Account account = api.account(provider, tokens.access());
            if (provider.kind() == Provider.Kind.GITLAB) {
                Optional<int[]> version = api.version(provider, tokens.access());
                if (version.isEmpty() || version.get()[0] < ProviderApi.GITLAB_MIN_MAJOR) {
                    return new Finished(providerId, "git.version_unsupported");
                }
            }
            String result = transaction.execute(status -> {
                Optional<Provider> still = store.lockProvider(started.companyId(), providerId)
                    .filter(p -> !p.removed() && p.version() == provider.version());
                if (still.isEmpty()) {
                    return "git.provider_not_found";
                }
                UUID person = started.accountId();
                Instant at = clock.instant();
                store.saveConnection(new Connection(providerId, person, started.companyId(), account.id(), Connection.ACTIVE,
                    secrets.seal(Connection.accessPurpose(providerId, person), tokens.access()),
                    tokens.refresh() == null ? null : secrets.seal(Connection.refreshPurpose(providerId, person), tokens.refresh()),
                    tokens.accessExpiresAt(), tokens.refreshExpiresAt(), at, at));
                store.audit(started.companyId(), "connection", person, "connection.connected",
                    ProviderService.json(new Connected(providerId, account.id())), person, at);
                return null;
            });
            // A new connection may belong to another Git account: nothing read with the former one is used again.
            cache.forget(started.accountId(), providerId);
            return new Finished(providerId, result);
        } catch (ProviderApi.TokenRejected rejected) {
            return new Finished(providerId, "git.denied");
        } catch (GitHttp.Failure failure) {
            LOG.info("Connecting an account with Git provider {} failed with {}", providerId, failure.code());
            return new Finished(providerId, failure.code());
        } catch (GitSecrets.KeyMissing | GitSecrets.Unreadable unusable) {
            return new Finished(providerId, "git.secret_unreadable");
        }
    }

    public record Finished(UUID providerId, String code) {
    }

    private record Connected(UUID providerId, String externalUserId) {
    }

    public void disconnect(UUID accountId, UUID providerId) {
        transaction.executeWithoutResult(status -> {
            UUID companyId = access.company();
            store.provider(companyId, providerId).orElseThrow(() -> ApiProblemException.of(404, "git.provider_not_found"));
            if (store.deleteConnection(providerId, accountId) > 0) {
                store.audit(companyId, "connection", accountId, "connection.disconnected",
                    ProviderService.json(new Connected(providerId, null)), accountId, clock.instant());
            }
        });
        cache.forget(accountId, providerId);
    }

    /** Deleting the tokens of an account belongs to its deactivation and runs in its transaction (rule 5). */
    @EventListener
    void deactivated(AccountDeactivated event) {
        cache.forgetAccount(event.accountId());
        Instant now = clock.instant();
        for (UUID providerId : store.deleteConnectionsOfAccount(event.accountId())) {
            store.provider(providerId).ifPresent(provider -> store.audit(provider.companyId(), "connection", event.accountId(),
                "connection.ended", ProviderService.json(new Connected(providerId, null)), null, now));
        }
    }

    // ------------------------------------------------------------------------------------------------ using a token

    /**
     * The token of a person for one call, with the generation of the connection it belongs to: the instance with its version
     * and address, the Git account and the time of connecting, all taken from exactly the rows whose token is used (TASK-116,
     * F1). Whatever is kept or shown of an answer is bound to it, so after connecting another Git account, reconnecting or a
     * change of the instance nothing read before is used again, also when a late answer arrives only after the change.
     */
    public record Grant(String token, String generation) {
    }

    /**
     * Calls the provider with the token of the person. A token that ends within a minute is renewed first; a token the provider
     * refuses is renewed once and the call repeated. Without a usable connection the call fails with {@link NotConnected}.
     */
    public <T> T call(UUID companyId, UUID accountId, Provider provider, Function<String, T> work) {
        return callWith(companyId, accountId, provider, grant -> work.apply(grant.token()));
    }

    /**
     * Like {@link #call} with the generation of the connection, for answers that are kept. The answer is handed on only when
     * the generation still holds after the call; otherwise the call fails with {@code git.provider_changed} and nothing of it
     * is shown, kept or written (TASK-116, F1 and F2). A token the provider refuses is renewed only while the connection still
     * has exactly the state of the refused token; a late refusal of a former connection or of a token renewed meanwhile never
     * renews, marks or uses the refresh token of the newer state (TASK-117, F1).
     */
    public <T> T callWith(UUID companyId, UUID accountId, Provider provider, Function<Grant, T> work) {
        if (provider.removed()) {
            throw new NotConnected("git.provider_not_found");
        }
        Grant grant = token(companyId, accountId, provider);
        T result;
        try {
            result = work.apply(grant);
        } catch (ProviderApi.TokenRejected rejected) {
            Grant renewed = renew(companyId, provider, accountId, grant.generation());
            try {
                result = work.apply(renewed);
            } catch (ProviderApi.TokenRejected again) {
                renewLater(companyId, provider.id(), accountId, renewed.generation());
                throw new NotConnected("git.connection_renew");
            }
            grant = renewed;
        }
        requireCurrent(companyId, accountId, provider.id(), grant.generation());
        return result;
    }

    /** Whether the connection of the person with the instance is still the one of {@code generation}. */
    public boolean current(UUID companyId, UUID accountId, UUID providerId, String generation) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            Provider provider = store.provider(companyId, providerId).filter(p -> !p.removed()).orElse(null);
            Connection connection = store.connection(providerId, accountId).orElse(null);
            return provider != null && connection != null && connection.active() && connection.companyId().equals(companyId)
                && generation(provider, connection).equals(generation);
        }));
    }

    /**
     * Keeps of the read results of one answer only those whose connection is still the one of the person, all checked in one
     * reading immediately before the answer is put together (TASK-118, F1). A result is kept when its instance and the
     * connection of the person are unchanged and active; a token renewed by another read of the same answer keeps it, because
     * the same Git account read it. {@code null} as generation stands for a result without Git data, which is always kept.
     */
    public <T> List<T> keepCurrent(UUID companyId, UUID accountId, List<T> results, Function<T, UUID> providerOf,
            Function<T, String> generationOf, Function<T, T> withoutData) {
        java.util.Set<UUID> providers = results.stream().filter(r -> generationOf.apply(r) != null).map(providerOf)
            .collect(Collectors.toSet());
        if (providers.isEmpty()) {
            return results;
        }
        Map<UUID, String> connections = transaction.execute(status -> {
            Map<UUID, String> found = new java.util.HashMap<>();
            for (UUID providerId : providers) {
                Provider provider = store.provider(companyId, providerId).filter(p -> !p.removed()).orElse(null);
                Connection connection = store.connection(providerId, accountId).orElse(null);
                if (provider != null && connection != null && connection.active() && connection.companyId().equals(companyId)) {
                    found.put(providerId, connectionOf(provider, connection));
                }
            }
            return found;
        });
        return results.stream().map(result -> {
            String generation = generationOf.apply(result);
            if (generation == null) {
                return result;
            }
            String connection = connections.get(providerOf.apply(result));
            return connection != null && generation.startsWith(connection + "|") ? result : withoutData.apply(result);
        }).toList();
    }

    /** Fails with {@code git.provider_changed} when the generation no longer holds. */
    public void requireCurrent(UUID companyId, UUID accountId, UUID providerId, String generation) {
        if (!current(companyId, accountId, providerId, generation)) {
            throw changed();
        }
    }

    /**
     * Holds the connection of {@code generation} until the end of the surrounding transaction, so disconnecting, connecting
     * again and renewing wait for it, and fails with {@code git.provider_changed} when it is no longer that connection
     * (TASK-117, F2). Writing a link or a release calls it after locking the instance and before writing anything.
     */
    public void holdCurrent(UUID companyId, UUID accountId, UUID providerId, String generation) {
        Provider provider = store.provider(companyId, providerId).filter(p -> !p.removed()).orElse(null);
        Connection connection = store.lockConnection(providerId, accountId).orElse(null);
        if (provider == null || connection == null || !connection.active() || !connection.companyId().equals(companyId)
            || !generation(provider, connection).equals(generation)) {
            throw changed();
        }
    }

    static NotConnected changed() {
        return new NotConnected("git.provider_changed");
    }

    /**
     * The connection: the instance with its version and address, the Git account, the time of connecting (TASK-116, F1) and the
     * state of the access token, a digest of its sealed form that changes with every renewal (TASK-117, F1).
     */
    static String generation(Provider provider, Connection connection) {
        return connectionOf(provider, connection) + "|" + tokenState(connection);
    }

    /** The generation without the state of the token: the same connection, whatever token it holds now. */
    private static String connectionOf(Provider provider, Connection connection) {
        return provider.id() + "|" + provider.version() + "|" + provider.baseUrl() + "|" + connection.externalUserId() + "|"
            + connection.connectedAt();
    }

    private static String tokenState(Connection connection) {
        if (connection.accessCipher() == null) {
            // A connection asked to be renewed holds no token; its state is still compared, never digested.
            return "none";
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(connection.accessCipher());
            return java.util.HexFormat.of().formatHex(digest, 0, 12);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Grant token(UUID companyId, UUID accountId, Provider provider) {
        record Rows(Provider provider, Connection connection) {
        }
        Rows rows = transaction.execute(status -> new Rows(store.provider(companyId, provider.id()).orElse(null),
            store.connection(provider.id(), accountId).orElse(null)));
        Provider fresh = rows.provider();
        if (fresh == null || fresh.removed()) {
            throw new NotConnected("git.provider_not_found");
        }
        // The caller read the instance before; a call with a changed instance would mix both states (TASK-116, F2).
        if (fresh.version() != provider.version()) {
            throw changed();
        }
        Connection connection = rows.connection();
        if (connection == null || !connection.companyId().equals(companyId)) {
            throw new NotConnected("git.not_connected");
        }
        if (!connection.active()) {
            throw new NotConnected("git.connection_renew");
        }
        String generation = generation(fresh, connection);
        if (!ending(connection)) {
            return new Grant(open(companyId, Connection.accessPurpose(provider.id(), accountId), connection.accessCipher(), provider.id(),
                accountId, generation), generation);
        }
        return renew(companyId, fresh, accountId, generation);
    }

    private boolean ending(Connection connection) {
        return connection.accessExpiresAt() != null && connection.accessExpiresAt().isBefore(clock.instant().plus(RENEW_BEFORE));
    }

    /**
     * Renews the token of exactly the state {@code expected} under the lock of the connection, so two requests of the same person
     * do not both use the refresh token (TASK-117, F1). The row is compared before the refresh token is read or the state is
     * changed: another connection by now ends the call with {@code git.provider_changed} and changes nothing; the same connection
     * with a token renewed meanwhile hands on that token with its own state, without a refresh. A renewal of this call returns the
     * generation of the token it stored.
     */
    private Grant renew(UUID companyId, Provider provider, UUID accountId, String expected) {
        record Outcome(String token, String generation) {
        }
        Outcome outcome = transaction.execute(status -> {
            Connection current = store.lockConnection(provider.id(), accountId).orElse(null);
            Provider still = store.provider(companyId, provider.id()).filter(p -> !p.removed()).orElse(null);
            if (current == null || still == null || !current.companyId().equals(companyId)) {
                throw changed();
            }
            String now = generation(still, current);
            if (!now.equals(expected)) {
                boolean sameConnection = expected.startsWith(connectionOf(still, current) + "|");
                if (!sameConnection) {
                    throw changed();
                }
                if (!current.active()) {
                    throw new NotConnected("git.connection_renew");
                }
                // Renewed meanwhile by another request of the person: its token is used as it is. A token of that newer state
                // that cannot be opened ends this call without writing anything; only a call that began with that state marks
                // it (TASK-118, F2).
                try {
                    return new Outcome(secrets.open(Connection.accessPurpose(provider.id(), accountId), current.accessCipher()), now);
                } catch (GitSecrets.Unreadable | GitSecrets.KeyMissing unusable) {
                    throw new NotConnected("git.connection_renew");
                }
            }
            if (!current.active()) {
                throw new NotConnected("git.connection_renew");
            }
            Instant at = clock.instant();
            boolean refreshEnded = current.refreshExpiresAt() != null && current.refreshExpiresAt().isBefore(at);
            if (current.refreshCipher() == null || refreshEnded || !still.secretSet()) {
                store.markRenew(provider.id(), accountId, at);
                return null;
            }
            try {
                String refresh = secrets.open(Connection.refreshPurpose(provider.id(), accountId), current.refreshCipher());
                String secret = secrets.open(Provider.secretPurpose(provider.id()), still.secretCipher());
                ProviderApi.Tokens tokens = api.refresh(still, secret, refresh, at);
                Connection renewed = new Connection(provider.id(), accountId, current.companyId(), current.externalUserId(), Connection.ACTIVE,
                    secrets.seal(Connection.accessPurpose(provider.id(), accountId), tokens.access()),
                    tokens.refresh() == null ? current.refreshCipher() : secrets.seal(Connection.refreshPurpose(provider.id(), accountId), tokens.refresh()),
                    tokens.accessExpiresAt(), tokens.refresh() == null ? current.refreshExpiresAt() : tokens.refreshExpiresAt(),
                    current.connectedAt(), at);
                store.saveConnection(renewed);
                return new Outcome(tokens.access(), generation(still, renewed));
            } catch (ProviderApi.TokenRejected | GitSecrets.Unreadable | GitSecrets.KeyMissing unusable) {
                store.markRenew(provider.id(), accountId, at);
                return null;
            }
        });
        if (outcome != null) {
            return new Grant(outcome.token(), outcome.generation());
        }
        throw new NotConnected("git.connection_renew");
    }

    private String open(UUID companyId, String purpose, byte[] cipher, UUID providerId, UUID accountId, String generation) {
        try {
            return secrets.open(purpose, cipher);
        } catch (GitSecrets.Unreadable | GitSecrets.KeyMissing unusable) {
            renewLater(companyId, providerId, accountId, generation);
            throw new NotConnected("git.connection_renew");
        }
    }

    /** Asks the person to renew exactly the connection of {@code generation}; a newer connection stays as it is (TASK-116, F1). */
    private void renewLater(UUID companyId, UUID providerId, UUID accountId, String generation) {
        transaction.executeWithoutResult(status -> {
            Connection current = store.lockConnection(providerId, accountId).orElse(null);
            Provider provider = store.provider(companyId, providerId).orElse(null);
            if (current != null && provider != null && generation(provider, current).equals(generation)) {
                store.markRenew(providerId, accountId, clock.instant());
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] hash(String state) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
