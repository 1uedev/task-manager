package taskmanager.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-111: provider instances, the own connection over OAuth with PKCE, renewal and the end of tokens, and the released
 * repositories of a project through the real HTTP interface with a real PostgreSQL. GitHub and GitLab are replaced by small
 * HTTP servers in this test that answer like their APIs; a third one speaks HTTPS with a certificate of its own CA.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class GitApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final String APP_SECRET = "app-secret-4f9c2a";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Stub GITHUB = new Stub(false);
    private static final Stub GITLAB = new Stub(true);
    private static final Tls TLS = new Tls();
    private static final String SHA = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678";

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
        registry.add("taskmanager.git.secret-key", () -> KEY);
        registry.add("taskmanager.git.public-url", () -> "https://tm.example/");
        registry.add("taskmanager.git.allow-http", () -> "true");
        registry.add("taskmanager.git.timeout", () -> "2s");
        registry.add("taskmanager.git.github-url", GITHUB::url);
        registry.add("taskmanager.git.github-api-url", () -> GITHUB.url() + "/api");
        registry.add("taskmanager.git.gitlab-com-url", GITLAB::url);
    }

    @AfterAll
    static void stop() {
        GITHUB.server.stop(0);
        GITLAB.server.stop(0);
        TLS.server.stop(0);
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;

    // ---------------------------------------------------------------- the providers of this test

    /** One request a stub provider saw: method, path with query, the bearer token and the form. */
    record Seen(String method, String path, String token, Map<String, String> form) {
    }

    /** GitHub (API under /api) or GitLab (API under /api/v4) as far as the Git link uses them. */
    static final class Stub {
        final HttpServer server;
        final boolean gitlab;
        final List<Seen> seen = new CopyOnWriteArrayList<>();
        final Set<String> tokens = Collections.synchronizedSet(new HashSet<>());
        final Set<String> refreshTokens = Collections.synchronizedSet(new HashSet<>());
        final AtomicInteger issued = new AtomicInteger();
        volatile String version = "16.4.1-ee";
        volatile boolean refuseRefresh;
        /** Tokens whose person sees no repository and no object, as after a withdrawn access (TASK-112). */
        final Set<String> denied = Collections.synchronizedSet(new HashSet<>());
        /** Every read of a repository or an object answers with the rate limit (TASK-112). */
        volatile boolean limited;
        /** Every read of a repository or an object answers 403 with the secondary rate limit of GitHub (TASK-114, F8). */
        volatile boolean secondaryLimited;
        /** The state of pull request 7 or merge request 3: open, draft, merged or closed. */
        volatile String changeState = "open";
        /** Milliseconds every answer waits, and the most requests seen at the same time (TASK-112). */
        volatile long delay;
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();

        Stub(boolean gitlab) {
            this.gitlab = gitlab;
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", exchange -> {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }
                    handle(exchange);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        List<Seen> seen(String prefix) {
            return seen.stream().filter(s -> s.path().startsWith(prefix)).toList();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
            Map<String, String> form = form(exchange.getRequestBody());
            seen.add(new Seen(exchange.getRequestMethod(), path + (query == null ? "" : "?" + query), token, form));
            String api = gitlab ? "/api/v4" : "/api";
            if (path.equals(gitlab ? "/oauth/token" : "/login/oauth/access_token")) {
                answer(exchange, 200, tokens(form));
                return;
            }
            if (gitlab && path.equals(api + "/version")) {
                if (token == null) {
                    answer(exchange, 401, Map.of("message", "401 Unauthorized"));
                } else {
                    answer(exchange, 200, Map.of("version", version));
                }
                return;
            }
            if (!gitlab && path.equals(api + "/meta")) {
                answer(exchange, 200, Map.of("verifiable_password_authentication", false));
                return;
            }
            if (token == null || !tokens.contains(token)) {
                answer(exchange, 401, Map.of("message", "Bad credentials"));
                return;
            }
            if (path.startsWith(api + "/repos/") || path.startsWith(api + "/repositories/") || path.startsWith(api + "/projects/")) {
                if (limited) {
                    answer(exchange, 429, Map.of("message", "rate limit"));
                    return;
                }
                if (secondaryLimited) {
                    exchange.getResponseHeaders().add("x-ratelimit-remaining", "4000");
                    answer(exchange, 403, Map.of("message", "You have exceeded a secondary rate limit. Please wait a few minutes before you try again."));
                    return;
                }
                if (denied.contains(token)) {
                    answer(exchange, 404, Map.of("message", "Not Found"));
                    return;
                }
                if (object(exchange, path, api)) {
                    return;
                }
            }
            if (path.equals(api + "/user")) {
                answer(exchange, 200, gitlab ? Map.of("id", 7, "username", "gl-user") : Map.of("id", 4711, "login", "octo"));
            } else if (!gitlab && path.equals(api + "/user/installations")) {
                answer(exchange, 200, Map.of("installations", List.of(Map.of("id", 1))));
            } else if (!gitlab && path.equals(api + "/user/installations/1/repositories")) {
                answer(exchange, 200, Map.of("repositories", List.of(repo(101, "alpha", "acme/alpha"), repo(102, "beta", "acme/be ta"))));
            } else if (gitlab && path.equals(api + "/projects")) {
                answer(exchange, 200, List.of(repo(201, "gamma", "grp/gamma")));
            } else if (path.equals(api + (gitlab ? "/projects/" : "/repositories/") + "999")) {
                exchange.getResponseHeaders().add("Location", "http://localhost:1/elsewhere");
                answer(exchange, 302, Map.of());
            } else if (path.startsWith(api + (gitlab ? "/projects/" : "/repositories/"))) {
                String id = path.substring(path.lastIndexOf('/') + 1);
                switch (id) {
                    case "101" -> answer(exchange, 200, repo(101, "alpha", "acme/alpha"));
                    case "102" -> answer(exchange, 200, repo(102, "beta", "acme/be ta"));
                    case "201" -> answer(exchange, 200, repo(201, "gamma", "grp/gamma"));
                    default -> answer(exchange, 404, Map.of("message", "Not Found"));
                }
            } else {
                answer(exchange, 404, Map.of("message", "Not Found"));
            }
        }

        /** Repositories by path, commits, pull requests and merge requests as far as TASK-112 reads them. */
        private boolean object(HttpExchange exchange, String path, String api) throws IOException {
            java.util.regex.Matcher commit = java.util.regex.Pattern.compile(Pattern.quote(api)
                + (gitlab ? "/projects/(\\d+)/repository/commits/([0-9a-f]+)" : "/repositories/(\\d+)/commits/([0-9a-f]+)")).matcher(path);
            java.util.regex.Matcher change = java.util.regex.Pattern.compile(Pattern.quote(api)
                + (gitlab ? "/projects/(\\d+)/merge_requests/(\\d+)" : "/repositories/(\\d+)/pulls/(\\d+)")).matcher(path);
            String repository = gitlab ? "201" : "101";
            if (path.equals(api + (gitlab ? "/projects/grp%2Fgamma" : "/repos/acme/alpha"))) {
                answer(exchange, 200, gitlab ? repo(201, "gamma", "grp/gamma") : repo(101, "alpha", "acme/alpha"));
            } else if (!gitlab && path.equals(api + "/repos/acme/beta")) {
                answer(exchange, 200, repo(102, "beta", "acme/beta"));
            } else if (commit.matches()) {
                if (!commit.group(1).equals(repository) || !SHA.startsWith(commit.group(2))) {
                    answer(exchange, gitlab ? 404 : 422, Map.of("message", "No commit found"));
                } else if (gitlab) {
                    answer(exchange, 200, Map.of("id", SHA, "title", "Anmeldung repariert", "message", "Anmeldung repariert\n\nDetails",
                        "author_name", "Gita Lab", "committed_date", "2026-11-12T09:30:00.000+01:00"));
                } else {
                    answer(exchange, 200, Map.of("sha", SHA, "commit", Map.of("message", "Anmeldung repariert\n\nDetails",
                        "author", Map.of("name", "Octo Cat", "date", "2026-11-12T08:30:00Z")), "html_url", "https://evil.example/c"));
                }
            } else if (change.matches()) {
                String number = gitlab ? "3" : "7";
                if (!change.group(1).equals(repository) || !change.group(2).equals(number)) {
                    answer(exchange, 404, Map.of("message", "Not Found"));
                    return true;
                }
                Map<String, Object> json = new LinkedHashMap<>();
                json.put(gitlab ? "iid" : "number", Integer.parseInt(number));
                json.put("title", gitlab ? "Merge Request Anmeldung" : "Pull Request Anmeldung");
                json.put("draft", "draft".equals(changeState));
                if (gitlab) {
                    json.put("state", switch (changeState) {
                        case "merged" -> "merged";
                        case "closed" -> "closed";
                        default -> "opened";
                    });
                    json.put("author", Map.of("username", "gl-user"));
                } else {
                    json.put("state", "merged".equals(changeState) || "closed".equals(changeState) ? "closed" : "open");
                    json.put("merged_at", "merged".equals(changeState) ? "2026-11-13T10:00:00Z" : null);
                    json.put("user", Map.of("login", "octo"));
                }
                answer(exchange, 200, json);
            } else {
                return false;
            }
            return true;
        }

        private Map<String, Object> repo(int id, String name, String path) {
            Map<String, Object> repo = new LinkedHashMap<>();
            repo.put("id", id);
            repo.put("name", name);
            repo.put(gitlab ? "path_with_namespace" : "full_name", path);
            repo.put(gitlab ? "web_url" : "html_url", "https://evil.example/" + name);
            return repo;
        }

        private Map<String, Object> tokens(Map<String, String> form) {
            if ("refresh_token".equals(form.get("grant_type"))) {
                if (refuseRefresh || !refreshTokens.remove(form.get("refresh_token"))) {
                    return Map.of("error", "bad_refresh_token");
                }
            } else if (!APP_SECRET.equals(form.get("client_secret")) || "wrong".equals(form.get("code"))) {
                return Map.of("error", "bad_verification_code");
            }
            int n = issued.incrementAndGet();
            String access = (gitlab ? "gl-" : "gh-") + n;
            String refresh = (gitlab ? "glr-" : "ghr-") + n;
            tokens.add(access);
            refreshTokens.add(refresh);
            return Map.of("access_token", access, "refresh_token", refresh, "expires_in", 28800, "token_type", "bearer");
        }
    }

    /** A GitLab address over HTTPS with a certificate for localhost that only its own CA trusts. */
    static final class Tls {
        final HttpsServer server;
        final String certificate;

        Tls() {
            try {
                Path dir = Files.createTempDirectory("git-tls");
                Path store = dir.resolve("stub.p12");
                String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
                run(keytool, "-genkeypair", "-alias", "stub", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost",
                    "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12", "-keystore", store.toString(),
                    "-storepass", "changeit", "-keypass", "changeit");
                certificate = run(keytool, "-exportcert", "-rfc", "-alias", "stub", "-keystore", store.toString(), "-storepass", "changeit");
                KeyStore keys = KeyStore.getInstance("PKCS12");
                try (InputStream in = Files.newInputStream(store)) {
                    keys.load(in, "changeit".toCharArray());
                }
                KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                factory.init(keys, "changeit".toCharArray());
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(factory.getKeyManagers(), null, null);
                server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.setHttpsConfigurator(new HttpsConfigurator(context));
                server.createContext("/", exchange -> answer(exchange, 401, Map.of("message", "401 Unauthorized")));
                server.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        String url() {
            return "https://localhost:" + server.getAddress().getPort();
        }

        private static String run(String... command) throws IOException, InterruptedException {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException(output);
            }
            return output.substring(output.indexOf("-----BEGIN") < 0 ? 0 : output.indexOf("-----BEGIN"));
        }
    }

    private static void answer(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> form(InputStream body) throws IOException {
        Map<String, String> form = new HashMap<>();
        String text = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        for (String pair : text.split("&")) {
            int at = pair.indexOf('=');
            if (at > 0) {
                form.put(URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, String body, String etag, String location) {
        JsonNode json() {
            return JSON.readTree(body);
        }

        String code() {
            return json().get("code").asString();
        }
    }

    final class Browser {
        private final HttpClient http = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();
        UUID id;

        Response send(String method, String path, Object body, String ifMatch) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
                if (body != null) {
                    request.header("Content-Type", "application/json");
                }
                request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
                if (ifMatch != null) {
                    request.header("If-Match", ifMatch);
                }
                if (!cookies.isEmpty()) {
                    List<String> pairs = new ArrayList<>();
                    cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                    request.header("Cookie", String.join("; ", pairs));
                }
                String csrf = cookies.get("__Host-tm-csrf");
                if (csrf != null) {
                    request.header("X-CSRF-Token", csrf);
                }
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                for (String cookie : response.headers().allValues("set-cookie")) {
                    String pair = cookie.substring(0, cookie.indexOf(';') < 0 ? cookie.length() : cookie.indexOf(';'));
                    String name = pair.substring(0, pair.indexOf('='));
                    if (cookie.toLowerCase().contains("max-age=0")) {
                        cookies.remove(name);
                    } else {
                        cookies.put(name, pair.substring(pair.indexOf('=') + 1));
                    }
                }
                return new Response(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(null),
                    response.headers().firstValue("Location").orElse(null));
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        Response get(String path) {
            return send("GET", path, null, null);
        }
    }

    private Browser login(UUID id, String username, String oneTimePassword) {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        Response login = browser.send("POST", "/api/v1/session", Map.of("username", username, "password", oneTimePassword), null);
        assertEquals(200, login.status(), login.body());
        Response changed = browser.send("POST", "/api/v1/session/password", Map.of("currentPassword", oneTimePassword, "newPassword", STRONG), null);
        assertEquals(200, changed.status(), changed.body());
        browser.id = id;
        return browser;
    }

    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", "git-" + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        return login(issued.account().id(), issued.account().username(), issued.oneTimePassword());
    }

    /** Grants project actions and field rules such as {@code ticket:READ:git} in a group of its own. */
    private void grant(UUID account, UUID project, String... actions) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseGet(companies::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            UUID group = UUID.randomUUID();
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "fixture-" + group);
            for (String action : actions) {
                if (!action.contains(":")) {
                    jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
                        companyId, group, project, action);
                    continue;
                }
                // "ticket:READ:git": a field rule of a resource type (TASK-112).
                String[] parts = action.split(":");
                jdbc.update("""
                        insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                        values (?, ?, ?, ?, ?, ?, 'ALLOW')
                        """, companyId, group, project, parts[0], parts[2], parts[1]);
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
    }

    // ---------------------------------------------------------------- fixtures

    private static Map<String, Object> provider(String kind, String name, String baseUrl, String secret, String ca) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("name", name);
        body.put("baseUrl", baseUrl);
        body.put("clientId", "client-" + name.hashCode());
        body.put("clientSecret", secret);
        body.put("caCertificate", ca);
        return body;
    }

    private UUID createProvider(Browser admin, String kind, String baseUrl) {
        Response created = admin.send("POST", "/api/v1/git/admin/providers",
            provider(kind, kind + " " + COUNTER.incrementAndGet(), baseUrl, APP_SECRET, null), null);
        assertEquals(201, created.status(), created.body());
        return UUID.fromString(created.json().get("id").asString());
    }

    private static Map<String, String> query(String url) {
        Map<String, String> query = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            int at = pair.indexOf('=');
            query.put(pair.substring(0, at), URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
        }
        return query;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** Starts the connection and comes back from the provider with the code; returns the address the browser goes on to. */
    private String connect(Browser browser, UUID provider, String code) {
        Response started = browser.send("POST", "/api/v1/git/providers/" + provider + "/connect", null, null);
        assertEquals(200, started.status(), started.body());
        String state = query(started.json().get("authorizeUrl").asString()).get("state");
        return callback(browser, state, code);
    }

    private String callback(Browser browser, String state, String code) {
        Response back = browser.get("/api/v1/git/oauth/callback?state=" + encode(state) + "&code=" + encode(code));
        assertEquals(303, back.status(), back.body());
        return back.location();
    }

    private record Setup(Browser admin, Browser manager, UUID project, UUID github) {
    }

    /** A provider, a project and a project manager connected to GitHub. */
    private Setup setup() {
        Browser admin = person(AdministrationRight.GIT_MANAGE, AdministrationRight.PROJECTS_MANAGE);
        UUID github = createProvider(admin, "github", null);
        int n = COUNTER.incrementAndGet();
        Response project = admin.send("POST", "/api/v1/projects", Map.of("key", "GT" + n, "name", "Git " + n), null);
        assertEquals(201, project.status(), project.body());
        UUID projectId = UUID.fromString(project.json().get("id").asString());
        Browser manager = person();
        join(admin, projectId, manager, "project.read", "project.manage");
        assertTrue(connect(manager, github, "c" + n).endsWith("connected=" + github));
        return new Setup(admin, manager, projectId, github);
    }

    /** Makes the person a member of the project and gives the actions there. */
    private void join(Browser admin, UUID project, Browser person, String... actions) {
        Response current = admin.get("/api/v1/projects/" + project);
        assertEquals(200, current.status(), current.body());
        Response added = admin.send("PUT", "/api/v1/projects/" + project + "/members/" + person.id, null, current.etag());
        assertEquals(200, added.status(), added.body());
        grant(person.id, project, actions);
    }

    private Response list(Browser browser, Setup setup) {
        return browser.get("/api/v1/projects/" + setup.project() + "/git/repositories");
    }

    private Response release(Browser browser, Setup setup, String externalId) {
        return browser.send("POST", "/api/v1/projects/" + setup.project() + "/git/repositories",
            Map.of("providerId", setup.github(), "externalId", externalId), null);
    }

    // ---------------------------------------------------------------- tests

    @Test
    void administrationKeepsTheSecretWriteOnlyAndChecksVersions(CapturedOutput output) {
        Browser admin = person(AdministrationRight.GIT_MANAGE);
        Browser other = person();
        String name = "GitHub " + COUNTER.incrementAndGet();
        Response created = admin.send("POST", "/api/v1/git/admin/providers", provider("github", name, null, APP_SECRET, null), null);
        assertEquals(201, created.status(), created.body());
        assertFalse(created.body().contains(APP_SECRET));
        assertTrue(created.json().get("secretSet").asBoolean());
        assertEquals(GITHUB.url(), created.json().get("baseUrl").asString());
        assertEquals("\"1\"", created.etag());
        UUID id = UUID.fromString(created.json().get("id").asString());

        Response listed = admin.get("/api/v1/git/admin/providers");
        assertEquals(200, listed.status());
        assertFalse(listed.body().contains(APP_SECRET));
        byte[] cipher = jdbc.queryForObject("select client_secret_cipher from git_provider where id = ?", byte[].class, id);
        assertFalse(new String(cipher, StandardCharsets.ISO_8859_1).contains(APP_SECRET));
        String audit = String.join(" ", jdbc.queryForList("select detail::text from git_audit_event where subject_id = ?", String.class, id));
        assertTrue(audit.contains(name));
        assertFalse(audit.contains(APP_SECRET));

        assertEquals(409, admin.send("POST", "/api/v1/git/admin/providers", provider("github", name, null, APP_SECRET, null), null).status());
        Response invalid = admin.send("POST", "/api/v1/git/admin/providers", provider("gitlab", "Eigenes", "ftp://git.example", APP_SECRET, null), null);
        assertEquals(400, invalid.status());
        assertTrue(invalid.body().contains("baseUrl.invalid"));
        Response badCa = admin.send("POST", "/api/v1/git/admin/providers", provider("gitlab", "Eigenes", GITLAB.url(), APP_SECRET, "kein Zertifikat"), null);
        assertEquals(400, badCa.status());
        assertTrue(badCa.body().contains("caCertificate.invalid"));

        Map<String, Object> change = provider("github", name + " neu", null, null, null);
        change.remove("clientSecret");
        Response unconditional = admin.send("PUT", "/api/v1/git/admin/providers/" + id, change, null);
        assertEquals(428, unconditional.status());
        assertEquals("git.precondition_required", unconditional.code());
        assertEquals(412, admin.send("PUT", "/api/v1/git/admin/providers/" + id, change, "\"7\"").status());
        Response changed = admin.send("PUT", "/api/v1/git/admin/providers/" + id, change, "\"1\"");
        assertEquals(200, changed.status(), changed.body());
        assertTrue(changed.json().get("secretSet").asBoolean(), "an omitted secret is kept");
        change.put("kind", "gitlab");
        assertTrue(admin.send("PUT", "/api/v1/git/admin/providers/" + id, change, changed.etag()).body().contains("kind.unchangeable"));

        Response check = admin.send("POST", "/api/v1/git/admin/providers/" + id + "/check", null, null);
        assertEquals(200, check.status(), check.body());
        assertTrue(check.json().get("ok").asBoolean(), check.body());

        assertEquals(403, other.get("/api/v1/git/admin/providers").status());
        assertEquals(403, other.send("POST", "/api/v1/git/admin/providers", provider("github", "X", null, APP_SECRET, null), null).status());
        Response visible = other.get("/api/v1/git/providers");
        assertEquals(200, visible.status());
        assertTrue(visible.body().contains(name + " neu"));
        assertFalse(visible.body().contains("client"));

        Response status = other.get("/api/v1/git/status");
        assertEquals("https://tm.example/api/v1/git/oauth/callback", status.json().get("callbackUrl").asString());
        assertTrue(status.json().get("secretKey").asBoolean());
        assertFalse(status.json().get("manage").asBoolean());

        assertEquals(204, admin.send("DELETE", "/api/v1/git/admin/providers/" + id, null, "\"2\"").status());
        assertFalse(other.get("/api/v1/git/providers").body().contains(name));
        assertFalse(output.getAll().contains(APP_SECRET));
    }

    @Test
    void connectingUsesPkceAStateOnceAndStoresTokensEncrypted(CapturedOutput output) {
        Browser admin = person(AdministrationRight.GIT_MANAGE);
        UUID github = createProvider(admin, "github", null);
        Browser person = person();
        Browser stranger = person();

        Response started = person.send("POST", "/api/v1/git/providers/" + github + "/connect", null, null);
        assertEquals(200, started.status(), started.body());
        String url = started.json().get("authorizeUrl").asString();
        assertTrue(url.startsWith(GITHUB.url() + "/login/oauth/authorize?"), url);
        Map<String, String> query = query(url);
        assertEquals("S256", query.get("code_challenge_method"));
        assertEquals("https://tm.example/api/v1/git/oauth/callback", query.get("redirect_uri"));

        assertTrue(callback(stranger, query.get("state"), "abc").endsWith("error=git.state_invalid"), "another person cannot finish it");
        Response again = person.send("POST", "/api/v1/git/providers/" + github + "/connect", null, null);
        query = query(again.json().get("authorizeUrl").asString());
        int before = GITHUB.seen("/login/oauth/access_token").size();
        assertEquals("https://tm.example/#/git?connected=" + github, callback(person, query.get("state"), "abc"));
        Seen exchange = GITHUB.seen("/login/oauth/access_token").get(before);
        assertEquals(query.get("code_challenge"), challenge(exchange.form().get("code_verifier")));
        assertEquals(APP_SECRET, exchange.form().get("client_secret"));
        assertTrue(callback(person, query.get("state"), "abc").endsWith("error=git.state_invalid"), "a state counts once");

        Response providers = person.get("/api/v1/git/providers");
        assertTrue(providers.body().contains("\"connection\":\"active\""), providers.body());
        String token = "gh-" + GITHUB.issued.get();
        byte[] cipher = jdbc.queryForObject("select access_cipher from git_connection where provider_id = ? and account_id = ?", byte[].class,
            github, person.id);
        assertFalse(new String(cipher, StandardCharsets.ISO_8859_1).contains(token));
        assertEquals("4711", jdbc.queryForObject("select external_user_id from git_connection where account_id = ?", String.class, person.id));

        Response denied = person.send("POST", "/api/v1/git/providers/" + github + "/connect", null, null);
        assertTrue(callback(person, query(denied.json().get("authorizeUrl").asString()).get("state"), "wrong").endsWith("error=git.denied"));

        assertEquals(204, person.send("DELETE", "/api/v1/git/providers/" + github + "/connection", null, null).status());
        assertEquals(0, jdbc.queryForObject("select count(*) from git_connection where account_id = ?", Integer.class, person.id));
        assertFalse(output.getAll().contains(token));
        assertFalse(output.getAll().contains(APP_SECRET));
    }

    @Test
    void aGitlabOfItsOwnNeedsVersion16() {
        Browser admin = person(AdministrationRight.GIT_MANAGE);
        UUID gitlab = createProvider(admin, "gitlab", GITLAB.url() + "/");
        Browser person = person();
        try {
            GITLAB.version = "15.11.3";
            assertTrue(connect(person, gitlab, "x").endsWith("error=git.version_unsupported"));
            assertEquals(0, jdbc.queryForObject("select count(*) from git_connection where account_id = ?", Integer.class, person.id));
        } finally {
            GITLAB.version = "16.4.1-ee";
        }
        assertTrue(connect(person, gitlab, "y").endsWith("connected=" + gitlab));
        Seen authorize = GITLAB.seen("/oauth/token").getLast();
        assertEquals("authorization_code", authorize.form().get("grant_type"));
        Response check = admin.send("POST", "/api/v1/git/admin/providers/" + gitlab + "/check", null, null);
        assertTrue(check.json().get("ok").asBoolean(), check.body());
    }

    @Test
    void releasingNeedsARepositoryTheOwnConnectionMayRead() {
        Setup setup = setup();
        Response candidates = setup.manager().get("/api/v1/projects/" + setup.project() + "/git/candidates?provider=" + setup.github() + "&q=al");
        assertEquals(200, candidates.status(), candidates.body());
        assertEquals(1, candidates.json().size());
        assertEquals(GITHUB.url() + "/acme/alpha", candidates.json().get(0).get("url").asString(), "the address is built, not taken from the answer");

        Response released = release(setup.manager(), setup, "101");
        assertEquals(201, released.status(), released.body());
        assertEquals(409, release(setup.manager(), setup, "101").status());
        Response hidden = release(setup.manager(), setup, "103");
        assertEquals(404, hidden.status());
        assertEquals("git.repository_not_found", hidden.code());
        Response redirected = release(setup.manager(), setup, "999");
        assertEquals(502, redirected.status());
        assertEquals("git.redirect_refused", redirected.code());
        assertEquals(400, release(setup.manager(), setup, "../1").status());
        assertEquals(201, release(setup.manager(), setup, "102").status());

        Response own = list(setup.manager(), setup);
        assertEquals(200, own.status(), own.body());
        assertTrue(own.json().get("manageable").asBoolean());
        JsonNode alpha = own.json().get("repositories").get(0);
        assertTrue(alpha.get("available").asBoolean());
        assertEquals("acme/alpha", alpha.get("repository").get("path").asString());
        assertTrue(own.json().get("repositories").get(1).get("repository").get("url").isNull(), "an unsafe path gives no address");

        Browser reader = person();
        join(setup.admin(), setup.project(), reader, "project.read");
        Response unconnected = list(reader, setup);
        assertEquals(200, unconnected.status(), unconnected.body());
        assertFalse(unconnected.json().get("manageable").asBoolean());
        JsonNode entry = unconnected.json().get("repositories").get(0);
        assertFalse(entry.get("available").asBoolean());
        assertEquals("git.not_connected", entry.get("reason").asString());
        assertTrue(entry.get("repository").isNull());
        assertFalse(unconnected.body().contains("alpha"));
        assertEquals(403, release(reader, setup, "101").status());
        assertEquals(403, reader.get("/api/v1/projects/" + setup.project() + "/git/candidates?provider=" + setup.github()).status());

        int calls = GITHUB.seen.size();
        Browser outsider = person();
        assertEquals(404, list(outsider, setup).status());
        assertEquals(GITHUB.seen.size(), calls, "no call for someone who cannot see the project");

        UUID releaseId = UUID.fromString(alpha.get("id").asString());
        assertEquals(403, reader.send("DELETE", "/api/v1/projects/" + setup.project() + "/git/repositories/" + releaseId, null, null).status());
        assertEquals(204, setup.manager().send("DELETE", "/api/v1/projects/" + setup.project() + "/git/repositories/" + releaseId, null, null).status());
        assertEquals(1, list(setup.manager(), setup).json().get("repositories").size());
    }

    @Test
    void everyReadUsesTheTokenOfTheViewer() {
        Setup setup = setup();
        assertEquals(201, release(setup.manager(), setup, "101").status());
        Browser other = person();
        join(setup.admin(), setup.project(), other, "project.read");
        assertTrue(connect(other, setup.github(), "o").endsWith("connected=" + setup.github()));
        String otherToken = "gh-" + GITHUB.issued.get();
        int before = GITHUB.seen.size();
        assertTrue(list(other, setup).json().get("repositories").get(0).get("available").asBoolean());
        List<Seen> reads = GITHUB.seen.subList(before, GITHUB.seen.size());
        assertFalse(reads.isEmpty());
        assertTrue(reads.stream().allMatch(seen -> otherToken.equals(seen.token())), reads.toString());
    }

    @Test
    void anEndingTokenIsRenewedAndARefusedRenewalAsksToReconnect() {
        Setup setup = setup();
        assertEquals(201, release(setup.manager(), setup, "101").status());
        jdbc.update("update git_connection set access_expires_at = now() - interval '1 minute' where account_id = ?", setup.manager().id);
        int refreshes = GITHUB.seen("/login/oauth/access_token").size();
        assertTrue(list(setup.manager(), setup).json().get("repositories").get(0).get("available").asBoolean());
        Seen refresh = GITHUB.seen("/login/oauth/access_token").get(refreshes);
        assertEquals("refresh_token", refresh.form().get("grant_type"));
        assertEquals("gh-" + GITHUB.issued.get(), GITHUB.seen.getLast().token());

        GITHUB.tokens.clear();
        GITHUB.refuseRefresh = true;
        try {
            JsonNode entry = list(setup.manager(), setup).json().get("repositories").get(0);
            assertEquals("git.connection_renew", entry.get("reason").asString());
            assertEquals("renew", jdbc.queryForObject("select state from git_connection where account_id = ?", String.class, setup.manager().id));
            assertNull(jdbc.queryForObject("select access_cipher from git_connection where account_id = ?", byte[].class, setup.manager().id));
        } finally {
            GITHUB.refuseRefresh = false;
        }
        assertTrue(connect(setup.manager(), setup.github(), "again").endsWith("connected=" + setup.github()));
        assertTrue(list(setup.manager(), setup).json().get("repositories").get(0).get("available").asBoolean());
    }

    @Test
    void deactivationAndRemovalEndTheTokensAtOnce() {
        Setup setup = setup();
        Browser second = person();
        assertTrue(connect(second, setup.github(), "s").endsWith("connected=" + setup.github()));
        accounts.deactivate("test", setup.manager().id);
        assertEquals(0, jdbc.queryForObject("select count(*) from git_connection where account_id = ?", Integer.class, setup.manager().id));
        assertEquals(1, jdbc.queryForObject("select count(*) from git_audit_event where operation = 'connection.ended' and subject_id = ?",
            Integer.class, setup.manager().id));

        Map<String, Object> change = provider("github", "GitHub umgezogen " + COUNTER.incrementAndGet(), null, null, null);
        change.remove("clientSecret");
        change.put("clientId", "another-app");
        Response current = setup.admin().get("/api/v1/git/admin/providers");
        String etag = null;
        for (JsonNode node : current.json()) {
            if (node.get("id").asString().equals(setup.github().toString())) {
                etag = "\"" + node.get("version").asLong() + "\"";
            }
        }
        assertNotNull(etag);
        assertEquals(200, setup.admin().send("PUT", "/api/v1/git/admin/providers/" + setup.github(), change, etag).status());
        assertEquals(0, jdbc.queryForObject("select count(*) from git_connection where provider_id = ?", Integer.class, setup.github()));
    }

    @Test
    void certificatesAreAlwaysCheckedAndAnOwnCaCanBeTrusted() {
        Browser admin = person(AdministrationRight.GIT_MANAGE);
        UUID untrusted = createProvider(admin, "gitlab", TLS.url());
        Response refused = admin.send("POST", "/api/v1/git/admin/providers/" + untrusted + "/check", null, null);
        assertFalse(refused.json().get("ok").asBoolean(), refused.body());
        assertEquals("git.tls_failed", refused.json().get("code").asString());

        Response trusted = admin.send("POST", "/api/v1/git/admin/providers",
            provider("gitlab", "Eigenes " + COUNTER.incrementAndGet(), TLS.url(), APP_SECRET, TLS.certificate), null);
        assertEquals(201, trusted.status(), trusted.body());
        Response check = admin.send("POST", "/api/v1/git/admin/providers/" + trusted.json().get("id").asString() + "/check", null, null);
        assertTrue(check.json().get("ok").asBoolean(), check.body());
    }

    @Test
    void portalAccountsAndUnfinishedLoginsCannotUseGit() {
        Browser admin = person(AdministrationRight.HELPDESK_MANAGE);
        Response customer = admin.send("POST", "/api/v1/helpdesk/customers", Map.of("name", "Nordwerk " + COUNTER.incrementAndGet(), "projects", List.of()), null);
        assertEquals(201, customer.status(), customer.body());
        var issued = accounts.createPortal("test", "kunde-" + COUNTER.incrementAndGet(), "Kunde",
            UUID.fromString(customer.json().get("id").asString()), null);
        Browser portal = login(issued.account().id(), issued.account().username(), issued.oneTimePassword());
        assertEquals(403, portal.get("/api/v1/git/providers").status());
        assertEquals(403, portal.get("/api/v1/git/status").status());

        var fresh = accounts.create("test", "git-" + COUNTER.incrementAndGet(), null);
        Browser unfinished = new Browser();
        unfinished.get("/api/v1/session");
        assertEquals(200, unfinished.send("POST", "/api/v1/session", Map.of("username", fresh.account().username(), "password", fresh.oneTimePassword()), null).status());
        Response blocked = unfinished.get("/api/v1/git/providers");
        assertEquals(403, blocked.status());
        assertEquals("auth.password_change_required", blocked.code());
        assertEquals(401, new Browser().get("/api/v1/git/providers").status());
    }

    // ---------------------------------------------------------------- links at tickets and requirements (TASK-112)

    private static final String[] WORKER = { "ticket.read", "ticket.create", "ticket.edit", "requirement.read", "requirement.edit",
        "ticket:READ:title", "ticket:WRITE:title", "ticket:READ:type", "ticket:WRITE:type", "ticket:READ:status", "ticket:READ:git",
        "ticket:WRITE:git", "requirement:READ:title", "requirement:WRITE:title", "requirement:READ:git", "requirement:WRITE:git" };

    private record Links(Setup setup, UUID gitlab, UUID gitlabRelease, Browser worker, long ticket, UUID requirement) {
        String ticketPath() {
            return "/api/v1/projects/" + setup.project() + "/tickets/" + ticket + "/git";
        }

        String requirementPath() {
            return "/api/v1/requirements/" + requirement + "/git";
        }
    }

    /** A project with alpha of GitHub and gamma of GitLab released, a ticket, a requirement and a worker connected to both. */
    private Links links() {
        Setup setup = setup();
        UUID gitlab = createProvider(setup.admin(), "gitlab_com", null);
        assertTrue(connect(setup.manager(), gitlab, "m" + COUNTER.incrementAndGet()).endsWith("connected=" + gitlab));
        assertEquals(201, release(setup.manager(), setup, "101").status());
        Response gamma = setup.manager().send("POST", "/api/v1/projects/" + setup.project() + "/git/repositories",
            Map.of("providerId", gitlab, "externalId", "201"), null);
        assertEquals(201, gamma.status(), gamma.body());
        Browser worker = person();
        join(setup.admin(), setup.project(), worker, WORKER);
        assertTrue(connect(worker, setup.github(), "w" + COUNTER.incrementAndGet()).endsWith("connected=" + setup.github()));
        assertTrue(connect(worker, gitlab, "w" + COUNTER.incrementAndGet()).endsWith("connected=" + gitlab));
        Response ticket = worker.send("POST", "/api/v1/projects/" + setup.project() + "/tickets", Map.of("type", "task", "title", "Anmeldung"), null);
        assertEquals(201, ticket.status(), ticket.body());
        Response requirement = worker.send("POST", "/api/v1/requirements/projects/" + setup.project() + "/requirements",
            Map.of("title", "Anmeldung sicher"), null);
        assertEquals(201, requirement.status(), requirement.body());
        return new Links(setup, gitlab, UUID.fromString(gamma.json().get("id").asString()), worker, ticket.json().get("number").asLong(),
            UUID.fromString(requirement.json().get("id").asString()));
    }

    private static Response link(Browser browser, String path, String url) {
        return browser.send("POST", path, Map.of("url", url), null);
    }

    private String ticketEtag(Links links, Browser browser) {
        Response ticket = browser.get("/api/v1/projects/" + links.setup().project() + "/tickets/" + links.ticket());
        assertEquals(200, ticket.status(), ticket.body());
        return ticket.etag();
    }

    private String requirementEtag(Links links, Browser browser) {
        Response requirement = browser.get("/api/v1/requirements/" + links.requirement());
        assertEquals(200, requirement.status(), requirement.body());
        return requirement.etag() + "|" + requirement.json().get("status").asString() + "|" + requirement.json().get("revision").asInt();
    }

    private static List<JsonNode> gitHistory(JsonNode entries) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode entry : entries) {
            for (JsonNode change : entry.get("changes")) {
                if ("git".equals(change.get("field").asString())) {
                    found.add(change);
                }
            }
        }
        return found;
    }

    @Test
    void commitsPullAndMergeRequestsAreLinkedOnceAtTicketAndRequirement() {
        Links links = links();
        Browser worker = links.worker();
        String before = ticketEtag(links, worker);
        String requirementBefore = requirementEtag(links, worker);

        Response commit = link(worker, links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA.substring(0, 7) + "?diff=split#top");
        assertEquals(201, commit.status(), commit.body());
        assertEquals("commit", commit.json().get("kind").asString());
        assertEquals(SHA, commit.json().get("commit").get("sha").asString(), "the full SHA is stored");
        assertEquals("a1b2c3d", commit.json().get("commit").get("shortSha").asString());
        assertEquals("Anmeldung repariert", commit.json().get("commit").get("message").asString());
        assertEquals(GITHUB.url() + "/acme/alpha/commit/" + SHA, commit.json().get("url").asString());
        Response again = link(worker, links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA);
        assertEquals(409, again.status(), again.body());
        assertEquals("git.link_exists", again.code());

        Response pull = link(worker, links.ticketPath(), GITHUB.url() + "/acme/alpha/pull/7/files");
        assertEquals(201, pull.status(), pull.body());
        assertEquals("open", pull.json().get("change").get("state").asString());
        assertEquals(7, pull.json().get("change").get("number").asInt());
        Response merge = worker.send("POST", links.ticketPath(), Map.of("releaseId", links.gitlabRelease(), "kind", "merge_request", "key", "3"), null);
        assertEquals(201, merge.status(), merge.body());
        assertEquals(GITLAB.url() + "/grp/gamma/-/merge_requests/3", merge.json().get("url").asString());
        Response repository = link(worker, links.ticketPath(), GITHUB.url() + "/acme/alpha.git");
        assertEquals(201, repository.status(), repository.body());
        assertEquals("repository", repository.json().get("kind").asString());
        assertEquals(409, worker.send("POST", links.ticketPath(), Map.of("releaseId", links.gitlabRelease(), "kind", "merge_request", "key", "3"), null)
            .status());

        Response list = worker.get(links.ticketPath());
        assertEquals(200, list.status(), list.body());
        assertTrue(list.json().get("readable").asBoolean());
        assertTrue(list.json().get("writable").asBoolean());
        assertEquals(4, list.json().get("links").size());
        for (JsonNode shown : list.json().get("links")) {
            assertTrue(shown.get("available").asBoolean(), shown.toString());
        }
        assertEquals(4, jdbc.queryForObject("select count(*) from git_link where subject_id = (select id from ticket where project_id = ? and number = ?)",
            Integer.class, links.setup().project(), links.ticket()));
        String stored = String.join(" ", jdbc.queryForList("select object_key || ' ' || repository_id from git_link", String.class));
        assertFalse(stored.contains("alpha") || stored.contains("Anmeldung"), "only stable identifiers are stored");
        assertEquals(before, ticketEtag(links, worker), "a link leaves the version of the ticket");

        Response history = worker.get("/api/v1/projects/" + links.setup().project() + "/tickets/" + links.ticket() + "/history");
        assertEquals(200, history.status(), history.body());
        List<JsonNode> entries = gitHistory(history.json());
        assertEquals(4, entries.size(), history.body());
        assertEquals("commit", entries.get(0).get("to").asString(), "the history names the kind only (TASK-114, F6)");
        assertFalse(history.body().contains("Anmeldung repariert"));

        Response onRequirement = link(worker, links.requirementPath(), GITLAB.url() + "/grp/gamma/-/commit/" + SHA);
        assertEquals(201, onRequirement.status(), onRequirement.body());
        assertEquals("Gita Lab", onRequirement.json().get("commit").get("author").asString());
        assertEquals(201, link(worker, links.requirementPath(), GITHUB.url() + "/acme/alpha/pull/7").status());
        assertEquals(409, link(worker, links.requirementPath(), GITLAB.url() + "/grp/gamma/-/commit/" + SHA.substring(0, 10)).status());
        assertEquals(requirementBefore, requirementEtag(links, worker), "version, status and revision of the requirement stay");
        Response requirementHistory = worker.get("/api/v1/requirements/" + links.requirement() + "/history");
        assertEquals(200, requirementHistory.status(), requirementHistory.body());
        assertTrue(requirementHistory.body().contains("\"kind\":\"git_linked\""), requirementHistory.body());
        assertTrue(requirementHistory.body().contains("\"to\":\"commit\""), requirementHistory.body());
        assertFalse(requirementHistory.body().contains("commit:"), requirementHistory.body());

        Response unknown = link(worker, links.ticketPath(), "https://elsewhere.example/acme/alpha");
        assertEquals(400, unknown.status());
        assertEquals("git.address_unknown", unknown.code());
        assertEquals("git.address_unknown", link(worker, links.ticketPath(), GITHUB.url() + "/acme/alpha/issues/3").code());
        Response missing = link(worker, links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/ffffffffff");
        assertEquals(404, missing.status());
        assertEquals("git.object_not_found", missing.code());
        Response hidden = link(worker, links.ticketPath(), GITHUB.url() + "/acme/secret");
        assertEquals("git.object_not_found", hidden.code(), "unknown and not readable answer alike");
        Response unreleased = link(worker, links.ticketPath(), GITHUB.url() + "/acme/beta");
        assertEquals(409, unreleased.status());
        assertEquals("git.repository_not_released", unreleased.code());
        assertEquals(400, worker.send("POST", links.ticketPath(), Map.of("releaseId", links.gitlabRelease(), "kind", "pull_request", "key", "3"), null)
            .status(), "pull requests are GitHub's");

        String commitKey = commit.json().get("key").asString();
        assertEquals(404, worker.send("DELETE", links.ticketPath() + "/" + UUID.randomUUID(), null, null).status(), "the identifier is no key");
        Response removed = worker.send("DELETE", links.ticketPath() + "/" + commitKey, null, null);
        assertEquals(204, removed.status(), removed.body());
        assertEquals(404, worker.send("DELETE", links.ticketPath() + "/" + commitKey, null, null).status());
        List<JsonNode> after = gitHistory(worker.get("/api/v1/projects/" + links.setup().project() + "/tickets/" + links.ticket() + "/history").json());
        assertEquals("commit", after.getLast().get("from").asString());
        assertTrue(after.getLast().get("to").isNull());
        assertEquals(before, ticketEtag(links, worker));
    }

    @Test
    void withoutAccessOnlyTheKindIsShownAndAWithdrawnAccessEndsAtTheNextRead() {
        Links links = links();
        Response commit = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA);
        assertEquals(201, commit.status(), commit.body());

        Browser reader = person();
        join(links.setup().admin(), links.setup().project(), reader, "ticket.read", "ticket:READ:title", "ticket:READ:git");
        Response notConnected = reader.get(links.ticketPath());
        assertEquals(200, notConnected.status(), notConnected.body());
        JsonNode shown = notConnected.json().get("links").get(0);
        assertFalse(shown.get("available").asBoolean());
        assertEquals("git.not_connected", shown.get("reason").asString());
        assertEquals("commit", shown.get("kind").asString());
        // TASK-114, F6: the kind and the reason only; no key for a reader who may not remove it.
        for (String field : List.of("key", "providerId", "providerName", "providerKind", "linkedAt", "linkedBy", "repository", "commit", "change", "url")) {
            assertTrue(shown.get(field).isNull(), field);
        }
        assertFalse(notConnected.body().contains(commit.json().get("key").asString()), notConnected.body());
        assertFalse(notConnected.body().contains(SHA.substring(0, 7)) || notConnected.body().contains("alpha"), notConnected.body());
        assertFalse(notConnected.json().get("writable").asBoolean());
        Response refused = link(reader, links.ticketPath(), GITHUB.url() + "/acme/alpha");
        assertEquals(403, refused.status());

        assertTrue(connect(reader, links.setup().github(), "r" + COUNTER.incrementAndGet()).endsWith("connected=" + links.setup().github()));
        GITHUB.denied.add("gh-" + GITHUB.issued.get());
        Response withoutAccess = reader.get(links.ticketPath());
        assertEquals("git.unavailable", withoutAccess.json().get("links").get(0).get("reason").asString());
        assertFalse(withoutAccess.body().contains(SHA.substring(0, 7)) || withoutAccess.body().contains("alpha"), withoutAccess.body());
        Response history = reader.get("/api/v1/projects/" + links.setup().project() + "/tickets/" + links.ticket() + "/history");
        assertEquals(List.of("commit"), gitHistory(history.json()).stream().map(c -> c.get("to").asString()).toList());

        Browser blind = person();
        join(links.setup().admin(), links.setup().project(), blind, "ticket.read", "ticket:READ:title");
        Response unreadable = blind.get(links.ticketPath());
        assertEquals(200, unreadable.status());
        assertFalse(unreadable.json().get("readable").asBoolean());
        assertEquals(0, unreadable.json().get("links").size());
        assertTrue(gitHistory(blind.get("/api/v1/projects/" + links.setup().project() + "/tickets/" + links.ticket() + "/history").json()).isEmpty());
        assertEquals(404, person().get(links.ticketPath()).status(), "a stranger does not see the ticket");

        // G03 (TASK-114, F1): the worker read the commit and it is kept; after the access ends already the next ordinary read
        // shows nothing more, because the repository is read live before anything kept is shown.
        assertTrue(links.worker().get(links.ticketPath()).json().get("links").get(0).get("available").asBoolean());
        int commitReads = GITHUB.seen("/api/repositories/101/commits/").size();
        assertTrue(links.worker().get(links.ticketPath()).json().get("links").get(0).get("available").asBoolean());
        assertEquals(commitReads, GITHUB.seen("/api/repositories/101/commits/").size(), "the commit itself is kept");
        GITHUB.denied.addAll(GITHUB.tokens);
        try {
            Response next = links.worker().get(links.ticketPath());
            assertEquals("git.unavailable", next.json().get("links").get(0).get("reason").asString(), next.body());
            assertFalse(next.body().contains(SHA.substring(0, 7)) || next.body().contains("alpha"), next.body());
            Response refreshed = links.worker().get(links.ticketPath() + "?refresh=true");
            assertEquals("git.unavailable", refreshed.json().get("links").get(0).get("reason").asString(), refreshed.body());
            assertEquals(1, jdbc.queryForObject("select count(*) from git_link where subject_id = (select id from ticket where project_id = ? and number = ?)",
                Integer.class, links.setup().project(), links.ticket()), "a link is never removed by itself");
        } finally {
            GITHUB.denied.clear();
        }
        assertEquals(204, links.worker().send("DELETE", "/api/v1/git/providers/" + links.setup().github() + "/connection", null, null).status());
        Response disconnected = links.worker().get(links.ticketPath());
        assertEquals("git.not_connected", disconnected.json().get("links").get(0).get("reason").asString());

        Browser unlinker = person();
        join(links.setup().admin(), links.setup().project(), unlinker, "ticket.read", "ticket.edit", "ticket:READ:git", "ticket:WRITE:git");
        assertEquals(404, unlinker.send("DELETE", links.ticketPath() + "/" + commit.json().get("key").asString(), null, null).status(),
            "the key of another person removes nothing");
        String own = unlinker.get(links.ticketPath()).json().get("links").get(0).get("key").asString();
        assertNotEquals(commit.json().get("key").asString(), own, "each person gets a key of their own");
        assertEquals(204, unlinker.send("DELETE", links.ticketPath() + "/" + own, null, null).status(), "removing needs only the write rule");
    }

    @Test
    void aMergedRequestShowsItsStateAndARateLimitKeepsThePageUsable() {
        Links links = links();
        Response merge = links.worker().send("POST", links.requirementPath(),
            Map.of("releaseId", links.gitlabRelease(), "kind", "merge_request", "key", "3"), null);
        assertEquals(201, merge.status(), merge.body());
        Response pull = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/pull/7");
        assertEquals(201, pull.status(), pull.body());
        String requirement = requirementEtag(links, links.worker());
        String ticket = ticketEtag(links, links.worker());
        assertEquals("open", links.worker().get(links.requirementPath()).json().get("links").get(0).get("change").get("state").asString());
        try {
            GITLAB.changeState = "merged";
            GITHUB.changeState = "draft";
            assertEquals("open", links.worker().get(links.requirementPath()).json().get("links").get(0).get("change").get("state").asString());
            assertEquals("merged", links.worker().get(links.requirementPath() + "?refresh=true").json().get("links").get(0).get("change").get("state")
                .asString());
            assertEquals("draft", links.worker().get(links.ticketPath() + "?refresh=true").json().get("links").get(0).get("change").get("state")
                .asString());
            GITHUB.changeState = "merged";
            assertEquals("merged", links.worker().get(links.ticketPath() + "?refresh=true").json().get("links").get(0).get("change").get("state")
                .asString());
            assertEquals(requirement, requirementEtag(links, links.worker()), "a merged merge request changes nothing");
            assertEquals(ticket, ticketEtag(links, links.worker()));

            GITHUB.limited = true;
            Response limited = links.worker().get(links.ticketPath() + "?refresh=true");
            assertEquals(200, limited.status(), limited.body());
            assertEquals("git.rate_limited", limited.json().get("links").get(0).get("reason").asString());
            int calls = GITHUB.seen("/api/repositories/101").size();
            links.worker().get(links.ticketPath() + "?refresh=true");
            assertEquals(calls + 1, GITHUB.seen("/api/repositories/101").size(), "one call, no retry loop");
            Response refused = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha");
            assertEquals(503, refused.status());
            assertEquals("git.rate_limited", refused.code());
            assertEquals(200, links.worker().get("/api/v1/projects/" + links.setup().project() + "/tickets/" + links.ticket()).status());
        } finally {
            GITHUB.limited = false;
            GITHUB.changeState = "open";
            GITLAB.changeState = "open";
        }
    }

    @Test
    void atMostFiftyLinksAndNothingOfGitInSearch() {
        Links links = links();
        UUID ticketId = jdbc.queryForObject("select id from ticket where project_id = ? and number = ?", UUID.class, links.setup().project(),
            links.ticket());
        UUID company = jdbc.queryForObject("select company_id from ticket where id = ?", UUID.class, ticketId);
        for (int i = 1; i <= 50; i++) {
            jdbc.update("""
                    insert into git_link (id, company_id, subject_kind, subject_id, project_id, provider_id, repository_id, object_kind, object_key,
                        linked_at, linked_by)
                    values (?, ?, 'ticket', ?, ?, ?, '101', 'pull_request', ?, now(), ?)
                    """, UUID.randomUUID(), company, ticketId, links.setup().project(), links.setup().github(), Integer.toString(100 + i),
                links.worker().id);
        }
        Response tooMany = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha");
        assertEquals(409, tooMany.status(), tooMany.body());
        assertEquals("git.links_too_many", tooMany.code());
        Response list = links.worker().get(links.ticketPath());
        assertEquals(50, list.json().get("links").size());
        GITHUB.maxInFlight.set(0);
        GITHUB.delay = 40;
        try {
            assertEquals(50, links.worker().get(links.ticketPath() + "?refresh=true").json().get("links").size());
        } finally {
            GITHUB.delay = 0;
        }
        assertTrue(GITHUB.maxInFlight.get() <= 4 && GITHUB.maxInFlight.get() >= 2, "at most four reads at the same time: " + GITHUB.maxInFlight.get());

        assertEquals(201, link(links.worker(), links.requirementPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA).status());
        Response search = links.worker().get("/api/v1/search/tickets?q=" + encode("Anmeldung repariert"));
        assertEquals(200, search.status(), search.body());
        assertFalse(search.body().contains("Anmeldung repariert") || search.body().contains(SHA.substring(0, 7)), search.body());
        Response requirements = links.worker().get("/api/v1/search/requirements?q=" + encode("repariert"));
        assertFalse(requirements.body().contains(SHA.substring(0, 7)) || requirements.body().contains("repariert\""), requirements.body());
        Response ticket = links.worker().get("/api/v1/projects/" + links.setup().project() + "/tickets/" + links.ticket());
        assertFalse(ticket.body().contains("pull_request") || ticket.body().contains("alpha"), ticket.body());
    }

    // ---------------------------------------------------------------- fixes after the review of TASK-112 (TASK-114)

    @Test
    void aNewConnectionNeverSeesWhatTheFormerOneKept() {
        Links links = links();
        assertEquals(201, link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/pull/7").status());
        assertEquals("open", links.worker().get(links.ticketPath()).json().get("links").get(0).get("change").get("state").asString());
        try {
            GITHUB.changeState = "merged";
            assertEquals("open", links.worker().get(links.ticketPath()).json().get("links").get(0).get("change").get("state").asString(),
                "kept for the same connection");
            // F2: connecting again starts a new generation; what the former connection read is never used again.
            assertTrue(connect(links.worker(), links.setup().github(), "again" + COUNTER.incrementAndGet()).endsWith("connected=" + links.setup().github()));
            assertEquals("merged", links.worker().get(links.ticketPath()).json().get("links").get(0).get("change").get("state").asString());
        } finally {
            GITHUB.changeState = "open";
        }
    }

    @Test
    void theReleaseIsCheckedBeforeTheObjectIsFetched() {
        Links links = links();
        int before = GITHUB.seen("/api/repositories/102/").size();
        // F3: beta is readable but not released; an existing and a missing commit answer alike and no object is fetched.
        Response existing = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/beta/commit/" + SHA);
        Response missing = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/beta/commit/ffffffffff");
        assertEquals(409, existing.status(), existing.body());
        assertEquals("git.repository_not_released", existing.code());
        assertEquals(409, missing.status(), missing.body());
        assertEquals("git.repository_not_released", missing.code());
        assertEquals(before, GITHUB.seen("/api/repositories/102/").size(), "nothing below the repository is read");
    }

    @Test
    void overlappingInstancesKeepTheirOwnReading() {
        Links links = links();
        // F7: two own GitLab instances, one below the other. Only the outer one is connected and has gamma released.
        UUID outer = createProvider(links.setup().admin(), "gitlab", GITLAB.url());
        createProvider(links.setup().admin(), "gitlab", GITLAB.url() + "/grp");
        assertTrue(connect(links.setup().manager(), outer, "o" + COUNTER.incrementAndGet()).endsWith("connected=" + outer));
        assertTrue(connect(links.worker(), outer, "o" + COUNTER.incrementAndGet()).endsWith("connected=" + outer));
        Response released = links.setup().manager().send("POST", "/api/v1/projects/" + links.setup().project() + "/git/repositories",
            Map.of("providerId", outer, "externalId", "201"), null);
        assertEquals(201, released.status(), released.body());
        Response linked = link(links.worker(), links.requirementPath(), GITLAB.url() + "/grp/gamma/-/merge_requests/3");
        assertEquals(201, linked.status(), linked.body());
        assertEquals(outer.toString(), linked.json().get("providerId").asString());
        assertEquals("grp/gamma", linked.json().get("repository").get("path").asString());
    }

    @Test
    void anInstanceInUseKeepsItsAddress() {
        Links links = links();
        Browser admin = links.setup().admin();
        UUID own = createProvider(admin, "gitlab", GITLAB.url());
        Map<String, Object> moved = provider("gitlab", "Eigenes " + COUNTER.incrementAndGet(), TLS.url(), null, null);
        moved.remove("clientSecret");
        Response free = admin.send("PUT", "/api/v1/git/admin/providers/" + own, moved, "\"1\"");
        assertEquals(200, free.status(), "an unused instance may move: " + free.body());
        moved.put("baseUrl", GITLAB.url());
        Response back = admin.send("PUT", "/api/v1/git/admin/providers/" + own, moved, free.etag());
        assertEquals(200, back.status(), back.body());
        assertTrue(connect(links.setup().manager(), own, "u" + COUNTER.incrementAndGet()).endsWith("connected=" + own));
        assertEquals(201, links.setup().manager().send("POST", "/api/v1/projects/" + links.setup().project() + "/git/repositories",
            Map.of("providerId", own, "externalId", "201"), null).status());
        // F4: with a release the address is a trust decision of its own; a new server is a new instance.
        moved.put("baseUrl", TLS.url());
        Response refused = admin.send("PUT", "/api/v1/git/admin/providers/" + own, moved, back.etag());
        assertEquals(409, refused.status(), refused.body());
        assertEquals("git.provider_in_use", refused.code());
        moved.put("baseUrl", GITLAB.url());
        moved.put("name", "Umbenannt " + COUNTER.incrementAndGet());
        assertEquals(200, admin.send("PUT", "/api/v1/git/admin/providers/" + own, moved, back.etag()).status(), "the name may change");
    }

    @Test
    void everyGitRouteNeedsTheReadActionFirst() {
        Links links = links();
        Response commit = link(links.worker(), links.requirementPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA);
        assertEquals(201, commit.status(), commit.body());
        Response onTicket = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA);
        assertEquals(201, onTicket.status(), onTicket.body());
        // F5: the edit action with both field rules but without the read action sees neither requirement nor ticket.
        Browser editor = person();
        join(links.setup().admin(), links.setup().project(), editor, "requirement.edit", "requirement:READ:git", "requirement:WRITE:git",
            "ticket.edit", "ticket:READ:git", "ticket:WRITE:git");
        assertTrue(connect(editor, links.setup().github(), "e" + COUNTER.incrementAndGet()).endsWith("connected=" + links.setup().github()));
        for (String path : List.of(links.requirementPath(), links.ticketPath())) {
            assertEquals(404, editor.get(path).status(), path);
            assertEquals(404, editor.get(path + "/choices").status(), path);
            assertEquals(404, link(editor, path, GITHUB.url() + "/acme/alpha").status(), path);
            assertEquals(404, editor.send("DELETE", path + "/" + commit.json().get("key").asString(), null, null).status(), path);
        }
        assertEquals(2, jdbc.queryForObject("select count(*) from git_link where provider_id = ?", Integer.class, links.setup().github()));
    }

    @Test
    void theChoiceHoldsOnlyReadableRepositories() {
        Links links = links();
        Response all = links.worker().get(links.ticketPath() + "/choices");
        assertEquals(200, all.status(), all.body());
        assertEquals(2, all.json().size(), all.body());
        // F6: without a connection to GitLab.com gamma is no longer offered, and nothing of it is named.
        assertEquals(204, links.worker().send("DELETE", "/api/v1/git/providers/" + links.gitlab() + "/connection", null, null).status());
        Response readable = links.worker().get(links.ticketPath() + "/choices");
        assertEquals(1, readable.json().size(), readable.body());
        assertEquals("acme/alpha", readable.json().get(0).get("repository").get("path").asString());
        assertFalse(readable.body().contains(links.gitlabRelease().toString()), readable.body());
        GITHUB.denied.addAll(GITHUB.tokens);
        try {
            assertEquals(0, links.worker().get(links.ticketPath() + "/choices").json().size(), "access is read live (F1)");
        } finally {
            GITHUB.denied.clear();
        }
    }

    @Test
    void theSecondaryRateLimitOfGitHubIsARateLimitAndNotKept() {
        Links links = links();
        assertEquals(201, link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA).status());
        GITHUB.secondaryLimited = true;
        try {
            // F8: 403 with calls left but the message of the secondary limit.
            Response limited = links.worker().get(links.ticketPath());
            assertEquals("git.rate_limited", limited.json().get("links").get(0).get("reason").asString(), limited.body());
            Response refused = link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/pull/7");
            assertEquals(503, refused.status(), refused.body());
            assertEquals("git.rate_limited", refused.code());
        } finally {
            GITHUB.secondaryLimited = false;
        }
        assertTrue(links.worker().get(links.ticketPath()).json().get("links").get(0).get("available").asBoolean(), "the limit was not kept");
    }

    @Test
    void aWithdrawalAndALinkWaitForEachOther() throws Exception {
        Links links = links();
        UUID ticketId = jdbc.queryForObject("select id from ticket where project_id = ? and number = ?", UUID.class, links.setup().project(),
            links.ticket());
        UUID release = jdbc.queryForObject("select id from git_repository where project_id = ? and external_id = '101'", UUID.class,
            links.setup().project());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            // F9, first order: a withdrawal that is not committed yet holds the release; the link waits and is refused after it.
            try (java.sql.Connection held = jdbc.getDataSource().getConnection()) {
                held.setAutoCommit(false);
                held.createStatement().execute("delete from git_repository where id = '" + release + "'");
                var adding = pool.submit(() -> link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA));
                waitForLockWait();
                held.commit();
                Response refused = adding.get(20, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(409, refused.status(), refused.body());
                assertEquals("git.repository_not_released", refused.code());
            }
            assertEquals(0, jdbc.queryForObject("select count(*) from git_link where subject_id = ?", Integer.class, ticketId));
            assertEquals(201, release(links.setup().manager(), links.setup(), "101").status());
            UUID again = jdbc.queryForObject("select id from git_repository where project_id = ? and external_id = '101'", UUID.class,
                links.setup().project());

            // Second order: a link that holds the release row until its commit makes the withdrawal wait for it.
            try (java.sql.Connection held = jdbc.getDataSource().getConnection()) {
                held.setAutoCommit(false);
                held.createStatement().execute("select pg_advisory_xact_lock(hashtextextended('git_link:ticket:" + ticketId + "', 0))");
                var adding = pool.submit(() -> link(links.worker(), links.ticketPath(), GITHUB.url() + "/acme/alpha/commit/" + SHA));
                waitForLockWait();
                var withdrawing = pool.submit(() -> links.setup().manager().send("DELETE",
                    "/api/v1/projects/" + links.setup().project() + "/git/repositories/" + again, null, null));
                waitForLockWaits(2);
                held.commit();
                assertEquals(201, adding.get(20, java.util.concurrent.TimeUnit.SECONDS).status());
                assertEquals(204, withdrawing.get(20, java.util.concurrent.TimeUnit.SECONDS).status());
            }
            assertEquals(1, jdbc.queryForObject("select count(*) from git_link where subject_id = ?", Integer.class, ticketId),
                "the link was stored before the withdrawal and stays");
        } finally {
            pool.shutdownNow();
        }
    }

    private void waitForLockWait() throws InterruptedException {
        waitForLockWaits(1);
    }

    /** Waits until PostgreSQL shows the given number of sessions waiting for a lock. */
    private void waitForLockWaits(int count) throws InterruptedException {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer.class);
            if (waiting != null && waiting >= count) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no session waited for a lock");
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
