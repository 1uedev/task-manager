package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.TestDatabase;
import taskmanager.identity.ApiClient.Response;
import taskmanager.identity.account.AccountService;
import taskmanager.permissions.PermissionContexts;

/**
 * TASK-039: the permissions context of a real request is built on the server from the session, the company of the
 * installation and the current rights version. The probe route stands for any later business endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PermissionContextsProbeTest.Probe.class)
class PermissionContextsProbeTest {
    @RestController
    static class Probe {
        private final PermissionContexts contexts;

        Probe(PermissionContexts contexts) {
            this.contexts = contexts;
        }

        @GetMapping("/api/v1/permission-context-probe")
        Map<String, Object> probe() {
            var context = contexts.require();
            return Map.of("companyId", context.companyId().toString(), "userId", context.userId().toString(),
                "sessionId", context.sessionId().toString(), "rightsVersion", context.rightsVersion(),
                "passwordChangeRequired", context.passwordChangeRequired(), "external", context.external(),
                "organization", String.valueOf(context.organizationId()));
        }
    }

    private static final String DATABASE = freshDatabase();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
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

    @Test
    void theContextOfARequestComesFromTheServerNotFromTheClient() {
        var issued = accounts.bootstrapAdministrator("probe-admin");
        ApiClient anonymous = new ApiClient(port);
        assertEquals(401, anonymous.get("/api/v1/permission-context-probe").status(), "no login, no handler");

        ApiClient browser = new ApiClient(port);
        assertEquals(200, browser.get("/api/v1/session").status());
        assertEquals(200, browser.post("/api/v1/session", Map.of("username", "probe-admin", "password", issued.oneTimePassword())).status());
        assertEquals(200, browser.post("/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", "Correct-Horse-Battery-9")).status());
        // A company or user supplied by the client is ignored: nothing in the request is read for it.
        browser.headers.put("X-Company-Id", UUID.randomUUID().toString());
        Response response = browser.get("/api/v1/permission-context-probe?companyId=" + UUID.randomUUID());
        assertEquals(200, response.status(), response.body());
        assertEquals(jdbc.queryForObject("select id from platform_company", String.class), response.json().get("companyId").asString());
        assertEquals(issued.account().id().toString(), response.json().get("userId").asString());
        assertEquals(3, response.json().get("rightsVersion").asInt(), "the version after the initial setup");
        assertFalse(response.json().get("passwordChangeRequired").asBoolean());
        assertFalse(response.json().get("external").asBoolean());
        assertEquals("null", response.json().get("organization").asString());
    }
}
