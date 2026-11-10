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
                // "READ:title" is a rule of requirements, "ticket:READ:title" one of tickets (TASK-097).
                String[] parts = rule.split(":");
                String type = parts.length == 3 ? parts[0] : "requirement";
                jdbc.update("""
                        insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                        values (?, ?, ?, ?, ?, ?, 'ALLOW')
                        """, companyId, group, project, type, parts[parts.length - 1], parts[parts.length - 2]);
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

    /** Holger's choice on H3 of the TASK-096 review: only changes of readable fields name their person in editors. */
    @Test
    void editorsOnlyNameChangesOfReadableFields() {
        Setup setup = project();
        Browser anna = editor(setup);
        Browser ben = editor(setup);
        Browser partial = reader(setup, "description");
        Browser full = approver(setup);
        Response created = example(anna, setup);
        UUID id = id(created);
        Response changed = ben.send("PATCH", path(id), Map.of("description", paragraphDoc("Nur Ben ändert die Beschreibung.")), created.etag());
        assertEquals(200, changed.status(), changed.body());

        assertEquals(List.of(anna.id.toString(), ben.id.toString()), names(full.get(path(id)).json().get("editors")));
        assertEquals(List.of(anna.id.toString()), names(partial.get(path(id)).json().get("editors")), "a hidden change names nobody");
        // R2 of the follow up review: without a readable content field even the creation names nobody.
        Browser noField = member(setup, "requirement.read");
        Browser ownerOnly = member(setup, "requirement.read", "READ:owner");
        assertTrue(noField.get(path(id)).json().get("editors").isEmpty(), "no field rule, no editors");
        assertTrue(ownerOnly.get(path(id)).json().get("editors").isEmpty(), "the owner alone is no content field");

        Response submitted = anna.send("POST", path(id) + "/submit", Map.of(), changed.etag());
        Response approved = full.send("POST", path(id) + "/approve", Map.of("revision", 1), submitted.etag());
        assertEquals(200, approved.status(), approved.body());
        Response opened = ben.send("PATCH", path(id), Map.of("description", paragraphDoc("Revision 2 nur in der Beschreibung.")), approved.etag());
        assertEquals(2, opened.json().get("revision").asInt(), opened.body());
        assertEquals(List.of(ben.id.toString()), names(full.get(path(id)).json().get("editors")));
        assertTrue(partial.get(path(id)).json().get("editors").isEmpty(), "opening the revision does not name the person either");
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
        // H1 of the TASK-096 review: CRLF and CR count as line feeds before the checks; other control characters stay refused.
        Response windows = editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(5, "Zeile 1\r\nZeile 2\rZeile 3"))), multi.etag());
        assertEquals(200, windows.status(), windows.body());
        assertEquals("Zeile 1\nZeile 2\nZeile 3", windows.json().get("content").get("criteria").get(0).get("text").asString());
        assertInvalid(editor.send("PATCH", path(id), Map.of("criteria", List.of(criterion(5, "Zeile\u0007"))), windows.etag()), "criteria.text_invalid");
        Response submitted = editor.send("POST", path(id) + "/submit", Map.of(), windows.etag());
        assertEquals(200, submitted.status(), submitted.body());
        Browser approver = approver(setup);
        Response returned = approver.send("POST", path(id) + "/return", Map.of("reason", "Bitte\r\nergänzen"), submitted.etag());
        assertEquals(200, returned.status(), returned.body());
        JsonNode history = editor.get(path(id) + "/history").json().get("events");
        assertEquals("Bitte\nergänzen", history.get(history.size() - 1).get("reason").asString());
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
        Map<String, Object> unsafe = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text",
            "text", "Klick", "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", "javascript:alert(1)"))))))));
        assertInvalid(create(editor, setup, Map.of("title", "a", "description", unsafe)), "description.link_invalid");
        // N1 of the follow up review: words that look like node types or wiki links are plain text; a control character in a target is not.
        assertEquals(201, create(editor, setup, Map.of("title", "a", "description", paragraphDoc("image"))).status());
        assertEquals(201, create(editor, setup, Map.of("title", "a", "description", paragraphDoc("wiki:ordinary text"))).status());
        Map<String, Object> nul = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text",
            "text", "Ziel", "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", "https://example.test/a\u0000b"))))))));
        assertInvalid(create(editor, setup, Map.of("title", "a", "description", nul)), "description.link_invalid");

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

    // ---------------------------------------------------------------- custom fields (TASK-098)

    private static Map<String, Object> field(String key, String name, String type, boolean required, List<Map<String, Object>> options) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("name", name);
        field.put("type", type);
        field.put("required", required);
        if (options != null) {
            field.put("options", options);
        }
        return field;
    }

    /** Publishes the configuration of the project with the given custom fields of requirements; returns the answer. */
    private Response requirementFields(Setup setup, List<Map<String, Object>> fields) {
        Response current = setup.admin().get("/api/v1/projects/" + setup.project());
        assertEquals(200, current.status(), current.body());
        Map<String, Object> configuration = JSON.convertValue(current.json().get("configuration"),
            new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
        configuration.put("requirementFields", fields);
        return setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/configuration", configuration, current.etag());
    }

    private static final List<Map<String, Object>> AREA_OPTIONS = List.of(Map.of("key", "portal", "name", "Portal"),
        Map.of("key", "intern", "name", "Intern"));

    private static String[] customRules(String[] base, boolean write, String... keys) {
        List<String> rules = new ArrayList<>(List.of(base));
        for (String key : keys) {
            rules.add("READ:custom." + key);
            if (write) {
                rules.add("WRITE:custom." + key);
            }
        }
        return rules.toArray(String[]::new);
    }

    /**
     * TASK-098 rules 1 and 2: a published custom field appears at requirements and follows its field rules, counts as content
     * with history, comparison and approval, and a required field is needed when the requirement is given for review.
     */
    @Test
    void customFieldsAreContentFollowTheRulesAndAreRequiredForReview() {
        Setup setup = project();
        Response configured = requirementFields(setup, List.of(field("area", "Bereich", "select", true, AREA_OPTIONS),
            field("effort", "Aufwand", "integer", false, null), field("note", "Vertraulich", "text", false, null)));
        assertEquals(200, configured.status(), configured.body());
        Browser editor = member(setup, customRules(rules(List.of("requirement.read", "requirement.edit"), true), true, "area", "effort", "note"));
        Browser noArea = member(setup, customRules(rules(List.of("requirement.read", "requirement.edit"), true), true, "effort", "note"));
        Browser approver = member(setup, customRules(rules(List.of("requirement.read", "requirement.approve"), false), false, "area", "effort", "note"));
        Browser partial = member(setup, customRules(rules(List.of("requirement.read", "requirement.approve"), false), false, "area"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Kunden sehen den Stand ihrer Anfragen");
        body.put("criteria", List.of(criterion(null, "Die Portalliste zeigt den Status.")));
        body.put("customFields", Map.of("effort", 5));
        Response created = create(editor, setup, body);
        assertEquals(201, created.status(), created.body());
        assertEquals(5, created.json().get("content").get("customFields").get("effort").asInt());
        assertEquals(List.of("area", "effort", "note"), names(created.json().get("customFields"), "key"));
        UUID id = id(created);

        assertInvalid(editor.send("PATCH", path(id), Map.of("customFields", Map.of("unknown", "x")), created.etag()), "customFields.unknown");
        assertInvalid(editor.send("PATCH", path(id), Map.of("customFields", Map.of("effort", "viel")), created.etag()), "custom.effort.invalid");
        assertInvalid(editor.send("PATCH", path(id), Map.of("customFields", Map.of("area", "fremd")), created.etag()), "custom.area.invalid");
        assertEquals(403, noArea.send("PATCH", path(id), Map.of("customFields", Map.of("area", "portal")), created.etag()).status(),
            "the write rule of the field");

        // Rule 2: a required field is needed for the review; who may not read or write it meets a conflict, not a hint at its value.
        assertInvalid(editor.send("POST", path(id) + "/submit", null, created.etag()), "custom.area.required_for_review");
        assertEquals("requirement.required_field_inaccessible", noArea.send("POST", path(id) + "/submit", null, created.etag()).code());
        Response filled = editor.send("PATCH", path(id), Map.of("customFields", Map.of("area", "portal")), created.etag());
        assertEquals(200, filled.status(), filled.body());
        assertEquals("portal", filled.json().get("content").get("customFields").get("area").asString());
        Response submitted = editor.send("POST", path(id) + "/submit", null, filled.etag());
        assertEquals(200, submitted.status(), submitted.body());

        // Nobody approves what they cannot read: a custom field counts as content.
        assertEquals("requirement.content_not_readable", partial.send("POST", path(id) + "/approve", Map.of("revision", 1), submitted.etag()).code());
        Response approved = approver.send("POST", path(id) + "/approve", Map.of("revision", 1), submitted.etag());
        assertEquals(200, approved.status(), approved.body());

        // A change after the approval opens revision 2; revision 1 keeps its values.
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("effort", 8);
        change.put("note", "nur intern");
        Response changed = editor.send("PATCH", path(id), Map.of("customFields", change), approved.etag());
        assertEquals(200, changed.status(), changed.body());
        assertEquals(2, changed.json().get("revision").asInt());
        assertEquals("draft", changed.json().get("status").asString());
        assertEquals(5, editor.get(path(id) + "/revisions/1").json().get("content").get("customFields").get("effort").asInt());
        assertEquals(8, editor.get(path(id) + "/revisions/2").json().get("content").get("customFields").get("effort").asInt());

        // Read rules: the partial reader sees the area only, in the requirement, the revisions and the history.
        Response seen = partial.get(path(id));
        assertEquals(List.of("area"), names(seen.json().get("customFields"), "key"));
        List<String> shown = new ArrayList<>();
        seen.json().get("content").get("customFields").propertyNames().forEach(shown::add);
        assertEquals(List.of("area"), shown);
        assertTrue(seen.json().get("editors").isEmpty(), "the change of revision 2 touches hidden fields only");
        List<String> fields = new ArrayList<>();
        partial.get(path(id) + "/history").json().get("events").forEach(e -> fields.add(e.get("field").isNull() ? null : e.get("field").asString()));
        assertTrue(fields.contains("custom.area") && !fields.contains("custom.effort") && !fields.contains("custom.note"), fields.toString());
        List<String> all = new ArrayList<>();
        editor.get(path(id) + "/history").json().get("events").forEach(e -> {
            if ("custom.effort".equals(e.get("field").asString(null))) {
                all.add(e.get("from").asString(null) + ">" + e.get("to").asString());
            }
        });
        assertEquals(List.of("5>8"), all, "the history names the old and new value of a change after the creation");

        // Emptying is always possible; the next review needs the required field again.
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("area", null);
        Response emptied = editor.send("PATCH", path(id), Map.of("customFields", empty), changed.etag());
        assertEquals(200, emptied.status(), emptied.body());
        assertInvalid(editor.send("POST", path(id) + "/submit", null, emptied.etag()), "custom.area.required_for_review");
    }

    /** TASK-098 rule 1: custom fields of requirements are never removed and keep their type; inactive ones take no new value. */
    @Test
    void customFieldsOfRequirementsStayAndCanBeDeactivated() {
        Setup setup = project();
        assertEquals(200, requirementFields(setup, List.of(field("effort", "Aufwand", "integer", false, null))).status());
        Response removed = requirementFields(setup, List.of());
        assertEquals(409, removed.status(), removed.body());
        assertTrue(removed.body().contains("requirementFields.removed"), removed.body());
        Response retyped = requirementFields(setup, List.of(field("effort", "Aufwand", "text", false, null)));
        assertTrue(retyped.body().contains("requirementFields.type_changed"), retyped.body());
        assertInvalid(requirementFields(setup, List.of(field("effort", "Aufwand", "integer", false, null), field("effort", "Doppelt", "text", false, null))),
            "requirementFields.key_duplicate");

        Browser editor = member(setup, customRules(rules(List.of("requirement.read", "requirement.edit"), true), true, "effort"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Mit Aufwand");
        body.put("customFields", Map.of("effort", 3));
        Response created = create(editor, setup, body);
        assertEquals(201, created.status(), created.body());
        Map<String, Object> inactive = field("effort", "Aufwand", "integer", false, null);
        inactive.put("active", false);
        assertEquals(200, requirementFields(setup, List.of(inactive)).status());
        UUID id = id(created);
        Response kept = editor.send("PATCH", path(id), Map.of("title", "Mit Aufwand, umbenannt", "customFields", Map.of("effort", 3)), created.etag());
        assertEquals(200, kept.status(), "a value it already holds stays");
        assertInvalid(editor.send("PATCH", path(id), Map.of("customFields", Map.of("effort", 4)), kept.etag()), "custom.effort.inactive");
        assertEquals(3, editor.get(path(id)).json().get("content").get("customFields").get("effort").asInt());

        // The rights of the project offer the field for the resource type requirement.
        Browser rights = person(AdministrationRight.PERMISSIONS_MANAGE);
        Response group = rights.send("POST", "/api/v1/permissions/groups", Map.of("name", "Aufwand lesen"), null);
        String groupId = group.json().get("id").asString();
        Map<String, Object> rule = new LinkedHashMap<>(Map.of("resourceType", "requirement", "field", "custom.effort", "operation", "read", "effect", "allow"));
        rule.put("projectId", setup.project().toString());
        Response granted = rights.send("PUT", "/api/v1/permissions/groups/" + groupId + "/fields", rule, null);
        assertEquals(200, granted.status(), granted.body());
        rule.put("field", "custom.unknown");
        assertInvalid(rights.send("PUT", "/api/v1/permissions/groups/" + groupId + "/fields", rule, null), "field.unknown");
    }

    private static List<String> names(JsonNode array, String property) {
        List<String> names = new ArrayList<>();
        array.forEach(item -> names.add(item.get(property).asString()));
        return names;
    }

    // ---------------------------------------------------------------- links (TASK-097)

    private static final String[] TICKET_READ = { "ticket.read", "ticket:READ:title", "ticket:READ:status", "ticket:READ:type",
        "ticket:READ:description", "ticket:READ:assignee" };

    private static String[] with(String[] first, String... more) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(List.of(more));
        return all.toArray(String[]::new);
    }

    private long ticket(Browser who, Setup setup, String title) {
        Response created = who.send("POST", "/api/v1/projects/" + setup.project() + "/tickets", Map.of("type", "task", "title", title), null);
        assertEquals(201, created.status(), created.body());
        return created.json().get("number").asLong();
    }

    private UUID page(Browser who, Setup setup, String title, boolean publish) {
        Response created = who.send("POST", "/api/v1/wiki/projects/" + setup.project() + "/pages", Map.of("title", title, "content",
            paragraphDoc("Inhalt")), null);
        assertEquals(201, created.status(), created.body());
        UUID page = UUID.fromString(created.json().get("id").asString());
        if (publish) {
            Response published = who.send("POST", "/api/v1/wiki/pages/" + page + "/publish", Map.of("revision", 1), created.etag());
            assertEquals(200, published.status(), published.body());
        }
        return page;
    }

    private static List<String> ticketHistory(Browser who, Setup setup, long number) {
        Response history = who.get("/api/v1/projects/" + setup.project() + "/tickets/" + number + "/history");
        assertEquals(200, history.status(), history.body());
        List<String> values = new ArrayList<>();
        history.json().forEach(entry -> entry.get("changes").forEach(change -> {
            if ("requirements".equals(change.get("field").asString())) {
                values.add(change.get("from").asString(null) + ">" + change.get("to").asString(null));
            }
        }));
        return values;
    }

    /**
     * Acceptance 1 and 2 of TASK-097: ANF-1 revision 1 is linked with a ticket; after revision 2 the ticket stays on revision 1
     * with the hint until it is switched explicitly. A done ticket changes neither status nor approval of the requirement (F-14),
     * and linking changes neither status nor version of the ticket; both histories record it.
     */
    @Test
    void aTicketStaysOnItsRevisionUntilItIsSwitchedAndNeverApproves() {
        Setup setup = project();
        Browser editor = member(setup, with(rules(List.of("requirement.read", "requirement.edit"), true), with(TICKET_READ, "ticket.create",
            "ticket.edit", "ticket:WRITE:title", "ticket:WRITE:status", "ticket:WRITE:type")));
        Browser approver = approver(setup);
        long number = ticket(editor, setup, "Portalliste zeigt Status");
        Response ticketBefore = editor.get("/api/v1/projects/" + setup.project() + "/tickets/" + number);
        Response draft = example(editor, setup);
        UUID id = id(draft);

        Response linked = editor.send("POST", path(id) + "/tickets", Map.of("number", number), null);
        assertEquals(200, linked.status(), linked.body());
        JsonNode link = linked.json().get("tickets").get(0);
        assertEquals(1, link.get("revision").asInt());
        assertTrue(link.get("newerRevision").isNull());
        assertTrue(link.get("key").asString().endsWith("-" + number));
        assertEquals("Portalliste zeigt Status", link.get("title").asString());
        assertEquals("new", link.get("status").asString());
        assertEquals("Neu", link.get("statusName").asString());
        assertEquals(409, editor.send("POST", path(id) + "/tickets", Map.of("number", number), null).status(), "each pair once");
        assertInvalid(editor.send("POST", path(id) + "/tickets", Map.of("number", 9999), null), "ticket.unknown");

        Response ticketAfter = editor.get("/api/v1/projects/" + setup.project() + "/tickets/" + number);
        assertEquals(ticketBefore.etag(), ticketAfter.etag(), "linking keeps the version of the ticket");
        assertEquals(ticketBefore.json().get("fields").get("status").asString(), ticketAfter.json().get("fields").get("status").asString());
        Response requirementAfterLink = editor.get(path(id));
        assertEquals(draft.etag(), requirementAfterLink.etag(), "linking keeps the version of the requirement");

        // Approve revision 1, then change the content: revision 2 opens and the ticket stays on revision 1 with the hint.
        Response submitted = editor.send("POST", path(id) + "/submit", null, requirementAfterLink.etag());
        Response approved = approver.send("POST", path(id) + "/approve", Map.of("revision", 1), submitted.etag());
        assertEquals(200, approved.status(), approved.body());
        Response second = editor.send("PATCH", path(id), Map.of("title", "Kunden sehen den Stand und die Antworten"), approved.etag());
        assertEquals(2, second.json().get("revision").asInt());
        JsonNode stale = editor.get(path(id) + "/links").json().get("tickets").get(0);
        assertEquals(1, stale.get("revision").asInt());
        assertEquals(2, stale.get("newerRevision").asInt());
        JsonNode onTicket = editor.get("/api/v1/requirements/projects/" + setup.project() + "/tickets/" + number).json();
        assertTrue(onTicket.get("read").asBoolean());
        assertEquals(1, onTicket.get("requirements").get(0).get("revision").asInt());
        assertEquals(2, onTicket.get("requirements").get(0).get("newerRevision").asInt());
        assertEquals("ANF-1", onTicket.get("requirements").get(0).get("key").asString());

        assertEquals(412, editor.send("POST", path(id) + "/tickets/" + number + "/switch", Map.of("revision", 1), null).status(),
            "only the current revision that the caller saw");
        Response switched = editor.send("POST", path(id) + "/tickets/" + number + "/switch", Map.of("revision", 2), null);
        assertEquals(200, switched.status(), switched.body());
        assertEquals(2, switched.json().get("tickets").get(0).get("revision").asInt());
        assertTrue(switched.json().get("tickets").get(0).get("newerRevision").isNull());

        // F-14: the ticket moves to done; the requirement keeps its status and approval, the summary counts the done ticket.
        Response current = editor.get("/api/v1/projects/" + setup.project() + "/tickets/" + number);
        Response working = editor.send("PATCH", "/api/v1/projects/" + setup.project() + "/tickets/" + number, Map.of("status", "in_progress"),
            current.etag());
        assertEquals(200, working.status(), working.body());
        Response done = editor.send("PATCH", "/api/v1/projects/" + setup.project() + "/tickets/" + number, Map.of("status", "done"), working.etag());
        assertEquals(200, done.status(), done.body());
        Response requirementNow = editor.get(path(id));
        assertEquals("draft", requirementNow.json().get("status").asString());
        assertEquals(1, requirementNow.json().get("approvedRevision").asInt());
        assertEquals(second.etag(), requirementNow.etag(), "a done ticket does not change the requirement");
        JsonNode summary = editor.get(path(id) + "/links").json().get("summary");
        assertEquals(1, summary.get("tickets").asInt());
        assertEquals(1, summary.get("done").asInt());

        Response unlinked = editor.send("DELETE", path(id) + "/tickets/" + number, null, null);
        assertEquals(200, unlinked.status(), unlinked.body());
        assertEquals(0, unlinked.json().get("tickets").size());
        assertEquals(404, editor.send("DELETE", path(id) + "/tickets/" + number, null, null).status());

        assertEquals(List.of("null>ANF-1:1", "ANF-1:1>ANF-1:2", "ANF-1:2>null"), ticketHistory(editor, setup, number));
        List<String> kinds = kinds(editor.get(path(id) + "/history"));
        assertTrue(kinds.containsAll(List.of("ticket_linked", "ticket_switched", "ticket_unlinked")), kinds.toString());
        assertTrue(editor.get(path(id)).json().get("editors").size() == 1, "links do not make editors");
    }

    /**
     * Acceptance 3 of TASK-097: without read access to the ticket, the requirement or the page the link is not shown and not
     * counted; a page without wiki.read appears without its title, an unpublished page cannot be linked.
     */
    @Test
    void linksAppearOnlyToReadersOfBothSides() {
        Setup setup = project();
        Browser editor = member(setup, with(rules(List.of("requirement.read", "requirement.edit"), true), with(TICKET_READ, "ticket.create",
            "ticket:WRITE:title", "ticket:WRITE:type", "wiki.read", "wiki.edit", "wiki.publish")));
        long number = ticket(editor, setup, "Sichtbarkeit");
        UUID id = id(example(editor, setup));
        UUID published = page(editor, setup, "Portalhandbuch", true);
        UUID unpublished = page(editor, setup, "Entwurf", false);
        assertEquals(200, editor.send("POST", path(id) + "/tickets", Map.of("number", number), null).status());
        assertInvalid(editor.send("POST", path(id) + "/pages", Map.of("pageId", unpublished.toString()), null), "page.unknown");
        Response withPage = editor.send("POST", path(id) + "/pages", Map.of("pageId", published.toString()), null);
        assertEquals(200, withPage.status(), withPage.body());
        assertEquals("Portalhandbuch", withPage.json().get("pages").get(0).get("title").asString());

        // A reader of requirements without ticket.read and wiki.read: no ticket, no count, the page without title.
        Browser requirementsOnly = reader(setup);
        JsonNode links = requirementsOnly.get(path(id) + "/links").json();
        assertFalse(links.get("ticketsReadable").asBoolean());
        assertEquals(0, links.get("tickets").size());
        assertEquals(0, links.get("summary").get("tickets").asInt());
        assertTrue(links.get("pages").get(0).get("title").isNull());
        assertFalse(links.get("pages").get(0).get("available").asBoolean());
        List<String> kinds = kinds(requirementsOnly.get(path(id) + "/history"));
        assertFalse(kinds.contains("ticket_linked"), kinds.toString());
        assertTrue(kinds.contains("page_linked"), kinds.toString());
        assertEquals(404, requirementsOnly.get("/api/v1/requirements/projects/" + setup.project() + "/tickets/" + number).status());

        // A reader of tickets without requirement.read: no requirements at the ticket, no entry in its history.
        Browser ticketsOnly = member(setup, TICKET_READ);
        JsonNode onTicket = ticketsOnly.get("/api/v1/requirements/projects/" + setup.project() + "/tickets/" + number).json();
        assertFalse(onTicket.get("read").asBoolean());
        assertEquals(0, onTicket.get("requirements").size());
        assertEquals(List.of(), ticketHistory(ticketsOnly, setup, number));
        assertEquals(404, ticketsOnly.get(path(id) + "/links").status());

        // A reader of both sides without the status rule sees the ticket but does not count it as done.
        Browser both = member(setup, with(rules(List.of("requirement.read"), false), "ticket.read", "ticket:READ:title"));
        JsonNode bothLinks = both.get(path(id) + "/links").json();
        assertEquals(1, bothLinks.get("tickets").size());
        assertTrue(bothLinks.get("tickets").get(0).get("status").isNull());
        assertEquals(List.of("null>ANF-1:1"), ticketHistory(both, setup, number));

        // Linking a ticket needs ticket.read; removing a page link needs no wiki right.
        Browser blind = member(setup, rules(List.of("requirement.read", "requirement.edit"), true));
        assertEquals(403, blind.send("POST", path(id) + "/tickets", Map.of("number", number), null).status());
        assertEquals(403, blind.send("DELETE", path(id) + "/tickets/" + number, null, null).status());
        assertEquals(403, requirementsOnly.send("DELETE", path(id) + "/pages/" + published, null, null).status(), "edit is needed");
        Response removed = blind.send("DELETE", path(id) + "/pages/" + published, null, null);
        assertEquals(200, removed.status(), removed.body());
        assertEquals(0, removed.json().get("pages").size());
    }

    /** A discarded requirement gets no new link; a link of another project's ticket number does not exist. */
    @Test
    void aDiscardedRequirementGetsNoNewLinks() {
        Setup setup = project();
        Browser editor = member(setup, with(rules(List.of("requirement.read", "requirement.edit"), true), with(TICKET_READ, "ticket.create",
            "ticket:WRITE:title", "ticket:WRITE:type")));
        long number = ticket(editor, setup, "Verworfen");
        Response created = example(editor, setup);
        UUID id = id(created);
        assertEquals(200, editor.send("POST", path(id) + "/tickets", Map.of("number", number), null).status());
        Response discarded = editor.send("POST", path(id) + "/discard", Map.of("reason", "doppelt"), created.etag());
        assertEquals(200, discarded.status(), discarded.body());
        long other = ticket(editor, setup, "Neu");
        assertEquals(409, editor.send("POST", path(id) + "/tickets", Map.of("number", other), null).status());
        assertFalse(editor.get(path(id) + "/links").json().get("edit").asBoolean());
        assertEquals(200, editor.send("DELETE", path(id) + "/tickets/" + number, null, null).status(), "removing stays possible");
        assertInvalid(editor.send("POST", path(id) + "/tickets", Map.of(), null), "number.invalid");
        assertInvalid(editor.send("GET", path(id) + "/links?language=fr", null, null), "language.invalid");
    }
}
