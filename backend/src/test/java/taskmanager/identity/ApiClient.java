package taskmanager.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A minimal browser stand in: keeps cookies by name (the {@code Secure} flag is ignored because the tests
 * speak plain HTTP to localhost) and sends the CSRF header like the interface would.
 */
final class ApiClient {
    record Response(int status, String body, Map<String, List<String>> headers, JsonNode json) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }

        List<String> setCookies() {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("set-cookie"))
                .flatMap(e -> e.getValue().stream()).toList();
        }

        String code() {
            return json.get("code").asString();
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;
    final Map<String, String> cookies = new LinkedHashMap<>();
    /** Extra headers for the next requests, for example a foreign Origin. */
    final Map<String, String> headers = new LinkedHashMap<>();
    /** When false the CSRF header is not sent. */
    boolean sendCsrf = true;
    /** Overrides the CSRF header value when set. */
    String csrfHeaderOverride;

    ApiClient(int port) {
        this.base = "http://localhost:" + port;
    }

    Response get(String path) {
        return send("GET", path, null);
    }

    Response post(String path, Object body) {
        return send("POST", path, body);
    }

    Response put(String path, Object body) {
        return send("PUT", path, body);
    }

    Response delete(String path) {
        return send("DELETE", path, null);
    }

    Response send(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
            if (body != null) {
                request.header("Content-Type", "application/json");
                request.method(method, HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : JSON.writeValueAsString(body)));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            if (!cookies.isEmpty()) {
                List<String> pairs = new ArrayList<>();
                cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                request.header("Cookie", String.join("; ", pairs));
            }
            if (sendCsrf) {
                String token = csrfHeaderOverride != null ? csrfHeaderOverride : cookies.get("__Host-tm-csrf");
                if (token != null) {
                    request.header("X-CSRF-Token", token);
                }
            }
            headers.forEach(request::header);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            for (String cookie : response.headers().allValues("set-cookie")) {
                String pair = cookie.substring(0, cookie.indexOf(';') < 0 ? cookie.length() : cookie.indexOf(';'));
                String name = pair.substring(0, pair.indexOf('='));
                String value = pair.substring(pair.indexOf('=') + 1);
                if (cookie.toLowerCase().contains("max-age=0")) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
            JsonNode json = response.body().isEmpty() ? null : JSON.readTree(response.body());
            return new Response(response.statusCode(), response.body(), response.headers().map(), json);
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    ApiClient copy() {
        // Shares nothing: a second browser.
        return new ApiClient(Integer.parseInt(base.substring(base.lastIndexOf(':') + 1)));
    }
}
