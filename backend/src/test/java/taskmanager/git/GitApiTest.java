package taskmanager.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        Stub(boolean gitlab) {
            this.gitlab = gitlab;
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", this::handle);
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

    /** Grants project actions in a group of its own. */
    private void grant(UUID account, UUID project, String... actions) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseGet(companies::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            UUID group = UUID.randomUUID();
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "fixture-" + group);
            for (String action : actions) {
                jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
                    companyId, group, project, action);
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

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
