package taskmanager.git.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only way the git module talks to a provider (TASK-111, rule 7). It calls addresses the code builds from a registered
 * provider, never one from an input; it never follows a redirect, always checks TLS with the certificates of the JVM plus the
 * own CA of the provider, uses the proxy of the JVM, stops after the time limit of the operation and reads at most 2 MB of an
 * answer. A failure carries a fixed code; texts of the provider never reach a log or an answer.
 */
@Component
class GitHttp {
    static final int MAX_BYTES = 2_000_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final GitProperties properties;
    private final Map<String, HttpClient> clients = new ConcurrentHashMap<>();

    GitHttp(GitProperties properties) {
        this.properties = properties;
    }

    /** An answer of a provider: status, headers and at most {@link #MAX_BYTES} of the body. */
    record Answer(int status, Map<String, List<String>> headers, byte[] body) {
        JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (RuntimeException invalid) {
                throw new Failure("git.unexpected_answer", invalid);
            }
        }

        Optional<String> header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).flatMap(entry -> entry.getValue().stream())
                .findFirst();
        }

        boolean ok() {
            return status >= 200 && status < 300;
        }

        /**
         * An answer that says the rate limit is reached: 429, or 403 with no calls left, with {@code Retry-After} or with the
         * message of the secondary rate limit as GitHub answers (TASK-114, F8). Nothing of the text is passed on.
         */
        boolean limited() {
            if (status == 429) {
                return true;
            }
            if (status != 403) {
                return false;
            }
            return header("x-ratelimit-remaining").map("0"::equals).orElse(false) || header("retry-after").isPresent()
                || body != null && new String(body, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT).contains("secondary rate limit");
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

    Answer get(Provider provider, String url, String token) {
        HttpRequest.Builder request = request(url).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(provider, request.build());
    }

    Answer postForm(Provider provider, String url, Map<String, String> form) {
        String body = form.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
        return send(provider, request(url).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(properties.timeout()).header("Accept", "application/json")
            .header("User-Agent", "task-manager");
    }

    private Answer send(Provider provider, HttpRequest request) {
        HttpClient client = client(provider);
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream in = response.body()) {
                body = in.readNBytes(MAX_BYTES + 1);
            }
            if (body.length > MAX_BYTES) {
                throw new Failure("git.unexpected_answer", null);
            }
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                throw new Failure("git.redirect_refused", null);
            }
            return new Answer(response.statusCode(), response.headers().map(), body);
        } catch (HttpTimeoutException timeout) {
            throw new Failure("git.timeout", timeout);
        } catch (SSLException tls) {
            throw new Failure("git.tls_failed", tls);
        } catch (ConnectException refused) {
            throw new Failure("git.unreachable", refused);
        } catch (IOException failure) {
            throw new Failure(tls(failure) ? "git.tls_failed" : "git.unreachable", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure("git.unreachable", interrupted);
        }
    }

    private static boolean tls(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException || cause instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    private HttpClient client(Provider provider) {
        String key = provider.id() + ":" + provider.version();
        return clients.computeIfAbsent(key, ignored -> {
            clients.keySet().removeIf(other -> other.startsWith(provider.id() + ":"));
            HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.timeout()).proxy(ProxySelector.getDefault());
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
            throw new IllegalStateException("The trust of a Git provider could not be set up", failure);
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
