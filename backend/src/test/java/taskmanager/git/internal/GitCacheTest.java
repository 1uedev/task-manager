package taskmanager.git.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The cache bound to the generation of a connection (TASK-114, F2) and the rate limits of GitHub (TASK-114, F8). */
class GitCacheTest {
    private final UUID person = UUID.randomUUID();
    private final UUID provider = UUID.randomUUID();
    private final GitCache cache = new GitCache(Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void aLateAnswerOfAFormerConnectionIsNeverReadByTheNewOne() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread late = Thread.ofVirtual().start(() -> cache.read(person, provider, "old", "change:1:7", false, () -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return "seen by the former Git account";
        }));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        // The person connects another Git account meanwhile; then the former answer arrives and is written.
        cache.forget(person, provider);
        release.countDown();
        late.join(5000);
        AtomicInteger loads = new AtomicInteger();
        assertEquals("fresh", cache.read(person, provider, "new", "change:1:7", false, () -> {
            loads.incrementAndGet();
            return "fresh";
        }));
        assertEquals(1, loads.get(), "the new generation reads the provider itself");
        assertEquals("fresh", cache.read(person, provider, "new", "change:1:7", false, () -> "again"), "and keeps its own answer");
    }

    @Test
    void aSecondaryLimitIsALimit() {
        byte[] none = new byte[0];
        assertTrue(new GitHttp.Answer(429, Map.of(), none).limited());
        assertTrue(new GitHttp.Answer(403, Map.of("x-ratelimit-remaining", List.of("0")), none).limited());
        assertTrue(new GitHttp.Answer(403, Map.of("Retry-After", List.of("60")), none).limited());
        assertTrue(new GitHttp.Answer(403, Map.of("x-ratelimit-remaining", List.of("4000")),
            "{\"message\":\"You have exceeded a secondary rate limit.\"}".getBytes(StandardCharsets.UTF_8)).limited());
        assertFalse(new GitHttp.Answer(403, Map.of("x-ratelimit-remaining", List.of("4000")),
            "{\"message\":\"Resource not accessible\"}".getBytes(StandardCharsets.UTF_8)).limited(), "an ordinary 403 is no access");
        assertFalse(new GitHttp.Answer(404, Map.of("Retry-After", List.of("60")), none).limited());
    }
}
