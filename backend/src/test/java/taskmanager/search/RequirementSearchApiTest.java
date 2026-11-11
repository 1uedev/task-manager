package taskmanager.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import taskmanager.search.internal.SearchProjection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-103: requirements in the search through the real HTTP interface with the real requirements, permissions and projects
 * modules and a real PostgreSQL: the current revision and the newest approved one as stages of their own, words only in
 * readable fields (T09), filters on unreadable fields that match nothing, the key with and without the project key, discarded
 * requirements only with a status filter, custom fields as words and filters, the excerpt with the number of its criterion and
 * the same answers after a rebuild of the projection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequirementSearchApiTest {
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
    @Autowired SearchProjection projection;

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
    }

    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", "reqs-" + COUNTER.incrementAndGet(), null);
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

    private record Setup(UUID project, Browser admin, String key) {
    }

    private Setup project() {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.PROJECTS_MANAGE);
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "RS" + n, "name", "Anforderungen " + n), null);
        assertEquals(201, created.status(), created.body());
        return new Setup(UUID.fromString(created.json().get("id").asString()), admin, "RS" + n);
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

    // ---------------------------------------------------------------- tests

    private static String search(Setup setup, String query) {
        return "/api/v1/search/requirements?project=" + setup.project() + "&" + query;
    }

    private static String q(String text) {
        return "q=" + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<String> keys(Response page) {
        assertEquals(200, page.status(), page.body());
        List<String> keys = new ArrayList<>();
        page.json().get("items").forEach(item -> keys.add(item.get("key").asString()));
        return keys;
    }

    private static String key(Response requirement) {
        return requirement.json().get("key").asString();
    }

    /**
     * Rule 2 and its criterion: a word only the draft of revision 2 holds is found in the current revision and not with
     * "zuletzt freigegeben", which finds the approved revision 1 with its own words; the stages never mix.
     */
    @Test
    void theCurrentRevisionAndTheApprovedOneAreSearchedApart() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser approver = approver(setup);
        Response done = approved(setup, editor, approver);
        Response changed = editor.send("PATCH", path(id(done)), Map.of("title", "Kunden sehen Zwischenstände"), done.etag());
        assertEquals(200, changed.status(), changed.body());
        String found = key(done);

        Response current = editor.get(search(setup, q("Zwischenst")));
        assertEquals(List.of(found), keys(current));
        JsonNode hit = current.json().get("items").get(0);
        assertEquals("current", hit.get("stage").asString());
        assertEquals(2, hit.get("revision").asInt());
        assertEquals(1, hit.get("approvedRevision").asInt());
        assertFalse(hit.get("approved").asBoolean());
        assertEquals("draft", hit.get("status").asString());
        assertEquals("Kunden sehen Zwischenstände", hit.get("title").asString());
        assertEquals(1, hit.get("titleMarks").size());

        assertEquals(List.of(), keys(editor.get(search(setup, q("Zwischenst") + "&stage=approved"))), "the draft is not approved");
        Response approvedStage = editor.get(search(setup, q("Supportanfragen") + "&stage=approved"));
        assertEquals(List.of(found), keys(approvedStage));
        JsonNode old = approvedStage.json().get("items").get(0);
        assertEquals(1, old.get("revision").asInt());
        assertTrue(old.get("approved").asBoolean());
        assertEquals("Kunden sehen den Stand ihrer freigegebenen Supportanfragen", old.get("title").asString());
        assertEquals(List.of(), keys(editor.get(search(setup, q("Supportanfragen")))), "the old title is not in the current revision");

        // A requirement that was never approved has no approved stage.
        Response fresh = example(editor, setup);
        assertEquals(List.of(key(fresh)), keys(editor.get(search(setup, q("Supportanfragen")))));
        assertEquals(List.of(found), keys(editor.get(search(setup, q("Portalliste") + "&stage=approved"))));
    }

    /** Rule 5: a word in a criterion gives the excerpt of that criterion with its fixed number; a description word its own. */
    @Test
    void theExcerptNamesTheCriterionItCameFrom() {
        Setup setup = project();
        Browser editor = editor(setup);
        Response created = example(editor, setup);
        JsonNode snippet = editor.get(search(setup, q("Organisationen"))).json().get("items").get(0).get("snippet");
        assertEquals("criteria", snippet.get("field").asString());
        assertEquals(2, snippet.get("criterion").asInt());
        assertEquals("Anfragen anderer Organisationen erscheinen nicht.", snippet.get("excerpt").get("text").asString());
        assertEquals(1, snippet.get("excerpt").get("marks").size());

        JsonNode described = editor.get(search(setup, q("Bearbeitungsstand"))).json().get("items").get(0).get("snippet");
        assertEquals("description", described.get("field").asString());
        assertTrue(described.get("criterion").isNull());
        assertEquals("Im Portal sehen berechtigte Kunden den Bearbeitungsstand.", described.get("excerpt").get("text").asString());
        assertEquals(List.of(key(created)), keys(editor.get(search(setup, q("portal kunden status")))), "words spread over fields");
    }

    /**
     * Rule 3 and T09: a word only in a field the caller may not read neither finds, ranks nor shows; a filter on such a field
     * matches nothing; the requirement stays findable by readable words and by its key, without the hidden values.
     */
    @Test
    void unreadableFieldsNeitherFindNorShow() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser partial = reader(setup, "criteria", "owner", "priority");
        Response created = example(editor, setup);
        Response owned = editor.send("PATCH", path(id(created)), Map.of("ownerId", editor.id.toString()), created.etag());
        assertEquals(200, owned.status(), owned.body());

        assertEquals(List.of(key(created)), keys(editor.get(search(setup, q("Organisationen")))));
        Response hidden = partial.get(search(setup, q("Organisationen")));
        assertEquals(List.of(), keys(hidden));
        assertEquals(0, hidden.json().get("total").asLong());
        assertEquals(List.of(), keys(partial.get(search(setup, q("Portal Organisationen")))), "every word in a readable field");
        assertEquals(List.of(), keys(partial.get(search(setup, "priority=high"))));
        assertEquals(List.of(), keys(partial.get(search(setup, "owner=" + editor.id))));
        assertEquals(List.of(key(created)), keys(editor.get(search(setup, "priority=high&owner=" + editor.id))));

        Response seen = partial.get(search(setup, q("Portal")));
        assertEquals(List.of(key(created)), keys(seen));
        JsonNode hit = seen.json().get("items").get(0);
        assertTrue(hit.get("priority").isNull());
        assertTrue(hit.get("owner").isNull());
        assertEquals("description", hit.get("snippet").get("field").asString());

        // Without the description only the title is left: no excerpt from the hidden criteria.
        Browser titleOnly = reader(setup, "criteria", "description");
        JsonNode titled = titleOnly.get(search(setup, q("Supportanfragen"))).json().get("items").get(0);
        assertTrue(titled.get("snippet").isNull());
        Browser blind = reader(setup, "title", "description", "criteria");
        JsonNode keyed = blind.get(search(setup, q(key(created)))).json().get("items").get(0);
        assertTrue(keyed.get("title").isNull(), "found by its key, the title stays hidden");
        assertEquals(List.of(), keys(blind.get(search(setup, q("Supportanfragen")))));
    }

    /** Rule 1 and 3: the key finds a requirement with or without the project key, for readers of the project only. */
    @Test
    void theKeyFindsTheRequirementForReadersOnly() {
        Setup setup = project();
        Browser editor = editor(setup);
        Response first = example(editor, setup);
        example(editor, setup);
        String projectKey = setup.key();
        String key = key(first);
        String all = "/api/v1/search/requirements?" + q(projectKey + " " + key);
        assertEquals(List.of(key), keys(editor.get(all)));
        assertEquals(List.of(key), keys(editor.get(all.replace(projectKey, projectKey.toLowerCase(java.util.Locale.ROOT)))));
        assertEquals(List.of(key), keys(editor.get(search(setup, q(key)))));
        assertEquals(List.of(), keys(editor.get("/api/v1/search/requirements?" + q("ZZ " + key))), "another project key");

        Browser outsider = member(setup, "requirement.edit");
        assertEquals(List.of(), keys(outsider.get("/api/v1/search/requirements?" + q(projectKey + " " + key))), "edit alone does not read");
        assertEquals(400, outsider.get(search(setup, q(key))).status(), "the project is unknown to them");
        Browser stranger = person();
        assertEquals(List.of(), keys(stranger.get("/api/v1/search/requirements?" + q(projectKey + " " + key))));
        assertEquals(400, stranger.get(search(setup, q(key))).status());
    }

    /** Rule 4: status, priority, owner and "once approved"; discarded requirements only with a status filter. */
    @Test
    void filtersAndDiscardedRequirements() {
        Setup setup = project();
        Browser editor = editor(setup);
        Browser approver = approver(setup);
        Response done = approved(setup, editor, approver);
        Response draft = example(editor, setup);
        Response gone = example(editor, setup);
        assertEquals(200, editor.send("POST", path(id(gone)) + "/discard", null, gone.etag()).status());

        assertEquals(List.of(key(draft), key(done)), keys(editor.get(search(setup, q("Supportanfragen")))));
        assertEquals(2, editor.get(search(setup, "")).json().get("total").asLong(), "no text lists every requirement but discarded ones");
        assertEquals(List.of(key(gone)), keys(editor.get(search(setup, q("Supportanfragen") + "&status=discarded"))));
        assertEquals(List.of(key(gone), key(draft)), keys(editor.get(search(setup, "status=discarded&status=draft"))));
        assertEquals(List.of(key(done)), keys(editor.get(search(setup, "approved=true"))));
        assertEquals(List.of(key(draft)), keys(editor.get(search(setup, "approved=false"))));
        assertEquals(List.of(key(done)), keys(editor.get(search(setup, "status=approved"))));
        assertEquals(List.of(), keys(editor.get(search(setup, "priority=low"))));

        assertInvalid(editor.get(search(setup, "status=done")), "status.invalid");
        assertInvalid(editor.get(search(setup, "priority=urgent")), "priority.invalid");
        assertInvalid(editor.get(search(setup, "stage=draft")), "stage.invalid");
        assertInvalid(editor.get(search(setup, "approved=yes")), "approved.invalid");
        assertInvalid(editor.get(search(setup, "owner=nobody")), "owner.invalid");
        assertInvalid(editor.get(search(setup, "limit=0")), "paging.invalid");

        // Pages: one statement gives total and items; past the last page the total stays.
        Response second = editor.get(search(setup, "limit=1&offset=1"));
        assertEquals(2, second.json().get("total").asLong());
        assertEquals(List.of(key(done)), keys(second));
        Response past = editor.get(search(setup, "limit=1&offset=5"));
        assertEquals(2, past.json().get("total").asLong());
        assertEquals(List.of(), keys(past));
    }

    /** Rule 1: text fields are searched like tickets, the other kinds filter; both follow the field rules. A rebuild agrees. */
    @Test
    void customFieldsAreWordsAndFiltersAfterTheirRules() {
        Setup setup = project();
        Response configured = requirementFields(setup, List.of(field("area", "Bereich", "select", false, AREA_OPTIONS),
            field("effort", "Aufwand", "integer", false, null), field("note", "Vertraulich", "text", false, null)));
        assertEquals(200, configured.status(), configured.body());
        Browser editor = member(setup, customRules(rules(List.of("requirement.read", "requirement.edit"), true), true, "area", "effort", "note"));
        Browser noNote = member(setup, customRules(rules(List.of("requirement.read"), false), false, "area", "effort"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Mit eigenen Feldern");
        body.put("customFields", Map.of("area", "portal", "effort", 5, "note", "Geheimprojekt Nordlicht"));
        Response created = create(editor, setup, body);
        assertEquals(201, created.status(), created.body());
        String key = key(created);

        Response noted = editor.get(search(setup, q("Nordlicht")));
        assertEquals(List.of(key), keys(noted));
        JsonNode snippet = noted.json().get("items").get(0).get("snippet");
        assertEquals("custom.note", snippet.get("field").asString());
        assertEquals("Geheimprojekt Nordlicht", snippet.get("excerpt").get("text").asString());
        assertEquals("portal", noted.json().get("items").get(0).get("customFields").get("area").asString());
        assertEquals(List.of(), keys(noNote.get(search(setup, q("Nordlicht")))), "the text of a hidden field");
        assertFalse(noNote.get(search(setup, "custom=area=portal")).json().get("items").get(0).get("customFields").has("note"));
        assertEquals(List.of(key), keys(noNote.get(search(setup, "custom=area=portal&custom=effort=5"))));
        assertEquals(List.of(), keys(noNote.get(search(setup, "custom=area=intern"))));
        assertEquals(List.of(), keys(noNote.get(search(setup, "custom=note=Geheimprojekt%20Nordlicht"))), "a filter on a hidden field");
        assertEquals(List.of(key), keys(editor.get(search(setup, "custom=note=Geheimprojekt%20Nordlicht"))));
        assertEquals(List.of(), keys(editor.get(search(setup, q("portal")))), "a choice is no word");

        projection.rebuild(companies.find().orElseThrow());
        assertEquals(List.of(key), keys(editor.get(search(setup, q("Nordlicht")))));
        assertEquals(List.of(key), keys(noNote.get(search(setup, "custom=area=portal&custom=effort=5"))));
    }
}
