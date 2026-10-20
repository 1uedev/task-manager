package taskmanager.identity.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * TASK-041 (review of TASK-040, F2): exactly one implementation of the administration port is accepted. None, several and
 * several with a primary bean are all refused, and no consumer may fall back to Spring's unique lookup, which lets a primary
 * bean win.
 */
class AdministrationRightsWiringTest {
    private static final Port FIRST = new Port();

    @Test
    void noPortIsRefused() {
        var beans = new DefaultListableBeanFactory();
        assertNull(AdministrationRights.unique(beans.getBeanProvider(AdministrationRights.class)));
        assertRefused(beans);
    }

    @Test
    void exactlyOnePortIsUsed() {
        var beans = new DefaultListableBeanFactory();
        register(beans, "only", FIRST, false);
        assertSame(FIRST, AdministrationRights.unique(beans.getBeanProvider(AdministrationRights.class)));
        var requests = requests(beans);
        assertDoesNotThrow(() -> requests.require(AdministrationRight.ACCOUNTS_MANAGE));
        assertDoesNotThrow(() -> requests.early(AdministrationRight.ACCOUNTS_MANAGE));
    }

    @Test
    void twoPortsWithoutAPrimaryAreRefused() {
        var beans = new DefaultListableBeanFactory();
        register(beans, "first", FIRST, false);
        register(beans, "second", new Port(), false);
        assertNull(AdministrationRights.unique(beans.getBeanProvider(AdministrationRights.class)));
        assertRefused(beans);
    }

    @Test
    void twoPortsWithAPrimaryAreRefusedAsWell() {
        var beans = new DefaultListableBeanFactory();
        register(beans, "ordinary", FIRST, false);
        register(beans, "primary", new Port(), true);
        assertNull(AdministrationRights.unique(beans.getBeanProvider(AdministrationRights.class)));
        assertRefused(beans);
    }

    /** Every consumer of the port goes through the counting lookup. */
    @Test
    void noConsumerAsksForAUniqueBeanOfThePort() throws IOException {
        Path main = Path.of("src/main/java");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(main)) {
            offenders = files.filter(file -> file.toString().endsWith(".java"))
                .filter(file -> {
                    try {
                        String source = Files.readString(file);
                        return source.contains("ObjectProvider<AdministrationRights>") && source.contains("getIfUnique");
                    } catch (IOException failure) {
                        throw new IllegalStateException(failure);
                    }
                })
                .map(file -> main.relativize(file).toString())
                .toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static void assertRefused(DefaultListableBeanFactory beans) {
        var requests = requests(beans);
        for (Runnable call : List.<Runnable>of(() -> requests.require(AdministrationRight.ACCOUNTS_MANAGE),
                () -> requests.early(AdministrationRight.ACCOUNTS_MANAGE))) {
            ApiProblemException refused = assertThrows(ApiProblemException.class, call::run);
            assertEquals(403, refused.status());
        }
    }

    private static AdministrationRequests requests(DefaultListableBeanFactory beans) {
        return new AdministrationRequests(beans.getBeanProvider(AdministrationRights.class), new DataSourceTransactionManager());
    }

    private static void register(DefaultListableBeanFactory beans, String name, Port port, boolean primary) {
        var definition = new RootBeanDefinition(AdministrationRights.class, () -> port);
        definition.setPrimary(primary);
        beans.registerBeanDefinition(name, definition);
    }

    /** Allows everything, so that only the wiring can refuse. */
    private static class Port implements AdministrationRights {
        @Override
        public void require(AdministrationRight right) {
        }

        @Override
        public Set<AdministrationRight> held() {
            return Set.of(AdministrationRight.values());
        }
    }
}
