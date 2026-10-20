package taskmanager.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-026: projects, their configuration and members through the real HTTP interface, the real permissions module and a
 * real PostgreSQL. Project actions are granted directly in the tables, as no rights endpoint exists yet (TASK-030).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectsApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final String READ = "project.read";
    private static final String MANAGE = "project.manage";

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

        Response create(String key, String name) {
            return browser.send("POST", "/api/v1/projects", Map.of("key", key, "name", name), null);
        }

        Response rename(UUID project, String name, String ifMatch) {
            return browser.send("PUT", "/api/v1/projects/" + project, Map.of("name", name), ifMatch);
        }

        Response configure(UUID project, Object configuration, String ifMatch) {
            return browser.send("PUT", "/api/v1/projects/" + project + "/configuration", configuration, ifMatch);
        }

        Response addMember(UUID project, UUID account, String ifMatch) {
            return browser.send("PUT", "/api/v1/projects/" + project + "/members/" + account, null, ifMatch);
        }

        Response removeMember(UUID project, UUID account, String ifMatch) {
            return browser.send("DELETE", "/api/v1/projects/" + project + "/members/" + account, null, ifMatch);
        }
    }

    private Browser anonymous() {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        return browser;
    }

    private Person person(AdministrationRight... rights) {
        var issued = accounts.create("test", "projects-" + COUNTER.incrementAndGet(), null);
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

    private Person manager() {
        return person(AdministrationRight.PROJECTS_MANAGE);
    }

    /** Grants project actions in a group of their own, for one project or, with {@code null}, for every project of the company. */
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

    private static String nextKey() {
        return "P" + COUNTER.incrementAndGet();
    }

    private static UUID idOf(Response project) {
        assertTrue(project.status() < 300, project.body());
        return UUID.fromString(project.json().get("id").asString());
    }

    private static void assertProblem(Response response, int status, String code) {
        assertEquals(status, response.status(), response.body());
        assertEquals(code, response.code(), response.body());
    }

    private static List<String> members(Response response) {
        return StreamSupport.stream(response.json().spliterator(), false).map(node -> node.get("accountId").asString()).toList();
    }

    private Map<String, Object> configuration(Response project) {
        return JSON.convertValue(project.json().get("configuration"), new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
    }

    // ---------------------------------------------------------------- creating and listing

    @Test
    void onlyTheCompanyRightCreatesAProjectWithTheInitialConfigurationAndTheCreatorAsMember() {
        Person nobody = person();
        assertProblem(nobody.create(nextKey(), "Ohne Recht"), 403, "auth.forbidden");
        assertProblem(anonymous().send("POST", "/api/v1/projects", Map.of("key", "AB", "name", "x"), null), 401, "auth.required");

        Person manager = manager();
        String key = nextKey();
        Response created = manager.create(key.toLowerCase(), "  Plattform  ");
        assertEquals(201, created.status(), created.body());
        assertEquals("\"1\"", created.header("ETag"));
        assertEquals(key, created.json().get("key").asString(), "the key is stored in capitals");
        assertEquals("Plattform", created.json().get("name").asString());
        assertEquals(1, created.json().get("configurationVersion").asLong());
        assertTrue(created.json().get("manageable").asBoolean());
        assertEquals(JSON.convertValue(ProjectConfiguration.initial(), Map.class), configuration(created));
        UUID id = idOf(created);
        assertEquals(List.of(manager.id().toString()), members(manager.get("/api/v1/projects/" + id + "/members")));
        assertEquals(List.of("project.created"), jdbc.queryForList("select event from project_audit_event where project_id = ?", String.class, id));

        assertProblem(manager.create(key, "Noch einmal"), 409, "project.key_taken");
        Response invalid = manager.create("1X", " ");
        assertProblem(invalid, 400, "request.invalid");
        assertEquals(List.of("key.invalid", "name.invalid"), invalid.violations());
    }

    @Test
    void theCompanyRightSeesEveryProjectSortedByKeyAndAMemberOnlyTheProjectsTheyMayRead() {
        Person manager = manager();
        Person reader = person();
        grant(reader.id(), null, READ);
        UUID b = idOf(manager.create("ZB" + COUNTER.incrementAndGet(), "B"));
        UUID a = idOf(manager.create("ZA" + COUNTER.incrementAndGet(), "A"));
        assertEquals(200, manager.addMember(b, reader.id(), "\"1\"").status());

        List<String> all = StreamSupport.stream(manager.get("/api/v1/projects").json().spliterator(), false)
            .map(node -> node.get("key").asString()).toList();
        assertEquals(all.stream().sorted().toList(), all);
        assertTrue(all.size() >= 2);

        Response mine = reader.get("/api/v1/projects");
        assertEquals(1, mine.json().size(), mine.body());
        assertEquals(b.toString(), mine.json().get(0).get("id").asString());
        assertFalse(mine.json().get(0).get("manageable").asBoolean());
        assertProblem(reader.get("/api/v1/projects/" + a), 404, "project.not_found");
        assertEquals(List.of(), StreamSupport.stream(person().get("/api/v1/projects").json().spliterator(), false).toList(),
            "membership without project.read shows nothing");
    }

    // ---------------------------------------------------------------- who may read and change

    @Test
    void projectActionsNeedTheMembershipAndAProjectGrantCoversOnlyItsProject() {
        Person manager = manager();
        UUID project = idOf(manager.create(nextKey(), "Delegiert"));
        UUID other = idOf(manager.create(nextKey(), "Anderes"));
        Person lead = person();
        grant(lead.id(), project, READ, MANAGE);

        assertProblem(lead.get("/api/v1/projects/" + project), 404, "project.not_found");
        assertProblem(lead.rename(project, "x", "\"1\""), 404, "project.not_found");
        assertEquals(200, manager.addMember(project, lead.id(), "\"1\"").status());
        assertEquals(200, manager.addMember(other, lead.id(), "\"1\"").status());

        Response visible = lead.get("/api/v1/projects/" + project);
        assertEquals(200, visible.status(), visible.body());
        assertTrue(visible.json().get("manageable").asBoolean());
        Response renamed = lead.rename(project, "Neu benannt", visible.header("ETag"));
        assertEquals(200, renamed.status(), renamed.body());
        assertEquals("\"3\"", renamed.header("ETag"));
        assertProblem(lead.get("/api/v1/projects/" + other), 404, "project.not_found");

        Person member = person();
        grant(member.id(), null, READ);
        assertEquals(200, lead.addMember(project, member.id(), renamed.header("ETag")).status(), "a delegated lead manages members");
        Response read = member.get("/api/v1/projects/" + project);
        assertEquals(200, read.status(), read.body());
        assertFalse(read.json().get("manageable").asBoolean());
        assertProblem(member.rename(project, "x", read.header("ETag")), 403, "auth.forbidden");
        assertProblem(member.removeMember(project, lead.id(), read.header("ETag")), 403, "auth.forbidden");
        assertProblem(member.get("/api/v1/projects/" + UUID.randomUUID()), 404, "project.not_found");
        assertProblem(manager.get("/api/v1/projects/" + UUID.randomUUID()), 404, "project.not_found");
    }

    @Test
    void aRemovedMemberLosesTheProjectAndAnUnknownAccountCannotBeAdded() {
        Person manager = manager();
        UUID project = idOf(manager.create(nextKey(), "Mitglieder"));
        Person member = person();
        grant(member.id(), null, READ);
        Response added = manager.addMember(project, member.id(), "\"1\"");
        assertEquals("\"2\"", added.header("ETag"));
        Response again = manager.addMember(project, member.id(), "\"2\"");
        assertEquals("\"2\"", again.header("ETag"), "adding an existing member changes nothing");
        assertEquals(200, member.get("/api/v1/projects/" + project).status());

        Response unknown = manager.addMember(project, UUID.randomUUID(), "\"2\"");
        assertProblem(unknown, 400, "request.invalid");
        assertEquals(List.of("accountId.unknown"), unknown.violations());

        Response removed = manager.removeMember(project, member.id(), "\"2\"");
        assertEquals(200, removed.status(), removed.body());
        assertEquals(List.of(manager.id().toString()), members(removed));
        assertProblem(member.get("/api/v1/projects/" + project), 404, "project.not_found");
        assertEquals(List.of("project.created", "project.member_added", "project.member_removed"),
            jdbc.queryForList("select event from project_audit_event where project_id = ? order by id", String.class, project));
    }

    @Test
    void everyChangeNeedsTheCurrentVersion() {
        Person manager = manager();
        UUID project = idOf(manager.create(nextKey(), "Versionen"));
        assertProblem(manager.rename(project, "x", null), 428, "project.precondition_required");
        assertProblem(manager.rename(project, "x", "\"7\""), 412, "project.version_conflict");
        assertProblem(manager.rename(project, "x", "1"), 412, "project.version_conflict");
        assertProblem(manager.configure(project, ProjectConfiguration.initial(), null), 428, "project.precondition_required");
        assertProblem(manager.addMember(project, manager.id(), "\"2\""), 412, "project.version_conflict");
        assertProblem(manager.removeMember(project, manager.id(), null), 428, "project.precondition_required");
        assertEquals("\"1\"", manager.get("/api/v1/projects/" + project).header("ETag"), "nothing changed");
    }

    // ---------------------------------------------------------------- configuration

    @Test
    void aConfigurationChangeAddsAVersionAndKeepsTheEarlierOne() {
        Person manager = manager();
        UUID project = idOf(manager.create(nextKey(), "Ablauf"));
        var initial = ProjectConfiguration.initial();
        var review = new ProjectConfiguration(initial.ticketTypes(),
            List.of(new ProjectConfiguration.Status("new", "Neu", "open"), new ProjectConfiguration.Status("review", "Prüfung", "in_progress"),
                new ProjectConfiguration.Status("done", "Erledigt", "done")),
            "new", List.of(new ProjectConfiguration.Transition("new", "review"), new ProjectConfiguration.Transition("review", "done")),
            initial.fields());

        Response invalid = manager.configure(project, Map.of("ticketTypes", List.of(), "statuses", List.of(), "initialStatus", "x",
            "transitions", List.of(), "fields", List.of()), "\"1\"");
        assertProblem(invalid, 400, "request.invalid");
        assertEquals(List.of("ticketTypes.required", "statuses.required", "initialStatus.unknown", "fields.invalid"), invalid.violations());

        Response changed = manager.configure(project, review, "\"1\"");
        assertEquals(200, changed.status(), changed.body());
        assertEquals("\"2\"", changed.header("ETag"));
        assertEquals(2, changed.json().get("configurationVersion").asLong());
        assertEquals(JSON.convertValue(review, Map.class), configuration(changed));

        Response first = manager.get("/api/v1/projects/" + project + "/configurations/1");
        assertEquals(200, first.status(), first.body());
        assertEquals(JSON.convertValue(initial, Map.class), JSON.convertValue(first.json(), Map.class), "earlier versions stay readable");
        assertProblem(manager.get("/api/v1/projects/" + project + "/configurations/3"), 404, "project.configuration_not_found");
        assertProblem(person().get("/api/v1/projects/" + project + "/configurations/1"), 404, "project.not_found");
    }

    /** Review F1 of TASK-026: a status without a category is an invalid configuration, not a server error. */
    @Test
    void aStatusWithoutCategoryIsRefusedAsInvalid() {
        Person manager = manager();
        UUID project = idOf(manager.create(nextKey(), "Kategorie"));
        String initial = JSON.writeValueAsString(ProjectConfiguration.initial());
        for (String body : List.of(initial.replace("\"category\":\"open\"", "\"category\":null"),
                initial.replace(",\"category\":\"open\"", ""))) {
            assertTrue(!body.equals(initial), "the probe must change the configuration");
            Response refused = manager.configure(project, body, "\"1\"");
            assertProblem(refused, 400, "request.invalid");
            assertEquals(List.of("statuses.category_invalid"), refused.violations());
        }
        assertEquals(1L, jdbc.queryForObject("select version from project where id = ?", Long.class, project));
        assertEquals(1, jdbc.queryForObject("select count(*) from project_configuration where project_id = ?", Integer.class, project));
    }

    // ---------------------------------------------------------------- the login and the locks

    @Test
    void aLoginThatMustStillChangeItsPasswordIsRefused() {
        var issued = accounts.create("test", "projects-" + COUNTER.incrementAndGet(), null);
        AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), AdministrationRight.PROJECTS_MANAGE);
        Browser browser = anonymous();
        assertEquals(200, browser.send("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null).status());
        assertProblem(browser.get("/api/v1/projects"), 403, "auth.password_change_required");
    }

    /**
     * A writer of the project holds its row exclusively. A member's request that arrives meanwhile waits in the check at the
     * project row and decides after the writer, here: after the membership is gone, the project does not exist for them.
     */
    @Test
    void aRequestOfAMemberWaitsForAConcurrentRemovalAndThenSeesIt() throws Exception {
        Person manager = manager();
        UUID project = idOf(manager.create(nextKey(), "Sperren"));
        Person member = person();
        grant(member.id(), null, READ);
        assertEquals(200, manager.addMember(project, member.id(), "\"1\"").status());

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> writer = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from project where id = ? for update", project);
            locked.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            jdbc.update("delete from project_member where project_id = ? and account_id = ?", project, member.id());
            jdbc.update("update project set version = version + 1 where id = ?", project);
        }));
        try {
            assertTrue(locked.await(15, TimeUnit.SECONDS));
            Future<Response> read = POOL.submit(() -> member.get("/api/v1/projects/" + project));
            assertTrue(waitsAt("from project where id"), "the member's check must wait at the project row");
            release.countDown();
            writer.get(15, TimeUnit.SECONDS);
            assertProblem(read.get(20, TimeUnit.SECONDS), 404, "project.not_found");
        } finally {
            release.countDown();
            writer.get(15, TimeUnit.SECONDS);
        }
    }

    /**
     * Review F2 of TASK-026: a foreign project that a concurrent change holds answers at once like an unknown one, instead of
     * waiting for the lock and failing in a way that tells it apart.
     */
    @Test
    void aForeignProjectHeldByAConcurrentChangeAnswersLikeAnUnknownOne() throws Exception {
        Person manager = manager();
        UUID project = idOf(manager.create(nextKey(), "Verborgen"));
        Person outsider = person();
        grant(outsider.id(), null, READ, MANAGE);
        assertProblem(outsider.get("/api/v1/projects/" + UUID.randomUUID()), 404, "project.not_found");

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> writer = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.queryForList("select id from project where id = ? for update", project);
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
            assertProblem(outsider.get("/api/v1/projects/" + project), 404, "project.not_found");
            assertProblem(outsider.browser().send("PUT", "/api/v1/projects/" + project, Map.of("name", "x"), "\"1\""), 404, "project.not_found");
            assertProblem(outsider.get("/api/v1/projects/" + project + "/members"), 404, "project.not_found");
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(3), "the refusal must not wait for the project row");
        } finally {
            release.countDown();
            writer.get(15, TimeUnit.SECONDS);
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
