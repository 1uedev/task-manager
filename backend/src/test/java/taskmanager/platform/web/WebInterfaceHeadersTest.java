package taskmanager.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import taskmanager.TestDatabase;

/** The image serves the built interface from the same origin as the API (TASK-031); the test classpath holds a synthetic stand-in. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebInterfaceHeadersTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Value("${local.server.port}") int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void rootServesTheInterfaceAndIsCheckedAgainOnEveryVisit() throws Exception {
        HttpResponse<String> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("Synthetic test page"));
        assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElse(null));
        assertProtected(response);
    }

    @Test
    void hashedAssetsMayBeCachedForLong() throws Exception {
        HttpResponse<String> response = get("/assets/index-test0001.js");
        assertEquals(200, response.statusCode());
        assertEquals("public, max-age=31536000, immutable", response.headers().firstValue("Cache-Control").orElse(null));
        assertProtected(response);
    }

    @Test
    void apiAnswersStayUncached() throws Exception {
        HttpResponse<String> response = get("/api/v1/session");
        assertEquals(200, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertProtected(response);
    }

    private static void assertProtected(HttpResponse<String> response) {
        assertEquals(WebInterfaceHeadersFilter.CONTENT_SECURITY_POLICY, response.headers().firstValue("Content-Security-Policy").orElse(null));
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElse(null));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
