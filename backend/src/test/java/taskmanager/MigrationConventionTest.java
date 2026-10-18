package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.modulith.core.ApplicationModule;

/** Enforces the migration convention from backend/README.md before any database is involved. */
class MigrationConventionTest {
    private static final Pattern NAME = Pattern.compile("V(\\d{4})__([a-z]+)_[a-z0-9]+(?:_[a-z0-9]+)*\\.sql");

    @Test
    void everyMigrationIsVersionedOwnedByAModuleAndUnique() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*");
        Set<String> modules = ModuleStructureTest.MODULES.stream().map(module -> module.getIdentifier().toString()).collect(Collectors.toSet());
        List<String> names = Arrays.stream(resources).map(Resource::getFilename).sorted().toList();
        assertFalse(names.isEmpty());
        Set<Integer> versions = new HashSet<>();
        List<Integer> ordered = new ArrayList<>();
        for (String name : names) {
            Matcher matcher = NAME.matcher(name);
            assertTrue(matcher.matches(), "Migration name violates the convention: " + name);
            assertTrue(modules.contains(matcher.group(2)), "Unknown owning module in " + name);
            int version = Integer.parseInt(matcher.group(1));
            assertTrue(versions.add(version), "Duplicate migration version in " + name);
            ordered.add(version);
        }
        assertEquals(1, ordered.getFirst(), "The schema history starts with V0001");
    }
}
