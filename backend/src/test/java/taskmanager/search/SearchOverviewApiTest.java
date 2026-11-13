package taskmanager.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-104: the overview "Alle" and the quick search through the real HTTP interface with the real tickets, projects, wiki,
 * requirements and permissions modules and a real PostgreSQL: one word found in all four areas (T20), areas without a right
 * as {@code null}, five hits with the total, the object a key names exactly opened directly only when it is readable, the
 * exact project key first in the project search and the 400 answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchOverviewApiTest {
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
        var issued = accounts.create("test", "overview-" + COUNTER.incrementAndGet(), null);
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

    private Setup project(String name) {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.PROJECTS_MANAGE);
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "OV" + n, "name", name), null);
        assertEquals(201, created.status(), created.body());
        return new Setup(UUID.fromString(created.json().get("id").asString()), admin, "OV" + n);
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

    private static final List<String> TICKET_FIELDS = List.of("title", "description", "type", "status", "assignee");
    private static final List<String> REQUIREMENT_FIELDS = List.of("title", "description", "criteria", "priority", "owner");

    private static List<String> ticketRules() {
        List<String> rules = new ArrayList<>(List.of("project.read", "ticket.read", "ticket.create"));
        for (String field : TICKET_FIELDS) {
            rules.add("ticket:READ:" + field);
            rules.add("ticket:WRITE:" + field);
        }
        return rules;
    }

    private static List<String> requirementRules() {
        List<String> rules = new ArrayList<>(List.of("requirement.read", "requirement.edit"));
        for (String field : REQUIREMENT_FIELDS) {
            rules.add("READ:" + field);
            rules.add("WRITE:" + field);
        }
        return rules;
    }

    private static final List<String> WIKI_RULES = List.of("wiki.read", "wiki.edit", "wiki.publish");

    @SafeVarargs
    private static String[] rules(List<String>... parts) {
        List<String> all = new ArrayList<>();
        for (List<String> part : parts) {
            all.addAll(part);
        }
        return all.toArray(String[]::new);
    }

    /** A word of letters only that no other test uses. */
    private static String word(String stem) {
        int n = COUNTER.incrementAndGet();
        StringBuilder letters = new StringBuilder();
        for (int rest = n; rest > 0; rest /= 26) {
            letters.append((char) ('a' + rest % 26));
        }
        return stem + "q" + letters;
    }

    private static Map<String, Object> paragraphDoc(String value) {
        return Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", value)))));
    }

    private static long ticket(Browser who, Setup setup, String title) {
        Response created = who.send("POST", "/api/v1/projects/" + setup.project() + "/tickets", Map.of("type", "task", "title", title), null);
        assertEquals(201, created.status(), created.body());
        return created.json().get("number").asLong();
    }

    private static UUID requirement(Browser who, Setup setup, String title) {
        Response created = who.send("POST", "/api/v1/requirements/projects/" + setup.project() + "/requirements",
            Map.of("title", title, "priority", "normal", "criteria", List.of()), null);
        assertEquals(201, created.status(), created.body());
        return UUID.fromString(created.json().get("id").asString());
    }

    /** A wiki page in German, published when asked. */
    private static UUID page(Browser who, Setup setup, String title, boolean publish) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("parentId", null);
        body.put("content", paragraphDoc("Inhalt"));
        body.put("language", "de");
        Response created = who.send("POST", "/api/v1/wiki/projects/" + setup.project() + "/pages", body, null);
        assertEquals(201, created.status(), created.body());
        UUID id = UUID.fromString(created.json().get("id").asString());
        if (publish) {
            Response published = who.send("POST", "/api/v1/wiki/pages/" + id + "/publish?language=de", Map.of("revision", 1), "\"1\"");
            assertEquals(200, published.status(), published.body());
        }
        return id;
    }

    private static Response all(Browser who, String q) {
        Response found = who.get("/api/v1/search/all?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        assertEquals(200, found.status(), found.body());
        return found;
    }

    private static List<String> titles(JsonNode area, String field) {
        return StreamSupport.stream(area.get("items").spliterator(), false).map(item -> field.equals("title") && item.has("fields")
            ? item.get("fields").get("title").asString() : item.get(field).asString()).toList();
    }

    private static void assertInvalid(Response response, String violation) {
        assertEquals(400, response.status(), response.body());
        List<String> violations = new ArrayList<>();
        response.json().get("violations").forEach(v -> violations.add(v.asString()));
        assertTrue(violations.contains(violation), response.body());
    }

    // ---------------------------------------------------------------- tests

    @Test
    void oneWordIsFoundInTicketProjectPublishedPageAndCurrentRequirementButNeverInADraft() {
        String term = word("polar");
        Setup setup = project("Projekt " + term);
        Browser person = member(setup, rules(ticketRules(), requirementRules(), WIKI_RULES));
        ticket(person, setup, "Ticket " + term);
        requirement(person, setup, "Anforderung " + term);
        page(person, setup, "Seite " + term, true);
        page(person, setup, "Entwurf " + term, false);

        JsonNode found = all(person, term).json();
        assertEquals(List.of("Ticket " + term), titles(found.get("tickets"), "title"));
        assertEquals(List.of(setup.key()), titles(found.get("projects"), "key"));
        assertEquals(List.of("Seite " + term), titles(found.get("wiki"), "title"), "the overview searches published pages only");
        assertEquals("published", found.get("wiki").get("items").get(0).get("stage").asString());
        assertEquals(List.of("Anforderung " + term), titles(found.get("requirements"), "title"));
        assertEquals("current", found.get("requirements").get("items").get(0).get("stage").asString());
        assertTrue(found.get("direct").isNull(), found.toString());
        for (String area : List.of("tickets", "projects", "wiki", "requirements")) {
            assertEquals(1, found.get(area).get("total").asLong(), area);
        }
    }

    @Test
    void areasWithoutARightAreNull() {
        String term = word("ohne");
        Setup setup = project("Projekt " + term);
        Browser writer = member(setup, rules(ticketRules(), requirementRules(), WIKI_RULES));
        ticket(writer, setup, "Ticket " + term);
        requirement(writer, setup, "Anforderung " + term);
        page(writer, setup, "Seite " + term, true);

        Browser ticketsOnly = member(setup, rules(ticketRules()));
        JsonNode found = all(ticketsOnly, term).json();
        assertEquals(1, found.get("tickets").get("total").asLong());
        assertEquals(1, found.get("projects").get("total").asLong(), "project.read shows the project");
        assertTrue(found.get("wiki").isNull(), found.toString());
        assertTrue(found.get("requirements").isNull(), found.toString());

        Browser stranger = person();
        JsonNode nothing = all(stranger, term).json();
        for (String area : List.of("tickets", "projects", "wiki", "requirements")) {
            assertTrue(nothing.get(area).isNull(), area + ": " + nothing);
        }
        assertTrue(nothing.get("direct").isNull());
    }

    @Test
    void fiveHitsPerAreaWithTheTotal() {
        String term = word("fuenf");
        Setup setup = project("Projekt " + term);
        Browser person = member(setup, rules(ticketRules()));
        for (int i = 0; i < 7; i++) {
            ticket(person, setup, term + " " + i);
        }
        JsonNode tickets = all(person, term).json().get("tickets");
        assertEquals(7, tickets.get("total").asLong());
        assertEquals(5, tickets.get("items").size());
    }

    @Test
    void anExactKeyOpensTheReadableObjectDirectly() {
        Setup setup = project("Direkt");
        Browser person = member(setup, rules(ticketRules(), requirementRules()));
        ticket(person, setup, "Erstes");
        long second = ticket(person, setup, "Zweites");
        UUID requirement = requirement(person, setup, "Anforderung direkt");

        JsonNode ticket = all(person, " " + setup.key().toLowerCase() + "-" + second + " ").json().get("direct");
        assertEquals("ticket", ticket.get("kind").asString(), ticket.toString());
        assertEquals(setup.project().toString(), ticket.get("projectId").asString());
        assertEquals(setup.key() + "-" + second, ticket.get("key").asString());
        assertEquals(second, ticket.get("number").asLong());

        JsonNode found = all(person, setup.key() + " ANF-1").json().get("direct");
        assertEquals("requirement", found.get("kind").asString(), found.toString());
        assertEquals(requirement.toString(), found.get("id").asString());
        assertEquals(setup.key() + " ANF-1", found.get("key").asString());
        assertTrue(all(person, "ANF-1").json().get("direct").isNull(), "a requirement key needs the project key");

        JsonNode project = all(person, setup.key().toLowerCase()).json().get("direct");
        assertEquals("project", project.get("kind").asString(), project.toString());
        assertEquals(setup.project().toString(), project.get("id").asString());
        assertTrue(project.get("number").isNull());

        assertTrue(all(person, setup.key() + "-99").json().get("direct").isNull(), "a number that does not exist");
    }

    @Test
    void aKeyThePersonMayNotReadOpensNothingAndAnswersLikeAnUnknownOne() {
        Setup setup = project("Verborgen");
        Browser writer = member(setup, rules(ticketRules(), requirementRules()));
        long number = ticket(writer, setup, "Geheim");
        requirement(writer, setup, "Geheime Anforderung");

        Browser ticketsOnly = member(setup, rules(List.of("ticket.read", "ticket:READ:title")));
        Response hidden = all(ticketsOnly, setup.key() + " ANF-1");
        Response unknown = all(ticketsOnly, setup.key() + " ANF-999");
        assertEquals(unknown.body(), hidden.body(), "the answer does not tell whether the requirement exists");

        Browser stranger = person();
        JsonNode outside = all(stranger, setup.key() + "-" + number).json();
        assertTrue(outside.get("direct").isNull(), outside.toString());
        assertTrue(all(stranger, setup.key()).json().get("direct").isNull(), "a project the person may not read");
    }

    @Test
    void theExactProjectKeyComesFirstInTheProjectSearch() {
        Setup named = project("Zuerst");
        Setup keyed = project("Andere");
        // A project whose name contains the key of the other comes after the project with exactly that key.
        Response renamed = named.admin().send("PUT", "/api/v1/projects/" + named.project(),
            Map.of("name", keyed.key() + " " + keyed.key(), "description", keyed.key() + " " + keyed.key() + " " + keyed.key()),
            named.admin().get("/api/v1/projects/" + named.project()).etag());
        assertEquals(200, renamed.status(), renamed.body());
        Browser admin = person(AdministrationRight.PROJECTS_MANAGE);
        Response found = admin.get("/api/v1/search/projects?q=" + keyed.key().toLowerCase());
        assertEquals(200, found.status(), found.body());
        assertEquals(List.of(keyed.key(), named.key()), titles(found.json(), "key"));
        assertEquals(2, found.json().get("total").asLong());
    }

    @Test
    void theNormalizedProjectKeyFindsItsProjectInFilterCountAndDirectHit() {
        // TASK-110, F5: straße names the key STRASSE after NFKC and upper case, although the index holds the word strasse.
        String key = "STRASSE" + (100 + COUNTER.incrementAndGet() % 900);
        Browser admin = person(AdministrationRight.PROJECTS_MANAGE);
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", key, "name", "Wegebau"), null);
        assertEquals(201, created.status(), created.body());
        UUID project = UUID.fromString(created.json().get("id").asString());
        Browser reader = person();
        add(new Setup(project, admin, key), reader.id);
        grant(reader.id, project, "project.read");
        String typed = "straße" + key.substring("STRASSE".length());

        Response found = reader.get("/api/v1/search/projects?q=" + URLEncoder.encode(typed, StandardCharsets.UTF_8));
        assertEquals(200, found.status(), found.body());
        assertEquals(1, found.json().get("total").asLong(), found.body());
        assertEquals(List.of(key), titles(found.json(), "key"));

        JsonNode overview = all(reader, typed).json();
        assertEquals(1, overview.get("projects").get("total").asLong(), overview.toString());
        assertEquals("project", overview.get("direct").get("kind").asString(), overview.toString());
        assertEquals(project.toString(), overview.get("direct").get("id").asString());
    }

    @Test
    void whoMayReadATicketButNotItsProjectFindsTheTicketDirectly() {
        // TASK-110, F2: the direct hit names the ticket, the ticket answers 200 and the project 404 like an unknown one.
        Setup setup = project("Nur Tickets");
        Browser writer = member(setup, rules(ticketRules()));
        long number = ticket(writer, setup, "Lesbar ohne Projekt");
        Browser reader = member(setup, "ticket.read", "ticket:READ:title");

        JsonNode overview = all(reader, setup.key() + "-" + number).json();
        assertEquals("ticket", overview.get("direct").get("kind").asString(), overview.toString());
        assertTrue(overview.get("projects").isNull(), "no project area without project.read");
        Response ticket = reader.get("/api/v1/projects/" + setup.project() + "/tickets/" + number);
        assertEquals(200, ticket.status(), ticket.body());
        assertEquals("Lesbar ohne Projekt", ticket.json().get("fields").get("title").asString());
        Response project = reader.get("/api/v1/projects/" + setup.project());
        assertEquals(404, project.status(), project.body());
        assertEquals("project.not_found", project.code());
    }

    @Test
    void invalidRequestsAnswer400() {
        Browser person = person();
        assertInvalid(person.get("/api/v1/search/all?q=x&preferred=fr"), "preferred.invalid");
        assertInvalid(person.get("/api/v1/search/all?q=" + "a".repeat(201)), "q.invalid");
        assertEquals(401, new Browser().get("/api/v1/search/all?q=x").status());
    }
}
