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
}
