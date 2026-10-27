package taskmanager.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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
 * The view of the operation on the background jobs through the real HTTP interface against a real PostgreSQL (TASK-054,
 * REQ-OPS-006). The executor is off, so the tests decide the state of every job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobsApiTest {
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
    @Autowired Jobs jobs;

    @BeforeEach
    void clean() {
        jdbc.update("delete from job");
        jdbc.update("delete from job_audit_event");
        jdbc.update("delete from job_worker");
    }

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, byte[] raw, Map<String, List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }

        JsonNode json() {
            return JSON.readTree(new String(raw, StandardCharsets.UTF_8));
        }

        String code() {
            return json().get("code").asString();
        }

        String body() {
            return new String(raw, StandardCharsets.UTF_8);
        }
    }

    final class Browser {
        private final HttpClient http = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();

        Response send(String method, String path, String contentType, byte[] body, String ifMatch) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
                request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
                if (contentType != null) {
                    request.header("Content-Type", contentType);
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
                HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
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
            return send("GET", path, null, null, null);
        }

        Response json(String method, String path, Object body, String ifMatch) {
            return send(method, path, "application/json", JSON.writeValueAsBytes(body), ifMatch);
        }

    }

    private Browser anonymous() {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        return browser;
    }

    /** A person with a complete login who holds exactly these administrative rights. */
    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", "person-" + COUNTER.incrementAndGet(), null);
        // Without any right the company still has to exist, as in an installation that was started.
        AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        Browser browser = anonymous();
        Response login = browser.json("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null);
        assertEquals(200, login.status(), login.body());
        Response changed = browser.json("POST", "/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG), null);
        assertEquals(200, changed.status(), changed.body());
        return browser;
    }

    private static void assertProblem(Response response, int status, String code) {
        assertEquals(status, response.status(), response.body());
        assertEquals("application/problem+json", response.header("Content-Type"));
        assertEquals(code, response.code());
    }

    private UUID job(UUID company, String kind, String state) {
        UUID id = new TransactionTemplate(transactions).execute(status -> jobs.enqueue(NewJob.of(company, kind).withPayload(Map.of("ticket", "secret-ticket"))));
        if (state.equals("running")) {
            jdbc.update("update job set state = 'running', attempts = 1, lease_owner = 'test', lease_until = now() + interval '1 minute' where id = ?", id);
        } else if (state.equals("failed")) {
            jdbc.update("update job set state = 'failed', attempts = max_attempts, finished_at = now(), error_code = 'job.failed', "
                + "error_reference = gen_random_uuid() where id = ?", id);
        } else if (!state.equals("queued")) {
            jdbc.update("update job set state = ?, finished_at = now() where id = ?", state, id);
        }
        return id;
    }

    private UUID company() {
        return companies.find().orElseThrow();
    }

    private int audits() {
        return jdbc.queryForObject("select count(*) from job_audit_event", Integer.class);
    }

    // ---------------------------------------------------------------- tests

    @Test
    void withoutTheRightNothingIsVisibleOrChangeable() {
        assertProblem(anonymous().get("/api/v1/jobs/summary"), 401, "auth.required");
        Browser other = person(AdministrationRight.ACCOUNTS_MANAGE, AdministrationRight.PERMISSIONS_MANAGE);
        UUID failed = job(company(), "test.mail", "failed");
        UUID queued = job(company(), "test.mail", "queued");
        assertProblem(other.get("/api/v1/jobs/summary"), 403, "auth.forbidden");
        assertProblem(other.get("/api/v1/jobs"), 403, "auth.forbidden");
        assertProblem(other.send("POST", "/api/v1/jobs/" + failed + "/retry", null, null, null), 403, "auth.forbidden");
        assertProblem(other.send("POST", "/api/v1/jobs/" + queued + "/cancel", null, null, null), 403, "auth.forbidden");
        assertEquals("failed", jdbc.queryForObject("select state from job where id = ?", String.class, failed));
        assertEquals("queued", jdbc.queryForObject("select state from job where id = ?", String.class, queued));
        assertEquals(0, audits());
    }

    @Test
    void theSummaryCountsTheJobsOfTheCompanyAndTheInstallationAndShowsBacklog() {
        Browser operator = person(AdministrationRight.JOBS_MANAGE);
        job(company(), "test.mail", "queued");
        job(null, "recurring.files.cleanup", "succeeded");
        job(company(), "test.mail", "failed");
        job(company(), "test.mail", "running");
        job(UUID.randomUUID(), "test.mail", "failed");

        Response summary = operator.get("/api/v1/jobs/summary");
        assertEquals(200, summary.status(), summary.body());
        JsonNode body = summary.json();
        assertEquals(1, body.get("states").get("queued").asInt());
        assertEquals(1, body.get("states").get("running").asInt());
        assertEquals(1, body.get("states").get("succeeded").asInt());
        assertEquals(1, body.get("states").get("failed").asInt());
        assertEquals(0, body.get("states").get("cancelled").asInt());
        assertEquals(0, body.get("activeWorkers").asInt());
        // A waiting job and nobody who works the queue off.
        assertTrue(body.get("backlog").asBoolean());
        assertNotNull(body.get("oldestDueAt").asString());

        jdbc.update("insert into job_worker (owner, started_at, seen_at) values ('w', now(), now())");
        body = operator.get("/api/v1/jobs/summary").json();
        assertEquals(1, body.get("activeWorkers").asInt());
        assertFalse(body.get("backlog").asBoolean());

        // A due job older than the backlog limit is backlog even with a worker.
        jdbc.update("update job set run_at = now() - interval '20 minutes' where state = 'queued'");
        body = operator.get("/api/v1/jobs/summary").json();
        assertTrue(body.get("backlog").asBoolean());
        assertTrue(body.get("oldestDueSeconds").asLong() >= 1200);
    }

    @Test
    void anEmptyQueueIsNoBacklogEvenWithoutAWorker() {
        Browser operator = person(AdministrationRight.JOBS_MANAGE);
        job(company(), "test.mail", "succeeded");
        JsonNode body = operator.get("/api/v1/jobs/summary").json();
        assertFalse(body.get("backlog").asBoolean());
        assertTrue(body.get("oldestDueAt").isNull());
        assertTrue(body.get("oldestDueSeconds").isNull());
        assertEquals("no-store", operator.get("/api/v1/jobs/summary").header("Cache-Control"));
    }

    @Test
    void theListShowsNewestFirstInPagesWithoutPayloadAndFiltersByState() {
        Browser operator = person(AdministrationRight.JOBS_MANAGE);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(job(company(), "test.mail", i % 2 == 0 ? "failed" : "succeeded"));
        }
        UUID foreign = job(UUID.randomUUID(), "test.mail", "failed");

        Response first = operator.get("/api/v1/jobs?limit=2");
        assertEquals(200, first.status(), first.body());
        assertEquals(2, first.json().size());
        assertEquals(ids.get(4).toString(), first.json().get(0).get("id").asString());
        assertEquals(ids.get(3).toString(), first.json().get(1).get("id").asString());
        assertFalse(first.body().contains("secret-ticket"), first.body());
        assertFalse(first.body().contains("payload"), first.body());
        long position = first.json().get(1).get("position").asLong();
        Response next = operator.get("/api/v1/jobs?limit=2&before=" + position);
        assertEquals(ids.get(2).toString(), next.json().get(0).get("id").asString());

        Response failed = operator.get("/api/v1/jobs?state=failed");
        assertEquals(3, failed.json().size());
        assertFalse(failed.body().contains(foreign.toString()));
        JsonNode one = failed.json().get(0);
        assertEquals("failed", one.get("state").asString());
        assertEquals("job.failed", one.get("errorCode").asString());
        assertNotNull(one.get("errorReference").asString());
        assertEquals("test.mail", one.get("kind").asString());
        assertFalse(one.get("installation").asBoolean());

        assertProblem(operator.get("/api/v1/jobs?state=broken"), 400, "job.state_invalid");
        assertProblem(operator.get("/api/v1/jobs?limit=101"), 400, "job.limit_invalid");
        assertProblem(operator.get("/api/v1/jobs?limit=0"), 400, "job.limit_invalid");
    }

    @Test
    void aFailedJobRunsAgainWithAllItsAttemptsAndTheRetryIsAudited() {
        Browser operator = person(AdministrationRight.JOBS_MANAGE);
        UUID failed = job(company(), "test.mail", "failed");
        Response retried = operator.send("POST", "/api/v1/jobs/" + failed + "/retry", null, null, null);
        assertEquals(200, retried.status(), retried.body());
        assertEquals("queued", retried.json().get("state").asString());
        assertEquals(0, retried.json().get("attempts").asInt());
        assertTrue(retried.json().get("errorCode").isNull());
        Map<String, Object> audit = jdbc.queryForMap("select operation, job_id, actor from job_audit_event");
        assertEquals("job.retried", audit.get("operation"));
        assertEquals(failed, audit.get("job_id"));
        assertTrue(((String) audit.get("actor")).startsWith("person-"));

        // A waiting job cannot be retried; a job of another company does not exist for this installation.
        assertProblem(operator.send("POST", "/api/v1/jobs/" + failed + "/retry", null, null, null), 409, "job.state_conflict");
        UUID foreign = job(UUID.randomUUID(), "test.mail", "failed");
        assertProblem(operator.send("POST", "/api/v1/jobs/" + foreign + "/retry", null, null, null), 404, "job.not_found");
        assertProblem(operator.send("POST", "/api/v1/jobs/" + UUID.randomUUID() + "/retry", null, null, null), 404, "job.not_found");
        assertEquals("failed", jdbc.queryForObject("select state from job where id = ?", String.class, foreign));
        assertEquals(1, audits());
    }

    @Test
    void onlyAWaitingJobCanBeCancelledAndACancelledOneCanRunAgain() {
        Browser operator = person(AdministrationRight.JOBS_MANAGE);
        UUID queued = job(company(), "test.mail", "queued");
        UUID running = job(company(), "test.mail", "running");
        Response cancelled = operator.send("POST", "/api/v1/jobs/" + queued + "/cancel", null, null, null);
        assertEquals(200, cancelled.status(), cancelled.body());
        assertEquals("cancelled", cancelled.json().get("state").asString());
        assertNotNull(cancelled.json().get("finishedAt").asString());
        assertProblem(operator.send("POST", "/api/v1/jobs/" + running + "/cancel", null, null, null), 409, "job.state_conflict");
        assertProblem(operator.send("POST", "/api/v1/jobs/" + queued + "/cancel", null, null, null), 409, "job.state_conflict");

        Response again = operator.send("POST", "/api/v1/jobs/" + queued + "/retry", null, null, null);
        assertEquals("queued", again.json().get("state").asString());
        assertEquals(List.of("job.cancelled", "job.retried"), jdbc.queryForList("select operation from job_audit_event order by id", String.class));
    }

    @Test
    void aWithdrawnRightIsSeenByTheNextRequest() {
        var issued = accounts.create("test", "withdrawn-" + COUNTER.incrementAndGet(), null);
        AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), AdministrationRight.JOBS_MANAGE);
        Browser browser = anonymous();
        assertEquals(200, browser.json("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null).status());
        // Before the password change the login is not complete.
        assertProblem(browser.get("/api/v1/jobs/summary"), 403, "auth.password_change_required");
        assertEquals(200, browser.json("POST", "/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG), null).status());
        UUID failed = job(company(), "test.mail", "failed");
        assertEquals(200, browser.get("/api/v1/jobs/summary").status());
        AdministrationFixture.withdrawAll(jdbc, companies, transactions, issued.account().id());
        assertProblem(browser.get("/api/v1/jobs/summary"), 403, "auth.forbidden");
        assertProblem(browser.send("POST", "/api/v1/jobs/" + failed + "/retry", null, null, null), 403, "auth.forbidden");
        assertEquals("failed", jdbc.queryForObject("select state from job where id = ?", String.class, failed));
    }
}
