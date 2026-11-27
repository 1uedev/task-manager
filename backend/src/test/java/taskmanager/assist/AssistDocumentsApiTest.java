package taskmanager.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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
 * TASK-127 (DEC-173): the review of a requirement and the revision of a wiki draft through the real HTTP interface with the real
 * identity, permissions, projects, requirements, wiki and AI modules and a real PostgreSQL. The model is a small HTTP server of this
 * test that answers like Ollama; it records what it was sent, so a test sees exactly which context left the application. No
 * call leaves the machine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AssistDocumentsApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Model MODEL = new Model();
    private static final String REVIEW = "{\"findings\": [{\"kind\": \"not_measurable\", \"location\": \"zeitnah\", "
        + "\"reason\": \"Nicht messbar\", \"suggestion\": \"innerhalb von 5 Minuten\", \"sources\": [\"S1\"]}]}";
    private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 1, 2, 3 };

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
        registry.add("taskmanager.ai.secret-key", () -> KEY);
        registry.add("taskmanager.helpdesk.secret-key", () -> KEY);
        registry.add("taskmanager.ai.plain-http-hosts", () -> "localhost,127.0.0.1");
        registry.add("taskmanager.ai.timeout", () -> "5s");
    }

    @AfterAll
    static void stop() {
        MODEL.server.stop(0);
    }

    @BeforeEach
    void reset() {
        MODEL.seen.clear();
        MODEL.answer = REVIEW;
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;

    // ---------------------------------------------------------------- the model of this test

    /** Answers {@code /api/chat} like Ollama with {@link #answer} and keeps every request body. */
    static final class Model {
        final HttpServer server;
        final List<JsonNode> seen = new CopyOnWriteArrayList<>();
        volatile String answer = REVIEW;

        Model() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", exchange -> {
                try {
                    handle(exchange);
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getRawPath();
            byte[] raw = exchange.getRequestBody().readAllBytes();
            if (path.equals("/api/tags")) {
                answer(exchange, Map.of("models", List.of(Map.of("name", "llama3.2:latest"))));
                return;
            }
            seen.add(JSON.readTree(raw));
            answer(exchange, Map.of("message", Map.of("role", "assistant", "content", answer), "done", true, "done_reason", "stop",
                "prompt_eval_count", 100, "eval_count", 20));
        }

        private static void answer(HttpExchange exchange, Object body) throws IOException {
            byte[] bytes = JSON.writeValueAsBytes(body);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        /** Everything the last request carried as text. */
        String lastPrompt() {
            assertFalse(seen.isEmpty(), "the model saw a request");
            return seen.get(seen.size() - 1).toString();
        }
    }

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, String body, String etag) {
        JsonNode json() {
            return JSON.readTree(body);
        }

        String code() {
            return json().get("code").asString();
        }

        List<String> violations() {
            JsonNode list = json().get("violations");
            return list == null ? List.of() : StreamSupport.stream(list.spliterator(), false).map(JsonNode::asString).toList();
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
                return new Response(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(null));
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        Response get(String path) {
            return send("GET", path, null, null);
        }

        Response upload(String path, byte[] content) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(content));
                List<String> pairs = new ArrayList<>();
                cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                request.header("Cookie", String.join("; ", pairs));
                request.header("X-CSRF-Token", cookies.get("__Host-tm-csrf"));
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                return new Response(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(null));
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
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
        var issued = accounts.create("test", "assistdoc-" + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        return login(issued.account().id(), issued.account().username(), issued.oneTimePassword());
    }

    /**
     * Grants project actions and field rules in a group of its own: {@code READ:title} is a rule of requirements, {@code READ:*}
     * every field of a requirement.
     */
    private UUID grant(UUID account, UUID project, String... rules) {
        UUID group = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseGet(companies::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "fixture-" + group);
            for (String rule : rules) {
                if (!rule.contains(":")) {
                    jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
                        companyId, group, project, rule);
                    continue;
                }
                String[] parts = rule.split(":");
                List<String> fields = parts[1].equals("*") ? List.of("title", "description", "criteria", "priority", "owner") : List.of(parts[1]);
                for (String field : fields) {
                    jdbc.update("""
                            insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                            values (?, ?, ?, 'requirement', ?, ?, 'ALLOW')
                            """, companyId, group, project, field, parts[0]);
                }
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
        return group;
    }

    /** Takes the person out of a group again, as the administration of rights does. */
    private void leave(UUID account, UUID group) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseThrow();
            jdbc.update("delete from permissions_membership where group_id = ? and user_id = ?", group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
    }

    // ---------------------------------------------------------------- the setting

    /** A project with an editor of requirements and wiki pages who may use AI, and the local model for both use cases. */
    private record Setup(Browser admin, UUID project, Browser editor) {
    }

    private Setup setup() {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.AI_MANAGE, AdministrationRight.PROJECTS_MANAGE, AdministrationRight.HELPDESK_MANAGE);
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "AD" + n, "name", "Dokumente " + n), null);
        assertEquals(201, created.status(), created.body());
        UUID project = UUID.fromString(created.json().get("id").asString());
        Setup setup = new Setup(admin, project, null);
        Browser editor = member(setup, "requirement.read", "requirement.edit", "wiki.read", "wiki.edit", "wiki.publish", "ai.use", "READ:*", "WRITE:*");
        model(admin);
        return new Setup(admin, project, editor);
    }

    private Browser member(Setup setup, String... rules) {
        Browser person = person();
        Response project = setup.admin().get("/api/v1/projects/" + setup.project());
        assertEquals(200, project.status(), project.body());
        Response added = setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + person.id, null, project.etag());
        assertEquals(200, added.status(), added.body());
        if (rules.length > 0) {
            grant(person.id, setup.project(), rules);
        }
        return person;
    }

    /** The local model of this test for both use cases, once per installation. */
    private void model(Browser admin) {
        Response providers = admin.get("/api/v1/ai/admin/providers");
        if (providers.json().size() > 0) {
            return;
        }
        Map<String, Object> access = new LinkedHashMap<>();
        access.put("kind", "ollama");
        access.put("name", "Lokal");
        access.put("baseUrl", MODEL.url());
        access.put("apiKey", null);
        access.put("dataPath", "local");
        Response provider = admin.send("POST", "/api/v1/ai/admin/providers", access, null);
        assertEquals(201, provider.status(), provider.body());
        Response model = admin.send("POST", "/api/v1/ai/admin/models", Map.of("providerId", provider.json().get("id").asString(), "name", "Lokal",
            "modelKey", "llama3.2", "contextTokens", 120000, "outputTokens", 2000), null);
        assertEquals(201, model.status(), model.body());
        for (String useCase : List.of("requirement_review", "wiki_revision")) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("modelId", model.json().get("id").asString());
            body.put("enabled", true);
            Response saved = admin.send("PUT", "/api/v1/ai/admin/assignments/" + useCase, body, "\"0\"");
            assertEquals(200, saved.status(), saved.body());
        }
    }

    private static UUID runId(Response started) {
        assertEquals(202, started.status(), started.body());
        return UUID.fromString(started.json().get("runId").asString());
    }

    /** Reads the execution until it ended. */
    private static JsonNode finished(Browser browser, UUID run) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < until) {
            Response read = browser.get("/api/v1/ai/runs/" + run);
            assertEquals(200, read.status(), read.body());
            String state = read.json().get("state").asString();
            if (!state.equals("queued") && !state.equals("running")) {
                return read.json();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the execution did not end");
    }

    // ---------------------------------------------------------------- requirements

    private static Map<String, Object> paragraphDoc(String value) {
        return Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", value)))));
    }

    private static Map<String, Object> criterion(String text) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("id", null);
        criterion.put("text", text);
        return criterion;
    }

    /** A requirement with a description and two criteria, one of them not measurable. */
    private static Response requirement(Browser who, Setup setup) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Kunden erhalten zeitnah eine Antwort");
        body.put("description", paragraphDoc("Der Support antwortet auf neue Anfragen."));
        body.put("criteria", List.of(criterion("Die erste Antwort kommt zeitnah."), criterion("Die Kundschaft sieht den Status im Portal.")));
        Response created = who.send("POST", "/api/v1/requirements/projects/" + setup.project() + "/requirements", body, null);
        assertEquals(201, created.status(), created.body());
        return created;
    }

    private static String path(Response requirement) {
        return "/api/v1/requirements/" + requirement.json().get("id").asString();
    }

    private static List<String> parts(Response started) {
        List<String> parts = new ArrayList<>();
        started.json().get("sources").forEach(s -> parts.add(s.get("id").asString() + ":" + s.get("part").asString()
            + (s.get("criterion").isNull() ? "" : ":" + s.get("criterion").asInt())));
        return parts;
    }

    private static Response review(Browser who, Response requirement, int revision) {
        Map<String, Object> body = new HashMap<>();
        body.put("revision", revision);
        body.put("language", "de");
        return who.send("POST", path(requirement) + "/ai/review", body, null);
    }

    /**
     * Rule 1: the review gets exactly the shown revision, every readable part as a required source with what it refers to, and
     * answers with findings that name the passage, the reason and a proposal. Nothing is written at the requirement.
     */
    @Test
    void theReviewPassesTheShownRevisionAndWritesNothing() throws InterruptedException {
        Setup setup = setup();
        Response created = requirement(setup.editor(), setup);
        Response options = setup.editor().get(path(created) + "/ai?revision=1");
        assertEquals(200, options.status(), options.body());
        assertTrue(options.json().get("review").asBoolean());

        Response started = review(setup.editor(), created, 1);
        UUID run = runId(started);
        assertEquals(List.of("S1:title", "S2:description", "S3:criterion:1", "S4:criterion:2"), parts(started));
        assertEquals(1, started.json().get("revision").asInt());
        JsonNode result = finished(setup.editor(), run);
        assertEquals("succeeded", result.get("state").asString(), result.toString());
        JsonNode finding = result.get("content").get("findings").get(0);
        assertEquals("not_measurable", finding.get("kind").asString());
        assertEquals("innerhalb von 5 Minuten", finding.get("suggestion").asString());

        String prompt = MODEL.lastPrompt();
        for (String expected : List.of("Kunden erhalten zeitnah eine Antwort", "Der Support antwortet auf neue Anfragen.", "Abnahmekriterium K1",
                "Die erste Antwort kommt zeitnah.", "Abnahmekriterium K2")) {
            assertTrue(prompt.contains(expected), expected);
        }
        Response after = setup.editor().get(path(created));
        assertEquals(created.etag(), after.etag(), "the review writes nothing at the requirement");
        assertEquals("draft", after.json().get("status").asString());
        assertEquals(1, after.json().get("revision").asInt());

        Response unknown = review(setup.editor(), created, 7);
        assertEquals(404, unknown.status(), unknown.body());
        assertEquals("requirement.revision_not_found", unknown.code());
        Response missing = setup.editor().send("POST", path(created) + "/ai/review", Map.of("language", "de"), null);
        assertEquals(400, missing.status(), missing.body());
        assertTrue(missing.violations().contains("revision.invalid"));
    }

    /**
     * Prüffall A04: the review of an approved requirement changes nothing; the proposal taken over and saved with the normal save
     * opens the next revision, and the review of the approved revision then no longer counts.
     */
    @Test
    void aTakeOverIntoAnApprovedRequirementOpensANewRevisionOnSave() throws InterruptedException {
        Setup setup = setup();
        Browser approver = member(setup, "requirement.read", "requirement.approve", "READ:*");
        Response created = requirement(setup.editor(), setup);
        Response submitted = setup.editor().send("POST", path(created) + "/submit", null, created.etag());
        assertEquals(200, submitted.status(), submitted.body());
        Response approved = approver.send("POST", path(created) + "/approve", Map.of("revision", 1), submitted.etag());
        assertEquals(200, approved.status(), approved.body());

        UUID run = runId(review(setup.editor(), approved, 1));
        assertEquals("succeeded", finished(setup.editor(), run).get("state").asString());
        Response unchanged = setup.editor().get(path(created));
        assertEquals("approved", unchanged.json().get("status").asString(), "the AI never changes the status");
        assertEquals(approved.etag(), unchanged.etag());

        List<Map<String, Object>> criteria = new ArrayList<>();
        unchanged.json().get("content").get("criteria").forEach(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.get("id").asInt());
            item.put("text", c.get("id").asInt() == 1 ? "Die erste Antwort kommt innerhalb von 5 Minuten." : c.get("text").asString());
            criteria.add(item);
        });
        Response saved = setup.editor().send("PATCH", path(created), Map.of("criteria", criteria), unchanged.etag());
        assertEquals(200, saved.status(), saved.body());
        assertEquals(2, saved.json().get("revision").asInt(), "the save of the taken over proposal opens the next revision");
        assertEquals("draft", saved.json().get("status").asString());

        JsonNode read = setup.editor().get("/api/v1/ai/runs/" + run).json();
        assertTrue(read.get("content").isNull());
        assertEquals("ai.source_changed", read.get("invalid").asString());
    }

    /** The field rules hold for the context, and the review needs the requirement, its revision and {@code ai.use}. */
    @Test
    void theReviewFollowsTheFieldRulesAndItsRights() throws InterruptedException {
        Setup setup = setup();
        Response created = requirement(setup.editor(), setup);
        Browser partial = member(setup, "requirement.read", "ai.use", "READ:title", "READ:criteria");
        Response started = review(partial, created, 1);
        assertEquals(List.of("S1:title", "S2:criterion:1", "S3:criterion:2"), parts(started));
        assertEquals("succeeded", finished(partial, runId(started)).get("state").asString());
        assertFalse(MODEL.lastPrompt().contains("Der Support antwortet"), "an unreadable description never leaves");

        Browser withoutAi = member(setup, "requirement.read", "READ:*");
        assertFalse(withoutAi.get(path(created) + "/ai?revision=1").json().get("review").asBoolean());
        assertEquals(403, review(withoutAi, created, 1).status());
        Browser outsider = person();
        assertEquals(404, review(outsider, created, 1).status());
        assertEquals(404, outsider.get(path(created) + "/ai?revision=1").status());
        Browser nothingReadable = member(setup, "requirement.read", "ai.use", "READ:priority");
        Response empty = review(nothingReadable, created, 1);
        assertEquals(409, empty.status(), empty.body());
        assertEquals("assist.no_sources", empty.code());
    }

    /**
     * Prüffall A09: every reading assembles the revision again for the reader. A save that changes the current revision, or the
     * loss of the read rule of a part it used, makes the review invalid.
     */
    @Test
    void aChangedRevisionOrALostRightMakesTheReviewInvalid() throws InterruptedException {
        Setup setup = setup();
        Response created = requirement(setup.editor(), setup);
        UUID run = runId(review(setup.editor(), created, 1));
        assertEquals("succeeded", finished(setup.editor(), run).get("state").asString());
        assertFalse(setup.editor().get("/api/v1/ai/runs/" + run).json().get("content").isNull());
        Response saved = setup.editor().send("PATCH", path(created), Map.of("description", paragraphDoc("Neue Beschreibung.")), created.etag());
        assertEquals(200, saved.status(), saved.body());
        assertEquals(1, saved.json().get("revision").asInt(), "a draft keeps its revision while its content changes");
        assertEquals("ai.source_changed", setup.editor().get("/api/v1/ai/runs/" + run).json().get("invalid").asString());

        Browser reader = member(setup, "requirement.read", "ai.use", "READ:title", "READ:criteria");
        UUID description = grant(reader.id, setup.project(), "READ:description");
        UUID second = runId(review(reader, saved, 1));
        assertEquals("succeeded", finished(reader, second).get("state").asString());
        assertFalse(reader.get("/api/v1/ai/runs/" + second).json().get("content").isNull());
        leave(reader.id, description);
        JsonNode lost = reader.get("/api/v1/ai/runs/" + second).json();
        assertTrue(lost.get("content").isNull());
        assertEquals("ai.source_changed", lost.get("invalid").asString());
    }

    // ---------------------------------------------------------------- wiki

    private static Map<String, Object> node(String type, Object... content) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        if (content.length > 0) {
            node.put("content", List.of(content));
        }
        return node;
    }

    private static Map<String, Object> text(String value, Map<String, Object> mark) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", value);
        if (mark != null) {
            node.put("marks", List.of(mark));
        }
        return node;
    }

    private static Map<String, Object> heading(String value) {
        Map<String, Object> heading = node("heading", text(value, null));
        heading.put("attrs", Map.of("level", 2));
        return heading;
    }

    private static Map<String, Object> paragraph(Object... content) {
        return node("paragraph", content);
    }

    private static Map<String, Object> image(String id) {
        Map<String, Object> image = node("image");
        image.put("attrs", Map.of("src", "/api/v1/wiki/images/" + id, "alt", "Formular"));
        return image;
    }

    private static Map<String, Object> wikiLink(UUID target) {
        return text("Anleitung", Map.of("type", "link", "attrs", Map.of("href", "wiki:" + target)));
    }

    /** A page in three sections with an image and a link to another page in the second; its draft is at revision 2. */
    private record Page(UUID id, UUID target, String image) {
    }

    private Page page(Setup setup) {
        Browser editor = setup.editor();
        Response target = editor.send("POST", "/api/v1/wiki/projects/" + setup.project() + "/pages", Map.of("title", "Ziel", "content",
            paragraphDoc("Ziel")), null);
        assertEquals(201, target.status(), target.body());
        UUID targetId = UUID.fromString(target.json().get("id").asString());
        Response created = editor.send("POST", "/api/v1/wiki/projects/" + setup.project() + "/pages", Map.of("title", "Ablauf", "content",
            paragraphDoc("Start")), null);
        assertEquals(201, created.status(), created.body());
        UUID page = UUID.fromString(created.json().get("id").asString());
        Response image = editor.upload("/api/v1/wiki/pages/" + page + "/images", PNG);
        assertEquals(201, image.status(), image.body());
        String imageId = image.json().get("id").asString();
        Response saved = editor.send("PUT", "/api/v1/wiki/pages/" + page + "/draft", Map.of("title", "Ablauf", "content", document(
            "Dieser Text ist eine sehr lange und ausführliche Einleitung.", targetId, imageId, "Schluss")), created.etag());
        assertEquals(200, saved.status(), saved.body());
        assertEquals(2, saved.json().get("draft").get("revision").asInt());
        return new Page(page, targetId, imageId);
    }

    private static Map<String, Object> document(String introduction, UUID target, String imageId, String end) {
        return node("doc", heading("Einleitung"), paragraph(text(introduction, null)), heading("Ablauf"),
            paragraph(text("Erst die Anfrage öffnen, dann alles prüfen, siehe ", null), wikiLink(target)), image(imageId), heading("Ende"),
            paragraph(text(end, null)));
    }

    private static Response revise(Browser who, Page page, Integer revision, Integer section, String goal, String own) {
        Map<String, Object> body = new HashMap<>();
        body.put("revision", revision);
        body.put("section", section);
        body.put("goal", goal);
        body.put("text", own);
        return who.send("POST", "/api/v1/wiki/pages/" + page.id() + "/ai/revision", body, null);
    }

    private static String answer(String text) {
        return JSON.writeValueAsString(Map.of("text", text, "notes", List.of("gekürzt")));
    }

    /**
     * Rule 2, prüffall A05: the section goes to the model as simple Markdown with placeholders instead of the image and the link,
     * and comes back with both in place. The result shows the section before and after and the whole draft with it; the page
     * itself is changed only by the normal save, which makes the suggestion invalid.
     */
    @Test
    void theRevisionOfASectionKeepsImagesAndLinksAndWritesNothing() throws InterruptedException {
        Setup setup = setup();
        Page page = page(setup);
        String base = "/api/v1/wiki/pages/" + page.id();
        assertTrue(setup.editor().get(base + "/ai").json().get("revision").asBoolean());
        MODEL.answer = answer("## Ablauf\n\nAnfrage öffnen und prüfen, siehe [[LINK-1]].\n\n[[IMAGE-1]]\n");
        Response started = revise(setup.editor(), page, 2, 2, "shorter", null);
        UUID run = runId(started);
        assertEquals(2, started.json().get("section").asInt());
        assertEquals("Ablauf", started.json().get("heading").asString());
        assertEquals("succeeded", finished(setup.editor(), run).get("state").asString());

        String prompt = MODEL.lastPrompt();
        assertTrue(prompt.contains("## Ablauf"), prompt);
        assertTrue(prompt.contains("[[LINK-1]]") && prompt.contains("[[IMAGE-1]]"), prompt);
        assertTrue(prompt.contains("Fasse den Text kürzer."), prompt);
        assertFalse(prompt.contains("ausführliche Einleitung"), "only the chosen section leaves");
        assertFalse(prompt.contains(page.target().toString()) || prompt.contains(page.image()), "no identifier of a link or image leaves");

        Response result = setup.editor().get(base + "/ai/revision/" + run);
        assertEquals(200, result.status(), result.body());
        JsonNode body = result.json();
        assertEquals(2, body.get("revision").asInt());
        assertTrue(body.get("original").toString().contains("Erst die Anfrage öffnen"));
        assertTrue(body.get("revised").toString().contains("Anfrage öffnen und prüfen"));
        String document = body.get("document").toString();
        for (String expected : List.of("ausführliche Einleitung", "Anfrage öffnen und prüfen", "wiki:" + page.target(), "/api/v1/wiki/images/" + page.image(),
                "Schluss")) {
            assertTrue(document.contains(expected), expected);
        }
        assertFalse(document.contains("Erst die Anfrage öffnen"));
        assertEquals("gekürzt", body.get("notes").get(0).asString());
        assertEquals(2, setup.editor().get(base).json().get("draft").get("revision").asInt(), "the revision writes nothing at the page");

        Response saved = setup.editor().send("PUT", base + "/draft", Map.of("title", "Ablauf", "content", body.get("document")), "\"2\"");
        assertEquals(200, saved.status(), saved.body());
        assertEquals(3, saved.json().get("draft").get("revision").asInt());
        assertTrue(saved.json().get("draft").get("content").toString().contains("wiki:" + page.target()));
        assertTrue(saved.json().get("published").isNull(), "publishing stays a separate action");
        assertEquals("ai.source_changed", setup.editor().get("/api/v1/ai/runs/" + run).json().get("invalid").asString());
        Response stale = setup.editor().get(base + "/ai/revision/" + run);
        assertEquals(409, stale.status(), stale.body());
        assertEquals("ai.source_changed", stale.code());
    }

    /** Prüffall A05: an answer that loses a placeholder or adds a link to a wiki page is refused as a whole. */
    @Test
    void aRevisionThatLosesAPlaceholderIsRefused() throws InterruptedException {
        Setup setup = setup();
        Page page = page(setup);
        MODEL.answer = answer("## Einleitung\n\nKurz.\n\n## Ablauf\n\nAnfrage öffnen, siehe [[LINK-1]].\n\n## Ende\n\nSchluss\n");
        UUID lost = runId(revise(setup.editor(), page, 2, null, "custom", "Bitte straffen"));
        JsonNode failed = finished(setup.editor(), lost);
        assertEquals("failed", failed.get("state").asString(), failed.toString());
        assertEquals("ai.placeholders_changed", failed.get("code").asString());
        assertTrue(failed.get("content").isNull());
        Response result = setup.editor().get("/api/v1/wiki/pages/" + page.id() + "/ai/revision/" + lost);
        assertEquals(409, result.status(), result.body());
        assertEquals("ai.placeholders_changed", result.code());

        MODEL.answer = answer("## Ablauf\n\nSiehe [[LINK-1]] und [neu](wiki:" + page.target() + ").\n\n[[IMAGE-1]]\n");
        UUID added = runId(revise(setup.editor(), page, 2, 2, "clearer", null));
        JsonNode refused = finished(setup.editor(), added);
        assertEquals("failed", refused.get("state").asString(), refused.toString());
        assertEquals("ai.invalid_output", refused.get("code").asString());
    }

    /**
     * The revision needs {@code wiki.edit} and the draft the person sees; a change in another section keeps it valid and the result
     * carries both changes.
     */
    @Test
    void theRevisionNeedsWikiEditAndTheShownDraft() throws InterruptedException {
        Setup setup = setup();
        Page page = page(setup);
        String base = "/api/v1/wiki/pages/" + page.id();
        Response published = setup.editor().send("POST", base + "/publish", Map.of("revision", 2), "\"2\"");
        assertEquals(200, published.status(), published.body());
        Browser reader = member(setup, "wiki.read", "ai.use");
        assertFalse(reader.get(base + "/ai").json().get("revision").asBoolean());
        assertEquals(403, revise(reader, page, 2, 1, "shorter", null).status());

        Response stale = revise(setup.editor(), page, 1, 1, "shorter", null);
        assertEquals(412, stale.status(), stale.body());
        assertEquals("wiki.version_conflict", stale.code());
        assertTrue(revise(setup.editor(), page, 2, 9, "shorter", null).violations().contains("section.invalid"));
        assertTrue(revise(setup.editor(), page, 2, 1, "custom", " ").violations().contains("goal.invalid"));
        assertTrue(revise(setup.editor(), page, 2, 1, "poetry", null).violations().contains("goal.invalid"));
        assertTrue(revise(setup.editor(), page, null, 1, "shorter", null).violations().contains("revision.invalid"));

        MODEL.answer = answer("## Einleitung\n\nEine kurze Einleitung.\n");
        UUID run = runId(revise(setup.editor(), page, 2, 1, "shorter", null));
        assertEquals("succeeded", finished(setup.editor(), run).get("state").asString());
        Response saved = setup.editor().send("PUT", base + "/draft", Map.of("title", "Ablauf", "content",
            document("Dieser Text ist eine sehr lange und ausführliche Einleitung.", page.target(), page.image(), "Schluss neu")), "\"2\"");
        assertEquals(200, saved.status(), saved.body());
        assertFalse(setup.editor().get("/api/v1/ai/runs/" + run).json().get("content").isNull(), "another section changed");
        JsonNode result = setup.editor().get(base + "/ai/revision/" + run).json();
        assertEquals(3, result.get("revision").asInt());
        assertTrue(result.get("document").toString().contains("Eine kurze Einleitung."));
        assertTrue(result.get("document").toString().contains("Schluss neu"));

        Response other = setup.editor().get("/api/v1/wiki/pages/" + page.target() + "/ai/revision/" + run);
        assertEquals(404, other.status(), "a result answers only at its page");
        assertEquals("ai.run_not_found", other.code());
    }

    /** A portal account reaches none of the new routes, before any object is read. */
    @Test
    void aPortalAccountCannotStartTheReviewOrTheRevision() {
        Setup setup = setup();
        Response requirement = requirement(setup.editor(), setup);
        Page page = page(setup);
        Response customer = setup.admin().send("POST", "/api/v1/helpdesk/customers", Map.of("name", "Nordwerk " + COUNTER.incrementAndGet(),
            "projects", List.of(setup.project())), null);
        assertEquals(201, customer.status(), customer.body());
        Map<String, Object> body = new HashMap<>();
        body.put("username", "kunde-" + COUNTER.incrementAndGet());
        body.put("displayName", "Kunde");
        body.put("organizationId", customer.json().get("id").asString());
        body.put("contactId", null);
        body.put("projectIds", List.of(setup.project()));
        Browser accountsAdmin = person(AdministrationRight.ACCOUNTS_MANAGE);
        Response created = accountsAdmin.send("POST", "/api/v1/portal-accounts", body, null);
        assertEquals(201, created.status(), created.body());
        Browser portal = login(UUID.fromString(created.json().get("account").get("id").asString()),
            created.json().get("account").get("username").asString(), created.json().get("oneTimePassword").asString());
        List<Response> refused = List.of(portal.get(path(requirement) + "/ai?revision=1"), review(portal, requirement, 1),
            portal.get("/api/v1/wiki/pages/" + page.id() + "/ai"), revise(portal, page, 2, 1, "shorter", null),
            portal.get("/api/v1/wiki/pages/" + page.id() + "/ai/revision/" + UUID.randomUUID()));
        for (Response response : refused) {
            assertEquals(403, response.status(), response.body());
            assertEquals("auth.forbidden", response.code());
        }
    }
}
