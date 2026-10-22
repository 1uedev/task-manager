package taskmanager.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.search.internal.SearchProjection;
import taskmanager.tickets.TicketChanged;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-029: search for tickets and projects through the real HTTP interface, the real permissions, projects, tickets and
 * tags modules and a real PostgreSQL. Business actions and field rules are granted directly in the tables, as no rights
 * endpoint exists yet (TASK-030). Every test searches for words of its own, so tests do not see each other's data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> ALL_FIELDS = ProjectConfiguration.FIELD_KEYS;
    private static final String[] TICKETS = {"ticket.read", "ticket.create", "ticket.edit", "READ:*", "WRITE:*", "READ:tags", "WRITE:tags"};

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
    @Autowired SearchProjection projection;
    @Autowired ApplicationEventPublisher events;

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

        JsonNode fields() {
            return json().get("fields");
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
                    request.method(method, HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : JSON.writeValueAsString(body)));
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
    }

    /** A person with a complete login. */
    private record Person(Browser browser, UUID id) {
        Response get(String path) {
            return browser.get(path);
        }
    }

    private Browser anonymous() {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        return browser;
    }

    private Person person(AdministrationRight... rights) {
        var issued = accounts.create("test", "search-" + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        Browser browser = anonymous();
        Response login = browser.send("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null);
        assertEquals(200, login.status(), login.body());
        Response changed = browser.send("POST", "/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG), null);
        assertEquals(200, changed.status(), changed.body());
        return new Person(browser, issued.account().id());
    }

    /**
     * Grants rules in a group of their own, for one project or, with {@code null}, for every project of the company: an
     * action key such as {@code ticket.read}, or a field rule {@code READ:title}, {@code WRITE:title}, {@code DENY-READ:title}
     * for the resource type {@code ticket}; {@code *} stands for all five fields, and {@code tags} is the field of the tags.
     */
    private void grant(UUID account, UUID project, String... rules) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseGet(companies::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            UUID group = UUID.randomUUID();
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "fixture-" + group);
            for (String rule : rules) {
                if (!rule.contains(":")) {
                    jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
                        companyId, group, project, rule);
                    continue;
                }
                String[] parts = rule.split(":");
                boolean deny = parts[0].startsWith("DENY-");
                String operation = deny ? parts[0].substring(5) : parts[0];
                for (String field : parts[1].equals("*") ? ALL_FIELDS : List.of(parts[1])) {
                    jdbc.update("""
                            insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                            values (?, ?, ?, 'ticket', ?, ?, ?)
                            """, companyId, group, project, field, operation, deny ? "DENY" : "ALLOW");
                }
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
    }

    /** A project created by its own manager, with the given people as members. */
    private record Project(UUID id, String key, Person manager) {
    }

    private Project project(Person... members) {
        return project("Suche", members);
    }

    private Project project(String name, Person... members) {
        Person manager = person(AdministrationRight.PROJECTS_MANAGE);
        String key = "S" + COUNTER.incrementAndGet();
        Response created = manager.browser().send("POST", "/api/v1/projects", Map.of("key", key, "name", name), null);
        assertEquals(201, created.status(), created.body());
        UUID id = UUID.fromString(created.json().get("id").asString());
        String version = created.header("ETag");
        for (Person member : members) {
            Response added = manager.browser().send("PUT", "/api/v1/projects/" + id + "/members/" + member.id(), null, version);
            assertEquals(200, added.status(), added.body());
            version = added.header("ETag");
        }
        return new Project(id, key, manager);
    }

    private static Response post(Person person, String path, Object body) {
        return person.browser().send("POST", path, body, null);
    }

    private static UUID tag(Person person, String path, String name) {
        Response created = post(person, path, Map.of("name", name));
        assertEquals(201, created.status(), created.body());
        return UUID.fromString(created.json().get("id").asString());
    }

    private static long ticket(Person person, Project project, Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("type", "task"));
        body.putAll(fields);
        Response created = post(person, "/api/v1/projects/" + project.id() + "/tickets", body);
        assertEquals(201, created.status(), created.body());
        return created.json().get("number").asLong();
    }

    private static long ticket(Person person, Project project, String title) {
        return ticket(person, project, Map.of("title", title));
    }

    private static String ticketTags(Project project, long number) {
        return "/api/v1/projects/" + project.id() + "/tickets/" + number + "/tags";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Response tickets(Person person, String query) {
        return person.get("/api/v1/search/tickets?" + query);
    }

    /** The keys of the found tickets, in the order of the answer, for a search text. */
    private static List<String> keys(Person person, String query) {
        Response found = tickets(person, query);
        assertEquals(200, found.status(), found.body());
        List<String> keys = StreamSupport.stream(found.json().get("items").spliterator(), false)
            .map(item -> item.get("key").asString()).toList();
        assertEquals(keys.size(), found.json().get("total").asLong(), "total counts the same tickets: " + found.body());
        return keys;
    }

    private static List<String> projectKeys(Person person, String query) {
        Response found = person.get("/api/v1/search/projects?" + query);
        assertEquals(200, found.status(), found.body());
        return StreamSupport.stream(found.json().get("items").spliterator(), false).map(item -> item.get("key").asString()).toList();
    }

    private static void assertInvalid(Response response, String violation) {
        assertEquals(400, response.status(), response.body());
        assertEquals("request.invalid", response.code(), response.body());
        assertEquals(List.of(violation), response.violations(), response.body());
    }

    private static String word() {
        return "wort" + COUNTER.incrementAndGet() + "x";
    }

    // ---------------------------------------------------------------- tickets

    @Test
    void wordsMatchTitleDescriptionTagsAndTheIdentifierAsPrefixes() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        String token = word();
        long first = ticket(worker, project, Map.of("title", "Drucker " + token + " defekt"));
        long second = ticket(worker, project, Map.of("title", "Anderes", "description", "Der Drucker im Lager, " + token));
        long third = ticket(worker, project, "Ohne Treffer");
        assertEquals(204, post(worker, ticketTags(project, third), Map.of("name", "Etikett " + token)).status());
        String k1 = project.key() + "-" + first;
        String k2 = project.key() + "-" + second;
        String k3 = project.key() + "-" + third;

        assertEquals(List.of(k3, k2, k1), keys(worker, "q=" + token).stream().sorted(java.util.Comparator.reverseOrder()).toList());
        assertEquals(List.of(k2, k1), keys(worker, "q=" + encode("DRUCK " + token)).stream().sorted(java.util.Comparator.reverseOrder()).toList(),
            "every word is required, each as the start of a word, without regard to case");
        assertEquals(List.of(k1), keys(worker, "q=" + encode("defe " + token.substring(0, token.length() - 1))));
        assertEquals(List.of(k3), keys(worker, "q=etikett%20" + token));
        assertEquals(List.of(k2), keys(worker, "q=" + encode(k2)), "the identifier names one ticket");
        assertEquals(List.of(k2), keys(worker, "q=" + encode(k2.toLowerCase())));
        assertEquals(List.of(), keys(worker, "q=" + encode("!!! ---")), "a text without words matches nothing");
        assertEquals(List.of(), keys(worker, "q=" + encode("'):* | !") + token + "x"), "operators are no search syntax");
        assertInvalid(tickets(worker, "q=" + "a".repeat(201)), "q.invalid");
        assertInvalid(tickets(worker, "q=" + encode("a b c d e f g h i j k")), "q.invalid");
        assertInvalid(tickets(worker, "q=" + "a".repeat(51)), "q.invalid");

        Response found = tickets(worker, "q=" + encode(k1));
        JsonNode hit = found.json().get("items").get(0);
        assertEquals(project.id().toString(), hit.get("projectId").asString());
        assertEquals(first, hit.get("number").asLong());
        assertEquals("Drucker " + token + " defekt", hit.get("fields").get("title").asString());
        assertEquals("new", hit.get("fields").get("status").asString());
        assertEquals(0, hit.get("tags").size());
    }

    @Test
    void theIdentifierRanksFirstAndThePageIsOrderedAndCounted() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        String token = word();
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(project.key() + "-" + ticket(worker, project, token + " Nummer " + i));
        }
        List<String> all = keys(worker, "q=" + token);
        assertEquals(List.of(created.get(4), created.get(3), created.get(2), created.get(1), created.get(0)), all,
            "equal rank: newest number first");
        Response page = tickets(worker, "q=" + token + "&limit=2&offset=2");
        assertEquals(5, page.json().get("total").asLong());
        assertEquals(2, page.json().get("items").size());
        assertEquals(created.get(2), page.json().get("items").get(0).get("key").asString());
        assertInvalid(tickets(worker, "limit=0"), "paging.invalid");
        assertInvalid(tickets(worker, "limit=51"), "paging.invalid");
        assertInvalid(tickets(worker, "offset=1001"), "paging.invalid");
        assertInvalid(tickets(worker, "offset=-1"), "paging.invalid");
    }

    @Test
    void filtersCombineWithTheTextAndEveryTagIsRequired() {
        Person worker = person();
        Person colleague = person();
        Project project = project(worker, colleague);
        Project other = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        grant(worker.id(), other.id(), TICKETS);
        String token = word();
        long open = ticket(worker, project, token + " offen");
        long assigned = ticket(worker, project, Map.of("title", token + " zugewiesen", "assignee", colleague.id().toString()));
        long bug = ticket(worker, project, Map.of("title", token + " Fehler", "type", "bug"));
        long elsewhere = ticket(worker, other, token + " anderswo");
        Response done = worker.browser().send("PATCH", "/api/v1/projects/" + project.id() + "/tickets/" + open,
            Map.of("status", "in_progress"), "\"1\"");
        assertEquals(200, done.status(), done.body());
        UUID red = tag(worker, "/api/v1/projects/" + project.id() + "/catalog/tags", "Rot");
        UUID blue = tag(worker, "/api/v1/projects/" + project.id() + "/catalog/tags", "Blau");
        assertEquals(204, post(worker, ticketTags(project, assigned), Map.of("tagId", red.toString())).status());
        assertEquals(204, post(worker, ticketTags(project, bug), Map.of("tagId", red.toString())).status());
        assertEquals(204, post(worker, ticketTags(project, bug), Map.of("tagId", blue.toString())).status());
        String p = project.key() + "-";

        assertEquals(List.of(other.key() + "-" + elsewhere), keys(worker, "q=" + token + "&project=" + other.id()));
        assertEquals(List.of(p + open), keys(worker, "q=" + token + "&status=in_progress"));
        assertEquals(List.of(p + bug), keys(worker, "q=" + token + "&type=bug"));
        assertEquals(List.of(p + assigned), keys(worker, "q=" + token + "&assignee=" + colleague.id()));
        assertEquals(List.of(p + bug, p + assigned), keys(worker, "q=" + token + "&tag=" + red));
        assertEquals(List.of(p + bug), keys(worker, "q=" + token + "&tag=" + red + "&tag=" + blue));
        assertEquals(List.of(p + bug, p + assigned), keys(worker, "q=" + token + "&type=bug&type=task&tag=" + red),
            "values of one filter combine with or");
        assertEquals(List.of(p + bug), keys(worker, "tag=" + blue), "filters work without a text");

        assertInvalid(tickets(worker, "project=nein"), "project.invalid");
        assertInvalid(tickets(worker, "project=" + UUID.randomUUID()), "project.unknown");
        assertInvalid(tickets(worker, "tag=" + UUID.randomUUID()), "tag.unknown");
        assertInvalid(tickets(worker, "status=%20"), "status.invalid");
        assertInvalid(tickets(colleague, "tag=" + red), "tag.unknown");
        assertEquals(401, anonymous().get("/api/v1/search/tickets?q=" + token).status());
    }

    @Test
    void wordsOfHiddenFieldsNeitherMatchNorCountNorRank() {
        Person worker = person();
        Person viewer = person();
        Project project = project(worker, viewer);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        grant(viewer.id(), project.id(), "ticket.read", "READ:title", "READ:status");
        String token = word();
        String secret = word();
        long visible = ticket(worker, project, Map.of("title", token + " sichtbar"));
        long hidden = ticket(worker, project, Map.of("title", "Titel", "description", token + " " + secret));
        long tagged = ticket(worker, project, Map.of("title", token + " mit Tag"));
        assertEquals(204, post(worker, ticketTags(project, tagged), Map.of("name", secret)).status());
        assertEquals(204, post(worker, ticketTags(project, visible), Map.of("name", token + "tag")).status());
        String p = project.key() + "-";

        assertEquals(3, keys(worker, "q=" + token).size(), "the worker reads every field");
        assertEquals(List.of(p + tagged, p + visible), keys(viewer, "q=" + token),
            "a word only in the hidden description does not find the ticket, and the total counts what is shown");
        assertEquals(List.of(), keys(viewer, "q=" + secret), "neither the hidden description nor hidden tag names match");
        assertEquals(List.of(), keys(viewer, "q=" + token + "tag"));
        assertEquals(List.of(), keys(viewer, "type=task"), "a filter on a hidden field matches nothing");
        assertEquals(List.of(), keys(viewer, "assignee=" + worker.id()));
        assertEquals(List.of(p + tagged, p + hidden, p + visible), keys(viewer, "status=new"));

        JsonNode hit = tickets(viewer, "q=" + encode(p + visible)).json().get("items").get(0);
        assertEquals(List.of("status", "title"), sortedNames(hit.get("fields")), "only readable fields are shown");
        assertTrue(hit.get("tags").isNull(), "tags are hidden without their read rule");

        // Ranks come from readable fields alone: the hidden description repeating the word does not move the ticket.
        String rankWord = word();
        Project ranked = project(worker, viewer);
        grant(worker.id(), ranked.id(), TICKETS);
        grant(viewer.id(), ranked.id(), "ticket.read", "READ:title");
        long plain = ticket(worker, ranked, Map.of("title", rankWord + " eins"));
        long boosted = ticket(worker, ranked, Map.of("title", rankWord + " zwei",
            "description", (rankWord + " ").repeat(20)));
        String r = ranked.key() + "-";
        assertEquals(List.of(r + boosted, r + plain), keys(worker, "q=" + rankWord));
        assertEquals(List.of(r + boosted, r + plain), keys(viewer, "q=" + rankWord), "equal rank, newest number first");
        long newer = ticket(worker, ranked, Map.of("title", rankWord + " drei"));
        assertEquals(List.of(r + newer, r + boosted, r + plain), keys(viewer, "q=" + rankWord));
        assertEquals(r + boosted, keys(worker, "q=" + rankWord).get(0), "for the worker the description lifts the ticket");
    }

    private static List<String> sortedNames(JsonNode object) {
        List<String> names = new ArrayList<>(object.propertyNames());
        names.sort(null);
        return names;
    }

    @Test
    void onlyMembersWithTicketReadFindTicketsAndRevokedRightsTakeEffectAtOnce() {
        Person worker = person();
        Person member = person();
        Person outsider = person();
        Project project = project(worker, member);
        grant(worker.id(), project.id(), TICKETS);
        grant(outsider.id(), null, TICKETS);
        String token = word();
        long number = ticket(worker, project, token);
        String key = project.key() + "-" + number;

        assertEquals(List.of(), keys(member, "q=" + token), "membership alone does not read tickets");
        assertEquals(List.of(), keys(outsider, "q=" + token), "company wide rules without membership find nothing");
        assertEquals(List.of(), keys(outsider, "q=" + encode(key)), "not even by the identifier");
        assertInvalid(tickets(outsider, "project=" + project.id()), "project.unknown");

        grant(member.id(), project.id(), "ticket.read", "READ:*");
        assertEquals(List.of(key), keys(member, "q=" + token));
        jdbc.update("delete from permissions_membership where user_id = ?", member.id());
        jdbc.update("update permissions_revision set revision = revision + 1");
        assertEquals(List.of(), keys(member, "q=" + token), "a revoked right hides the ticket at the next search");

        grant(member.id(), project.id(), "ticket.read", "READ:*");
        Response removed = project.manager().browser().send("DELETE", "/api/v1/projects/" + project.id() + "/members/" + member.id(),
            null, project.manager().get("/api/v1/projects/" + project.id()).header("ETag"));
        assertEquals(200, removed.status(), removed.body());
        assertEquals(List.of(), keys(member, "q=" + token), "leaving the project hides its tickets");
    }

    @Test
    void theProjectionFollowsChangesOfTicketsTagsAndProjects() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        String before = word();
        String after = word();
        long number = ticket(worker, project, before);
        String key = project.key() + "-" + number;
        assertEquals(List.of(key), keys(worker, "q=" + before));

        Response edited = worker.browser().send("PATCH", "/api/v1/projects/" + project.id() + "/tickets/" + number,
            Map.of("title", after), "\"1\"");
        assertEquals(200, edited.status(), edited.body());
        assertEquals(List.of(), keys(worker, "q=" + before));
        assertEquals(List.of(key), keys(worker, "q=" + after));

        String tagWord = word();
        UUID tagId = tag(worker, "/api/v1/projects/" + project.id() + "/catalog/tags", tagWord);
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("tagId", tagId.toString())).status());
        assertEquals(List.of(key), keys(worker, "q=" + tagWord));
        String renamed = word();
        grant(worker.id(), project.id(), "tags.manage");
        Response rename = worker.browser().send("PATCH", "/api/v1/projects/" + project.id() + "/catalog/tags/" + tagId, Map.of("name", renamed), "\"1\"");
        assertEquals(200, rename.status(), rename.body());
        assertEquals(List.of(), keys(worker, "q=" + tagWord), "a renamed tag no longer matches its old name");
        assertEquals(List.of(key), keys(worker, "q=" + renamed));
        JsonNode tags = tickets(worker, "q=" + renamed).json().get("items").get(0).get("tags");
        assertEquals(renamed, tags.get(0).get("name").asString());
        assertEquals(204, worker.browser().send("DELETE", ticketTags(project, number) + "/" + tagId, null, null).status());
        assertEquals(List.of(), keys(worker, "q=" + renamed));
        assertEquals(List.of(), keys(worker, "tag=" + tagId));

        String projectWord = word();
        String version = project.manager().get("/api/v1/projects/" + project.id()).header("ETag");
        Response changed = project.manager().browser().send("PUT", "/api/v1/projects/" + project.id(),
            Map.of("name", "Neu " + projectWord), version);
        assertEquals(200, changed.status(), changed.body());
        assertEquals(List.of(), projectKeys(worker, "q=" + projectWord), "the worker may not read the project");
        assertEquals(List.of(project.key()), projectKeys(project.manager(), "q=" + projectWord));
    }

    @Test
    void aRolledBackChangeLeavesNoTraceAndTheProjectionNeedsTheTransactionOfTheChange() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        String token = word();
        long number = ticket(worker, project, "Vorher");
        UUID ticketId = UUID.fromString(worker.get("/api/v1/projects/" + project.id() + "/tickets/" + number).json().get("id").asString());
        UUID companyId = companies.find().orElseThrow();
        TicketChanged change = new TicketChanged(ticketId, companyId, project.id(), number, token, null, "task", "new", null);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(change);
            assertEquals(1, jdbc.queryForObject("select count(*) from search_ticket where title = ?", Integer.class, token));
            status.setRollbackOnly();
        });
        assertEquals(List.of(), keys(worker, "q=" + token), "the rollback took the projection with it");
        assertThrows(IllegalTransactionStateException.class, () -> projection.on(change),
            "outside a transaction the projection refuses the change");
        assertEquals(List.of(project.key() + "-" + number), keys(worker, "q=vorher"));
    }

    @Test
    void aRebuildRestoresTheProjectionFromTheOwningModules() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        String token = word();
        grant(worker.id(), null, "tag.create");
        String tagWord = word();
        long number = ticket(worker, project, token);
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("name", tagWord, "catalog", "company")).status());
        jdbc.update("update search_ticket set title = 'verdorben', title_terms = 'verdorben', tags_text = '' where project_id = ?", project.id());
        assertEquals(List.of(), keys(worker, "q=" + token));

        projection.rebuild(companies.find().orElseThrow());
        String key = project.key() + "-" + number;
        assertEquals(List.of(key), keys(worker, "q=" + token));
        assertEquals(List.of(key), keys(worker, "q=" + tagWord));
        assertEquals(jdbc.queryForObject("select count(*) from ticket", Long.class),
            jdbc.queryForObject("select count(*) from search_ticket", Long.class));
        assertEquals(jdbc.queryForObject("select count(*) from tag_ticket", Long.class),
            jdbc.queryForObject("select count(*) from search_ticket_tag", Long.class));
        UUID company = companies.find().orElseThrow();
        assertEquals(false, projection.buildIfMissing(company), "a built projection is not built again at the start");
        jdbc.update("update search_ticket set title = 'verdorben', title_terms = 'verdorben' where project_id = ?", project.id());
        jdbc.update("delete from search_state");
        assertEquals(true, projection.buildIfMissing(company), "a company without a built projection gets one");
        assertEquals(List.of(key), keys(worker, "q=" + token));
    }

    @Test
    void aRebuildCopesWithMoreTicketsThanOneStatementTakesParameters() {
        Person worker = person();
        Project project = project(worker);
        UUID company = companies.find().orElseThrow();
        // 70,000 tickets exceed the 65,535 parameters of one PostgreSQL statement; the reference profile has 90,000.
        int many = 70_000;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("insert into ticket (id, company_id, project_id, number, title, type, status, configuration_version, "
                + "version, created_at, created_by, updated_at, updated_by) select gen_random_uuid(), ?, ?, n, 'Masse ' || n, "
                + "'task', 'new', 1, 1, now(), ?, now(), ? from generate_series(1, ?) n",
                company, project.id(), worker.id(), worker.id(), many);
            projection.rebuild(company);
            assertEquals(many, jdbc.queryForObject("select count(*) from search_ticket where project_id = ?", Long.class, project.id()));
            status.setRollbackOnly();
        });
    }

    // ---------------------------------------------------------------- projects

    @Test
    void projectsAreFoundByKeyNameDescriptionAndTagsWithTheProjectRights() {
        Person reader = person();
        Person member = person();
        Person outsider = person();
        String token = word();
        Project project = project("Lager " + token, reader, member);
        Project other = project("Büro " + token);
        grant(reader.id(), project.id(), "project.read");
        grant(outsider.id(), null, "project.read", "project.manage");
        Person admin = person(AdministrationRight.TAGS_MANAGE, AdministrationRight.PROJECTS_MANAGE);
        String tagWord = word();
        UUID branch = tag(admin, "/api/v1/tags", tagWord);
        assertEquals(204, post(admin, "/api/v1/projects/" + other.id() + "/tags", Map.of("tagId", branch.toString())).status());

        assertEquals(List.of(project.key()), projectKeys(reader, "q=" + token));
        assertEquals(List.of(project.key()), projectKeys(reader, "q=" + project.key().toLowerCase()));
        assertEquals(List.of(), projectKeys(member, "q=" + token), "membership alone does not read the project");
        assertEquals(List.of(), projectKeys(outsider, "q=" + token), "rules without membership find nothing");
        List<String> both = projectKeys(admin, "q=" + token);
        assertEquals(2, both.size(), "projects.manage finds every project of the company");
        assertEquals(List.of(other.key()), projectKeys(admin, "q=" + tagWord));
        assertEquals(List.of(other.key()), projectKeys(admin, "q=" + token + "&tag=" + branch));
        assertEquals(List.of(other.key()), projectKeys(admin, "q=b%C3%BCro%20" + token));

        grant(reader.id(), project.id(), "tag.create");
        UUID local = tag(reader, "/api/v1/projects/" + project.id() + "/catalog/tags", word());
        assertInvalid(admin.get("/api/v1/search/projects?tag=" + local), "tag.unknown");
        assertInvalid(admin.get("/api/v1/search/projects?limit=99"), "paging.invalid");
        assertInvalid(admin.get("/api/v1/search/projects?q=" + "b".repeat(201)), "q.invalid");
        Response hit = reader.get("/api/v1/search/projects?q=" + token);
        assertEquals("Lager " + token, hit.json().get("items").get(0).get("name").asString());
        assertEquals(1, hit.json().get("total").asLong());
    }
    // ---------------------------------------------------------------- findings of the independent review (PR #129)

    @Test
    void theSameTextFindsWhatWasStoredAlsoInFullWidthAndStructuredForm() {
        Person worker = person();
        String wide = "Ｆｕｌｌｗｉｄｔｈ" + COUNTER.incrementAndGet();
        Project project = project(wide, worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "project.read", "tag.create");
        long titled = ticket(worker, project, wide);
        String address = "support" + COUNTER.incrementAndGet() + "@example.test";
        long mailed = ticket(worker, project, Map.of("title", "Anfrage", "description", "Antwort an " + address));
        String tagged = "Ｔａｇ" + COUNTER.incrementAndGet() + "-Wert";
        long withTag = ticket(worker, project, "Mit Tag");
        assertEquals(204, post(worker, ticketTags(project, withTag), Map.of("name", tagged)).status());
        String p = project.key() + "-";

        assertEquals(List.of(p + titled), keys(worker, "q=" + encode(wide)), "full width input finds its stored text");
        assertEquals(List.of(p + titled), keys(worker, "q=" + encode(wide.toLowerCase())));
        assertEquals(List.of(p + mailed), keys(worker, "q=" + encode(address)), "a mail address is split alike on both sides");
        assertEquals(List.of(p + mailed), keys(worker, "q=" + encode(address.substring(0, 9))));
        assertEquals(List.of(p + withTag), keys(worker, "q=" + encode(tagged)));
        assertEquals(List.of(project.key()), projectKeys(worker, "q=" + encode(wide)));
        assertEquals(List.of(project.key()), projectKeys(project.manager(), "q=" + encode(wide)));
        JsonNode hit = tickets(worker, "q=" + encode(wide)).json().get("items").get(0);
        assertEquals(wide, hit.get("fields").get("title").asString(), "the answer keeps the original text");
    }

    @Test
    void aTagRenameAndARebuildWaitForEachOtherInsteadOfDeadlocking() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create", "tags.manage");
        long number = ticket(worker, project, "Ziel");
        UUID tag = tag(worker, "/api/v1/projects/" + project.id() + "/catalog/tags", word());
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("tagId", tag.toString())).status());
        UUID company = companies.find().orElseThrow();
        String renamed = word();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger gatePid = new AtomicInteger();
            // Holds search_tag so that the rename has entered the projection when the rebuild starts.
            Future<?> gate = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.execute("lock table search_tag in share mode");
                gatePid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                locked.countDown();
                await(release);
            }));
            try {
                assertTrue(locked.await(10, TimeUnit.SECONDS));
                Future<Response> rename = pool.submit(() -> worker.browser().send("PATCH",
                    "/api/v1/projects/" + project.id() + "/catalog/tags/" + tag, Map.of("name", renamed), "\"1\""));
                int renamePid = waiter(gatePid.get(), "search_tag");
                Future<String> rebuild = pool.submit(() -> {
                    try {
                        projection.rebuild(company);
                        return "ok";
                    } catch (RuntimeException failed) {
                        return failed.getClass().getSimpleName();
                    }
                });
                waiter(renamePid, "lock table search_state");
                release.countDown();
                gate.get(10, TimeUnit.SECONDS);
                assertEquals(200, rename.get(15, TimeUnit.SECONDS).status());
                assertEquals("ok", rebuild.get(15, TimeUnit.SECONDS), "the rebuild waits for the rename that entered first");
            } finally {
                release.countDown();
                gate.get(15, TimeUnit.SECONDS);
            }
        }
        assertEquals(List.of(project.key() + "-" + number), keys(worker, "q=" + renamed));
    }

    @Test
    void everyReadableFieldVectorServesTheTextMatchWithItsIndex() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.execute("set local enable_seqscan = off");
            String plan = String.join(" | ", jdbc.queryForList("""
                    explain (costs off) select ticket_id from search_ticket s where
                    (s.title_tsv @@ to_tsquery('simple', ?) or s.description_tsv @@ to_tsquery('simple', ?)
                        or s.tags_tsv @@ to_tsquery('simple', ?))
                    """, String.class, "'wort':*", "'wort':*", "'wort':*"));
            assertTrue(plan.contains("search_ticket_title") && plan.contains("search_ticket_description")
                && plan.contains("search_ticket_tags") && !plan.contains("Seq Scan"), plan);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(20, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            throw new IllegalStateException(interrupted);
        }
    }

    /** The backend that waits for {@code holder} while running a statement containing {@code statement}. */
    private int waiter(int holder, String statement) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            List<Integer> pids = jdbc.queryForList("""
                    select pid from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid()
                    and ? = any(pg_blocking_pids(pid)) and query ilike ?
                    """, Integer.class, holder, "%" + statement + "%");
            if (!pids.isEmpty()) {
                return pids.get(0);
            }
            Thread.sleep(10);
        }
        throw new AssertionError("nothing waits for " + holder + " in " + statement);
    }
}
