package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModuleStructureTest {
    static final ApplicationModules MODULES = ApplicationModules.of(TaskManagerApplication.class);

    @Test
    void containsTheFirstSliceModules() {
        Set<String> names = MODULES.stream().map(module -> module.getIdentifier().toString()).collect(Collectors.toSet());
        assertEquals(Set.of("platform", "identity", "permissions", "projects", "tickets", "tags", "search"), names);
    }

    @Test
    void boundariesAreRespected() {
        MODULES.verify();
    }
}
