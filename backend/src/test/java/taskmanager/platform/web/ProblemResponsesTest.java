package taskmanager.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.TestDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ProblemResponsesTest.FailingEndpoint.class)
class ProblemResponsesTest {
    private static final String SECRET = "synthetic-secret-value";

    @RestController
    static class FailingEndpoint {
        @GetMapping("/test-only/failure")
        String fail() {
            throw new IllegalStateException(SECRET);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Value("${local.server.port}") int port;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void unknownPathIsAProblemWithCodeAndCorrelationId() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/unknown?filter=" + SECRET);
        JsonNode problem = assertProblem(response, 404, "resource.not_found");
        assertFalse(problem.has("detail"));
    }

    @Test
    void unsupportedMethodIsAProblem() throws Exception {
        assertProblem(send("DELETE", "/test-only/failure"), 405, "request.method_not_allowed");
    }

    @Test
    void unexpectedErrorRevealsNoInternals() throws Exception {
        HttpResponse<String> response = send("GET", "/test-only/failure");
        assertProblem(response, 500, "server.internal_error");
        assertFalse(response.body().contains(SECRET), response.body());
        assertFalse(response.body().contains("IllegalStateException"), response.body());
    }

    @Test
    void clientCannotChooseTheCorrelationId() throws Exception {
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(uri("/api/v1/unknown")).header(CorrelationIdFilter.HEADER, "chosen-by-client").build(),
            HttpResponse.BodyHandlers.ofString());
        String id = response.headers().firstValue(CorrelationIdFilter.HEADER).orElseThrow();
        assertFalse(id.equals("chosen-by-client"));
        assertFalse(response.body().contains("chosen-by-client"), response.body());
    }

    private JsonNode assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow());
        JsonNode problem = json.readTree(response.body());
        assertEquals(status, problem.get("status").asInt());
        assertEquals(code, problem.get("code").asString());
        assertTrue(problem.has("title"));
        String header = response.headers().firstValue(CorrelationIdFilter.HEADER).orElseThrow();
        assertEquals(header, problem.get("correlationId").asString());
        UUID.fromString(header);
        return problem;
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
