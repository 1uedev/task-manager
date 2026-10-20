package taskmanager.tickets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * TASK-027: tickets through the real HTTP interface, the real permissions and projects modules and a real PostgreSQL.
 * Ticket actions and field rules are granted directly in the tables, as no rights endpoint exists yet (TASK-030).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketsApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final List<String> ALL_FIELDS = ProjectConfiguration.FIELD_KEYS;
    private static final String[] FULL = {"ticket.read", "ticket.create", "ticket.edit", "READ:*", "WRITE:*"};

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
        var issued = accounts.create("test", "tickets-" + COUNTER.incrementAndGet(), null);
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
     * for the resource type {@code ticket}; {@code *} stands for all five fields.
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
        String key = "T" + COUNTER.incrementAndGet();
        Response created = manager.browser().send("POST", "/api/v1/projects", Map.of("key", key, "name", "Tickets " + key), null);
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

    private static String tickets(Project project) {
        return "/api/v1/projects/" + project.id() + "/tickets";
    }

    private static Response create(Person person, Project project, Map<String, Object> body) {
        return person.browser().send("POST", tickets(project), body, null);
    }

    private static Response create(Person person, Project project, String title) {
        return create(person, project, Map.of("type", "task", "title", title));
    }

    private static Response patch(Person person, Project project, long number, Object body, String ifMatch) {
        return person.browser().send("PATCH", tickets(project) + "/" + number, body, ifMatch);
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

    private static List<String> names(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }

    private static List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
    }

    // ---------------------------------------------------------------- creating, reading, listing

    @Test
    void aMemberWithTicketRightsCreatesNumberedTicketsInTheInitialStatusAndReadsThemBack() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), FULL);
        assertProblem(anonymous().get(tickets(project)), 401, "auth.required");

        Response first = create(worker, project, map("type", "bug", "title", "  Absturz beim Speichern ", "description", "Schritte folgen"));
        assertEquals(201, first.status(), first.body());
        assertEquals("\"1\"", first.header("ETag"));
        assertEquals(project.key() + "-1", first.json().get("key").asString());
        assertEquals(1, first.json().get("number").asLong());
        assertEquals("Absturz beim Speichern", first.fields().get("title").asString());
        assertEquals("new", first.fields().get("status").asString(), "a new ticket starts in the initial status");
        assertTrue(first.fields().get("assignee").isNull());
        assertEquals(ALL_FIELDS, names(first.fields()));
        assertEquals(ALL_FIELDS, strings(first.json().get("writableFields")));
        assertEquals(1, first.json().get("configurationVersion").asLong());
        assertEquals(worker.id().toString(), first.json().get("createdBy").asString());

        Response second = create(worker, project, "Zweites Ticket");
        assertEquals(2, second.json().get("number").asLong());
        Response read = worker.get(tickets(project) + "/1");
        assertEquals(200, read.status(), read.body());
        assertEquals("Schritte folgen", read.fields().get("description").asString());

        Response list = worker.get(tickets(project));
        assertEquals(List.of(2L, 1L), StreamSupport.stream(list.json().spliterator(), false).map(n -> n.get("number").asLong()).toList());
        Response page = worker.get(tickets(project) + "?limit=1&before=2");
        assertEquals(1, page.json().size());
        assertEquals(1, page.json().get(0).get("number").asLong());
        assertProblem(worker.get(tickets(project) + "?limit=0"), 400, "request.invalid");

        Response history = worker.get(tickets(project) + "/1/history");
        assertEquals(1, history.json().size(), history.body());
        assertEquals(1, history.json().get(0).get("version").asLong());
        assertEquals(List.of("title", "description", "type", "status"),
            StreamSupport.stream(history.json().get(0).get("changes").spliterator(), false).map(c -> c.get("field").asString()).toList(),
            "creation records every value that is set");
    }

    @Test
    void numbersStayUniqueWhenTicketsAreCreatedAtTheSameTime() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), FULL);
        List<Future<Response>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int n = i;
            calls.add(POOL.submit(() -> create(worker, project, "Gleichzeitig " + n)));
        }
        List<Long> numbers = new ArrayList<>();
        for (Future<Response> call : calls) {
            Response response = call.get(30, TimeUnit.SECONDS);
            assertEquals(201, response.status(), response.body());
            numbers.add(response.json().get("number").asLong());
        }
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), numbers.stream().sorted().toList());
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void ticketsNeedTheMembershipAndATicketActionAndStayInsideTheirProject() {
        Person member = person();
        Person outsider = person();
        Project project = project(member);
        Project other = project(member);
        grant(member.id(), project.id(), FULL);
        grant(outsider.id(), null, FULL);
        assertEquals(201, create(member, project, "Intern").status());

        assertProblem(outsider.get(tickets(project)), 404, "project.not_found");
        assertProblem(outsider.get(tickets(project) + "/1"), 404, "project.not_found");
        assertProblem(create(outsider, project, "Fremd"), 404, "project.not_found");
        assertProblem(member.get(tickets(other)), 404, "project.not_found");
        assertProblem(create(member, other, "Falsches Projekt"), 404, "project.not_found");
        assertProblem(project.manager().get(tickets(project)), 404, "project.not_found");
        assertProblem(member.get(tickets(project) + "/99"), 404, "ticket.not_found");
        assertProblem(member.get("/api/v1/projects/" + UUID.randomUUID() + "/tickets"), 404, "project.not_found");

        Person reader = person();
        Project shared = project(member, reader);
        grant(member.id(), shared.id(), FULL);
        grant(reader.id(), shared.id(), "ticket.read", "READ:*");
        assertEquals(201, create(member, shared, "Lesbar").status());
        Response read = reader.get(tickets(shared) + "/1");
        assertEquals(200, read.status(), read.body());
        assertEquals(List.of(), strings(read.json().get("writableFields")));
        assertProblem(create(reader, shared, "Darf nicht"), 403, "auth.forbidden");
        assertProblem(patch(reader, shared, 1, map("title", "Darf nicht"), "\"1\""), 403, "auth.forbidden");
    }

    @Test
    void aHiddenFieldIsLeftOutOfAnswersAndHistoryAndCannotBeWritten() {
        Person writer = person();
        Person restricted = person();
        Project project = project(writer, restricted);
        grant(writer.id(), project.id(), FULL);
        grant(restricted.id(), project.id(), FULL);
        grant(restricted.id(), project.id(), "DENY-READ:description", "DENY-WRITE:status");
        assertEquals(201, create(writer, project, map("type", "task", "title", "Geheim", "description", "vertraulich")).status());
        assertEquals(200, patch(writer, project, 1, map("description", "noch vertraulicher"), "\"1\"").status());

        Response read = restricted.get(tickets(project) + "/1");
        assertEquals(List.of("title", "type", "status", "assignee"), names(read.fields()), "a deny wins over the allow of another group");
        assertFalse(read.body().contains("vertraulich"));
        assertEquals(List.of("title", "type", "assignee"), strings(read.json().get("writableFields")));
        Response history = restricted.get(tickets(project) + "/1/history");
        assertFalse(history.body().contains("vertraulich"), history.body());
        assertEquals(1, history.json().size(), "a version that changed only hidden fields is left out");
        Response list = restricted.get(tickets(project));
        assertFalse(list.body().contains("vertraulich"));

        assertProblem(patch(restricted, project, 1, map("description", "überschrieben"), "\"2\""), 403, "auth.forbidden");
        assertProblem(patch(restricted, project, 1, map("status", "in_progress", "title", "x"), "\"2\""), 403, "auth.forbidden");
        assertEquals("Geheim", writer.get(tickets(project) + "/1").fields().get("title").asString(), "a refused change writes nothing");
        assertProblem(create(restricted, project, map("type", "task", "title", "Mit Text", "description", "x")), 403, "auth.forbidden");
        assertEquals(201, create(restricted, project, "Ohne Text").status(), "without the hidden field the creation works");
    }

    // ---------------------------------------------------------------- the configuration

    @Test
    void typeStatusTransitionsAndAssigneeFollowTheProjectConfiguration() {
        Person worker = person();
        Person colleague = person();
        Person stranger = person();
        Project project = project(worker, colleague);
        grant(worker.id(), project.id(), FULL);

        Response invalid = create(worker, project, map("type", "epic", "title", " "));
        assertProblem(invalid, 400, "request.invalid");
        assertEquals(List.of("title.invalid", "type.unknown"), invalid.violations());
        assertEquals(201, create(worker, project, "Ablauf").status());

        assertProblem(patch(worker, project, 1, map("status", "done"), "\"1\""), 409, "ticket.transition_not_allowed");
        assertEquals(List.of("status.unknown"), patch(worker, project, 1, map("status", "waiting"), "\"1\"").violations());
        Response started = patch(worker, project, 1, map("status", "in_progress"), "\"1\"");
        assertEquals(200, started.status(), started.body());
        assertEquals("\"2\"", started.header("ETag"));
        assertEquals(200, patch(worker, project, 1, map("status", "done"), "\"2\"").status());

        assertEquals(List.of("assignee.not_member"), patch(worker, project, 1, map("assignee", stranger.id().toString()), "\"3\"").violations());
        assertEquals(List.of("assignee.not_member"), patch(worker, project, 1, map("assignee", "kein-konto"), "\"3\"").violations());
        Response assigned = patch(worker, project, 1, map("assignee", colleague.id().toString()), "\"3\"");
        assertEquals(colleague.id().toString(), assigned.fields().get("assignee").asString());
        Response same = patch(worker, project, 1, map("assignee", colleague.id().toString()), "\"4\"");
        assertEquals("\"4\"", same.header("ETag"), "a value that stays the same changes nothing");
        Response cleared = patch(worker, project, 1, map("assignee", null), "\"4\"");
        assertTrue(cleared.fields().get("assignee").isNull());

        assertEquals(List.of("fields.unknown"), patch(worker, project, 1, map("priority", "hoch"), "\"5\"").violations());
        assertEquals(List.of("title.invalid"), patch(worker, project, 1, Map.of("title", 5), "\"5\"").violations());
        assertEquals(List.of("fields.required"), patch(worker, project, 1, Map.of(), "\"5\"").violations());

        Response history = worker.get(tickets(project) + "/1/history");
        JsonNode statusChange = history.json().get(1).get("changes").get(0);
        assertEquals("status", statusChange.get("field").asString());
        assertEquals("new", statusChange.get("from").asString());
        assertEquals("in_progress", statusChange.get("to").asString());
        assertEquals(worker.id().toString(), history.json().get(1).get("actor").asString());
    }

    @Test
    void aChangedConfigurationAppliesToTheNextChangeAndRequiredFieldsAreEnforced() {
        Person worker = person();
        Person limited = person();
        Project project = project(worker, limited);
        grant(worker.id(), project.id(), FULL);
        grant(limited.id(), project.id(), "ticket.read", "ticket.create", "READ:*", "WRITE:title", "WRITE:type");
        assertEquals(201, create(worker, project, "Vorher").status());

        var initial = ProjectConfiguration.initial();
        List<ProjectConfiguration.Field> fields = new ArrayList<>(initial.fields());
        fields.set(1, new ProjectConfiguration.Field("description", true));
        var required = new ProjectConfiguration(initial.ticketTypes(), initial.statuses(), initial.initialStatus(), initial.transitions(), fields);
        String projectVersion = project.manager().get("/api/v1/projects/" + project.id()).header("ETag");
        Response configured = project.manager().browser().send("PUT", "/api/v1/projects/" + project.id() + "/configuration", required, projectVersion);
        assertEquals(200, configured.status(), configured.body());

        assertEquals(List.of("description.required"), create(worker, project, "Ohne Beschreibung").violations());
        assertProblem(create(limited, project, "Darf Beschreibung nicht"), 409, "ticket.required_field_inaccessible");
        Response renamed = patch(worker, project, 1, map("title", "Nachher"), "\"1\"");
        assertEquals(200, renamed.status(), renamed.body());
        assertEquals(2, renamed.json().get("configurationVersion").asLong(), "the ticket was checked against the current configuration");
        assertEquals(List.of("description.required"), patch(worker, project, 1, map("description", null), "\"2\"").violations());
    }

    // ---------------------------------------------------------------- versions, login and locks

    @Test
    void everyChangeNeedsTheCurrentVersion() {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), FULL);
        assertEquals(201, create(worker, project, "Version").status());
        assertProblem(patch(worker, project, 1, map("title", "x"), null), 428, "ticket.precondition_required");
        assertProblem(patch(worker, project, 1, map("title", "x"), "\"2\""), 412, "ticket.version_conflict");
        assertProblem(patch(worker, project, 1, map("title", "x"), "1"), 412, "ticket.version_conflict");
        assertProblem(patch(worker, project, 7, map("title", "x"), "\"1\""), 404, "ticket.not_found");
        assertEquals("Version", worker.get(tickets(project) + "/1").fields().get("title").asString());
    }

    @Test
    void aLoginThatMustStillChangeItsPasswordIsRefused() {
        var issued = accounts.create("test", "tickets-" + COUNTER.incrementAndGet(), null);
        Browser browser = anonymous();
        assertEquals(200, browser.send("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null).status());
        assertProblem(browser.get("/api/v1/projects/" + UUID.randomUUID() + "/tickets"), 403, "auth.password_change_required");
    }

    /**
     * A removal of the membership holds the project row exclusively. A change of the removed member that arrives meanwhile
     * waits in its check at the project row and is refused after the removal; the ticket stays unchanged.
     */
    @Test
    void aChangeOfAMemberWaitsForAConcurrentRemovalAndIsRefusedAfterIt() throws Exception {
        Person member = person();
        Project project = project(member);
        grant(member.id(), project.id(), FULL);
        assertEquals(201, create(member, project, "Unverändert").status());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> removal = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from project where id = ? for update", project.id());
            locked.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            jdbc.update("delete from project_member where project_id = ? and account_id = ?", project.id(), member.id());
            jdbc.update("update project set version = version + 1 where id = ?", project.id());
        }));
        try {
            assertTrue(locked.await(15, TimeUnit.SECONDS));
            Future<Response> change = POOL.submit(() -> patch(member, project, 1, map("title", "Geändert"), "\"1\""));
            assertTrue(waitsAt("from project where id"), "the member's check must wait at the project row");
            release.countDown();
            removal.get(15, TimeUnit.SECONDS);
            assertProblem(change.get(20, TimeUnit.SECONDS), 404, "project.not_found");
        } finally {
            release.countDown();
            removal.get(15, TimeUnit.SECONDS);
        }
        assertEquals("Unverändert", jdbc.queryForObject("select title from ticket where project_id = ?", String.class, project.id()));
        assertEquals(1L, jdbc.queryForObject("select version from ticket where project_id = ?", Long.class, project.id()));
    }

    /** A ticket row held longer than the lock timeout of the check ends the change with a retryable 503 instead of a 500. */
    @Test
    void aLockThatCannotBeTakenInTimeIsAnsweredAsRetryable() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), FULL);
        assertEquals(201, create(worker, project, "Gesperrt").status());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from ticket where project_id = ? for update", project.id());
            locked.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            assertTrue(locked.await(15, TimeUnit.SECONDS));
            Response busy = patch(worker, project, 1, map("title", "Wartet"), "\"1\"");
            assertProblem(busy, 503, "ticket.busy");
            assertEquals("1", busy.header("Retry-After"));
        } finally {
            release.countDown();
            holder.get(15, TimeUnit.SECONDS);
        }
        Response retried = patch(worker, project, 1, map("title", "Wiederholt"), "\"1\"");
        assertEquals(200, retried.status(), retried.body());
        assertNull(retried.json().get("violations"));
    }

    /** Like a project (review F2 of TASK-026): the tickets of a foreign project held by a change answer 404 at once, not 503. */
    @Test
    void theTicketsOfAForeignProjectHeldByAChangeAnswerLikeAnUnknownProject() throws Exception {
        Person member = person();
        Person outsider = person();
        Project project = project(member);
        grant(member.id(), project.id(), FULL);
        grant(outsider.id(), null, FULL);
        assertEquals(201, create(member, project, "Verborgen").status());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from project where id = ? for update", project.id());
            locked.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            assertTrue(locked.await(15, TimeUnit.SECONDS));
            long started = System.nanoTime();
            assertProblem(outsider.get(tickets(project)), 404, "project.not_found");
            assertProblem(outsider.get(tickets(project) + "/1"), 404, "project.not_found");
            assertProblem(create(outsider, project, "Fremd"), 404, "project.not_found");
            assertProblem(patch(outsider, project, 1, map("title", "Fremd"), "\"1\""), 404, "project.not_found");
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(3), "the refusal must not wait for the project row");
        } finally {
            release.countDown();
            holder.get(15, TimeUnit.SECONDS);
        }
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
}
