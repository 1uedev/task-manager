package taskmanager.helpdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import taskmanager.TestDatabase;

/** The published contract of the helpdesk and the mapped routes must not drift apart. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class HelpdeskContractTest {
    private static final Path CONTRACT = Path.of("..", "contracts", "helpdesk.openapi.yaml");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @SuppressWarnings("unchecked")
    private static Set<String> documented() throws IOException {
        Map<String, Object> document = new Yaml().load(Files.readString(CONTRACT));
        Set<String> operations = new TreeSet<>();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");
        for (var path : paths.entrySet()) {
            for (String method : path.getValue().keySet()) {
                if (Set.of("get", "post", "put", "delete", "patch").contains(method)) {
                    operations.add(method.toUpperCase() + " /api/v1" + path.getKey().replaceAll("\\{[^}]+}", "{id}"));
                }
            }
        }
        return operations;
    }

    private Set<String> mapped() {
        Set<String> operations = new TreeSet<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getName().startsWith("taskmanager.helpdesk.")) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            for (String pattern : info.getPatternValues()) {
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    operations.add(method + " " + pattern.replaceAll("\\{[^}]+}", "{id}"));
                }
            }
        }
        return operations;
    }

    @Test
    void everyMappedRouteIsDocumentedAndEveryDocumentedRouteExists() throws IOException {
        Set<String> mapped = mapped();
        assertEquals(28, mapped.size(), mapped.toString());
        assertEquals(mapped, documented());
    }
}
