package taskmanager.ai.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only way the ai module talks to a provider (TASK-125, rule 5). It calls addresses the code builds from a registered
 * access, never one from an input; it never follows a redirect, always checks TLS with the certificates of the JVM plus the
 * own CA of the access, uses the proxy of the JVM, stops after the given time and reads at most 2 MB of an answer. A waiting
 * call ends when its thread is interrupted, which is how a person cancels. A failure carries a fixed code; texts of the
 * provider never reach a log or an answer.
 */
@Component
class AiHttp {
    static final int MAX_BYTES = 2_000_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiProperties properties;
    private final Map<String, HttpClient> clients = new ConcurrentHashMap<>();

    AiHttp(AiProperties properties) {
        this.properties = properties;
    }

    /** An answer of a provider: status, headers and at most {@link #MAX_BYTES} of the body. */
    record Answer(int status, Map<String, List<String>> headers, byte[] body) {
        JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (RuntimeException invalid) {
                throw new Failure("ai.unexpected_answer", invalid);
            }
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** A call that did not get an answer to use: the code names why. */
    static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;

        Failure(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    Answer get(AiProvider provider, String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder request = request(url, headers, timeout).GET();
        return send(provider, request.build(), timeout);
    }

    Answer postJson(AiProvider provider, String url, Map<String, String> headers, String json, Duration timeout) {
        HttpRequest.Builder request = request(url, headers, timeout).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return send(provider, request.build(), timeout);
    }

    /** Checks that a text holds one to five PEM certificates; returns them, or nothing for an invalid text. */
    static Optional<List<X509Certificate>> certificates(String pem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> parsed = factory.generateCertificates(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            List<X509Certificate> result = new ArrayList<>();
            for (var certificate : parsed) {
                if (!(certificate instanceof X509Certificate x509)) {
                    return Optional.empty();
                }
                result.add(x509);
            }
            return result.isEmpty() || result.size() > 5 ? Optional.empty() : Optional.of(result);
        } catch (CertificateException | RuntimeException invalid) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------------------------------------ inside

    private HttpRequest.Builder request(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Accept", "application/json")
            .header("User-Agent", "task-manager");
        headers.forEach(builder::header);
        return builder;
    }

    private Answer send(AiProvider provider, HttpRequest request, Duration timeout) {
        HttpClient client = client(provider);
        CompletableFuture<HttpResponse<InputStream>> pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        try {
            HttpResponse<InputStream> response = pending.get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
            byte[] body;
            try (InputStream in = response.body()) {
                body = in.readNBytes(MAX_BYTES + 1);
            }
            if (body.length > MAX_BYTES) {
                throw new Failure("ai.unexpected_answer", null);
            }
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                throw new Failure("ai.redirect_refused", null);
            }
            return new Answer(response.statusCode(), response.headers().map(), body);
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new Failure("ai.cancelled", interrupted);
        } catch (TimeoutException late) {
            pending.cancel(true);
            throw new Failure("ai.timeout", late);
        } catch (CancellationException cancelled) {
            throw new Failure("ai.cancelled", cancelled);
        } catch (ExecutionException failed) {
            throw failure(failed.getCause() == null ? failed : failed.getCause());
        } catch (IOException failure) {
            throw failure(failure);
        }
    }

    private static Failure failure(Throwable cause) {
        if (cause instanceof Failure known) {
            return known;
        }
        if (cause instanceof HttpTimeoutException) {
            return new Failure("ai.timeout", cause);
        }
        if (cause instanceof SSLException || tls(cause)) {
            return new Failure("ai.tls_failed", cause);
        }
        if (cause instanceof ConnectException) {
            return new Failure("ai.unreachable", cause);
        }
        return new Failure("ai.unreachable", cause);
    }

    private static boolean tls(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException || cause instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    private HttpClient client(AiProvider provider) {
        String key = provider.id() + ":" + provider.version();
        return clients.computeIfAbsent(key, ignored -> {
            clients.keySet().removeIf(other -> other.startsWith(provider.id() + ":"));
            HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.connectTimeout()).proxy(ProxySelector.getDefault());
            if (provider.caCertificate() != null) {
                builder.sslContext(context(provider.id(), provider.caCertificate()));
            }
            return builder.build();
        });
    }

    /** The certificates of the JVM plus the own CA of the provider. */
    private static SSLContext context(UUID providerId, String pem) {
        try {
            TrustManagerFactory defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaults.init((KeyStore) null);
            KeyStore own = KeyStore.getInstance(KeyStore.getDefaultType());
            own.load(null, null);
            int n = 0;
            for (X509Certificate certificate : certificates(pem).orElse(List.of())) {
                own.setCertificateEntry("ca-" + providerId + "-" + n++, certificate);
            }
            TrustManagerFactory custom = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            custom.init(own);
            X509TrustManager first = x509(defaults.getTrustManagers());
            X509TrustManager second = x509(custom.getTrustManagers());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { new Either(first, second) }, null);
            return context;
        } catch (GeneralSecurityException | IOException failure) {
            throw new IllegalStateException("The trust of an AI provider could not be set up", failure);
        }
    }

    private static X509TrustManager x509(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new IllegalStateException("No X509 trust manager");
    }

    /** Trusts a server that one of the two trusts; the client side is never used. */
    private record Either(X509TrustManager first, X509TrustManager second) implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("No client certificates");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                first.checkServerTrusted(chain, authType);
            } catch (CertificateException notByTheJvm) {
                second.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<>(List.of(first.getAcceptedIssuers()));
            all.addAll(List.of(second.getAcceptedIssuers()));
            return all.toArray(X509Certificate[]::new);
        }
    }
}
