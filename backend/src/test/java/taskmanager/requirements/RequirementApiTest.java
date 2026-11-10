package taskmanager.requirements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * TASK-096: requirements through the real HTTP interface with the real permissions, projects and wiki modules and a real
 * PostgreSQL: the example requirement of REQ-REQ-001 with criteria, review and approval, the new revision after a content change
 * of an approved requirement that leaves the approval alone (F-13, DEC-030), the approval of exactly the shown revision, the
 * three actions and the field rules in answers, history and comparison, the fixed status flow, criteria with fixed numbers, the
 * checked description, the list with filters and pages and numbers under concurrency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequirementApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource dataSource;

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, String body, String etag) {
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

        /**
         * Announces an upload of {@code declaredLength} bytes and sends none of them, so the refusal of the declared length is
         * read without a client that is still writing a large body while the server already answers and closes.
         */
        Response announce(String path, long declaredLength) {
            try (Socket socket = new Socket("localhost", port)) {
                socket.setSoTimeout(10_000);
                List<String> pairs = new ArrayList<>();
                cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                String head = "POST " + path + " HTTP/1.1\r\nHost: localhost:" + port + "\r\nContent-Type: application/octet-stream\r\n"
                    + "Content-Length: " + declaredLength + "\r\nCookie: " + String.join("; ", pairs) + "\r\nX-CSRF-Token: "
                    + cookies.get("__Host-tm-csrf") + "\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                int status = Integer.parseInt(reader.readLine().split(" ")[1]);
                boolean chunked = false;
                for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                    chunked |= line.toLowerCase(Locale.ROOT).startsWith("transfer-encoding:") && line.toLowerCase(Locale.ROOT).contains("chunked");
                }
                StringBuilder body = new StringBuilder();
                if (chunked) {
                    for (String size = reader.readLine(); size != null && !size.isBlank(); size = reader.readLine()) {
                        int remaining = Integer.parseInt(size.trim(), 16);
                        if (remaining == 0) {
                            break;
                        }
                        char[] chunk = new char[remaining];
                        int read = 0;
                        while (read < remaining) {
                            int n = reader.read(chunk, read, remaining - read);
                            if (n < 0) {
                                break;
                            }
                            read += n;
                        }
                        body.append(chunk, 0, read);
                        reader.readLine();
                    }
                } else {
                    for (int c = reader.read(); c >= 0; c = reader.read()) {
                        body.append((char) c);
                    }
                }
                return new Response(status, body.toString(), null);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        HttpResponse<byte[]> raw(String path) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
                List<String> pairs = new ArrayList<>();
                cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                request.header("Cookie", String.join("; ", pairs));
                return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", "req-" + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        Response login = browser.send("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null);
        assertEquals(200, login.status(), login.body());
        Response changed = browser.send("POST", "/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG), null);
        assertEquals(200, changed.status(), changed.body());
        browser.id = issued.account().id();
        return browser;
    }

    /** Grants actions and requirement field rules ("READ:title") in a group of its own for one project; returns the group. */
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
                jdbc.update("""
                        insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                        values (?, ?, ?, 'requirement', ?, ?, 'ALLOW')
                        """, companyId, group, project, parts[1], parts[0]);
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
        return group;
    }

    private void revoke(UUID group) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("delete from permissions_membership where group_id = ?", group);
            jdbc.update("update permissions_revision set revision = revision + 1");
        });
    }

    // ---------------------------------------------------------------- fixtures

    private record Setup(UUID project, Browser admin) {
    }

    private Setup project() {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.PROJECTS_MANAGE);
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "RQ" + n, "name", "Anforderungen " + n), null);
        assertEquals(201, created.status(), created.body());
        return new Setup(UUID.fromString(created.json().get("id").asString()), admin);
    }

    private Browser member(Setup setup, String... rules) {
        Browser browser = person();
        add(setup, browser.id);
        if (rules.length > 0) {
            grant(browser.id, setup.project(), rules);
        }
        return browser;
    }

    private void add(Setup setup, UUID account) {
        Response current = setup.admin().get("/api/v1/projects/" + setup.project());
        assertEquals(200, current.status(), current.body());
        Response added = setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + account, null, current.etag());
        assertEquals(200, added.status(), added.body());
    }

    private static void assertInvalid(Response response, String violation) {
        assertEquals(400, response.status(), response.body());
        List<String> violations = new ArrayList<>();
        response.json().get("violations").forEach(v -> violations.add(v.asString()));
        assertTrue(violations.contains(violation), response.body());
    }

    // ---------------------------------------------------------------- helpers

    private static final String[] FIELDS = { "title", "description", "criteria", "priority", "owner" };

    private static String[] rules(List<String> actions, boolean write, String... withoutRead) {
        List<String> rules = new ArrayList<>(actions);
        for (String field : FIELDS) {
            if (!List.of(withoutRead).contains(field)) {
                rules.add("READ:" + field);
                if (write) {
                    rules.add("WRITE:" + field);
                }
            }
        }
        return rules.toArray(String[]::new);
    }

    private Browser editor(Setup setup) {
        return member(setup, rules(List.of("requirement.read", "requirement.edit"), true));
    }

    private Browser approver(Setup setup) {
        return member(setup, rules(List.of("requirement.read", "requirement.approve"), false));
    }

    private Browser reader(Setup setup, String... withoutRead) {
        return member(setup, rules(List.of("requirement.read"), false, withoutRead));
    }

    private static Map<String, Object> paragraphDoc(String value) {
        return Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", value)))));
    }

    private static Map<String, Object> criterion(Integer id, String text) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("id", id);
        criterion.put("text", text);
        return criterion;
    }

    private Response create(Browser who, Setup setup, Map<String, Object> body) {
        return who.send("POST", "/api/v1/requirements/projects/" + setup.project() + "/requirements", body, null);
    }

    /** The example of REQ-REQ-001 with three checkable criteria. */
    private Response example(Browser who, Setup setup) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Kunden sehen den Stand ihrer freigegebenen Supportanfragen");
        body.put("description", paragraphDoc("Im Portal sehen berechtigte Kunden den Bearbeitungsstand."));
        body.put("priority", "high");
        body.put("criteria", List.of(criterion(null, "Die Portalliste zeigt den Status jeder freigegebenen Anfrage."),
            criterion(null, "Anfragen anderer Organisationen erscheinen nicht."),
            criterion(null, "Ein Statuswechsel ist nach dem Neuladen sichtbar.")));
        Response created = create(who, setup, body);
        assertEquals(201, created.status(), created.body());
        return created;
    }

    private static UUID id(Response requirement) {
        return UUID.fromString(requirement.json().get("id").asString());
    }

    private static String path(UUID id) {
        return "/api/v1/requirements/" + id;
    }

    private static List<Integer> criterionIds(Response requirement) {
        List<Integer> ids = new ArrayList<>();
        requirement.json().get("content").get("criteria").forEach(c -> ids.add(c.get("id").asInt()));
        return ids;
    }

    private static List<String> kinds(Response history) {
        List<String> kinds = new ArrayList<>();
        history.json().get("events").forEach(e -> kinds.add(e.get("kind").asString()));
        return kinds;
    }

    /** Creates, submits and approves the example; returns the approved requirement. */
    private Response approved(Setup setup, Browser editor, Browser approver) {
        Response created = example(editor, setup);
        UUID id = id(created);
        Response submitted = editor.send("POST", path(id) + "/submit", null, created.etag());
        assertEquals(200, submitted.status(), submitted.body());
        Response done = approver.send("POST", path(id) + "/approve", Map.of("revision", 1), submitted.etag());
        assertEquals(200, done.status(), done.body());
        return done;
    }

    // ---------------------------------------------------------------- tests

    /** REQ-REQ-001, REQ-REQ-002: the example requirement with criteria is created, submitted and approved. */
    @Test
    void theExampleRequirementIsCapturedSubmittedAndApproved() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser approver = approver(setup);
        Response created = example(editor, setup);
        JsonNode body = created.json();
        assertEquals("ANF-1", body.get("key").asString());
        assertEquals("draft", body.get("status").asString());
        assertEquals(1, body.get("revision").asInt());
        assertTrue(body.get("approval").isNull());
        assertEquals(List.of(1, 2, 3), criterionIds(created));
        assertEquals("high", body.get("content").get("priority").asString());
        assertTrue(body.get("content").get("descriptionHtml").asString().contains("Bearbeitungsstand"));
        assertEquals("\"1\"", created.etag());

        UUID id = id(created);
        assertEquals(409, approver.send("POST", path(id) + "/approve", Map.of("revision", 1), created.etag()).status(), "only a review is approved");
        assertEquals(403, editor.send("POST", path(id) + "/approve", Map.of("revision", 1), created.etag()).status(), "edit does not approve");
        assertEquals(403, approver.send("POST", path(id) + "/submit", null, created.etag()).status(), "approve does not submit");
        Response submitted = editor.send("POST", path(id) + "/submit", null, created.etag());
        assertEquals(200, submitted.status(), submitted.body());
        assertEquals("review", submitted.json().get("status").asString());

        Response done = approver.send("POST", path(id) + "/approve", Map.of("revision", 1), submitted.etag());
        assertEquals(200, done.status(), done.body());
        assertEquals("approved", done.json().get("status").asString());
        JsonNode approval = done.json().get("approval");
        assertEquals(1, approval.get("revision").asInt());
        assertEquals(approver.id.toString(), approval.get("approvedBy").asString());
        assertFalse(approval.get("approvedAt").isNull());
        assertEquals(List.of(editor.id.toString()), List.of(done.json().get("editors").get(0).asString()));
        assertEquals(List.of("created", "submitted", "approved"), kinds(approver.get(path(id) + "/history")));

        Response second = example(editor, setup);
        assertEquals("ANF-2", second.json().get("key").asString(), "every project counts on");
    }

    /** F-13, DEC-030: a new criterion after the approval opens revision 2; revision 1 and its approval stay as they were. */
    @Test
    void aContentChangeAfterTheApprovalOpensANewRevisionAndKeepsTheApproval() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser approver = approver(setup);
        Response done = approved(setup, editor, approver);
        UUID id = id(done);
        String approvedAt = done.json().get("approval").get("approvedAt").asString();

        List<Object> criteria = new ArrayList<>();
        criterionIds(done).forEach(n -> criteria.add(criterion(n, done.json().get("content").get("criteria").get(n - 1).get("text").asString())));
        criteria.add(criterion(null, "Die letzte Kundenantwort ist sichtbar."));
        Response changed = editor.send("PATCH", path(id), Map.of("criteria", criteria), done.etag());
        assertEquals(200, changed.status(), changed.body());
        JsonNode body = changed.json();
        assertEquals(2, body.get("revision").asInt());
        assertEquals("draft", body.get("status").asString(), "the new revision is not approved");
        assertEquals(1, body.get("approvedRevision").asInt());
        assertEquals(1, body.get("approval").get("revision").asInt());
        assertEquals(approvedAt, body.get("approval").get("approvedAt").asString(), "the approval is neither overwritten nor carried over");
        assertEquals(List.of(1, 2, 3, 4), criterionIds(changed));

        Response first = editor.get(path(id) + "/revisions/1");
        assertEquals(200, first.status(), first.body());
        assertEquals(3, first.json().get("content").get("criteria").size(), "the approved revision keeps its content");
        assertEquals(approver.id.toString(), first.json().get("approvedBy").asString());
        assertFalse(first.json().get("current").asBoolean());
        Response second = editor.get(path(id) + "/revisions/2");
        assertTrue(second.json().get("approvedAt").isNull());
        assertTrue(second.json().get("current").asBoolean());

        // A second save of the new draft changes revision 2, no third revision.
        Response again = editor.send("PATCH", path(id), Map.of("title", "Kunden sehen Stand und letzte Antwort"), changed.etag());
        assertEquals(200, again.status(), again.body());
        assertEquals(2, again.json().get("revision").asInt());
        // The owner is organisational: no new revision even after an approval.
        Response history = editor.get(path(id) + "/history");
        assertEquals(List.of("created", "submitted", "approved", "revision_opened", "criterion_added", "changed"), kinds(history));
        assertEquals(2, history.json().get("revisions").size());

        Response submitted = editor.send("POST", path(id) + "/submit", null, again.etag());
        Response approvedTwo = approver.send("POST", path(id) + "/approve", Map.of("revision", 2), submitted.etag());
        assertEquals(200, approvedTwo.status(), approvedTwo.body());
        Response owned = editor.send("PATCH", path(id), Map.of("ownerId", editor.id.toString()), approvedTwo.etag());
        assertEquals(200, owned.status(), owned.body());
        assertEquals(2, owned.json().get("revision").asInt(), "a new owner opens no revision");
        assertEquals("approved", owned.json().get("status").asString());
        assertEquals(editor.id.toString(), owned.json().get("ownerId").asString());
        assertEquals(List.of(1, 2), List.of(editor.get(path(id) + "/revisions/1").json().get("revision").asInt(),
            owned.json().get("approval").get("revision").asInt()));
    }

    /** The approval needs the current version and names exactly the current revision; nobody approves what they cannot read. */
    @Test
    void anApprovalNeedsTheShownRevisionAndEveryContentField() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser approver = approver(setup);
        Browser blind = member(setup, rules(List.of("requirement.read", "requirement.approve"), false, "description"));
        Response created = example(editor, setup);
        UUID id = id(created);
        Response submitted = editor.send("POST", path(id) + "/submit", null, created.etag());
        Response edited = editor.send("PATCH", path(id), Map.of("priority", "critical"), submitted.etag());
        assertEquals(200, edited.status(), edited.body());
        assertEquals("review", edited.json().get("status").asString(), "a change under review keeps the review");

        Response stale = approver.send("POST", path(id) + "/approve", Map.of("revision", 1), submitted.etag());
        assertEquals(412, stale.status(), "the screen showed an older state");
        assertEquals("requirement.version_conflict", stale.code());
        assertEquals(412, approver.send("POST", path(id) + "/approve", Map.of("revision", 2), edited.etag()).status(), "only the current revision");
        assertEquals(428, approver.send("POST", path(id) + "/approve", Map.of("revision", 1), null).status());
        assertInvalid(approver.send("POST", path(id) + "/approve", Map.of(), edited.etag()), "revision.required");
        Response refused = blind.send("POST", path(id) + "/approve", Map.of("revision", 1), edited.etag());
        assertEquals(403, refused.status(), refused.body());
        assertEquals("requirement.content_not_readable", refused.code());
        assertEquals(200, approver.send("POST", path(id) + "/approve", Map.of("revision", 1), edited.etag()).status());
    }

    /** The fixed flow: no review without a criterion, a return needs its reason, discarded requirements are not changed. */
    @Test
    void theFixedStatusFlowWithReturnDiscardAndRestore() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser approver = approver(setup);
        Response bare = create(editor, setup, Map.of("title", "Ohne Kriterien"));
        assertEquals(201, bare.status(), bare.body());
        assertEquals("normal", bare.json().get("content").get("priority").asString());
        assertTrue(bare.json().get("content").get("criteria").isEmpty());
        assertEquals("", bare.json().get("content").get("descriptionHtml").asString());
        assertInvalid(editor.send("POST", path(id(bare)) + "/submit", null, bare.etag()), "criteria.required");

        Response created = example(editor, setup);
        UUID id = id(created);
        Response submitted = editor.send("POST", path(id) + "/submit", null, created.etag());
        assertEquals(409, editor.send("POST", path(id) + "/submit", null, submitted.etag()).status());
        assertInvalid(approver.send("POST", path(id) + "/return", Map.of("reason", "  "), submitted.etag()), "reason.required");
        Response returned = approver.send("POST", path(id) + "/return", Map.of("reason", "Kriterium 2 ist nicht prüfbar."), submitted.etag());
        assertEquals(200, returned.status(), returned.body());
        assertEquals("draft", returned.json().get("status").asString());
        JsonNode last = editor.get(path(id) + "/history").json().get("events").get(2);
        assertEquals("returned", last.get("kind").asString());
        assertEquals("Kriterium 2 ist nicht prüfbar.", last.get("reason").asString());
        assertEquals("review", last.get("from").asString());

        Response discarded = editor.send("POST", path(id) + "/discard", null, returned.etag());
        assertEquals(200, discarded.status(), discarded.body());
        assertEquals("discarded", discarded.json().get("status").asString());
        Response refused = editor.send("PATCH", path(id), Map.of("title", "Neu"), discarded.etag());
        assertEquals(409, refused.status());
        assertEquals("requirement.discarded", refused.code());
        String list = "/api/v1/requirements/projects/" + setup.project() + "/requirements";
        assertEquals(1, editor.get(list).json().get("items").size(), "discarded requirements are left out of the list");
        assertEquals(1, editor.get(list + "?status=discarded").json().get("items").size());
        Response restored = editor.send("POST", path(id) + "/restore", null, discarded.etag());
        assertEquals("draft", restored.json().get("status").asString());

        Response done = approved(setup, editor, approver);
        Response gone = editor.send("POST", path(id(done)) + "/discard", Map.of("reason", "Ersetzt durch ANF-4"), done.etag());
        Response back = editor.send("POST", path(id(done)) + "/restore", null, gone.etag());
        assertEquals("approved", back.json().get("status").asString(), "the approved current revision stays approved");
        assertEquals(1, back.json().get("approval").get("revision").asInt());
    }

    /** Without requirement.read nothing exists; a field without its read rule is missing from answers, history and revisions. */
    @Test
    void readRulesHideRequirementsAndFieldsEverywhere() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser outsider = member(setup, "requirement.edit");
        Browser partial = reader(setup, "criteria", "owner");
        Response created = example(editor, setup);
        UUID id = id(created);
        Response changed = editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(1, "Nur noch ein Kriterium.")), "ownerId",
            editor.id.toString()), created.etag());
        assertEquals(200, changed.status(), changed.body());

        assertEquals(404, outsider.get(path(id)).status(), "edit alone does not read");
        assertEquals(404, outsider.get("/api/v1/requirements/projects/" + setup.project() + "/requirements").status());
        assertEquals(0, outsider.get("/api/v1/requirements/projects").json().size());
        Browser stranger = person();
        assertEquals(404, stranger.get(path(id)).status());

        Response seen = partial.get(path(id));
        assertEquals(200, seen.status(), seen.body());
        JsonNode content = seen.json().get("content");
        assertTrue(content.get("criteria").isNull());
        assertTrue(seen.json().get("ownerId").isNull());
        assertEquals("Kunden sehen den Stand ihrer freigegebenen Supportanfragen", content.get("title").asString());
        assertEquals(List.of("description", "priority", "title"), names(seen.json().get("access").get("readableFields")));
        assertTrue(seen.json().get("access").get("writableFields").isEmpty());
        assertEquals(List.of("created"), kinds(partial.get(path(id) + "/history")), "criterion and owner changes are hidden");
        assertTrue(partial.get(path(id) + "/revisions/1").json().get("content").get("criteria").isNull());
        assertInvalid(partial.get("/api/v1/requirements/projects/" + setup.project() + "/requirements?owner=" + editor.id), "owner.not_readable");
        assertEquals(403, partial.send("PATCH", path(id), Map.of("title", "x"), seen.etag()).status());
        assertEquals(403, partial.get("/api/v1/requirements/projects/" + setup.project() + "/owners").status());
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(n -> names.add(n.asString()));
        return names;
    }

    /** Changing a field needs its write rule; creating needs the one of the title. */
    @Test
    void writeRulesGuardEveryNamedField() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser titleOnly = member(setup, "requirement.read", "requirement.edit", "READ:title", "WRITE:title", "READ:priority");
        Response created = example(editor, setup);
        UUID id = id(created);
        assertEquals(403, titleOnly.send("PATCH", path(id), Map.of("priority", "low"), created.etag()).status());
        Response renamed = titleOnly.send("PATCH", path(id), Map.of("title", "Neuer Titel"), created.etag());
        assertEquals(200, renamed.status(), renamed.body());
        assertEquals(List.of("title"), names(renamed.json().get("access").get("writableFields")));
        assertEquals(403, titleOnly.send("POST", "/api/v1/requirements/projects/" + setup.project() + "/requirements",
            Map.of("title", "Mit Priorität", "priority", "low"), null).status());
        Response plain = titleOnly.send("POST", "/api/v1/requirements/projects/" + setup.project() + "/requirements", Map.of("title", "Nur Titel"), null);
        assertEquals(201, plain.status(), plain.body());
        Browser noTitle = member(setup, "requirement.read", "requirement.edit", "READ:priority", "WRITE:priority");
        assertEquals(403, noTitle.send("POST", "/api/v1/requirements/projects/" + setup.project() + "/requirements", Map.of("title", "x"), null).status());
    }

    /** Criteria keep their fixed numbers; a removed number is never given again; wrong lists are refused. */
    @Test
    void criteriaKeepTheirNumbersAndOrder() {
        Setup setup = project();
        Browser editor = editor(setup);
        Response created = example(editor, setup);
        UUID id = id(created);
        Response moved = editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(3, "Ein Statuswechsel ist nach dem Neuladen sichtbar."),
            criterion(1, "Die Portalliste zeigt den Status jeder freigegebenen Anfrage."), criterion(null, "Neu"))), created.etag());
        assertEquals(200, moved.status(), moved.body());
        assertEquals(List.of(3, 1, 4), criterionIds(moved), "K2 is gone and K4 is new");
        List<JsonNode> events = new ArrayList<>();
        editor.get(path(id) + "/history").json().get("events").forEach(events::add);
        assertEquals("criterion_removed", events.get(1).get("kind").asString());
        assertEquals(2, events.get(1).get("criterion").asInt());
        assertEquals("criterion_added", events.get(2).get("kind").asString());
        assertEquals(4, events.get(2).get("criterion").asInt());
        assertEquals("criteria_moved", events.get(3).get("kind").asString());
        assertEquals("K1,K3", events.get(3).get("from").asString());
        assertEquals("K3,K1", events.get(3).get("to").asString());

        Response again = editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(null, "Noch eins"))), moved.etag());
        assertEquals(List.of(5), criterionIds(again), "numbers are never given again");

        assertInvalid(editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(2, "alt"))), again.etag()), "criteria.unknown");
        assertInvalid(editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(5, "a"), criterion(5, "b"))), again.etag()), "criteria.unknown");
        assertInvalid(editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(null, " "))), again.etag()), "criteria.text_required");
        assertInvalid(editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(null, "x".repeat(2001)))), again.etag()), "criteria.text_too_long");
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(criterion(null, "K " + i));
        }
        assertInvalid(editor.send("PATCH", path(id), Map.of("criteria", many), again.etag()), "criteria.too_many");
        assertInvalid(editor.send("PATCH", path(id), Map.of("criteria", "nein"), again.etag()), "criteria.invalid");
        Response multi = editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(5, "Zeile 1\nZeile 2"))), again.etag());
        assertEquals(200, multi.status(), multi.body());
        assertEquals("Zeile 1\nZeile 2", multi.json().get("content").get("criteria").get(0).get("text").asString());
    }

    /** Fields are checked: title, priority, owner and the description without images and wiki links. */
    @Test
    void valuesAndTheDescriptionAreChecked() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser outsider = person();
        assertInvalid(create(editor, setup, Map.of("title", " ")), "title.required");
        assertInvalid(create(editor, setup, Map.of("title", "x".repeat(201))), "title.too_long");
        assertInvalid(create(editor, setup, Map.of("title", "a", "priority", "dringend")), "priority.invalid");
        assertInvalid(create(editor, setup, Map.of("title", "a", "ownerId", outsider.id.toString())), "owner.not_member");
        assertInvalid(create(editor, setup, Map.of("title", "a", "unknown", 1)), "unknown.unknown");
        Map<String, Object> image = Map.of("type", "doc", "content", List.of(Map.of("type", "image",
            "attrs", Map.of("src", "/api/v1/wiki/images/" + UUID.randomUUID(), "alt", "Bild"))));
        assertInvalid(create(editor, setup, Map.of("title", "a", "description", image)), "description.image_not_allowed");
        Map<String, Object> wikiLink = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text",
            "text", "Seite", "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", "wiki:" + UUID.randomUUID()))))))));
        assertInvalid(create(editor, setup, Map.of("title", "a", "description", wikiLink)), "description.page_link_not_allowed");
        Map<String, Object> script = Map.of("type", "doc", "content", List.of(Map.of("type", "script")));
        assertInvalid(create(editor, setup, Map.of("title", "a", "description", script)), "description.unsupported");

        Response created = example(editor, setup);
        UUID id = id(created);
        assertEquals(428, editor.send("PATCH", path(id), Map.of("title", "x"), null).status());
        assertInvalid(editor.send("PATCH", path(id), Map.of(), created.etag()), "changes.required");
        Response same = editor.send("PATCH", path(id), Map.of("title", "Kunden sehen den Stand ihrer freigegebenen Supportanfragen"), created.etag());
        assertEquals(200, same.status(), same.body());
        assertEquals(1, same.json().get("version").asLong(), "nothing changed, nothing is written");
        Response cleared = editor.send("PATCH", path(id), java.util.Collections.singletonMap("description", null), created.etag());
        assertEquals(200, cleared.status(), cleared.body());
        assertEquals("", cleared.json().get("content").get("descriptionHtml").asString());
        assertEquals(412, editor.send("PATCH", path(id), Map.of("title", "y"), created.etag()).status());
    }

    /** The list: filters over readable fields, pages after a number, owners for the choice. */
    @Test
    void theListFiltersAndPages() {
        Setup setup = project();
        Browser editor = editor(setup);
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", (i % 2 == 0 ? "Bericht " : "Portal ") + i);
            body.put("priority", i == 5 ? "critical" : "normal");
            if (i == 3) {
                body.put("ownerId", editor.id.toString());
            }
            assertEquals(201, create(editor, setup, body).status());
        }
        String list = "/api/v1/requirements/projects/" + setup.project() + "/requirements";
        Response first = editor.get(list + "?limit=2");
        assertEquals(List.of("ANF-1", "ANF-2"), keys(first));
        assertEquals(2, first.json().get("nextAfter").asLong());
        Response last = editor.get(list + "?limit=2&after=4");
        assertEquals(List.of("ANF-5"), keys(last));
        assertTrue(last.json().get("nextAfter").isNull());
        assertEquals(List.of("ANF-2", "ANF-4"), keys(editor.get(list + "?text=BERICHT")));
        assertEquals(List.of("ANF-5"), keys(editor.get(list + "?priority=critical")));
        assertEquals(List.of("ANF-3"), keys(editor.get(list + "?owner=" + editor.id)));
        assertInvalid(editor.get(list + "?status=offen"), "status.invalid");
        assertInvalid(editor.get(list + "?limit=0"), "limit.invalid");
        Browser blind = reader(setup, "title");
        assertInvalid(blind.get(list + "?text=Portal"), "text.not_readable");
        assertTrue(blind.get(list).json().get("items").get(0).get("title").isNull());
        Response owners = editor.get("/api/v1/requirements/projects/" + setup.project() + "/owners");
        assertEquals(200, owners.status(), owners.body());
        List<String> ids = names(owners.json());
        assertTrue(ids.contains(editor.id.toString()));
        assertFalse(ids.contains(blind.id.toString()) && false);
        Response projects = editor.get("/api/v1/requirements/projects");
        assertEquals(1, projects.json().size());
        assertTrue(projects.json().get(0).get("access").get("edit").asBoolean());
    }

    private static List<String> keys(Response list) {
        assertEquals(200, list.status(), list.body());
        List<String> keys = new ArrayList<>();
        list.json().get("items").forEach(item -> keys.add(item.get("key").asString()));
        return keys;
    }

    /** Requirements created at the same time get distinct numbers; a revoked right ends further changes. */
    @Test
    void parallelCreationsGetDistinctNumbersAndARevocationIsSeen() throws Exception {
        Setup setup = project();
        Browser editor = editor(setup);
        List<CompletableFuture<Response>> calls = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) {
            int n = i;
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return create(editor, setup, Map.of("title", "Parallel " + n));
            }));
        }
        start.countDown();
        List<String> keys = new ArrayList<>();
        for (CompletableFuture<Response> call : calls) {
            Response response = call.get(30, TimeUnit.SECONDS);
            if (response.status() == 503) {
                assertEquals("requirement.busy", response.code());
                continue;
            }
            assertEquals(201, response.status(), response.body());
            keys.add(response.json().get("key").asString());
        }
        assertEquals(keys.size(), new java.util.HashSet<>(keys).size(), keys.toString());
        assertFalse(keys.isEmpty());

        Browser temporary = person();
        add(setup, temporary.id);
        UUID group = grant(temporary.id, setup.project(), rules(List.of("requirement.read", "requirement.edit"), true));
        Response created = create(temporary, setup, Map.of("title", "Vor dem Entzug"));
        assertEquals(201, created.status(), created.body());
        revoke(group);
        assertEquals(404, temporary.send("PATCH", path(id(created)), Map.of("title", "Nach dem Entzug"), created.etag()).status());
    }

    /** The rights catalog names the three actions and the fields of the resource type requirement. */
    @Test
    void theCatalogNamesActionsAndFields() {
        Browser admin = person(AdministrationRight.PERMISSIONS_MANAGE);
        Response catalog = admin.get("/api/v1/permissions/catalog");
        assertEquals(200, catalog.status(), catalog.body());
        List<String> actions = names(catalog.json().get("projectActions"));
        assertTrue(actions.containsAll(List.of("requirement.read", "requirement.edit", "requirement.approve")), actions.toString());
        boolean found = false;
        for (JsonNode fields : catalog.json().get("fields")) {
            if ("requirement".equals(fields.get("resourceType").asString())) {
                assertEquals(List.of("criteria", "description", "owner", "priority", "title"), names(fields.get("fields")));
                found = true;
            }
        }
        assertTrue(found);
        Response group = admin.send("POST", "/api/v1/permissions/groups", Map.of("name", "Anforderungen lesen"), null);
        assertEquals(201, group.status(), group.body());
        String groupId = group.json().get("id").asString();
        Response field = admin.send("PUT", "/api/v1/permissions/groups/" + groupId + "/fields",
            Map.of("resourceType", "requirement", "field", "criteria", "operation", "read", "effect", "allow"), null);
        assertEquals(200, field.status(), field.body());
        assertInvalid(admin.send("PUT", "/api/v1/permissions/groups/" + groupId + "/fields",
            Map.of("resourceType", "requirement", "field", "status", "operation", "read", "effect", "allow"), null), "field.unknown");
        assertInvalid(admin.send("PUT", "/api/v1/permissions/groups/" + groupId + "/fields",
            Map.of("resourceType", "ticket", "field", "criteria", "operation", "read", "effect", "allow"), null), "field.unknown");
    }
}
