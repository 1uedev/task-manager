package taskmanager.sla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.helpdesk.CustomerAnswered;
import taskmanager.helpdesk.ReplySent;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import taskmanager.sla.internal.SlaWatch;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-068 and TASK-070: calendars, rules, the priority, the deadlines and the escalation stages of tickets through the real HTTP interface with the real
 * permissions, projects, tickets and helpdesk modules and a real PostgreSQL. Calendars here are open around the clock, so
 * a due time is the start plus the budget. A sent reply and an answer of the customer are published as the helpdesk does
 * it in its transaction; the helpdesk tests run them through a real mail server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SlaApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

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

    @AfterAll
    static void stop() {
        POOL.shutdownNow();
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired org.springframework.context.ApplicationContext context;

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
        var issued = accounts.create("test", "sla-" + COUNTER.incrementAndGet(), null);
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

    /** Grants actions and field rules of the resource type {@code ticket} in a group of its own, for one project. */
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
                List<String> fields = parts[1].equals("*")
                    ? List.of("title", "description", "type", "status", "assignee", "customer", "contact", "mail", "priority")
                    : List.of(parts[1]);
                for (String field : fields) {
                    jdbc.update("""
                            insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                            values (?, ?, ?, 'ticket', ?, ?, 'ALLOW')
                            """, companyId, group, project, field, parts[0]);
                }
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
    }

    // ---------------------------------------------------------------- fixtures

    private record Setup(UUID project, Browser admin, Browser agent, UUID customer, UUID calendar, String calendarVersion) {
    }

    private static Map<String, Object> statuses() {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("ticketTypes", List.of(Map.of("key", "task", "name", "Aufgabe"), Map.of("key", "bug", "name", "Fehler")));
        configuration.put("statuses", List.of(
            Map.of("key", "new", "name", "Neu", "category", "open"),
            Map.of("key", "waiting", "name", "Wartet auf Kunde", "category", "in_progress", "sla", "waiting"),
            Map.of("key", "done", "name", "Gelöst", "category", "done", "sla", "resolved"),
            Map.of("key", "rejected", "name", "Abgelehnt", "category", "done", "sla", "cancelled")));
        configuration.put("initialStatus", "new");
        List<Map<String, Object>> transitions = new ArrayList<>();
        for (String from : List.of("new", "waiting", "done", "rejected")) {
            for (String to : List.of("new", "waiting", "done", "rejected")) {
                if (!from.equals(to)) {
                    transitions.add(Map.of("from", from, "to", to));
                }
            }
        }
        configuration.put("transitions", transitions);
        configuration.put("fields", List.of(Map.of("key", "title", "required", true), Map.of("key", "description", "required", false),
            Map.of("key", "type", "required", true), Map.of("key", "status", "required", true), Map.of("key", "assignee", "required", false)));
        return configuration;
    }

    /** Hours of every day from midnight to midnight. */
    private static Map<String, Object> aroundTheClock(String name) {
        List<Map<String, Object>> week = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            week.add(Map.of("day", day, "hours", List.of(Map.of("from", "00:00", "to", "24:00"))));
        }
        return Map.of("name", name, "timeZone", "Europe/Berlin", "week", week, "exceptions", List.of());
    }

    private static Map<String, Object> budgets(int first, int resolution) {
        Map<String, Object> budgets = new LinkedHashMap<>();
        budgets.put("low", Map.of("firstResponse", first * 2, "resolution", resolution * 2));
        budgets.put("normal", Map.of("firstResponse", first, "resolution", resolution));
        budgets.put("high", Map.of("firstResponse", first / 2, "resolution", resolution / 2));
        budgets.put("critical", Map.of("firstResponse", first / 4, "resolution", resolution / 4));
        return budgets;
    }

    private static Map<String, Object> rule(String name, UUID project, UUID customer, String type, UUID calendar, int first, int resolution) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name);
        rule.put("projectId", project);
        rule.put("customerId", customer);
        rule.put("ticketType", type);
        rule.put("calendarId", calendar);
        rule.put("budgets", budgets(first, resolution));
        return rule;
    }

    /** A project with the SLA kinds above, an agent, a customer released for it and a calendar. */
    private Setup setup() {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.SLA_MANAGE, AdministrationRight.HELPDESK_MANAGE, AdministrationRight.PROJECTS_MANAGE);
        Browser agent = person();
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "SL" + n, "name", "Service " + n), null);
        assertEquals(201, created.status(), created.body());
        UUID project = UUID.fromString(created.json().get("id").asString());
        Response added = admin.send("PUT", "/api/v1/projects/" + project + "/members/" + agent.id, null, created.etag());
        assertEquals(200, added.status(), added.body());
        Response configured = admin.send("PUT", "/api/v1/projects/" + project + "/configuration", statuses(), added.etag());
        assertEquals(200, configured.status(), configured.body());
        grant(agent.id, project, "ticket.read", "ticket.create", "ticket.edit", "READ:*", "WRITE:*");
        Response customer = admin.send("POST", "/api/v1/helpdesk/customers", Map.of("name", "Nordwerk " + n, "projects", List.of(project)), null);
        assertEquals(201, customer.status(), customer.body());
        Response calendar = admin.send("POST", "/api/v1/sla/calendars", aroundTheClock("Rund um die Uhr " + n), null);
        assertEquals(201, calendar.status(), calendar.body());
        return new Setup(project, admin, agent, UUID.fromString(customer.json().get("id").asString()),
            UUID.fromString(calendar.json().get("id").asString()), calendar.etag());
    }

    private UUID ruleFor(Setup setup, UUID customer, String type, int first, int resolution) {
        Response created = setup.admin().send("POST", "/api/v1/sla/rules", rule("Regel " + COUNTER.incrementAndGet(), setup.project(), customer, type,
            setup.calendar(), first, resolution), null);
        assertEquals(201, created.status(), created.body());
        return UUID.fromString(created.json().get("id").asString());
    }

    private long ticket(Setup setup, String type) {
        Response created = setup.agent().send("POST", "/api/v1/projects/" + setup.project() + "/tickets", Map.of("type", type, "title", "Anfrage"), null);
        assertEquals(201, created.status(), created.body());
        return created.json().get("number").asLong();
    }

    private UUID ticketId(Setup setup, long number) {
        return jdbc.queryForObject("select id from ticket where project_id = ? and number = ?", UUID.class, setup.project(), number);
    }

    private Response setCustomer(Setup setup, long number, UUID customer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customer);
        body.put("contactId", null);
        return setup.agent().send("PUT", "/api/v1/projects/" + setup.project() + "/tickets/" + number + "/helpdesk/customer", body, null);
    }

    private Response status(Setup setup, long number, String status) {
        String path = "/api/v1/projects/" + setup.project() + "/tickets/" + number;
        Response current = setup.agent().get(path);
        Response changed = setup.agent().send("PATCH", path, Map.of("status", status), current.etag());
        assertEquals(200, changed.status(), changed.body());
        return changed;
    }

    private Response sla(Browser browser, Setup setup, long number) {
        return browser.get("/api/v1/projects/" + setup.project() + "/tickets/" + number + "/sla");
    }

    private Response priority(Browser browser, Setup setup, long number, String priority) {
        return browser.send("PUT", "/api/v1/projects/" + setup.project() + "/tickets/" + number + "/sla/priority", Map.of("priority", priority), null);
    }

    private void publish(Object event) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> events.publishEvent(event));
    }

    private static Instant instant(JsonNode node) {
        return Instant.parse(node.asString());
    }

    /** A group of the rights administration without rights, with the given members. */
    private UUID group(UUID... members) {
        UUID group = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseGet(companies::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "Leitstand " + COUNTER.incrementAndGet());
            for (UUID member : members) {
                jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, member);
            }
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
        return group;
    }

    private static Map<String, Object> stage(String clock, int percent, UUID group) {
        return Map.of("clock", clock, "percent", percent, "groupId", group);
    }

    /** A standard rule of the project, 60 and 240 minutes, with the given stages. */
    private UUID ruleWithStages(Setup setup, List<Map<String, Object>> stages) {
        Map<String, Object> body = rule("Stufen " + COUNTER.incrementAndGet(), setup.project(), null, null, setup.calendar(), 60, 240);
        body.put("escalations", stages);
        Response created = setup.admin().send("POST", "/api/v1/sla/rules", body, null);
        assertEquals(201, created.status(), created.body());
        assertEquals(stages.size(), created.json().get("escalations").size(), created.body());
        return UUID.fromString(created.json().get("id").asString());
    }

    private Instant started(Setup setup, long number) {
        return instant(sla(setup.agent(), setup, number).json().get("cycles").get(0).get("startedAt"));
    }

    private int watch(Instant now) {
        return context.getBean(SlaWatch.class).watch(now);
    }

    private List<Map<String, Object>> stagesOf(UUID ticket) {
        return jdbc.queryForList("select kind, percent, skipped from sla_escalation where ticket_id = ? order by percent", ticket);
    }

    private List<JsonNode> listed(Browser browser, Setup setup, long number) {
        Response list = browser.get("/api/v1/sla/escalations");
        assertEquals(200, list.status(), list.body());
        return java.util.stream.StreamSupport.stream(list.json().spliterator(), false)
            .filter(item -> item.get("projectId").asString().equals(setup.project().toString()) && item.get("number").asLong() == number).toList();
    }

    // ---------------------------------------------------------------- administration

    @Test
    void calendarsAndRulesNeedTheRightAndAreCheckedAndVersioned() {
        Setup setup = setup();
        Browser stranger = person(AdministrationRight.PROJECTS_MANAGE);
        assertEquals(403, stranger.get("/api/v1/sla/calendars").status());
        assertEquals(403, stranger.send("POST", "/api/v1/sla/calendars", aroundTheClock("Fremd"), null).status());
        assertEquals(403, stranger.get("/api/v1/sla/rules").status());

        Map<String, Object> invalid = new LinkedHashMap<>(aroundTheClock(""));
        invalid.put("timeZone", "Mond/Krater");
        invalid.put("week", List.of(Map.of("day", 1, "hours", List.of(Map.of("from", "17:00", "to", "09:00")))));
        Response refused = setup.admin().send("POST", "/api/v1/sla/calendars", invalid, null);
        assertEquals(400, refused.status(), refused.body());
        assertEquals(List.of("name.invalid", "timeZone.invalid", "week.hours_invalid", "week.closed"),
            JSON.convertValue(refused.json().get("violations"), List.class));
        String name = setup.admin().get("/api/v1/sla/calendars/" + setup.calendar()).json().get("name").asString();
        assertEquals(409, setup.admin().send("POST", "/api/v1/sla/calendars", aroundTheClock(name.toUpperCase()), null).status());

        Map<String, Object> office = new LinkedHashMap<>(aroundTheClock("Büro"));
        office.put("week", List.of(Map.of("day", 1, "hours", List.of(Map.of("from", "09:00", "to", "17:00")))));
        office.put("exceptions", List.of(Map.of("date", "2026-12-24", "hours", List.of())));
        Response changed = setup.admin().send("PUT", "/api/v1/sla/calendars/" + setup.calendar(), office, setup.calendarVersion());
        assertEquals(200, changed.status(), changed.body());
        assertEquals("\"2\"", changed.etag());
        assertEquals("2026-12-24", changed.json().get("exceptions").get(0).get("date").asString());
        assertEquals(412, setup.admin().send("PUT", "/api/v1/sla/calendars/" + setup.calendar(), office, setup.calendarVersion()).status());
        assertEquals(428, setup.admin().send("PUT", "/api/v1/sla/calendars/" + setup.calendar(), office, null).status());
        assertEquals(2, jdbc.queryForObject("select count(*) from sla_calendar_version where calendar_id = ?", Integer.class, setup.calendar()));

        Response noCustomer = setup.admin().send("POST", "/api/v1/sla/rules", rule("Nur Typ", setup.project(), null, "bug", setup.calendar(), 60, 240), null);
        assertEquals(400, noCustomer.status());
        assertEquals(List.of("ticketType.requires_customer"), JSON.convertValue(noCustomer.json().get("violations"), List.class));
        Map<String, Object> broken = rule("Kaputt", UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), 60, 240);
        broken.put("budgets", Map.of("normal", Map.of("firstResponse", 0, "resolution", 1)));
        assertEquals(List.of("projectId.unknown", "customerId.not_released", "calendarId.unknown", "budgets.invalid"),
            JSON.convertValue(setup.admin().send("POST", "/api/v1/sla/rules", broken, null).json().get("violations"), List.class));

        UUID standard = ruleFor(setup, null, null, 60, 240);
        Response twice = setup.admin().send("POST", "/api/v1/sla/rules", rule("Noch ein Standard", setup.project(), null, null, setup.calendar(), 30, 90), null);
        assertEquals(409, twice.status(), "two rules that match the same tickets equally well");
        assertEquals("sla.rule_conflict", twice.code());
        UUID special = ruleFor(setup, setup.customer(), "bug", 30, 120);
        Response rules = setup.admin().get("/api/v1/sla/rules");
        assertEquals(2, java.util.stream.StreamSupport.stream(rules.json().spliterator(), false)
            .filter(r -> r.get("projectId").asString().equals(setup.project().toString())).count(), rules.body());

        Response options = setup.admin().get("/api/v1/sla/options");
        assertEquals(200, options.status(), options.body());
        assertTrue(options.body().contains(setup.project().toString()) && options.body().contains(setup.customer().toString()), options.body());

        assertEquals(204, setup.admin().send("DELETE", "/api/v1/sla/rules/" + standard, null, "\"1\"").status());
        assertEquals(404, setup.admin().send("DELETE", "/api/v1/sla/rules/" + standard, null, "\"2\"").status());
        assertEquals(201, setup.admin().send("POST", "/api/v1/sla/rules", rule("Neuer Standard", setup.project(), null, null, setup.calendar(), 30, 90),
            null).status(), "a removed rule frees its place");
        assertEquals(List.of("calendar.created", "calendar.changed", "rule.created", "rule.created", "rule.removed", "rule.created"),
            jdbc.queryForList("select operation from sla_audit_event where target_id in (select id from sla_calendar where id = ?)"
                + " or target_id in (select id from sla_rule where project_id = ?) order by id", String.class, setup.calendar(), setup.project()));
        assertFalse(special.equals(standard));
    }

    @Test
    void twoAdministrationsStoringTheSameMatchAtOnceGetOneRule() throws Exception {
        Setup setup = setup();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int n = i;
            calls.add(() -> setup.admin().send("POST", "/api/v1/sla/rules", rule("Gleich " + n, setup.project(), setup.customer(), null, setup.calendar(),
                60, 240), null).status());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : POOL.invokeAll(calls)) {
            statuses.add(result.get());
        }
        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
        assertTrue(statuses.stream().allMatch(s -> s == 201 || s == 409), statuses.toString());
    }

    // ---------------------------------------------------------------- deadlines of a ticket

    @Test
    void aTicketWithoutCustomerHasNoDeadlinesAndWithoutRuleNoneAssigned() {
        Setup setup = setup();
        long number = ticket(setup, "task");
        Response view = sla(setup.agent(), setup, number);
        assertEquals(200, view.status(), view.body());
        assertEquals("no_customer", view.json().get("state").asString());
        assertEquals("normal", view.json().get("priority").asString());
        assertEquals(List.of("priority"), JSON.convertValue(view.json().get("writable"), List.class));
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        assertEquals("no_rule", sla(setup.agent(), setup, number).json().get("state").asString(), "a customer without a rule");
        assertEquals(0, jdbc.queryForObject("select count(*) from sla_cycle where ticket_id = ?", Integer.class, ticketId(setup, number)));
    }

    @Test
    void theFirstCycleStartsAtTheCreationAndTheMostSpecificRuleApplies() {
        Setup setup = setup();
        ruleFor(setup, null, null, 120, 480);
        ruleFor(setup, setup.customer(), null, 60, 240);
        ruleFor(setup, setup.customer(), "bug", 20, 80);
        long task = ticket(setup, "task");
        long bug = ticket(setup, "bug");
        Instant created = jdbc.queryForObject("select created_at from ticket where id = ?", java.sql.Timestamp.class, ticketId(setup, task)).toInstant()
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        assertEquals(200, setCustomer(setup, task, setup.customer()).status());
        assertEquals(200, setCustomer(setup, bug, setup.customer()).status());

        JsonNode cycle = sla(setup.agent(), setup, task).json().get("cycles").get(0);
        assertEquals(1, cycle.get("number").asInt());
        assertEquals(created, instant(cycle.get("startedAt")), "rule 7: the creation, not the moment the customer was set");
        assertEquals(created.plus(Duration.ofMinutes(60)), instant(cycle.get("firstResponse").get("dueAt")), "customer rule");
        assertEquals(created.plus(Duration.ofMinutes(240)), instant(cycle.get("resolution").get("dueAt")));
        assertEquals("running", cycle.get("firstResponse").get("state").asString());
        assertEquals("Europe/Berlin", cycle.get("timeZone").asString());
        JsonNode bugCycle = sla(setup.agent(), setup, bug).json().get("cycles").get(0);
        assertEquals(20 * 60, bugCycle.get("firstResponse").get("budgetSeconds").asLong(), "customer and type rule");
    }

    @Test
    void replyPauseAnswerAndResolutionMoveTheClocks() {
        Setup setup = setup();
        ruleFor(setup, null, null, 60, 240);
        long number = ticket(setup, "task");
        UUID ticket = ticketId(setup, number);
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());

        status(setup, number, "waiting");
        JsonNode waiting = sla(setup.agent(), setup, number).json().get("cycles").get(0);
        assertEquals("running", waiting.get("firstResponse").get("state").asString(), "DEC-032: the open first response keeps running");
        assertEquals("paused", waiting.get("resolution").get("state").asString());
        assertTrue(waiting.get("resolution").get("dueAt").isNull());

        Instant sent = Instant.now();
        publish(new ReplySent(companies.find().orElseThrow(), setup.project(), ticket, UUID.randomUUID(), sent));
        JsonNode replied = sla(setup.agent(), setup, number).json().get("cycles").get(0);
        assertEquals("met", replied.get("firstResponse").get("state").asString());
        assertEquals("paused", replied.get("resolution").get("state").asString(), "a reply of the support does not end the waiting");

        publish(new CustomerAnswered(companies.find().orElseThrow(), setup.project(), ticket, Instant.now()));
        JsonNode answered = sla(setup.agent(), setup, number).json().get("cycles").get(0);
        assertEquals("running", answered.get("resolution").get("state").asString());

        status(setup, number, "done");
        JsonNode resolved = sla(setup.agent(), setup, number).json().get("cycles").get(0);
        assertEquals("met", resolved.get("outcome").asString(), "resolved after a sent reply");
        assertEquals("met", resolved.get("resolution").get("state").asString());
        assertFalse(resolved.get("resolution").get("breached").asBoolean());

        // Rule 12: back into an open status starts the next cycle with a fresh budget.
        status(setup, number, "new");
        JsonNode cycles = sla(setup.agent(), setup, number).json().get("cycles");
        assertEquals(2, cycles.size());
        assertEquals(2, cycles.get(0).get("number").asInt());
        assertEquals("running", cycles.get(0).get("firstResponse").get("state").asString());
        assertEquals("met", cycles.get(1).get("outcome").asString(), "the earlier cycle stays");
    }

    @Test
    void theResolvedStatusAloneDoesNotMeetTheResolutionAndCancelledEndsTheCycle() {
        Setup setup = setup();
        ruleFor(setup, null, null, 60, 240);
        long number = ticket(setup, "task");
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        status(setup, number, "done");
        JsonNode cycle = sla(setup.agent(), setup, number).json().get("cycles").get(0);
        assertTrue(cycle.get("outcome").isNull(), "H-09: no reply was sent");
        assertEquals("running", cycle.get("resolution").get("state").asString());
        status(setup, number, "rejected");
        cycle = sla(setup.agent(), setup, number).json().get("cycles").get(0);
        assertEquals("cancelled", cycle.get("outcome").asString());
        assertEquals("cancelled", cycle.get("firstResponse").get("state").asString());
        status(setup, number, "new");
        assertEquals(2, sla(setup.agent(), setup, number).json().get("cycles").size(), "out of cancelled into open: a new cycle");
    }

    @Test
    void removingTheCustomerCancelsTheCycleAndSettingItAgainStartsANewOneNow() {
        Setup setup = setup();
        ruleFor(setup, null, null, 60, 240);
        long number = ticket(setup, "task");
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        assertEquals(200, setCustomer(setup, number, null).status());
        Response view = sla(setup.agent(), setup, number);
        assertEquals("no_customer", view.json().get("state").asString());
        assertEquals("customer_removed", view.json().get("cycles").get(0).get("reason").asString());
        Instant before = Instant.now().minusSeconds(1);
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        JsonNode second = sla(setup.agent(), setup, number).json().get("cycles").get(0);
        assertEquals(2, second.get("number").asInt());
        assertTrue(instant(second.get("startedAt")).isAfter(before));
    }

    @Test
    void aHigherPriorityKeepsTheUsedTimeAndIsInTheHistory() {
        Setup setup = setup();
        ruleFor(setup, null, null, 60, 240);
        long number = ticket(setup, "task");
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        Instant start = instant(sla(setup.agent(), setup, number).json().get("cycles").get(0).get("startedAt"));
        assertEquals(0, start.getNano(), "clocks count whole seconds");

        Response changed = priority(setup.agent(), setup, number, "critical");
        assertEquals(200, changed.status(), changed.body());
        assertEquals("critical", changed.json().get("priority").asString());
        JsonNode cycle = changed.json().get("cycles").get(0);
        assertEquals("critical", cycle.get("priority").asString());
        assertEquals(15 * 60, cycle.get("firstResponse").get("budgetSeconds").asLong());
        assertEquals(start.plus(Duration.ofMinutes(15)), instant(cycle.get("firstResponse").get("dueAt")), "the used time stays used");
        assertEquals(400, priority(setup.agent(), setup, number, "dringend").status());

        Response history = setup.agent().get("/api/v1/projects/" + setup.project() + "/tickets/" + number + "/history");
        assertTrue(history.body().contains("\"field\":\"priority\"") && history.body().contains("\"to\":\"critical\""), history.body());
        assertEquals(200, priority(setup.agent(), setup, number, "critical").status(), "the same priority again changes nothing");
        assertEquals(1, jdbc.queryForObject("select count(*) from ticket_change where ticket_id = ? and field = 'priority'", Integer.class,
            ticketId(setup, number)));
    }

    @Test
    void theDeadlinesFollowTheReadRulesAndThePriorityItsWriteRule() {
        Setup setup = setup();
        ruleFor(setup, null, null, 60, 240);
        long number = ticket(setup, "task");
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());

        Browser reader = person();
        Response member = setup.admin().get("/api/v1/projects/" + setup.project());
        assertEquals(200, setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + reader.id, null, member.etag()).status());
        grant(reader.id, setup.project(), "ticket.read", "READ:title", "READ:priority");
        Response limited = sla(reader, setup, number);
        assertEquals(200, limited.status(), limited.body());
        assertEquals("normal", limited.json().get("priority").asString());
        assertTrue(limited.json().get("state").isNull(), "without the read rule of customer no deadlines");
        assertTrue(limited.json().get("cycles").isNull());
        assertEquals(403, priority(reader, setup, number, "high").status());

        Browser outsider = person();
        assertEquals(404, sla(outsider, setup, number).status());
        assertEquals("project.not_found", sla(outsider, setup, number).code());
        assertEquals(404, priority(outsider, setup, number, "high").status());
        assertEquals(404, sla(setup.agent(), setup, 999).status());
    }

    @Test
    void concurrentChangesOfOneTicketLeaveOneOpenCycle() throws Exception {
        Setup setup = setup();
        ruleFor(setup, null, null, 60, 240);
        long number = ticket(setup, "task");
        UUID ticket = ticketId(setup, number);
        UUID company = companies.find().orElseThrow();
        List<Callable<Integer>> calls = new ArrayList<>();
        calls.add(() -> setCustomer(setup, number, setup.customer()).status());
        for (int i = 0; i < 3; i++) {
            calls.add(() -> {
                publish(new ReplySent(company, setup.project(), ticket, UUID.randomUUID(), Instant.now()));
                return 200;
            });
            calls.add(() -> priority(setup.agent(), setup, number, "high").status());
        }
        for (Future<Integer> result : POOL.invokeAll(calls)) {
            assertTrue(List.of(200, 503).contains(result.get()), "only success or a retryable answer");
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from sla_cycle where ticket_id = ?", Integer.class, ticket));
        assertEquals(2, jdbc.queryForObject("select count(*) from sla_clock c join sla_cycle y on y.id = c.cycle_id where y.ticket_id = ?",
            Integer.class, ticket));
    }

    // ---------------------------------------------------------------- escalations (TASK-070)

    @Test
    void stagesOfARuleAreCheckedAgainstTheGroupsAndVersioned() {
        Setup setup = setup();
        UUID leads = group(setup.agent().id);
        Response options = setup.admin().get("/api/v1/sla/options");
        assertTrue(options.body().contains(leads.toString()), "the groups are offered as recipients");
        Map<String, Object> body = rule("Kaputte Stufen", setup.project(), null, null, setup.calendar(), 60, 240);
        body.put("escalations", List.of(stage("first_response", 80, UUID.randomUUID()), stage("first_response", 0, leads),
            stage("resolution", 80, leads), stage("resolution", 80, leads)));
        Response refused = setup.admin().send("POST", "/api/v1/sla/rules", body, null);
        assertEquals(400, refused.status(), refused.body());
        assertEquals(List.of("escalations.group_unknown", "escalations.invalid", "escalations.duplicate"),
            JSON.convertValue(refused.json().get("violations"), List.class));

        UUID rule = ruleWithStages(setup, List.of(stage("first_response", 80, leads), stage("first_response", 100, leads)));
        Map<String, Object> changed = rule("Weniger Stufen", setup.project(), null, null, setup.calendar(), 60, 240);
        changed.put("escalations", List.of(stage("resolution", 90, leads)));
        Response updated = setup.admin().send("PUT", "/api/v1/sla/rules/" + rule, changed, "\"1\"");
        assertEquals(200, updated.status(), updated.body());
        assertEquals("resolution", updated.json().get("escalations").get(0).get("clock").asString());
        assertEquals(2, jdbc.queryForObject("select count(*) from sla_rule_version where rule_id = ?", Integer.class, rule), "two versions");
        assertEquals(2, jdbc.queryForObject("select jsonb_array_length(escalations) from sla_rule_version where rule_id = ? and version = 1", Integer.class, rule));
    }

    @Test
    void theWatchReachesEachStageOnceAndTheListShowsItToGroupMembersWhoMayRead() {
        Setup setup = setup();
        Browser stranger = person();
        Browser noCustomerRule = person();
        Browser outsider = person();
        Response member = setup.admin().get("/api/v1/projects/" + setup.project());
        assertEquals(200, setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + noCustomerRule.id, null, member.etag()).status());
        grant(noCustomerRule.id, setup.project(), "ticket.read", "READ:title", "READ:priority");
        UUID leads = group(setup.agent().id, stranger.id, noCustomerRule.id);
        ruleWithStages(setup, List.of(stage("first_response", 50, leads), stage("first_response", 100, leads), stage("resolution", 50, leads)));
        long number = ticket(setup, "task");
        UUID ticket = ticketId(setup, number);
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        Instant start = started(setup, number);
        String changes = "select count(*) from ticket_change where ticket_id = ?";
        int before = jdbc.queryForObject(changes, Integer.class, ticket);
        String assignee = jdbc.queryForObject("select coalesce(assignee::text, '') || status from ticket where id = ?", String.class, ticket);

        watch(start.plus(Duration.ofMinutes(29)));
        assertEquals(List.of(), stagesOf(ticket), "below the threshold");
        watch(start.plus(Duration.ofMinutes(30)));
        assertEquals(1, stagesOf(ticket).size(), "S-13: the share of the budget is reached");
        watch(start.plus(Duration.ofMinutes(45)));
        assertEquals(1, stagesOf(ticket).size(), "rule 3: at most once");

        List<JsonNode> items = listed(setup.agent(), setup, number);
        assertEquals(1, items.size());
        assertEquals(50, items.get(0).get("percent").asInt());
        assertEquals("first_response", items.get(0).get("kind").asString());
        assertEquals(start.plus(Duration.ofMinutes(30)), instant(items.get(0).get("thresholdAt")));
        assertTrue(items.get(0).get("cycleOutcome").isNull());
        assertTrue(items.get(0).get("groupName").asString().startsWith("Leitstand"));
        assertEquals(List.of(), listed(stranger, setup, number), "a member of the group who may not read the ticket");
        assertEquals(List.of(), listed(noCustomerRule, setup, number), "without the read rule of customer the deadlines stay hidden");
        assertEquals(List.of(), listed(outsider, setup, number), "not a member of the group");
        JsonNode hint = sla(setup.agent(), setup, number).json().get("cycles").get(0).get("escalations");
        assertEquals(1, hint.size(), "the hint at the ticket");
        assertEquals(50, hint.get(0).get("percent").asInt());

        // Rights apply when reading: the ticket becomes readable for the stranger later, and the stage shows up.
        Response again = setup.admin().get("/api/v1/projects/" + setup.project());
        assertEquals(200, setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + stranger.id, null, again.etag()).status());
        grant(stranger.id, setup.project(), "ticket.read", "READ:*");
        assertEquals(1, listed(stranger, setup, number).size());

        assertEquals("open", jdbc.queryForObject("select case when count(*) = 0 then 'open' else 'closed' end from sla_cycle where ticket_id = ? and outcome is not null",
            String.class, ticket));
        assertEquals(before, jdbc.queryForObject(changes, Integer.class, ticket), "rule 4: the stages change nothing at the ticket");
        assertEquals(assignee, jdbc.queryForObject("select coalesce(assignee::text, '') || status from ticket where id = ?", String.class, ticket),
            "rule 4: no assignment and no status change");
    }

    @Test
    void afterAnOutageOnlyTheHighestStageIsReachedAndTwoWatchesStoreItOnce() throws Exception {
        Setup setup = setup();
        UUID support = group(setup.agent().id);
        UUID lead = group(setup.agent().id);
        ruleWithStages(setup, List.of(stage("first_response", 80, support), stage("first_response", 100, lead)));
        long number = ticket(setup, "task");
        UUID ticket = ticketId(setup, number);
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        Instant late = started(setup, number).plus(Duration.ofHours(3));

        List<Callable<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            runs.add(() -> watch(late));
        }
        for (Future<Integer> run : POOL.invokeAll(runs)) {
            run.get();
        }
        List<Map<String, Object>> stored = stagesOf(ticket);
        assertEquals(2, stored.size(), stored.toString());
        assertEquals(Boolean.TRUE, stored.get(0).get("skipped"), "H-11: the warning is passed over");
        assertEquals(Boolean.FALSE, stored.get(1).get("skipped"));
        List<JsonNode> items = listed(setup.agent(), setup, number);
        assertEquals(1, items.size(), "no series of old warnings");
        assertEquals(100, items.get(0).get("percent").asInt());
        assertEquals(lead.toString(), items.get(0).get("groupId").asString());
        watch(late.plus(Duration.ofHours(1)));
        assertEquals(2, stagesOf(ticket).size(), "a skipped stage does not come later");
    }

    @Test
    void aPauseOrAMetClockBeforeTheThresholdReachesNoStageAndAReachedOneStays() {
        Setup setup = setup();
        UUID leads = group(setup.agent().id);
        ruleWithStages(setup, List.of(stage("first_response", 50, leads), stage("resolution", 50, leads)));
        long number = ticket(setup, "task");
        UUID ticket = ticketId(setup, number);
        assertEquals(200, setCustomer(setup, number, setup.customer()).status());
        Instant start = started(setup, number);
        status(setup, number, "waiting");
        publish(new ReplySent(companies.find().orElseThrow(), setup.project(), ticket, UUID.randomUUID(), Instant.now()));
        watch(start.plus(Duration.ofHours(10)));
        assertEquals(List.of(), stagesOf(ticket), "the first response is met and the resolution paused");

        long other = ticket(setup, "task");
        UUID otherId = ticketId(setup, other);
        assertEquals(200, setCustomer(setup, other, setup.customer()).status());
        watch(started(setup, other).plus(Duration.ofMinutes(31)));
        assertEquals(1, stagesOf(otherId).size());
        status(setup, other, "rejected");
        JsonNode cycle = sla(setup.agent(), setup, other).json().get("cycles").get(0);
        assertEquals("cancelled", cycle.get("outcome").asString());
        assertEquals(1, cycle.get("escalations").size(), "the stage stays in the history");
        List<JsonNode> items = listed(setup.agent(), setup, other);
        assertEquals(1, items.size());
        assertEquals("cancelled", items.get(0).get("cycleOutcome").asString());
        watch(started(setup, other).plus(Duration.ofHours(10)));
        assertEquals(1, stagesOf(otherId).size(), "a closed cycle reaches nothing");
    }
}
