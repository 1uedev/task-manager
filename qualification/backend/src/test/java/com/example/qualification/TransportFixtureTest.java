package com.example.qualification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class TransportFixtureTest {
    record Probe(UUID id, String label, String version, String note) {}
    record Problem(String type, String title, int status, String code) {}
    @Test void javaWireFixturesFeedTheTypescriptClientTests() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        Path directory = Path.of("target", "contract-fixtures");
        Files.createDirectories(directory);
        String body = mapper.writeValueAsString(new Probe(UUID.fromString("00000000-0000-4000-8000-000000000021"), "Prüfdatensatz", "opaque-v1", null));
        assertEquals("opaque-v1", mapper.readTree(body).get("version").asText());
        Files.writeString(directory.resolve("success.json"), body);
        Files.writeString(directory.resolve("conflict.json"), mapper.writeValueAsString(new Problem("about:blank", "Version conflict", 412, "VERSION_CONFLICT")));
        Files.writeString(directory.resolve("forbidden.json"), mapper.writeValueAsString(new Problem("about:blank", "Forbidden", 403, "FORBIDDEN")));
    }
}
