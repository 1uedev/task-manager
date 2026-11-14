package taskmanager.git.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * What a person last read of a Git object (TASK-112, rule 5): only in memory, per person, provider and object, for 60
 * seconds, never handed to another person and never written to the database. An answer that the object is not available is
 * kept like a found one; a failure, a rate limit or a missing connection is never kept. "Aktualisieren" reads past it,
 * disconnecting forgets what the person read from that provider.
 */
@Component
class GitCache {
    static final Duration TTL = Duration.ofSeconds(60);
    private static final int MAX_ENTRIES = 20_000;

    private final Clock clock;
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    GitCache(Clock clock) {
        this.clock = clock;
    }

    private record Key(UUID accountId, UUID providerId, String object) {
    }

    private record Entry(Instant until, Object value) {
    }

    @SuppressWarnings("unchecked")
    <T> T read(UUID accountId, UUID providerId, String object, boolean refresh, Supplier<T> load) {
        Key key = new Key(accountId, providerId, object);
        Instant now = clock.instant();
        Entry entry = entries.get(key);
        if (!refresh && entry != null && entry.until().isAfter(now)) {
            return (T) entry.value();
        }
        T value = load.get();
        if (entries.size() >= MAX_ENTRIES) {
            entries.values().removeIf(old -> !old.until().isAfter(now));
            if (entries.size() >= MAX_ENTRIES) {
                entries.clear();
            }
        }
        entries.put(key, new Entry(now.plus(TTL), value));
        return value;
    }

    void forget(UUID accountId, UUID providerId) {
        entries.keySet().removeIf(key -> key.accountId().equals(accountId) && key.providerId().equals(providerId));
    }

    void forgetAccount(UUID accountId) {
        entries.keySet().removeIf(key -> key.accountId().equals(accountId));
    }

    void forgetProvider(UUID providerId) {
        entries.keySet().removeIf(key -> key.providerId().equals(providerId));
    }
}
