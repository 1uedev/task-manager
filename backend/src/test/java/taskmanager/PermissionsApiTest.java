package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-030: the group administration over the real HTTP interface, the real permissions, identity, projects and tickets
 * modules and a real PostgreSQL. A right granted here takes effect on the business endpoints, and its withdrawal too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PermissionsApiTest {
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
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, String body, Map<String, List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }

        JsonNode json() {
            return JSON.readTree(body);
        }

        String code() {
            return json().get("code").asString();
        }

        List<String> violations() {
            return StreamSupport.stream(json().get("violations").spliterator(), false).map(JsonNode::asString).toList();
        }
    }

    final class Browser {
        private final HttpClient http = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();

        Response send(String method, String path, Object body, String ifMatch) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
                if (body != null) {
                    request.header("Content-Type", "application/json");
                    request.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
                } else {
                    request.method(method, HttpRequest.BodyPublishers.noBody());
                }
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
                return new Response(response.statusCode(), response.body(), response.headers().map());
            } catch (java.io.IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        Response get(String path) {
            return send("GET", path, null, null);
        }

        Response put(String path, Object body) {
            return send("PUT", path, body, null);
        }

        Response post(String path, Object body) {
            return send("POST", path, body, null);
        }

        Response delete(String path) {
            return send("DELETE", path, null, null);
        }
    }

    private record Person(Browser browser, UUID id) {
    }

    private Browser anonymous() {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        return browser;
    }

    private Person person(AdministrationRight... rights) {
        return person(false, rights);
    }

    /** With {@code administration}, a member of the company administration: permissions.manage and every product right. */
    private Person person(boolean administration, AdministrationRight... rights) {
        var issued = accounts.create("test", "perm-" + COUNTER.incrementAndGet(), "Person " + COUNTER.get());
        if (administration) {
            AdministrationFixture.grantAdministration(jdbc, companies, transactions, issued.account().id());
        } else if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        Browser browser = anonymous();
        Response login = browser.post("/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()));
        assertEquals(200, login.status(), login.body());
        Response changed = browser.post("/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG));
        assertEquals(200, changed.status(), changed.body());
        return new Person(browser, issued.account().id());
    }

    private record Project(UUID id, Person manager) {
    }

    private Project project(Person... members) {
        Person manager = person(AdministrationRight.PROJECTS_MANAGE);
        Response created = manager.browser().post("/api/v1/projects", Map.of("key", "P" + COUNTER.incrementAndGet(), "name", "Rechte"));
        assertEquals(201, created.status(), created.body());
        UUID id = UUID.fromString(created.json().get("id").asString());
        String version = created.header("ETag");
        for (Person member : members) {
            Response added = manager.browser().send("PUT", "/api/v1/projects/" + id + "/members/" + member.id(), null, version);
            assertEquals(200, added.status(), added.body());
            version = added.header("ETag");
        }
        return new Project(id, manager);
    }

    private static UUID group(Person admin, String name) {
        Response created = admin.browser().post("/api/v1/permissions/groups", Map.of("name", name));
        assertEquals(201, created.status(), created.body());
        return UUID.fromString(created.json().get("id").asString());
    }

    private static Response action(Person admin, UUID group, String action, UUID project, boolean enabled) {
        Map<String, Object> body = new HashMap<>(Map.of("action", action, "enabled", enabled));
        body.put("projectId", project);
        return admin.browser().put("/api/v1/permissions/groups/" + group + "/actions", body);
    }

    private static Response field(Person admin, UUID group, String field, UUID project, String operation, String effect) {
        Map<String, Object> body = new HashMap<>(Map.of("resourceType", "ticket", "field", field, "operation", operation, "effect", effect));
        body.put("projectId", project);
        return admin.browser().put("/api/v1/permissions/groups/" + group + "/fields", body);
    }

    private static void ok(Response response) {
        assertEquals(200, response.status(), response.body());
    }

    private static void assertForbidden(Response response) {
        assertEquals(403, response.status(), response.body());
        assertEquals("auth.forbidden", response.code(), response.body());
    }

    private static void assertInvalid(Response response, String... violations) {
        assertEquals(400, response.status(), response.body());
        assertEquals("request.invalid", response.code(), response.body());
        assertEquals(List.of(violations), response.violations(), response.body());
    }

    private static List<String> rights(Person person) {
        return StreamSupport.stream(person.browser().get("/api/v1/session").json().get("rights").spliterator(), false)
            .map(JsonNode::asString).toList();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void aRightsLockNotGrantedInTimeAnswersRetryableBusy() throws Exception {
        Person admin = person(true);
        UUID company = companies.find().orElseThrow();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            // Holds the rights revision like a long running change; the driver reports the timeout as SQLSTATE 55P03.
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForList("select revision from permissions_revision where company_id = ? for update", Long.class, company);
                locked.countDown();
                try {
                    assertTrue(release.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            try {
                assertTrue(locked.await(15, TimeUnit.SECONDS));
                String name = "Wartet " + COUNTER.incrementAndGet();
                Response busy = admin.browser().post("/api/v1/permissions/groups", Map.of("name", name));
                assertEquals(503, busy.status(), busy.body());
                assertEquals("permissions.busy", busy.code(), busy.body());
                assertEquals("1", busy.header("Retry-After"));
                assertEquals(0, jdbc.queryForObject("select count(*) from permissions_group where name = ?", Integer.class, name),
                    "nothing was created");
            } finally {
                release.countDown();
                holder.get(15, TimeUnit.SECONDS);
            }
        }
        group(admin, "Nach dem Warten " + COUNTER.incrementAndGet());
    }

    @Test
    void onlyTheCompanyAdministrationReachesTheGroupAdministration() {
        Person admin = person(true);
        Person accountsOnly = person(AdministrationRight.ACCOUNTS_MANAGE);
        UUID group = group(admin, "Nur Verwaltung " + COUNTER.incrementAndGet());
        assertTrue(rights(admin).contains("permissions.manage"), "the session names the right, so an interface can offer the page");
        assertFalse(rights(accountsOnly).contains("permissions.manage"));

        for (Response refused : List.of(accountsOnly.browser().get("/api/v1/permissions/catalog"),
                accountsOnly.browser().get("/api/v1/permissions/groups"),
                accountsOnly.browser().get("/api/v1/permissions/groups/" + group),
                accountsOnly.browser().post("/api/v1/permissions/groups", Map.of("name", "Fremd")),
                accountsOnly.browser().put("/api/v1/permissions/groups/" + group + "/members/" + accountsOnly.id(), null),
                action(accountsOnly, group, "ticket.read", null, true),
                field(accountsOnly, group, "title", null, "read", "allow"),
                accountsOnly.browser().get("/api/v1/permissions/audit"))) {
            assertForbidden(refused);
        }
        assertEquals(401, anonymous().get("/api/v1/permissions/groups").status());
        ok(admin.browser().get("/api/v1/permissions/catalog"));
        Response groups = admin.browser().get("/api/v1/permissions/groups");
        ok(groups);
        assertTrue(groups.body().contains(group.toString()));
    }

    @Test
    void aGrantedProjectActionAndFieldRulesTakeEffectOnTheTicketsAndTheirWithdrawalToo() {
        Person admin = person(true);
        Person worker = person();
        Project project = project(worker);
        Response created = project.manager().browser().post("/api/v1/projects/" + project.id() + "/tickets", Map.of("type", "task", "title", "x"));
        assertEquals(404, created.status(), "the manager of projects is no member and has no ticket right: " + created.body());
        String tickets = "/api/v1/projects/" + project.id() + "/tickets";
        assertNotEquals(200, worker.browser().get(tickets).status(), "no right yet");

        UUID group = group(admin, "Entwicklung " + COUNTER.incrementAndGet());
        ok(admin.browser().put("/api/v1/permissions/groups/" + group + "/members/" + worker.id(), null));
        for (String key : List.of("ticket.read", "ticket.create", "ticket.edit")) {
            ok(action(admin, group, key, project.id(), true));
        }
        for (String key : List.of("title", "type", "status", "description", "assignee")) {
            ok(field(admin, group, key, null, "read", "allow"));
            ok(field(admin, group, key, null, "write", "allow"));
        }
        Response detail = admin.browser().get("/api/v1/permissions/groups/" + group);
        ok(detail);
        assertEquals(List.of(worker.id().toString()), StreamSupport.stream(detail.json().get("members").spliterator(), false).map(JsonNode::asString).toList());
        assertEquals(3, detail.json().get("actions").size());
        assertEquals(10, detail.json().get("fields").size());
        JsonNode rule = detail.json().get("fields").get(0);
        assertEquals("allow", rule.get("effect").asString());
        assertTrue(List.of("read", "write").contains(rule.get("operation").asString()));

        Response ticket = worker.browser().post(tickets, Map.of("type", "task", "title", "Erstes", "description", "geheim"));
        assertEquals(201, ticket.status(), ticket.body());
        String path = tickets + "/" + ticket.json().get("number").asLong();
        assertTrue(worker.browser().get(path).json().get("fields").has("description"));

        // A deny wins over the allow of the same group, for this project only.
        ok(field(admin, group, "description", project.id(), "read", "deny"));
        Response hidden = worker.browser().get(path);
        ok(hidden);
        assertFalse(hidden.json().get("fields").has("description"), hidden.body());
        ok(field(admin, group, "description", project.id(), "read", "remove"));
        assertTrue(worker.browser().get(path).json().get("fields").has("description"));

        ok(action(admin, group, "ticket.read", project.id(), false));
        assertNotEquals(200, worker.browser().get(path).status(), "withdrawn at once");
        ok(action(admin, group, "ticket.read", null, true));
        ok(worker.browser().get(path));
        ok(admin.browser().delete("/api/v1/permissions/groups/" + group + "/members/" + worker.id()));
        assertNotEquals(200, worker.browser().get(path).status(), "without the group no right remains");
    }

    @Test
    void keysScopesAndNamesAreCheckedAgainstTheCatalog() {
        Person admin = person(true);
        UUID group = group(admin, "Prüfung " + COUNTER.incrementAndGet());
        Project project = project();
        assertInvalid(admin.browser().post("/api/v1/permissions/groups", Map.of("name", "  ")), "name.invalid");
        assertInvalid(admin.browser().post("/api/v1/permissions/groups", Map.of("name", "x".repeat(81))), "name.invalid");
        assertInvalid(action(admin, group, "ticket.delete", null, true), "action.unknown");
        assertInvalid(action(admin, group, "accounts.manage", project.id(), true), "action.company_only");
        assertInvalid(admin.browser().put("/api/v1/permissions/groups/" + group + "/actions", Map.of("action", "ticket.read")), "enabled.required");
        assertInvalid(field(admin, group, "secret", null, "read", "allow"), "field.unknown");
        assertInvalid(field(admin, group, "title", null, "READ", "maybe"), "operation.invalid", "effect.invalid");
        assertInvalid(admin.browser().put("/api/v1/permissions/groups/" + group + "/fields",
            Map.of("resourceType", "wiki", "field", "title", "operation", "read", "effect", "allow")), "resourceType.unknown");
        assertInvalid(admin.browser().get("/api/v1/permissions/audit?limit=101"), "limit.out_of_range");

        Response unknownGroup = admin.browser().get("/api/v1/permissions/groups/" + UUID.randomUUID());
        assertEquals(404, unknownGroup.status());
        assertEquals("permissions.group_not_found", unknownGroup.code());
        Response unknownProject = action(admin, group, "ticket.read", UUID.randomUUID(), true);
        assertEquals(404, unknownProject.status());
        assertEquals("project.not_found", unknownProject.code());
        assertForbidden(admin.browser().put("/api/v1/permissions/groups/" + group + "/members/" + UUID.randomUUID(), null));

        Response repeated = action(admin, group, "tag.create", project.id(), true);
        ok(repeated);
        assertEquals(repeated.json(), action(admin, group, "tag.create", project.id(), true).json(), "setting twice is the same state");
    }

    @Test
    void theLastActiveAdministrationCannotBeRemovedThroughTheInterface() {
        // A database of its own would be cleaner; here the test adds its own administration and removes every other one.
        Person admin = person(true);
        UUID only = group(admin, "Letzte " + COUNTER.incrementAndGet());
        ok(admin.browser().put("/api/v1/permissions/groups/" + only + "/members/" + admin.id(), null));
        ok(action(admin, only, "permissions.manage", null, true));
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("""
                delete from permissions_membership m using permissions_action a
                where a.company_id = m.company_id and a.group_id = m.group_id and a.project_id is null
                  and a.action_key = 'permissions.manage' and a.group_id <> ?
                """, only);
            jdbc.update("update permissions_revision set revision = revision + 1");
        });

        Response removal = admin.browser().delete("/api/v1/permissions/groups/" + only + "/members/" + admin.id());
        assertEquals(409, removal.status(), removal.body());
        assertEquals("permissions.last_administrator", removal.code());
        Response withdrawal = action(admin, only, "permissions.manage", null, false);
        assertEquals(409, withdrawal.status(), withdrawal.body());

        Person second = person();
        ok(admin.browser().put("/api/v1/permissions/groups/" + only + "/members/" + second.id(), null));
        Response left = admin.browser().delete("/api/v1/permissions/groups/" + only + "/members/" + admin.id());
        assertEquals(204, left.status(), "the change commits, but the former administrator may no longer read the group: " + left.body());
        assertForbidden(admin.browser().get("/api/v1/permissions/groups"));
        ok(second.browser().get("/api/v1/permissions/groups"));
    }

    @Test
    void theAuditNeedsItsOwnRightAndNamesEveryChange() {
        Person admin = person(true);
        assertForbidden(admin.browser().get("/api/v1/permissions/audit"));
        UUID group = group(admin, "Prüfer " + COUNTER.incrementAndGet());
        ok(admin.browser().put("/api/v1/permissions/groups/" + group + "/members/" + admin.id(), null));
        ok(action(admin, group, "permissions.audit.read", null, true));
        Response audit = admin.browser().get("/api/v1/permissions/audit?limit=3");
        ok(audit);
        assertEquals(3, audit.json().size());
        JsonNode newest = audit.json().get(0);
        assertEquals("action.changed", newest.get("operation").asString());
        assertEquals("permissions.audit.read", newest.get("action").asString());
        assertEquals("allow", newest.get("effect").asString());
        assertEquals(admin.id().toString(), newest.get("actorId").asString());
    }

    @Test
    void everyCompleteLoginReadsTheNamesOfTheAccountsAndNothingElse() {
        Person reader = person();
        Person other = person();
        Response directory = reader.browser().get("/api/v1/directory/accounts");
        ok(directory);
        JsonNode entry = StreamSupport.stream(directory.json().spliterator(), false)
            .filter(node -> node.get("id").asString().equals(other.id().toString())).findFirst().orElseThrow();
        assertEquals(List.of("active", "displayName", "id", "username"),
            StreamSupport.stream(((Iterable<String>) () -> entry.propertyNames().iterator()).spliterator(), false).sorted().toList());
        assertTrue(entry.get("active").asBoolean());
        assertEquals(401, anonymous().get("/api/v1/directory/accounts").status());
    }
}
