package taskmanager.ai.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TASK-129: canonical addresses, the target of a key and requests whose headers cannot be built, without a server. */
class AiInternalsTest {
    private static final AiProperties PROPERTIES = new AiProperties("", Duration.ofSeconds(3), Duration.ofSeconds(1), Duration.ofSeconds(3), 1, 1,
        Duration.ofHours(24), 60000, List.of("localhost"), "https://api.openai.com", "https://api.anthropic.com");

    @Test
    void twoSpellingsOfOneServerAreOneAddress() {
        assertEquals("https://api.openai.com/v1", AiTargets.canonical("HTTPS://API.OpenAI.COM.:443/v1/").url());
        assertEquals("http://localhost:11434", AiTargets.canonical("http://LocalHost:11434//").url());
        assertEquals("http://ollama.example", AiTargets.canonical("http://ollama.example:80").url());
        assertEquals("https://xn--bcher-kva.example", AiTargets.canonical("https://Bücher.example").url());
        assertEquals("https://[::1]:8443", AiTargets.canonical("https://[::1]:8443/").url());
        for (String invalid : List.of("ftp://x.example", "https://user@x.example", "https://x.example/?a=1", "https://x.example/#f",
                "https://x.example/a b", "https://.", "x.example")) {
            assertNull(AiTargets.canonical(invalid), invalid);
        }
        assertTrue(AiTargets.canonical("https://API.anthropic.com.").external());
        assertFalse(AiTargets.canonical("https://ollama.example").external());
        assertTrue(AiTargets.canonical("http://localhost:11434").released(List.of("localhost")));
        assertTrue(AiTargets.canonical("http://ollama.lan").released(List.of("ollama.lan:80")));
        assertFalse(AiTargets.canonical("http://ollama.lan:8080").released(List.of("ollama.lan:80")));
    }

    @Test
    void aCallOfAnAlreadyCancelledThreadSendsNothing() throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            AiHttp http = new AiHttp(PROPERTIES);
            AiProvider provider = new AiProvider(UUID.randomUUID(), UUID.randomUUID(), AiProvider.Kind.OLLAMA, "x", base, null, null, null,
                AiProvider.DataPath.LOCAL, true, 1, Instant.now(), UUID.randomUUID(), Instant.now(), UUID.randomUUID(), null);
            // Proof that the server is reachable at all, so that the next assertion means something.
            assertEquals(200, http.get(provider, base + "/api/tags", Map.of(), Duration.ofSeconds(2)).status());
            assertEquals(1, requests.get());
            Thread.currentThread().interrupt();
            try {
                AiHttp.Failure get = assertThrows(AiHttp.Failure.class, () -> http.get(provider, base + "/api/tags", Map.of(), Duration.ofSeconds(2)));
                assertEquals("ai.cancelled", get.code());
                AiHttp.Failure post = assertThrows(AiHttp.Failure.class,
                    () -> http.postJson(provider, base + "/api/chat", Map.of(), "{}", Duration.ofSeconds(2)));
                assertEquals("ai.cancelled", post.code());
                assertTrue(Thread.currentThread().isInterrupted(), "the mark of the cancellation stays for the caller");
            } finally {
                Thread.interrupted();
            }
            Thread.sleep(200);
            assertEquals(1, requests.get(), "no request left the process after the cancellation");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theTargetOfAKeyCoversAddressAndCa() {
        String plain = AiTargets.target("https://a.example", null);
        assertEquals(plain, AiTargets.target("https://a.example", null));
        assertFalse(plain.equals(AiTargets.target("https://b.example", null)));
        assertFalse(plain.equals(AiTargets.target("https://a.example", "-----BEGIN CERTIFICATE-----")));
        assertTrue(plain.matches("[0-9a-f]{64}"));
    }

    @Test
    void aRequestWithAnInvalidHeaderFailsWithoutItsText() {
        AiHttp http = new AiHttp(PROPERTIES);
        AiProvider provider = new AiProvider(UUID.randomUUID(), UUID.randomUUID(), AiProvider.Kind.OPENAI, "x", "http://localhost:1", null, null,
            null, AiProvider.DataPath.LOCAL, true, 1, Instant.now(), UUID.randomUUID(), Instant.now(), UUID.randomUUID(), null);
        AiHttp.Failure failure = assertThrows(AiHttp.Failure.class,
            () -> http.get(provider, "http://localhost:1/v1/models", Map.of("Authorization", "Bearer secret\r\nX: y"), Duration.ofSeconds(1)));
        assertEquals("ai.request_invalid", failure.code());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("secret"));
    }

    @Test
    void validKeysAreVisibleAscii() {
        assertTrue(ConfigService.validKey("sk-abc_123.XYZ"));
        assertTrue(ConfigService.validKey("k".repeat(500)));
        assertFalse(ConfigService.validKey("k".repeat(501)));
        assertFalse(ConfigService.validKey("sk abc"));
        assertFalse(ConfigService.validKey("sk-ä"));
        assertFalse(ConfigService.validKey("sk-\n"));
    }

    @Test
    void ipv6LiteralsHaveOneForm() {
        Map<String, String> forms = new java.util.LinkedHashMap<>();
        forms.put("[::1]", "[::1]");
        forms.put("[0:0:0:0:0:0:0:1]", "[::1]");
        forms.put("[0000:0000:0000:0000:0000:0000:0000:0001]", "[::1]");
        forms.put("[::]", "[::]");
        forms.put("[0:0:0:0:0:0:0:0]", "[::]");
        forms.put("[2001:DB8:0:0:1:0:0:1]", "[2001:db8::1:0:0:1]");
        forms.put("[2001:db8:0:0:0:0:2:1]", "[2001:db8::2:1]");
        forms.put("[2001:0db8:0:1:1:1:1:1]", "[2001:db8:0:1:1:1:1:1]");
        forms.put("[1:0:0:2:0:0:0:3]", "[1:0:0:2::3]");
        forms.put("[::ffff:192.0.2.1]", "[::ffff:c000:201]");
        forms.put("[FE80::1]", "[fe80::1]");
        forms.forEach((given, expected) -> assertEquals(expected, AiTargets.ipv6(given), given));
        for (String invalid : List.of("[1:2:3]", "[1::2::3]", "[12345::1]", "[g::1]", "[1:2:3:4:5:6:7:8:9]", "[::1.2.3]", "[::256.1.1.1]", "::1", "[]")) {
            assertNull(AiTargets.ipv6(invalid), invalid);
        }
        assertEquals("https://[::1]:8443", AiTargets.canonical("https://[0:0:0:0:0:0:0:1]:8443/").url());
        assertTrue(AiTargets.canonical("http://[0:0:0:0:0:0:0:1]:11434").released(List.of("[::1]:11434")));
    }

    @Test
    void entriesOfTheHttpReleaseGetTheSameForm() {
        AiProperties properties = new AiProperties("", Duration.ofSeconds(3), Duration.ofSeconds(1), Duration.ofSeconds(3), 1, 1,
            Duration.ofHours(24), 60000, List.of(" LocalHost ", "[0:0:0:0:0:0:0:1]:11434", "", "Ollama.LAN:8080"), "https://api.openai.com",
            "https://api.anthropic.com");
        assertEquals(List.of("localhost", "[::1]:11434", "ollama.lan:8080"), properties.plainHttpHosts());
        assertThrows(IllegalArgumentException.class, () -> new AiProperties("", Duration.ofSeconds(3), Duration.ofSeconds(1), Duration.ofSeconds(3),
            1, 1, Duration.ofHours(24), 60000, List.of("[1:2:3]"), "https://api.openai.com", "https://api.anthropic.com"));
    }

    @Test
    void aSecretIsFoundInEveryDecodedValue() {
        String secret = "sk-test-7f3a9c41";
        tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        assertTrue(AiFormats.mentions(json.readTree("{\"a\": [{\"b\": \"x \\u0073k-test-7f3a9c41\"}]}"), secret));
        assertTrue(AiFormats.mentions(json.readTree("{\"" + secret + "\": 1}"), secret));
        assertTrue(AiFormats.mentions(json.readTree("[\"" + secret + "\"]"), secret));
        assertFalse(AiFormats.mentions(json.readTree("{\"a\": [\"sk-test\", 7, null, true]}"), secret));
        assertFalse(AiFormats.mentions(json.readTree("{\"a\": \"" + secret + "\"}"), ""));
    }

    @Test
    void oneOuterCodeBlockKeepsTheBackticksOfItsValues() {
        String json = "{\"text\": \"```java x()```\"}";
        assertEquals("```java x()```", AiFormats.object(json).get("text").asString());
        assertEquals("```java x()```", AiFormats.object("```json\n" + json + "\n```").get("text").asString());
        assertThrows(AiFormats.Invalid.class, () -> AiFormats.object("```json\n" + json + "\n```\n```json\n" + json + "\n```"));
        assertThrows(AiFormats.Invalid.class, () -> AiFormats.object("```json\n" + json + "\n``` und mehr"));
        assertThrows(AiFormats.Invalid.class, () -> AiFormats.object("Hier: ```json\n" + json + "\n```"));
    }
}
