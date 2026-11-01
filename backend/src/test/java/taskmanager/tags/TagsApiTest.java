package taskmanager.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.AfterAll;
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
import taskmanager.projects.ProjectConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-028: tag catalogs and tag assignments through the real HTTP interface, the real permissions, projects and tickets
 * modules and a real PostgreSQL. Business actions and field rules are granted directly in the tables, as no rights endpoint
 * exists yet (TASK-030).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TagsApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
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

    @AfterAll
    static void stopThreads() {
        POOL.shutdownNow();
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
        var issued = accounts.create("test", "tags-" + COUNTER.incrementAndGet(), null);
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
        Person manager = person(AdministrationRight.PROJECTS_MANAGE);
        String key = "G" + COUNTER.incrementAndGet();
        Response created = manager.browser().send("POST", "/api/v1/projects", Map.of("key", key, "name", "Tags " + key), null);
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

    private static String catalog(Project project) {
        return "/api/v1/projects/" + project.id() + "/catalog/tags";
    }

    private static String ticketTags(Project project, long number) {
        return "/api/v1/projects/" + project.id() + "/tickets/" + number + "/tags";
    }

    private static String projectTags(Project project) {
        return "/api/v1/projects/" + project.id() + "/tags";
    }

    private static Response post(Person person, String path, Object body) {
        return person.browser().send("POST", path, body, null);
    }

    private static Response patch(Person person, String path, Object body, String ifMatch) {
        return person.browser().send("PATCH", path, body, ifMatch);
    }

    private static Response delete(Person person, String path) {
        return person.browser().send("DELETE", path, null, null);
    }

    /** Creates a tag and returns its identifier. */
    private static UUID tag(Person person, String path, String name) {
        Response created = post(person, path, Map.of("name", name));
        assertEquals(201, created.status(), created.body());
        return UUID.fromString(created.json().get("id").asString());
    }

    private static long ticket(Person person, Project project, String title) {
        Response created = post(person, "/api/v1/projects/" + project.id() + "/tickets", Map.of("type", "task", "title", title));
        assertEquals(201, created.status(), created.body());
        return created.json().get("number").asLong();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static void assertProblem(Response response, int status, String code) {
        assertEquals(status, response.status(), response.body());
        assertEquals(code, response.code(), response.body());
    }

    private static List<String> tagNames(Response response) {
        assertEquals(200, response.status(), response.body());
        return StreamSupport.stream(response.json().spliterator(), false).map(tag -> tag.get("name").asString()).toList();
    }

    private List<String> audit(UUID tagId) {
        return jdbc.queryForList("select operation from tag_audit_event where tag_id = ? order by id", String.class, tagId);
    }

    // ---------------------------------------------------------------- the company catalog

    @Test
    void theCompanyCatalogIsReadByEveryLoginAndChangedOnlyWithItsRights() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person creator = person();
        grant(creator.id(), null, "tag.create");
        Person reader = person();
        assertProblem(anonymous().get("/api/v1/tags"), 401, "auth.required");

        String token = "k" + COUNTER.incrementAndGet() + "q";
        String search = "/api/v1/tags?q=" + token;
        Response created = post(admin, "/api/v1/tags", Map.of("name", "  Kunde \t  Nord " + token));
        assertEquals(201, created.status(), created.body());
        assertEquals("\"1\"", created.header("ETag"));
        assertEquals("Kunde Nord " + token, created.json().get("name").asString(), "outer spaces go, inner spaces collapse");
        assertEquals("company", created.json().get("catalog").asString());
        assertTrue(created.json().get("projectId").isNull());
        UUID id = UUID.fromString(created.json().get("id").asString());

        assertProblem(post(creator, "/api/v1/tags", Map.of("name", "KUNDE NORD " + token.toUpperCase())), 409, "tag.name_taken");
        assertEquals(201, post(creator, "/api/v1/tags", Map.of("name", "Rechnung " + token)).status(), "tag.create adds tags");
        assertProblem(post(reader, "/api/v1/tags", Map.of("name", "Ohne Recht")), 403, "auth.forbidden");
        assertEquals(List.of("Kunde Nord " + token, "Rechnung " + token), tagNames(reader.get(search)));
        assertEquals(List.of("Rechnung " + token), tagNames(reader.get("/api/v1/tags?q=rechnung%20" + token)));
        assertEquals(List.of("Rechnung " + token), tagNames(reader.get(search + "&after=kunde%20nord%20" + token)));
        assertEquals(List.of("Kunde Nord " + token), tagNames(reader.get(search + "&limit=1")));
        assertProblem(reader.get("/api/v1/tags?limit=0"), 400, "request.invalid");
        assertEquals(200, reader.get("/api/v1/tags/" + id).status());

        assertProblem(patch(creator, "/api/v1/tags/" + id, Map.of("name", "Kunde Süd"), "\"1\""), 403, "auth.forbidden");
        assertProblem(patch(admin, "/api/v1/tags/" + id, Map.of("name", "Kunde Süd"), null), 428, "tag.precondition_required");
        assertProblem(patch(admin, "/api/v1/tags/" + id, Map.of("name", "Kunde Süd"), "\"7\""), 412, "tag.version_conflict");
        assertProblem(patch(admin, "/api/v1/tags/" + id, Map.of("name", "rechnung " + token), "\"1\""), 409, "tag.name_taken");
        Response renamed = patch(admin, "/api/v1/tags/" + id, Map.of("name", "Kunde Süd"), "\"1\"");
        assertEquals(200, renamed.status(), renamed.body());
        assertEquals("\"2\"", renamed.header("ETag"));
        Response unchanged = patch(admin, "/api/v1/tags/" + id, Map.of("name", "Kunde Süd"), "\"2\"");
        assertEquals("\"2\"", unchanged.header("ETag"), "no real change keeps the version");
        Response archived = patch(admin, "/api/v1/tags/" + id, Map.of("archived", true), "\"2\"");
        assertTrue(archived.json().get("archived").asBoolean());
        assertEquals(List.of("Rechnung " + token), tagNames(reader.get(search)), "archived tags are left out by default");
        assertEquals(List.of("Rechnung " + token), tagNames(reader.get(search + "&archived=true")), "the renamed tag left the search");
        assertTrue(tagNames(reader.get("/api/v1/tags?archived=true&q=kunde%20s%C3%BCd")).contains("Kunde Süd"), "archived=true shows archived tags");
        assertEquals(List.of("tag.created", "tag.renamed", "tag.archived"), audit(id));
    }

    @Test
    void namesAndBodiesAreChecked() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        for (String name : List.of("", "   ", "a".repeat(51), "Zeile\nUmbruch".replace("\n", "\u0007"), "Nul\u0000")) {
            assertProblem(post(admin, "/api/v1/tags", Map.of("name", name)), 400, "request.invalid");
        }
        assertEquals(201, post(admin, "/api/v1/tags", Map.of("name", "ä".repeat(50))).status(), "50 characters are allowed");
        assertEquals("Fein", post(admin, "/api/v1/tags", Map.of("name", "Ｆein")).json().get("name").asString(),
            "names are stored in Unicode NFKC");
        Response unknown = post(admin, "/api/v1/tags", Map.of("name", "Gut", "color", "red"));
        assertProblem(unknown, 400, "request.invalid");
        assertEquals(List.of("fields.unknown"), unknown.violations());
        assertProblem(post(admin, "/api/v1/tags", "[]"), 400, "request.invalid");
        UUID id = tag(admin, "/api/v1/tags", "Prüfen");
        assertEquals(List.of("fields.required"), patch(admin, "/api/v1/tags/" + id, Map.of(), "\"1\"").violations());
        assertEquals(List.of("archived.invalid"), patch(admin, "/api/v1/tags/" + id, Map.of("archived", "ja"), "\"1\"").violations());
        assertProblem(admin.get("/api/v1/tags/" + UUID.randomUUID()), 404, "tag.not_found");
    }

    @Test
    void concurrentCreationsOfOneNameYieldOneTag() throws Exception {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        List<Future<Response>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(POOL.submit(() -> post(admin, "/api/v1/tags", Map.of("name", "Gleichzeitig"))));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Response> call : calls) {
            statuses.add(call.get(30, TimeUnit.SECONDS).status());
        }
        assertEquals(List.of(201, 409, 409, 409, 409, 409), statuses.stream().sorted().toList());
    }

    // ---------------------------------------------------------------- the project catalog

    @Test
    void theProjectCatalogIsVisibleOnlyToMembersWithAProjectRight() {
        Person member = person();
        Person plain = person();
        Person outsider = person(AdministrationRight.TAGS_MANAGE);
        grant(outsider.id(), null, "tag.create", "ticket.read", "project.read");
        Project project = project(member, plain);
        Project other = project(member);
        grant(member.id(), project.id(), "ticket.read", "tag.create");
        grant(member.id(), other.id(), "ticket.read");
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        UUID shared = tag(admin, "/api/v1/tags", "Intern");

        Response created = post(member, catalog(project), Map.of("name", "Intern"));
        assertEquals(201, created.status(), "the same name may exist once in the company and once in each project");
        assertEquals("project", created.json().get("catalog").asString());
        assertEquals(project.id().toString(), created.json().get("projectId").asString());
        UUID local = UUID.fromString(created.json().get("id").asString());
        assertProblem(post(member, catalog(project), Map.of("name", "intern")), 409, "tag.name_taken");
        assertProblem(post(member, catalog(other), Map.of("name", "Woanders")), 403, "auth.forbidden");

        assertEquals(List.of("Intern"), tagNames(member.get(catalog(project))));
        assertEquals(List.of(), tagNames(member.get(catalog(other))));
        assertProblem(member.get(catalog(other) + "/" + local), 404, "tag.not_found");
        assertProblem(plain.get(catalog(project)), 404, "project.not_found");
        assertProblem(outsider.get(catalog(project)), 404, "project.not_found");
        assertProblem(post(outsider, catalog(project), Map.of("name", "Fremd")), 404, "project.not_found");
        assertProblem(member.get("/api/v1/tags/" + local), 404, "tag.not_found");
        List<String> companyIds = StreamSupport.stream(member.get("/api/v1/tags?q=intern").json().spliterator(), false)
            .map(tag -> tag.get("id").asString()).toList();
        assertTrue(companyIds.contains(shared.toString()));
        assertFalse(companyIds.contains(local.toString()), "the company catalog never shows project tags");

        assertProblem(patch(member, catalog(project) + "/" + local, Map.of("archived", true), "\"1\""), 403, "auth.forbidden");
        grant(member.id(), project.id(), "tags.manage");
        Response archived = patch(member, catalog(project) + "/" + local, Map.of("archived", true), "\"1\"");
        assertEquals(200, archived.status(), archived.body());
        assertProblem(patch(member, catalog(project) + "/" + shared, Map.of("archived", true), "\"1\""), 404, "tag.not_found");
    }

    // ---------------------------------------------------------------- tags of tickets

    @Test
    void ticketsTakeTagsOfTheCompanyAndOfTheirProjectWithTheFieldRights() {
        Person worker = person();
        Person viewer = person();
        Person blind = person();
        Person outsider = person();
        Project project = project(worker, viewer, blind);
        Project other = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        grant(worker.id(), other.id(), "ticket.read", "tag.create");
        grant(viewer.id(), project.id(), "ticket.read", "READ:*", "READ:tags");
        grant(blind.id(), project.id(), "ticket.read", "READ:*");
        grant(outsider.id(), null, TICKETS);
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        UUID company = tag(admin, "/api/v1/tags", "Kunde");
        UUID foreign = tag(worker, catalog(other), "Fremdprojekt");
        long number = ticket(worker, project, "Mit Tags");

        assertEquals(204, post(worker, ticketTags(project, number), Map.of("tagId", company.toString())).status());
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("tagId", company.toString())).status(), "idempotent");
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("name", "Dringend")).status(),
            "a new name is created in the project catalog");
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("name", "kunde", "catalog", "company")).status(),
            "an existing name of the company catalog is taken as it is");
        assertEquals(List.of("Dringend"), tagNames(worker.get(catalog(project))));
        assertEquals(List.of("Dringend", "Kunde"), tagNames(viewer.get(ticketTags(project, number))));
        assertEquals("\"1\"", worker.get("/api/v1/projects/" + project.id() + "/tickets/" + number).header("ETag"),
            "a tag assignment does not change the ticket");

        assertProblem(blind.get(ticketTags(project, number)), 403, "auth.forbidden");
        assertProblem(post(viewer, ticketTags(project, number), Map.of("tagId", company.toString())), 403, "auth.forbidden");
        assertProblem(outsider.get(ticketTags(project, number)), 404, "project.not_found");
        assertProblem(post(outsider, ticketTags(project, number), Map.of("tagId", company.toString())), 404, "project.not_found");
        assertProblem(post(worker, ticketTags(project, number), Map.of("tagId", foreign.toString())), 404, "tag.not_found");
        assertProblem(post(worker, ticketTags(project, 99), Map.of("tagId", company.toString())), 404, "ticket.not_found");
        assertProblem(worker.get(ticketTags(project, 99)), 404, "ticket.not_found");

        Person editor = person();
        Project shared = project(editor);
        grant(editor.id(), shared.id(), TICKETS);
        long second = ticket(editor, shared, "Ohne Katalogrecht");
        assertProblem(post(editor, ticketTags(shared, second), Map.of("name", "Neu")), 403, "auth.forbidden");
        assertEquals(204, post(editor, ticketTags(shared, second), Map.of("name", "Kunde", "catalog", "company")).status(),
            "an existing tag needs no catalog right");
        Person writer = person();
        Project hidden = project(writer, editor);
        grant(writer.id(), hidden.id(), "ticket.edit", "WRITE:tags", "READ:tags");
        grant(editor.id(), hidden.id(), TICKETS);
        long third = ticket(editor, hidden, "Ohne Lesen");
        tag(admin, "/api/v1/tags", "Geheim");
        assertProblem(post(writer, ticketTags(hidden, third), Map.of("name", "Geheim")), 403, "auth.forbidden");
        assertEquals(204, post(writer, ticketTags(hidden, third), Map.of("name", "Geheim", "catalog", "company")).status(),
            "without a reader action the project catalog stays closed, the company catalog does not");

        assertEquals(200, patch(admin, "/api/v1/tags/" + company, Map.of("archived", true), "\"1\"").status());
        assertProblem(post(editor, ticketTags(shared, second), Map.of("tagId", company.toString())), 409, "tag.archived");
        assertEquals(List.of("Dringend", "Kunde"), tagNames(viewer.get(ticketTags(project, number))), "an archived tag stays assigned");
        assertEquals(204, delete(worker, ticketTags(project, number) + "/" + company).status());
        assertEquals(204, delete(worker, ticketTags(project, number) + "/" + company).status(), "idempotent");
        assertEquals(List.of("Dringend"), tagNames(viewer.get(ticketTags(project, number))));
        assertEquals(List.of("tag.created", "tag.assigned", "tag.assigned", "tag.archived", "tag.unassigned"), audit(company));
    }

    @Test
    void assignmentsAndRemovalsAppearInTheTicketHistoryOnlyForReadersOfTheTags() {
        Person worker = person();
        Person viewer = person();
        Person blind = person();
        Project project = project(worker, viewer, blind);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        grant(viewer.id(), project.id(), "ticket.read", "READ:*", "READ:tags");
        grant(blind.id(), project.id(), "ticket.read", "READ:*");
        long number = ticket(worker, project, "Mit Historie");
        UUID tagId = tag(worker, catalog(project), "Verlauf");
        String history = "/api/v1/projects/" + project.id() + "/tickets/" + number + "/history";

        assertEquals(204, post(worker, ticketTags(project, number), Map.of("tagId", tagId.toString())).status());
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("tagId", tagId.toString())).status());
        Response edited = patch(worker, "/api/v1/projects/" + project.id() + "/tickets/" + number, Map.of("title", "Geändert"), "\"1\"");
        assertEquals(200, edited.status(), edited.body());
        assertEquals(204, delete(worker, ticketTags(project, number) + "/" + tagId).status());
        assertEquals(204, delete(worker, ticketTags(project, number) + "/" + tagId).status());
        assertEquals("\"2\"", worker.get("/api/v1/projects/" + project.id() + "/tickets/" + number).header("ETag"),
            "only the change of the title raises the version");

        Response seen = viewer.get(history);
        assertEquals(200, seen.status(), seen.body());
        List<String> entries = new ArrayList<>();
        for (JsonNode entry : seen.json()) {
            JsonNode changes = entry.get("changes");
            if (changes.get(0).get("field").asString().equals("tags")) {
                assertEquals(1, changes.size(), seen.body());
                assertEquals(worker.id().toString(), entry.get("actor").asString());
                JsonNode from = changes.get(0).get("from");
                JsonNode to = changes.get(0).get("to");
                entries.add(entry.get("version").asLong() + (from.isNull() ? " +" + to.asString() : " -" + from.asString()));
            } else {
                entries.add(entry.get("version").asLong() + " fields");
            }
        }
        assertEquals(List.of("1 fields", "1 +" + tagId, "2 fields", "2 -" + tagId), entries,
            "one entry per actual assignment and removal, with the version the ticket had");

        Response hidden = blind.get(history);
        assertEquals(200, hidden.status(), hidden.body());
        assertEquals(2, hidden.json().size(), hidden.body());
        for (JsonNode entry : hidden.json()) {
            for (JsonNode change : entry.get("changes")) {
                assertFalse(change.get("field").asString().equals("tags"), hidden.body());
            }
        }
    }

    @Test
    void assignmentBodiesAreChecked() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        long number = ticket(worker, project, "Prüfung");
        assertEquals(List.of("target.required"), post(worker, ticketTags(project, number), Map.of()).violations());
        assertEquals(List.of("target.ambiguous"), post(worker, ticketTags(project, number),
            Map.of("tagId", UUID.randomUUID().toString(), "name", "x")).violations());
        assertEquals(List.of("tagId.invalid"), post(worker, ticketTags(project, number), Map.of("tagId", "nein")).violations());
        assertEquals(List.of("catalog.invalid"), post(worker, ticketTags(project, number), Map.of("name", "x", "catalog", "wiki")).violations());
        assertEquals(List.of("name.invalid"), post(worker, ticketTags(project, number), Map.of("name", " ")).violations());
        assertEquals(List.of("fields.unknown"), post(project.manager(), projectTags(project), Map.of("name", "x", "catalog", "company")).violations());
    }

    // ---------------------------------------------------------------- tags of projects

    @Test
    void projectsTakeTagsOfTheCompanyCatalogWithTheProjectRights() {
        Person reader = person();
        Person delegate = person();
        Project project = project(reader, delegate);
        grant(reader.id(), project.id(), "project.read");
        grant(delegate.id(), project.id(), "project.manage", "tag.create");
        Person outsider = person();
        grant(outsider.id(), null, "project.read", "project.manage");
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        UUID branch = tag(admin, "/api/v1/tags", "Branche Handel");
        UUID local = tag(delegate, catalog(project), "Nur hier");

        assertEquals(204, post(project.manager(), projectTags(project), Map.of("tagId", branch.toString())).status(),
            "projects.manage changes the tags of every project");
        assertEquals(List.of("Branche Handel"), tagNames(reader.get(projectTags(project))));
        assertProblem(post(reader, projectTags(project), Map.of("tagId", branch.toString())), 403, "auth.forbidden");
        assertProblem(outsider.get(projectTags(project)), 404, "project.not_found");
        assertProblem(post(delegate, projectTags(project), Map.of("tagId", local.toString())), 404, "tag.not_found");
        assertProblem(post(delegate, projectTags(project), Map.of("name", "Neu für Projekte")), 403, "auth.forbidden");
        assertEquals(204, delete(delegate, projectTags(project) + "/" + branch).status());
        assertEquals(List.of(), tagNames(project.manager().get(projectTags(project))));
        assertProblem(project.manager().get("/api/v1/projects/" + UUID.randomUUID() + "/tags"), 404, "project.not_found");
    }

    @Test
    void unicodeSpacesAndCaseFoldingYieldTheSameName() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        String token = "u" + COUNTER.incrementAndGet();
        Response first = post(admin, "/api/v1/tags", Map.of("name", "Nord Süd " + token));
        assertEquals(201, first.status(), first.body());
        for (String space : List.of("\u1680", "\u2028", "\u2029", "\u2003 \u2003")) {
            assertProblem(post(admin, "/api/v1/tags", Map.of("name", "Nord" + space + "Süd " + token)), 409, "tag.name_taken");
        }
        Response separated = post(admin, "/api/v1/tags", Map.of("name", "Ost\u2028West " + token));
        assertEquals("Ost West " + token, separated.json().get("name").asString(), "a line separator becomes one space (review F1)");

        assertEquals(201, post(admin, "/api/v1/tags", Map.of("name", "Straße " + token)).status());
        assertProblem(post(admin, "/api/v1/tags", Map.of("name", "STRASSE " + token)), 409, "tag.name_taken");
        assertEquals(List.of("Straße " + token), tagNames(admin.get("/api/v1/tags?q=strasse%20" + token)),
            "search folds the case in the same way (review F2)");
    }

    // ---------------------------------------------------------------- locks

    @Test
    void archivingWaitsForAnAssignmentThatHoldsTheTag() throws Exception {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        UUID id = tag(admin, "/api/v1/tags", "Gesperrt");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> assignment = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from tag where id = ? for share", id);
            locked.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            assertTrue(locked.await(15, TimeUnit.SECONDS));
            Future<Response> archive = POOL.submit(() -> patch(admin, "/api/v1/tags/" + id, Map.of("archived", true), "\"1\""));
            assertTrue(waitsAt("from tag where company_id"), "archiving must wait for the shared tag lock");
            assertFalse(archive.isDone());
            release.countDown();
            assertEquals(200, archive.get(30, TimeUnit.SECONDS).status());
        } finally {
            release.countDown();
            assignment.get(30, TimeUnit.SECONDS);
        }
    }

    /** Runs the statements in a transaction of its own and keeps it open until the returned action releases it. */
    private Runnable hold(String... statements) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (String statement : statements) {
                jdbc.execute(statement);
            }
            locked.countDown();
            try {
                assertTrue(release.await(60, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(locked.await(15, TimeUnit.SECONDS));
        return () -> {
            release.countDown();
            try {
                holder.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Test
    void aLockedTagOfAnotherCatalogAnswersLikeAnUnknownOneWithoutWaiting() throws Exception {
        Person worker = person();
        Person stranger = person();
        Project project = project(worker);
        Project foreign = project(stranger);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tags.manage");
        grant(stranger.id(), foreign.id(), "ticket.read", "tag.create");
        UUID secret = tag(stranger, catalog(foreign), "Geheimprojekt");
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        UUID company = tag(admin, "/api/v1/tags", "Gemeinsam " + COUNTER.incrementAndGet());
        long number = ticket(worker, project, "Fremder Tag");

        Runnable release = hold("select id from tag where id = '" + secret + "' for update",
            "select id from tag where id = '" + company + "' for share");
        try {
            long start = System.nanoTime();
            assertProblem(post(worker, ticketTags(project, number), Map.of("tagId", secret.toString())), 404, "tag.not_found");
            assertProblem(patch(worker, catalog(project) + "/" + company, Map.of("archived", true), "\"1\""), 404, "tag.not_found");
            assertProblem(patch(worker, catalog(project) + "/" + secret, Map.of("archived", true), "\"1\""), 404, "tag.not_found");
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000,
                "tags of other catalogs are neither locked nor waited for (review F3)");
        } finally {
            release.run();
        }
    }

    @Test
    void anAssignmentThatWaitedForAFieldChangeCarriesALaterTime() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        long number = ticket(worker, project, "Reihenfolge");
        UUID tag = tag(worker, catalog(project), "Später");
        UUID ticket = jdbc.queryForObject("select id from ticket where project_id = ? and number = ?", UUID.class, project.id(), number);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // A field change holds the ticket row, then writes its entry with the time after its lock (TASK-042 review, F1).
        Future<?> change = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from ticket where id = ? for update", ticket);
            locked.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            jdbc.update("update ticket set version = version + 1, title = 'Neu' where id = ?", ticket);
            jdbc.update("""
                    insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value)
                    select id, version, clock_timestamp(), ?, 'title', 'Reihenfolge', 'Neu' from ticket where id = ?
                    """, worker.id(), ticket);
        }));
        assertTrue(locked.await(15, TimeUnit.SECONDS));
        Future<Response> assign = POOL.submit(() -> post(worker, ticketTags(project, number), Map.of("tagId", tag.toString())));
        assertTrue(waitsAt("select version from ticket"), "the assignment waits at the ticket row");
        Thread.sleep(300);
        release.countDown();
        change.get(30, TimeUnit.SECONDS);
        assertEquals(204, assign.get(30, TimeUnit.SECONDS).status());
        var entries = jdbc.queryForList("select version, field, occurred_at from ticket_change where ticket_id = ? order by id", ticket);
        var field = entries.stream().filter(row -> "title".equals(row.get("field")) && ((Number) row.get("version")).longValue() == 2)
            .findFirst().orElseThrow();
        var tagged = entries.getLast();
        assertEquals("tags", tagged.get("field"));
        assertEquals(2L, ((Number) tagged.get("version")).longValue());
        assertFalse(((java.sql.Timestamp) tagged.get("occurred_at")).before((java.sql.Timestamp) field.get("occurred_at")),
            "the later entry must not carry an earlier time");
        assertFalse(jdbc.queryForObject("select assigned_at from tag_ticket where ticket_id = ? and tag_id = ?", java.sql.Timestamp.class, ticket, tag)
            .before((java.sql.Timestamp) field.get("occurred_at")));
    }

    private boolean waitsAt(String statementPart) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from pg_stat_activity
                    where datname = current_database() and pid <> pg_backend_pid()
                    and query ilike ? and cardinality(pg_blocking_pids(pid)) > 0)
                    """, Boolean.class, "%" + statementPart + "%"))) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    // ---------------------------------------------------------------- catalog care (TASK-072)

    private static String merge(String tagPath) {
        return tagPath + "/merge";
    }

    private static Response mergeInto(Person person, String tagPath, UUID target, String version) {
        return person.browser().send("POST", merge(tagPath), Map.of("into", target.toString()), version);
    }

    private static List<String> ids(Response response) {
        assertEquals(200, response.status(), response.body());
        return StreamSupport.stream(response.json().spliterator(), false).map(tag -> tag.get("id").asString()).toList();
    }

    @Test
    void descriptionAndColorAreSetWithTagsManageAndChecked() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person reader = person();
        UUID id = tag(admin, "/api/v1/tags", "Farbe " + COUNTER.incrementAndGet());
        String path = "/api/v1/tags/" + id;
        assertTrue(admin.get(path).json().get("manageable").asBoolean());
        assertFalse(reader.get(path).json().get("manageable").asBoolean());
        assertTrue(reader.get(path).json().get("description").isNull());
        assertTrue(reader.get(path).json().get("color").isNull());

        assertProblem(patch(reader, path, Map.of("color", "teal"), "\"1\""), 403, "auth.forbidden");
        Response invalidColor = patch(admin, path, Map.of("color", "#00ff00"), "\"1\"");
        assertProblem(invalidColor, 400, "request.invalid");
        assertEquals(List.of("color.invalid"), invalidColor.violations());
        Response tooLong = patch(admin, path, Map.of("description", "x".repeat(501)), "\"1\"");
        assertProblem(tooLong, 400, "request.invalid");
        assertEquals(List.of("description.invalid"), tooLong.violations());
        assertEquals(List.of("description.invalid"), patch(admin, path, Map.of("description", "Zeile\u0007"), "\"1\"").violations());
        assertEquals(List.of("color.invalid"), patch(admin, path, Map.of("color", 3), "\"1\"").violations());

        Response changed = patch(admin, path, Map.of("description", "  Kunden aus dem Norden\r\nund Osten  ", "color", "teal"), "\"1\"");
        assertEquals(200, changed.status(), changed.body());
        assertEquals("\"2\"", changed.header("ETag"));
        assertEquals("Kunden aus dem Norden\nund Osten", changed.json().get("description").asString());
        assertEquals("teal", changed.json().get("color").asString());
        assertEquals("teal", reader.get(path).json().get("color").asString(), "who sees the tag sees its color");
        assertEquals("\"2\"", patch(admin, path, Map.of("color", "teal"), "\"2\"").header("ETag"), "the same color changes nothing");
        assertEquals(500, patch(admin, path, Map.of("description", "ä".repeat(500)), "\"2\"").json().get("description").asString().length());

        Map<String, Object> clear = new HashMap<>();
        clear.put("description", null);
        clear.put("color", null);
        Response cleared = patch(admin, path, clear, "\"3\"");
        assertEquals(200, cleared.status(), cleared.body());
        assertTrue(cleared.json().get("description").isNull());
        assertTrue(cleared.json().get("color").isNull());
        assertTrue(patch(admin, path, Map.of("description", "   "), "\"4\"").json().get("description").isNull(), "a blank description is no description");
        assertEquals(List.of("tag.created", "tag.described", "tag.colored", "tag.described", "tag.described", "tag.colored"), audit(id));
    }

    @Test
    void mergingMovesEveryAssignmentOnceFreesTheNameAndKeepsHistoryAndVersions() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person member = person(AdministrationRight.TAGS_MANAGE);
        Project project = project(member);
        grant(member.id(), project.id(), TICKETS);
        grant(member.id(), project.id(), "project.read");
        String token = "m" + COUNTER.incrementAndGet();
        UUID source = tag(admin, "/api/v1/tags", "Quelle " + token);
        UUID target = tag(admin, "/api/v1/tags", "Ziel " + token);
        long both = ticket(member, project, "Beide");
        long only = ticket(member, project, "Nur die erste");
        for (long number : List.of(both, only)) {
            assertEquals(204, post(member, ticketTags(project, number), Map.of("tagId", source.toString())).status());
        }
        assertEquals(204, post(member, ticketTags(project, both), Map.of("tagId", target.toString())).status());
        assertEquals(204, post(project.manager(), projectTags(project), Map.of("tagId", source.toString())).status());
        String history = "/api/v1/projects/" + project.id() + "/tickets/" + both + "/history";
        int entries = member.get(history).json().size();

        Response preview = member.get(merge("/api/v1/tags/" + source) + "?into=" + target);
        assertEquals(200, preview.status(), preview.body());
        assertEquals(2, preview.json().get("usage").get("tickets").asLong());
        assertEquals(1, preview.json().get("usage").get("projects").asLong());
        assertEquals("Ziel " + token, preview.json().get("target").get("name").asString());
        Response outsider = admin.get(merge("/api/v1/tags/" + source) + "?into=" + target);
        assertEquals(0, outsider.json().get("usage").get("tickets").asLong(), "tickets the caller cannot read are not counted");
        assertEquals(0, outsider.json().get("usage").get("projects").asLong());
        Response usage = member.get("/api/v1/tags?usage=true&q=" + token);
        assertEquals(2, usage.json().get(0).get("usage").get("tickets").asLong(), usage.body());
        assertTrue(member.get("/api/v1/tags?q=" + token).json().get(0).get("usage").isNull(), "usage only when asked for");

        Response merged = mergeInto(admin, "/api/v1/tags/" + source, target, "\"1\"");
        assertEquals(200, merged.status(), merged.body());
        assertEquals(target.toString(), merged.json().get("id").asString());
        for (long number : List.of(both, only)) {
            assertEquals(List.of(target.toString()), ids(member.get(ticketTags(project, number))), "one assignment, no duplicate");
        }
        assertEquals(List.of(target.toString()), ids(member.get(projectTags(project))));
        assertEquals(entries, member.get(history).json().size(), "merging writes no ticket history");
        assertEquals("\"1\"", member.get("/api/v1/projects/" + project.id() + "/tickets/" + both).header("ETag"), "ticket versions stay");

        Response gone = admin.get("/api/v1/tags/" + source);
        assertEquals(200, gone.status(), gone.body());
        assertEquals(target.toString(), gone.json().get("mergedInto").get("id").asString());
        assertEquals("Ziel " + token, gone.json().get("mergedInto").get("name").asString());
        assertEquals(List.of("Ziel " + token), tagNames(admin.get("/api/v1/tags?archived=true&q=" + token)), "the source left the catalog");
        assertProblem(patch(admin, "/api/v1/tags/" + source, Map.of("name", "Wieder"), "\"2\""), 409, "tag.merged");
        assertProblem(post(member, ticketTags(project, only), Map.of("tagId", source.toString())), 409, "tag.merged");
        assertEquals(201, post(admin, "/api/v1/tags", Map.of("name", "Quelle " + token)).status(), "the name is free again");
        assertEquals("tag.merged", audit(source).getLast());
        assertEquals("tag.merged_in", audit(target).getLast());

        Response found = member.get("/api/v1/search/tickets?tag=" + target);
        assertEquals(200, found.status(), found.body());
        assertEquals(2, found.json().get("total").asLong(), found.body());
        assertProblem(member.get("/api/v1/search/tickets?tag=" + source), 400, "request.invalid");
        assertEquals(0, member.get("/api/v1/search/tickets?q=quelle%20" + token).json().get("total").asLong(),
            "the old name no longer finds the tickets");
        assertEquals(2, member.get("/api/v1/search/tickets?q=ziel%20" + token).json().get("total").asLong());
    }

    @Test
    void mergingStaysInsideOneCatalogAndChecksItsTags() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person creator = person();
        grant(creator.id(), null, "tag.create");
        Person projectAdmin = person();
        Person member = person();
        Project project = project(projectAdmin, member);
        grant(projectAdmin.id(), project.id(), "tags.manage");
        grant(member.id(), project.id(), "ticket.read");
        String token = "r" + COUNTER.incrementAndGet();
        UUID a = tag(admin, "/api/v1/tags", "A " + token);
        UUID b = tag(admin, "/api/v1/tags", "B " + token);
        UUID own = tag(projectAdmin, catalog(project), "Eigen " + token);
        UUID other = tag(projectAdmin, catalog(project), "Anders " + token);
        String company = "/api/v1/tags/";

        assertProblem(mergeInto(creator, company + a, b, "\"1\""), 403, "auth.forbidden");
        assertProblem(creator.get(merge(company + a) + "?into=" + b), 403, "auth.forbidden");
        assertProblem(mergeInto(admin, company + a, a, "\"1\""), 400, "request.invalid");
        assertProblem(mergeInto(admin, company + a, own, "\"1\""), 404, "tag.not_found");
        assertProblem(mergeInto(projectAdmin, catalog(project) + "/" + own, a, "\"1\""), 404, "tag.not_found");
        assertProblem(admin.browser().send("POST", merge(company + a), Map.of("into", "x"), "\"1\""), 400, "request.invalid");
        assertProblem(admin.browser().send("POST", merge(company + a), Map.of("into", b.toString()), null), 428, "tag.precondition_required");
        assertProblem(mergeInto(admin, company + a, b, "\"9\""), 412, "tag.version_conflict");
        assertProblem(mergeInto(projectAdmin, company + a, b, "\"1\""), 403, "auth.forbidden");
        assertProblem(mergeInto(member, catalog(project) + "/" + own, other, "\"1\""), 403, "auth.forbidden");

        assertEquals(200, patch(admin, company + b, Map.of("archived", true), "\"1\"").status());
        assertProblem(mergeInto(admin, company + a, b, "\"1\""), 409, "tag.archived");
        assertEquals(200, patch(admin, company + b, Map.of("archived", false), "\"2\"").status());
        assertEquals(200, patch(admin, company + a, Map.of("archived", true), "\"1\"").status());
        assertEquals(200, mergeInto(admin, company + a, b, "\"2\"").status(), "an archived source may be merged");
        assertProblem(mergeInto(admin, company + b, a, "\"3\""), 409, "tag.merged");

        Response inProject = mergeInto(projectAdmin, catalog(project) + "/" + own, other, "\"1\"");
        assertEquals(200, inProject.status(), inProject.body());
        assertEquals(List.of("Anders " + token), tagNames(member.get(catalog(project) + "?q=" + token)));
    }

    @Test
    void aSecondMergeOfTheSamePairWaitsAndThenSeesTheFirst() throws Exception {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        String token = "w" + COUNTER.incrementAndGet();
        UUID a = tag(admin, "/api/v1/tags", "Eins " + token);
        UUID b = tag(admin, "/api/v1/tags", "Zwei " + token);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // An assignment in progress holds both tags shared; both merges wait for it.
        Future<?> holder = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from tag where id in (?, ?) for share", a, b);
            held.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(held.await(10, TimeUnit.SECONDS));
        Future<Response> first = POOL.submit(() -> mergeInto(admin, "/api/v1/tags/" + a, b, "\"1\""));
        Future<Response> second = POOL.submit(() -> mergeInto(admin, "/api/v1/tags/" + b, a, "\"1\""));
        Thread.sleep(500);
        assertFalse(first.isDone() || second.isDone(), "both merges wait for the assignment");
        release.countDown();
        holder.get(20, TimeUnit.SECONDS);
        List<Integer> statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS).status(), second.get(20, TimeUnit.SECONDS).status()));
        statuses.sort(null);
        assertEquals(List.of(200, 409), statuses, "one merge wins, the other sees a merged tag; no deadlock");
        assertEquals(1, jdbc.queryForObject("select count(*) from tag where id in (?, ?) and merged_into is null", Integer.class, a, b));
    }

    @Test
    void suggestionsPutTheBestMatchesFirstAndLeaveOutArchivedMergedAndHiddenTags() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person member = person();
        Person stranger = person();
        Project project = project(member);
        Person foreigner = person();
        Project foreign = project(foreigner);
        grant(foreigner.id(), foreign.id(), "tag.create");
        grant(member.id(), project.id(), "ticket.read", "tag.create");
        String token = "s" + COUNTER.incrementAndGet();
        tag(admin, "/api/v1/tags", "Zz " + token + "anfang");
        tag(admin, "/api/v1/tags", token + " Start");
        tag(member, catalog(project), "Mitte" + token + "x");
        UUID archived = tag(admin, "/api/v1/tags", token + " Archiv");
        assertEquals(200, patch(admin, "/api/v1/tags/" + archived, Map.of("archived", true), "\"1\"").status());
        tag(foreigner, catalog(foreign), token + " Fremd");

        Response found = member.get("/api/v1/projects/" + project.id() + "/catalog/suggestions?q=" + token);
        assertEquals(200, found.status(), found.body());
        List<String> names = StreamSupport.stream(found.json().get("items").spliterator(), false).map(tag -> tag.get("name").asString()).toList();
        assertEquals(List.of(token + " Start", "Zz " + token + "anfang", "Mitte" + token + "x"), names,
            "start of the name, start of a word, then anywhere; no archived and no foreign tags");
        assertTrue(found.json().get("createProject").asBoolean());
        assertFalse(found.json().get("createCompany").asBoolean());
        Response company = member.get("/api/v1/tags/suggestions?q=" + token);
        assertEquals(2, company.json().get("items").size(), company.body());
        assertFalse(company.json().get("createCompany").asBoolean());
        assertTrue(admin.get("/api/v1/tags/suggestions?q=" + token).json().get("createCompany").asBoolean());
        assertProblem(stranger.get("/api/v1/projects/" + project.id() + "/catalog/suggestions?q=" + token), 404, "project.not_found");
        assertProblem(member.get("/api/v1/tags/suggestions?limit=21"), 400, "request.invalid");
        assertEquals(1, member.get("/api/v1/projects/" + project.id() + "/catalog/suggestions?limit=1&q=" + token).json().get("items").size());
        assertEquals(0, member.get("/api/v1/tags/suggestions?q=" + token + "%25").json().get("items").size(),
            "a percent sign is a character, not a wildcard");
    }

    @Test
    void theLookupNamesArchivedAndMergedTagsOfVisibleCatalogsOnly() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person member = person();
        Person stranger = person();
        Project project = project(member);
        Person foreigner = person();
        Project foreign = project(foreigner);
        grant(foreigner.id(), foreign.id(), "tag.create");
        grant(member.id(), project.id(), "ticket.read", "tags.manage");
        String token = "l" + COUNTER.incrementAndGet();
        UUID shared = tag(admin, "/api/v1/tags", "Geteilt " + token);
        UUID own = tag(member, catalog(project), "Eigen " + token);
        UUID kept = tag(member, catalog(project), "Bleibt " + token);
        UUID hidden = tag(foreigner, catalog(foreign), "Fremd " + token);
        assertEquals(200, mergeInto(member, catalog(project) + "/" + own, kept, "\"1\"").status());
        String lookup = "/api/v1/projects/" + project.id() + "/catalog/lookup?id=" + shared + "&id=" + own + "&id=" + hidden;

        Response found = member.get(lookup);
        assertEquals(200, found.status(), found.body());
        assertEquals(List.of(own.toString(), shared.toString()), ids(found), "the foreign tag is left out without a hint");
        assertEquals(kept.toString(), found.json().get(0).get("mergedInto").get("id").asString());
        assertTrue(found.json().get(0).get("manageable").asBoolean());
        assertProblem(stranger.get(lookup), 404, "project.not_found");
        assertProblem(member.get("/api/v1/projects/" + project.id() + "/catalog/lookup"), 400, "request.invalid");
        assertProblem(member.get("/api/v1/projects/" + project.id() + "/catalog/lookup?id=nein"), 400, "request.invalid");
    }

    @Test
    void aTicketAndAProjectHoldAtMostThirtyTags() throws Exception {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        long number = ticket(worker, project, "Viele Tags");
        String token = "t" + COUNTER.incrementAndGet();
        for (int i = 0; i < 29; i++) {
            assertEquals(204, post(worker, ticketTags(project, number), Map.of("name", token + "-" + i)).status());
        }
        // Two assignments race for the last place: exactly one gets it.
        Future<Response> one = POOL.submit(() -> post(worker, ticketTags(project, number), Map.of("name", token + "-a")));
        Future<Response> two = POOL.submit(() -> post(worker, ticketTags(project, number), Map.of("name", token + "-b")));
        List<Integer> statuses = new ArrayList<>(List.of(one.get(20, TimeUnit.SECONDS).status(), two.get(20, TimeUnit.SECONDS).status()));
        statuses.sort(null);
        assertEquals(List.of(204, 409), statuses);
        assertEquals(30, tagCount(worker, project, number));
        assertProblem(post(worker, ticketTags(project, number), Map.of("name", token + "-c")), 409, "tag.limit_reached");
        assertTrue(tagNames(worker.get(catalog(project) + "?q=" + token + "-c")).isEmpty(), "the refused name was not created");
        String present = worker.get(ticketTags(project, number)).json().get(0).get("id").asString();
        assertEquals(204, post(worker, ticketTags(project, number), Map.of("tagId", present)).status(), "an existing assignment stays possible");

        for (int i = 0; i < 30; i++) {
            UUID id = tag(admin, "/api/v1/tags", token + "-p" + i);
            assertEquals(204, post(project.manager(), projectTags(project), Map.of("tagId", id.toString())).status());
        }
        UUID extra = tag(admin, "/api/v1/tags", token + "-p30");
        assertProblem(post(project.manager(), projectTags(project), Map.of("tagId", extra.toString())), 409, "tag.limit_reached");
    }

    // ---------------------------------------------------------------- bulk change (TASK-073)

    private static Response bulk(Person person, String action, List<UUID> tags, List<UUID> tickets) {
        return post(person, "/api/v1/tags/bulk", Map.of("action", action, "tags", tags.stream().map(UUID::toString).toList(),
            "tickets", tickets.stream().map(UUID::toString).toList()));
    }

    private UUID ticketId(Project project, long number) {
        return jdbc.queryForObject("select id from ticket where project_id = ? and number = ?", UUID.class, project.id(), number);
    }

    private static void assertCounts(Response response, int changed, int unchanged, int notMatching, int refused) {
        assertEquals(200, response.status(), response.body());
        JsonNode json = response.json();
        assertEquals(List.of(changed, unchanged, notMatching, refused), List.of(json.get("changed").asInt(), json.get("unchanged").asInt(),
            json.get("notMatching").asInt(), json.get("refused").asInt()), response.body());
    }

    private static JsonNode item(Response response, UUID ticket) {
        for (JsonNode item : response.json().get("items")) {
            if (item.get("ticketId").asString().equals(ticket.toString())) {
                return item;
            }
        }
        throw new AssertionError("no item for " + ticket + ": " + response.body());
    }

    @Test
    void aBulkChangeChecksEveryTicketForItselfAndNamesOnlyReadableTickets() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person worker = person();
        Person author = person();
        Project first = project(worker, author);
        Project second = project(worker, author);
        Project readOnly = project(worker, author);
        Project closed = project(author);
        for (Project project : List.of(first, second, readOnly, closed)) {
            grant(author.id(), project.id(), TICKETS);
        }
        grant(worker.id(), first.id(), TICKETS);
        grant(worker.id(), first.id(), "tag.create");
        grant(worker.id(), second.id(), TICKETS);
        grant(worker.id(), readOnly.id(), "ticket.read", "READ:*", "READ:tags");
        UUID company = tag(admin, "/api/v1/tags", "Sammel " + COUNTER.incrementAndGet());
        UUID local = tag(worker, catalog(first), "Nur hier");
        long a = ticket(author, first, "Eins");
        long b = ticket(author, first, "Zwei");
        long c = ticket(author, second, "Drei");
        long d = ticket(author, readOnly, "Vier");
        long e = ticket(author, closed, "Fünf");
        List<UUID> all = List.of(ticketId(first, a), ticketId(first, b), ticketId(second, c), ticketId(readOnly, d), ticketId(closed, e));

        Response added = bulk(worker, "add", List.of(company, local), all);
        assertCounts(added, 3, 0, 0, 2);
        assertEquals("changed", item(added, all.get(2)).get("outcome").asString(), "the company tag fits the other project");
        assertEquals(List.of(company.toString()), StreamSupport.stream(worker.get(ticketTags(second, c)).json().spliterator(), false)
            .map(tag -> tag.get("id").asString()).toList(), "the project tag stays in its project");
        JsonNode refused = item(added, all.get(3));
        assertEquals("refused", refused.get("outcome").asString());
        assertEquals(readOnly.key() + "-" + d, refused.get("key").asString());
        assertEquals("auth.forbidden", refused.get("code").asString(), "a readable ticket is named with the reason");
        JsonNode hidden = item(added, all.get(4));
        assertEquals("refused", hidden.get("outcome").asString());
        assertTrue(hidden.get("key").isNull() && hidden.get("code").isNull(), "an unreadable ticket is neither named nor explained");
        assertEquals(first.key() + "-" + a, item(added, all.get(0)).get("key").asString());
        assertEquals(2, tagCount(worker, first, a));
        assertEquals("\"1\"", worker.get("/api/v1/projects/" + first.id() + "/tickets/" + a).header("ETag"), "the version stays");
        assertEquals(2L, jdbc.queryForObject("select count(*) from ticket_change where ticket_id = ? and field = 'tags'", Long.class, all.get(0)));

        assertCounts(bulk(worker, "add", List.of(company, local), all.subList(0, 3)), 0, 3, 0, 0);
        assertEquals(2L, jdbc.queryForObject("select count(*) from tag_ticket where ticket_id = ?", Long.class, all.get(0)),
            "repeating adds nothing twice");
        assertCounts(bulk(worker, "add", List.of(local), List.of(all.get(2))), 0, 0, 1, 0);
        assertEquals("not_matching", item(bulk(worker, "remove", List.of(local), List.of(all.get(2))), all.get(2)).get("outcome").asString());

        Response removed = bulk(worker, "remove", List.of(company), all);
        assertCounts(removed, 3, 0, 0, 2);
        assertEquals(1, tagCount(worker, first, a));
        assertCounts(bulk(worker, "remove", List.of(company), all.subList(0, 3)), 0, 3, 0, 0);

        // The tags of a bulk change are checked before any ticket.
        assertEquals(200, patch(admin, "/api/v1/tags/" + company, Map.of("archived", true), "\"1\"").status());
        assertProblem(bulk(worker, "add", List.of(company), all), 409, "tag.archived");
        assertCounts(bulk(worker, "remove", List.of(company), all.subList(0, 1)), 0, 1, 0, 0);
        grant(author.id(), closed.id(), "tag.create");
        UUID foreign = tag(author, catalog(closed), "Fremd " + COUNTER.incrementAndGet());
        assertProblem(bulk(worker, "add", List.of(foreign), all), 404, "tag.not_found");
        assertProblem(bulk(worker, "add", List.of(UUID.randomUUID()), all), 404, "tag.not_found");
        UUID source = tag(admin, "/api/v1/tags", "Quelle " + COUNTER.incrementAndGet());
        UUID target = tag(admin, "/api/v1/tags", "Ziel " + COUNTER.incrementAndGet());
        assertEquals(200, mergeInto(admin, "/api/v1/tags/" + source, target, "\"1\"").status());
        assertProblem(bulk(worker, "remove", List.of(source), all), 409, "tag.merged");
        assertEquals(List.of("tag.created", "tag.assigned", "tag.assigned", "tag.assigned", "tag.unassigned", "tag.unassigned",
            "tag.unassigned", "tag.archived"), audit(company));
    }

    @Test
    void aBulkChangeChecksItsBodyAndLimits() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person worker = person();
        UUID company = tag(admin, "/api/v1/tags", "Grenze " + COUNTER.incrementAndGet());
        List<String> ticket = List.of(UUID.randomUUID().toString());
        assertEquals(List.of("action.invalid"), post(worker, "/api/v1/tags/bulk",
            Map.of("action", "replace", "tags", List.of(company.toString()), "tickets", ticket)).violations());
        assertEquals(List.of("tags.invalid"), post(worker, "/api/v1/tags/bulk", Map.of("action", "add", "tags", List.of(), "tickets", ticket)).violations());
        List<String> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> UUID.randomUUID().toString()).toList();
        assertEquals(List.of("tags.invalid"), post(worker, "/api/v1/tags/bulk", Map.of("action", "add", "tags", eleven, "tickets", ticket)).violations());
        List<String> many = java.util.stream.IntStream.range(0, 501).mapToObj(i -> UUID.randomUUID().toString()).toList();
        assertEquals(List.of("tickets.invalid"), post(worker, "/api/v1/tags/bulk",
            Map.of("action", "add", "tags", List.of(company.toString()), "tickets", many)).violations());
        assertEquals(List.of("tickets.invalid"), post(worker, "/api/v1/tags/bulk",
            Map.of("action", "add", "tags", List.of(company.toString()), "tickets", List.of("kein"))).violations());
        assertEquals(List.of("fields.unknown"), post(worker, "/api/v1/tags/bulk",
            Map.of("action", "add", "tags", List.of(company.toString()), "tickets", ticket, "name", "x")).violations());
        Response unknown = post(worker, "/api/v1/tags/bulk", Map.of("action", "add", "tags", List.of(company.toString(), company.toString()),
            "tickets", List.of(ticket.get(0), ticket.get(0))));
        assertCounts(unknown, 0, 0, 0, 1);
        assertEquals(1, unknown.json().get("items").size(), "a repeated ticket counts once");
        assertTrue(item(unknown, UUID.fromString(ticket.get(0))).get("key").isNull());
    }

    @Test
    void aBulkChangeKeepsTheLimitPerTicketWhole() {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), project.id(), "tag.create");
        long full = ticket(worker, project, "Fast voll");
        long empty = ticket(worker, project, "Leer");
        String token = "m" + COUNTER.incrementAndGet();
        for (int i = 0; i < 29; i++) {
            assertEquals(204, post(worker, ticketTags(project, full), Map.of("name", token + "-" + i)).status());
        }
        UUID one = tag(admin, "/api/v1/tags", token + "-x");
        UUID two = tag(admin, "/api/v1/tags", token + "-y");
        Response result = bulk(worker, "add", List.of(one, two), List.of(ticketId(project, full), ticketId(project, empty)));
        assertCounts(result, 1, 0, 0, 1);
        assertEquals("tag.limit_reached", item(result, ticketId(project, full)).get("code").asString());
        assertEquals(29, tagCount(worker, project, full), "a refused ticket keeps no part of the change");
        assertEquals(2, tagCount(worker, project, empty));
    }

    @Test
    void aBulkChangeWaitsForASingleChangeOfATicketAndThenAppliesIt() throws Exception {
        Person admin = person(AdministrationRight.TAGS_MANAGE);
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        long first = ticket(worker, project, "Gleichzeitig");
        long second = ticket(worker, project, "Daneben");
        UUID company = tag(admin, "/api/v1/tags", "Parallel " + COUNTER.incrementAndGet());
        UUID held = ticketId(project, second);
        // A change of the second ticket holds its row; the bulk change finishes the first ticket and waits there.
        Runnable release = hold("select id from ticket where id = '" + held + "' for update");
        Future<Response> running;
        try {
            running = POOL.submit(() -> bulk(worker, "add", List.of(company), List.of(ticketId(project, first), held)));
            assertTrue(waitsAt("from ticket"), "the bulk change waits for the ticket row");
            assertEquals(1, tagCount(worker, project, first), "the first ticket is already committed");
            assertFalse(running.isDone());
        } finally {
            release.run();
        }
        assertCounts(running.get(30, TimeUnit.SECONDS), 2, 0, 0, 0);
        assertEquals(1, tagCount(worker, project, second));
    }

    private static int tagCount(Person person, Project project, long number) {
        return person.get(ticketTags(project, number)).json().size();
    }
}
