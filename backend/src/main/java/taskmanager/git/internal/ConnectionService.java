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
     * The token of a person for one call, with the generation of the connection it belongs to: the Git account, the time of
     * connecting and the address of the instance. Whatever is kept of an answer is bound to it, so after connecting another
     * Git account, reconnecting or a new address nothing read before is used again, also when a late answer arrives only
     * after the change (TASK-114, F2).
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

    /** Like {@link #call} with the generation of the connection, for answers that are kept. */
    public <T> T callWith(UUID companyId, UUID accountId, Provider provider, Function<Grant, T> work) {
        if (provider.removed()) {
            throw new NotConnected("git.provider_not_found");
        }
        Grant grant = token(companyId, accountId, provider, false);
        try {
            return work.apply(grant);
        } catch (ProviderApi.TokenRejected rejected) {
            Grant renewed = token(companyId, accountId, provider, true);
            try {
                return work.apply(renewed);
            } catch (ProviderApi.TokenRejected again) {
                renewLater(provider.id(), accountId);
                throw new NotConnected("git.connection_renew");
            }
        }
    }

    private Grant token(UUID companyId, UUID accountId, Provider provider, boolean refused) {
        Connection connection = transaction.execute(status -> store.connection(provider.id(), accountId).orElse(null));
        if (connection == null || !connection.companyId().equals(companyId)) {
            throw new NotConnected("git.not_connected");
        }
        if (!connection.active()) {
            throw new NotConnected("git.connection_renew");
        }
        String generation = provider.id() + "|" + provider.baseUrl() + "|" + connection.externalUserId() + "|" + connection.connectedAt();
        Instant now = clock.instant();
        boolean ending = connection.accessExpiresAt() != null && connection.accessExpiresAt().isBefore(now.plus(RENEW_BEFORE));
        if (!refused && !ending) {
            return new Grant(open(Connection.accessPurpose(provider.id(), accountId), connection.accessCipher(), provider.id(), accountId), generation);
        }
        return new Grant(renew(provider, accountId, connection), generation);
    }

    /** Renews under the lock of the connection, so two requests of the same person do not both use the refresh token. */
    private String renew(Provider provider, UUID accountId, Connection seen) {
        return transaction.execute(status -> {
            Connection current = store.lockConnection(provider.id(), accountId).orElse(null);
            if (current == null) {
                throw new NotConnected("git.not_connected");
            }
            if (!current.active()) {
                throw new NotConnected("git.connection_renew");
            }
            Instant now = clock.instant();
            if (!current.updatedAt().equals(seen.updatedAt())) {
                // Another request renewed meanwhile.
                return open(Connection.accessPurpose(provider.id(), accountId), current.accessCipher(), provider.id(), accountId);
            }
            boolean refreshEnded = current.refreshExpiresAt() != null && current.refreshExpiresAt().isBefore(now);
            if (current.refreshCipher() == null || refreshEnded || !provider.secretSet()) {
                store.markRenew(provider.id(), accountId, now);
                return null;
            }
            try {
                String refresh = secrets.open(Connection.refreshPurpose(provider.id(), accountId), current.refreshCipher());
                String secret = secrets.open(Provider.secretPurpose(provider.id()), provider.secretCipher());
                ProviderApi.Tokens tokens = api.refresh(provider, secret, refresh, now);
                store.saveConnection(new Connection(provider.id(), accountId, current.companyId(), current.externalUserId(), Connection.ACTIVE,
                    secrets.seal(Connection.accessPurpose(provider.id(), accountId), tokens.access()),
                    tokens.refresh() == null ? current.refreshCipher() : secrets.seal(Connection.refreshPurpose(provider.id(), accountId), tokens.refresh()),
                    tokens.accessExpiresAt(), tokens.refresh() == null ? current.refreshExpiresAt() : tokens.refreshExpiresAt(),
                    current.connectedAt(), now));
                return tokens.access();
            } catch (ProviderApi.TokenRejected | GitSecrets.Unreadable | GitSecrets.KeyMissing unusable) {
                store.markRenew(provider.id(), accountId, now);
                return null;
            }
        }) instanceof String token ? token : renewNeeded();
    }

    private static String renewNeeded() {
        throw new NotConnected("git.connection_renew");
    }

    private String open(String purpose, byte[] cipher, UUID providerId, UUID accountId) {
        try {
            return secrets.open(purpose, cipher);
        } catch (GitSecrets.Unreadable | GitSecrets.KeyMissing unusable) {
            renewLater(providerId, accountId);
            throw new NotConnected("git.connection_renew");
        }
    }

    private void renewLater(UUID providerId, UUID accountId) {
        transaction.executeWithoutResult(status -> store.markRenew(providerId, accountId, clock.instant()));
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
