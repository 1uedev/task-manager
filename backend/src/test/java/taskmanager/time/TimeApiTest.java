package taskmanager.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-069: time entries through the real HTTP interface with the real permissions, projects and tickets modules and a real
 * PostgreSQL: the three time actions, the calculation example of docs/DOMAIN_MODEL.md, corrections and cancellations with
 * their history, versions, the rules of a booking, hidden tickets and the CSV export.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TimeApiTest {
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
        var issued = accounts.create("test", "time-" + COUNTER.incrementAndGet(), null);
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

    /** Grants actions and ticket field rules in a group of its own for one project; returns the group. */
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
                        values (?, ?, ?, 'ticket', ?, ?, 'ALLOW')
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
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "TM" + n, "name", "Zeiten " + n), null);
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

    private long ticket(Setup setup, Browser author, String title) {
        Response created = author.send("POST", "/api/v1/projects/" + setup.project() + "/tickets", Map.of("type", "task", "title", title), null);
        assertEquals(201, created.status(), created.body());
        return created.json().get("number").asLong();
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private static Map<String, Object> booking(String date, Integer minutes, String description, Long ticket, UUID person) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date);
        body.put("minutes", minutes);
        body.put("description", description);
        body.put("ticketNumber", ticket);
        body.put("personId", person);
        return body;
    }

    private Response book(Setup setup, Browser who, int minutes, String description) {
        return who.send("POST", "/api/v1/time/projects/" + setup.project() + "/entries", booking(today(), minutes, description, null, null), null);
    }

    private static UUID id(Response detail) {
        return UUID.fromString(detail.json().get("entry").get("id").asString());
    }

    private static Map<String, Object> correction(String date, int minutes, String description, Long ticket, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date);
        body.put("minutes", minutes);
        body.put("description", description);
        body.put("ticketNumber", ticket);
        body.put("reason", reason);
        return body;
    }

    private Response list(Browser who, Setup setup, String extra) {
        return who.get("/api/v1/time/entries?project=" + setup.project() + extra);
    }

    private static void assertInvalid(Response response, String violation) {
        assertEquals(400, response.status(), response.body());
        List<String> violations = new ArrayList<>();
        response.json().get("violations").forEach(v -> violations.add(v.asString()));
        assertTrue(violations.contains(violation), response.body());
    }

    // ---------------------------------------------------------------- tests

    /** The calculation example of docs/DOMAIN_MODEL.md: 90 + 30 = 120, correction to 75 gives 105, cancelling 30 leaves 75. */
    @Test
    void sumsFollowTheEffectiveValuesThroughCorrectionAndCancellation() {
        Setup setup = project();
        Browser nina = member(setup, "time.book");
        Browser omar = member(setup, "time.book");
        Browser lead = member(setup, "time.read");
        Response first = book(setup, nina, 90, "Analyse");
        assertEquals(201, first.status(), first.body());
        Response second = book(setup, omar, 30, "Rückfrage");
        assertEquals(201, second.status(), second.body());
        assertEquals(120, list(lead, setup, "").json().get("totalMinutes").asLong());
        assertEquals(90, list(nina, setup, "").json().get("totalMinutes").asLong(), "without time.read only the own entries count");

        Response corrected = nina.send("PUT", "/api/v1/time/entries/" + id(first), correction(today(), 75, "Analyse", null, null), first.etag());
        assertEquals(200, corrected.status(), corrected.body());
        assertEquals("\"2\"", corrected.etag());
        assertEquals(105, list(lead, setup, "").json().get("totalMinutes").asLong());

        Response cancelled = omar.send("POST", "/api/v1/time/entries/" + id(second) + "/cancel", Map.of("reason", "doppelt"), second.etag());
        assertEquals(200, cancelled.status(), cancelled.body());
        assertTrue(cancelled.json().get("entry").get("cancelled").asBoolean());
        JsonNode sums = list(lead, setup, "").json();
        assertEquals(75, sums.get("totalMinutes").asLong());
        assertEquals(1, sums.get("entries").size(), "cancelled entries are left out unless asked for");
        assertEquals(1, sums.get("perPerson").size());
        assertEquals(nina.id.toString(), sums.get("perPerson").get(0).get("personId").asString());
        JsonNode withCancelled = list(lead, setup, "&cancelled=true").json();
        assertEquals(2, withCancelled.get("entries").size());
        assertEquals(75, withCancelled.get("totalMinutes").asLong());

        JsonNode history = lead.get("/api/v1/time/entries/" + id(first)).json().get("revisions");
        assertEquals(2, history.size());
        assertEquals("created", history.get(0).get("kind").asString());
        assertEquals(90, history.get(0).get("minutes").asInt());
        assertEquals("corrected", history.get(1).get("kind").asString());
        assertEquals(75, history.get(1).get("minutes").asInt());
        assertEquals(nina.id.toString(), history.get(1).get("actorId").asString());

        Response again = omar.send("POST", "/api/v1/time/entries/" + id(second) + "/cancel", Map.of(), cancelled.etag());
        assertEquals(409, again.status(), again.body());
        assertEquals("time.entry_cancelled", again.code());
    }

    @Test
    void theTimeActionsDecideWhatIsVisibleAndChangeable() {
        Setup setup = project();
        Browser booker = member(setup, "time.book");
        Browser other = member(setup, "time.book");
        Browser ticketsOnly = member(setup, "ticket.read", "READ:title");
        Browser stranger = person();
        Response own = book(setup, booker, 60, "Eigene Arbeit");
        Response foreign = book(setup, other, 45, "Andere Arbeit");

        assertEquals(404, list(ticketsOnly, setup, "").status(), "reading tickets shows no times");
        assertEquals(404, list(stranger, setup, "").status());
        assertEquals(404, book(setup, ticketsOnly, 10, "x").status());
        assertEquals(404, booker.get("/api/v1/time/entries/" + id(foreign)).status(), "a foreign entry does not exist without time.read");
        assertEquals(200, booker.get("/api/v1/time/entries/" + id(own)).status());
        assertEquals(403, booker.get("/api/v1/time/projects/" + setup.project() + "/people").status());
        assertEquals(0, ticketsOnly.get("/api/v1/time/projects").json().size());
        JsonNode projects = booker.get("/api/v1/time/projects").json();
        assertEquals(1, projects.size());
        assertTrue(projects.get(0).get("access").get("book").asBoolean());
        assertFalse(projects.get(0).get("access").get("read").asBoolean());

        Browser reader = member(setup, "time.read");
        JsonNode seen = list(reader, setup, "").json();
        assertEquals(2, seen.get("entries").size());
        for (JsonNode entry : seen.get("entries")) {
            assertFalse(entry.get("changeable").asBoolean(), "time.read changes nothing");
        }
        Response readerChange = reader.send("PUT", "/api/v1/time/entries/" + id(own), correction(today(), 30, "x", null, "Grund"), own.etag());
        assertEquals(403, readerChange.status(), readerChange.body());
        assertEquals(5, reader.get("/api/v1/time/projects/" + setup.project() + "/people").json().size(),
            "the members: the creating administration, booker, other, ticketsOnly and the reader");
    }

    @Test
    void timeManageBooksAndCorrectsForActiveMembersWithAReason() {
        Setup setup = project();
        Browser lead = member(setup, "time.manage");
        Browser worker = member(setup, "time.book");
        Browser outsider = person();
        Response refused = worker.send("POST", "/api/v1/time/projects/" + setup.project() + "/entries",
            booking(today(), 30, "für andere", null, lead.id), null);
        assertEquals(403, refused.status(), refused.body());

        Response forWorker = lead.send("POST", "/api/v1/time/projects/" + setup.project() + "/entries",
            booking(today(), 30, "Nachtrag", null, worker.id), null);
        assertEquals(201, forWorker.status(), forWorker.body());
        assertEquals(worker.id.toString(), forWorker.json().get("entry").get("personId").asString());
        assertFalse(forWorker.json().get("entry").get("own").asBoolean());
        assertInvalid(lead.send("POST", "/api/v1/time/projects/" + setup.project() + "/entries",
            booking(today(), 30, "Nachtrag", null, outsider.id), null), "person.not_member");

        assertInvalid(lead.send("PUT", "/api/v1/time/entries/" + id(forWorker), correction(today(), 20, "Nachtrag", null, " "), forWorker.etag()),
            "reason.required");
        Response corrected = lead.send("PUT", "/api/v1/time/entries/" + id(forWorker), correction(today(), 20, "Nachtrag", null, "falsch erfasst"),
            forWorker.etag());
        assertEquals(200, corrected.status(), corrected.body());
        assertEquals("falsch erfasst", corrected.json().get("revisions").get(1).get("reason").asString());
        assertEquals(lead.id.toString(), corrected.json().get("revisions").get(1).get("actorId").asString());
        assertInvalid(lead.send("POST", "/api/v1/time/entries/" + id(forWorker) + "/cancel", Map.of(), corrected.etag()), "reason.required");

        JsonNode workerView = list(worker, setup, "").json();
        assertEquals(20, workerView.get("totalMinutes").asLong(), "the booked person sees the entry made for them");
        assertTrue(workerView.get("entries").get(0).get("changeable").asBoolean());
    }

    @Test
    void aBookingIsCheckedBeforeItIsStored() {
        Setup setup = project();
        Browser worker = member(setup, "time.book", "ticket.read", "ticket.create", "READ:title", "WRITE:title", "WRITE:type", "READ:type");
        Browser blind = member(setup, "time.book");
        long number = ticket(setup, worker, "Fehler im Export");
        String path = "/api/v1/time/projects/" + setup.project() + "/entries";
        String future = LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();
        assertInvalid(worker.send("POST", path, booking(future, 30, "Planung", null, null), null), "date.future");
        assertEquals(201, worker.send("POST", path, booking(LocalDate.now(ZoneOffset.UTC).plusDays(1).toString(), 30, "andere Zeitzone", null, null),
            null).status(), "one day of slack for time zones ahead of UTC");
        assertEquals(201, worker.send("POST", path, booking("2020-02-29", 30, "alt", null, null), null).status(), "back in time without a limit");
        assertInvalid(worker.send("POST", path, booking(today(), 0, "x", null, null), null), "minutes.invalid");
        assertInvalid(worker.send("POST", path, booking(today(), 1441, "x", null, null), null), "minutes.invalid");
        assertInvalid(worker.send("POST", path, booking(today(), 30, "   ", null, null), null), "description.required");
        assertInvalid(worker.send("POST", path, booking(today(), 30, "x".repeat(1001), null, null), null), "description.too_long");
        assertInvalid(worker.send("POST", path, booking(today(), 30, "x", 999L, null), null), "ticket.unknown");
        assertInvalid(worker.send("POST", path, booking("31.10.2026", 30, "x", null, null), null), "date.invalid");
        assertInvalid(blind.send("POST", path, booking(today(), 30, "x", number, null), null), "ticket.unknown");

        Response onTicket = worker.send("POST", path, booking(today(), 1440, "  ganzer Tag  ", number, null), null);
        assertEquals(201, onTicket.status(), onTicket.body());
        assertEquals("ganzer Tag", onTicket.json().get("entry").get("description").asString());
        assertEquals(number, onTicket.json().get("entry").get("ticket").get("number").asLong());
        assertEquals("Fehler im Export", onTicket.json().get("entry").get("ticket").get("title").asString());
        JsonNode ofTicket = list(worker, setup, "&ticket=" + number).json();
        assertEquals(1, ofTicket.get("entries").size());
        assertEquals(1440, ofTicket.get("totalMinutes").asLong());
        assertEquals(404, list(blind, setup, "&ticket=" + number).status(), "a ticket the caller may not read does not exist");
    }

    @Test
    void aReaderWithoutTicketRightsSeesTheEntryButNotItsTicket() {
        Setup setup = project();
        Browser worker = member(setup, "time.book", "ticket.read", "ticket.create", "READ:title", "WRITE:title", "WRITE:type", "READ:type");
        Browser reader = member(setup, "time.read");
        Browser numbers = member(setup, "time.read", "ticket.read");
        long number = ticket(setup, worker, "Vertraulich");
        worker.send("POST", "/api/v1/time/projects/" + setup.project() + "/entries", booking(today(), 50, "am Ticket", number, null), null);
        JsonNode seen = list(reader, setup, "").json();
        assertTrue(seen.get("entries").get(0).get("ticket").isNull(), seen.toString());
        assertTrue(seen.get("perTicket").get(0).get("ticket").isNull());
        assertFalse(reader.get("/api/v1/time/export?project=" + setup.project()).body().contains("-" + number + "\""));
        JsonNode withNumber = list(numbers, setup, "").json().get("entries").get(0).get("ticket");
        assertEquals(number, withNumber.get("number").asLong());
        assertTrue(withNumber.get("title").isNull(), "the title needs its read rule");
    }

    @Test
    void changesNeedTheCurrentVersionAndTheCurrentRight() {
        Setup setup = project();
        Browser worker = person();
        add(setup, worker.id);
        UUID group = grant(worker.id, setup.project(), "time.book");
        Response booked = book(setup, worker, 30, "Test");
        String path = "/api/v1/time/entries/" + id(booked);
        Response missing = worker.send("PUT", path, correction(today(), 40, "Test", null, null), null);
        assertEquals(428, missing.status(), missing.body());
        Response first = worker.send("PUT", path, correction(today(), 40, "Test", null, null), booked.etag());
        assertEquals(200, first.status(), first.body());
        Response stale = worker.send("PUT", path, correction(today(), 50, "Test", null, null), booked.etag());
        assertEquals(412, stale.status(), stale.body());
        assertEquals("time.version_conflict", stale.code());
        Response unchanged = worker.send("PUT", path, correction(today(), 40, "Test", null, null), first.etag());
        assertEquals(first.etag(), unchanged.etag(), "the same values make no new revision");

        revoke(group);
        Response afterRevoke = worker.send("PUT", path, correction(today(), 50, "Test", null, null), first.etag());
        assertEquals(404, afterRevoke.status(), afterRevoke.body());
        assertEquals(404, book(setup, worker, 10, "danach").status());
    }

    @Test
    void theExportNamesPeriodFiltersAndUnitAndNeutralizesFormulas() {
        Setup setup = project();
        Browser worker = member(setup, "time.book", "time.read");
        String path = "/api/v1/time/projects/" + setup.project() + "/entries";
        worker.send("POST", path, booking("2026-10-01", 75, "=HYPERLINK(\"x\")", null, null), null);
        worker.send("POST", path, booking("2026-10-02", 20, "Abstimmung; Kunde", null, null), null);
        Response cancelled = worker.send("POST", path, booking("2026-10-03", 600, "falsch", null, null), null);
        worker.send("POST", "/api/v1/time/entries/" + id(cancelled) + "/cancel", Map.of(), cancelled.etag());

        Response csv = worker.get("/api/v1/time/export?project=" + setup.project() + "&from=2026-10-01&to=2026-10-31");
        assertEquals(200, csv.status(), csv.body());
        String[] lines = csv.body().replace("\uFEFF", "").split("\r\n");
        assertEquals("\"# Zeitraum\";\"2026-10-01 bis 2026-10-31\"", lines[0]);
        assertTrue(lines[1].contains("Projekt TM"), lines[1]);
        assertTrue(lines[2].startsWith("\"# Einheit\""), lines[2]);
        assertEquals("\"Datum\";\"Projekt\";\"Ticket\";\"Person\";\"Minuten\";\"Stunden\";\"Beschreibung\"", lines[3]);
        assertTrue(lines[4].startsWith("\"2026-10-01\""), lines[4]);
        assertTrue(lines[4].endsWith("\"75\";\"1,25\";\"'=HYPERLINK(\"\"x\"\")\""), lines[4]);
        assertTrue(lines[5].endsWith("\"20\";\"0,33\";\"Abstimmung; Kunde\""), lines[5]);
        assertEquals("\"Summe\";\"\";\"\";\"\";\"95\";\"1,58\";\"\"", lines[6]);
        assertEquals(7, lines.length, "the cancelled entry is not exported");
        assertInvalid(worker.get("/api/v1/time/entries?from=2026-10-31&to=2026-10-01"), "to.before_from");
    }
}
