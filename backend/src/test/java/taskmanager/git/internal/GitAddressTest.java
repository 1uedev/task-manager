package taskmanager.git.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-116 (H1): the shared address vectors of contracts/cases/git-addresses.json against the server; gitLinks.test.mjs runs
 * the same vectors against the browser stand in. A reading counts as the server takes it for a link: parsed and with a
 * valid key.
 */
class GitAddressTest {
    @Test
    void theSharedAddressVectorsReadAsTheServerReadsThem() throws Exception {
        JsonNode vectors = JsonMapper.builder().build().readTree(Files.readString(Path.of("..", "contracts", "cases", "git-addresses.json")));
        Map<UUID, String> names = new HashMap<>();
        List<Provider> providers = new ArrayList<>();
        Instant at = Instant.parse("2026-11-14T12:00:00Z");
        for (JsonNode entry : vectors.get("providers")) {
            UUID id = UUID.randomUUID();
            names.put(id, entry.get("id").asString());
            providers.add(new Provider(id, UUID.randomUUID(), Provider.Kind.of(entry.get("kind").asString()), entry.get("id").asString(),
                entry.get("baseUrl").asString(), "client", null, null, 1, at, UUID.randomUUID(), at, UUID.randomUUID(), null));
        }
        int checked = 0;
        for (JsonNode entry : vectors.get("cases")) {
            List<String> expected = new ArrayList<>();
            for (JsonNode reading : entry.get("expected")) {
                expected.add(reading.get("provider").asString() + " " + reading.get("path").asString() + " " + reading.get("kind").asString()
                    + " " + reading.get("key").asString());
            }
            List<String> actual = GitAddress.parse(entry.get("input").asString(), providers).stream()
                .filter(p -> GitAddress.validKey(p.kind(), p.key()))
                .map(p -> names.get(p.provider().id()) + " " + p.repositoryPath() + " " + p.kind() + " " + p.key()).toList();
            assertEquals(expected, actual, entry.get("input").asString());
            checked++;
        }
        assertEquals(vectors.get("cases").size(), checked);
    }
}
