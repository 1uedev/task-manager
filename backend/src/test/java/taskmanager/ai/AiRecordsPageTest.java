package taskmanager.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-129, H2: the record of the executions in pages. With exactly 201 entries a page of 200 names its next page, which holds
 * the last entry and no further link. A database of its own keeps the count exact.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiRecordsPageTest {
    private static final String DATABASE = freshDatabase();
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

    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (!cookies.isEmpty()) {
            request.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
        }
        if (cookies.containsKey("__Host-tm-csrf")) {
            request.header("X-CSRF-Token", cookies.get("__Host-tm-csrf"));
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        for (String cookie : response.headers().allValues("set-cookie")) {
            String pair = cookie.substring(0, cookie.indexOf(';') < 0 ? cookie.length() : cookie.indexOf(';'));
            cookies.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
        }
        return response;
    }

    @Test
    void aFullPageOf200NamesItsNextPage() throws Exception {
        var issued = accounts.create("test", "ai-records", null);
        AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), AdministrationRight.AI_MANAGE);
        assertEquals(200, send("GET", "/api/v1/session", null).statusCode());
        assertEquals(200, send("POST", "/api/v1/session", Map.of("username", issued.account().username(), "password", issued.oneTimePassword())).statusCode());
        assertEquals(200, send("POST", "/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword",
            "Correct-Horse-Battery-9")).statusCode());
        UUID company = companies.find().orElseThrow();
        Instant start = Instant.parse("2026-11-17T08:00:00Z");
        for (int i = 0; i < 201; i++) {
            jdbc.update("""
                    insert into ai_run (id, company_id, account_id, kind, use_case, language, state, code, started_at, finished_at)
                    values (?, ?, ?, 'use', 'ticket_summary', 'de', 'refused', 'ai.not_assigned', ?, ?)
                    """, UUID.randomUUID(), company, issued.account().id(), Timestamp.from(start.plusSeconds(i)), Timestamp.from(start.plusSeconds(i)));
        }
        assertEquals(201, jdbc.queryForObject("select count(*) from ai_run", Integer.class));

        HttpResponse<String> first = send("GET", "/api/v1/ai/admin/runs?limit=200", null);
        assertEquals(200, first.statusCode(), first.body());
        JsonNode page = JSON.readTree(first.body());
        assertEquals(200, page.get("runs").size());
        assertTrue(page.get("next").isString(), first.body());
        HttpResponse<String> second = send("GET", "/api/v1/ai/admin/runs?limit=200&before="
            + URLEncoder.encode(page.get("next").asString(), StandardCharsets.UTF_8), null);
        JsonNode rest = JSON.readTree(second.body());
        assertEquals(1, rest.get("runs").size());
        assertTrue(rest.get("next").isNull());
        assertEquals(start.toString(), Instant.parse(rest.get("runs").get(0).get("startedAt").asString()).toString());
    }
}
