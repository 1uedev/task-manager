package taskmanager;

import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Composition root. Direct sub-packages are application modules checked by Spring Modulith. The classes in this package
 * wire ports of one module to what another module offers, because no module may depend on the other in that direction.
 */
@SpringBootApplication
public class TaskManagerApplication {
    /** Container commands that run once and exit instead of starting the web server (TASK-024). */
    private static final Set<String> ADMIN_COMMANDS = Set.of("bootstrap-admin", "reset-admin-password");
    /** Builds the search projection again from the owning modules (TASK-029). */
    static final String REBUILD_SEARCH = "rebuild-search";

    public static void main(String[] args) {
        if (args.length > 0 && ADMIN_COMMANDS.contains(args[0])) {
            System.exit(runAdminCommand(args, new String[0]));
        }
        if (args.length > 0 && REBUILD_SEARCH.equals(args[0])) {
            System.exit(runSearchCommand(args, new String[0]));
        }
        SpringApplication.run(TaskManagerApplication.class, args);
    }

    static int runAdminCommand(String[] args, String... extraProperties) {
        if (args.length != 2) {
            System.err.println("Usage: " + args[0] + " <login name>");
            return 2;
        }
        var builder = new SpringApplicationBuilder(TaskManagerApplication.class)
            .web(WebApplicationType.NONE)
            .bannerMode(org.springframework.boot.Banner.Mode.OFF)
            .properties("taskmanager.admin-command=" + args[0], "taskmanager.admin-username=" + args[1],
                "logging.level.root=WARN", "spring.main.log-startup-info=false");
        // Command line arguments, so that tests can override settings that application.yaml resolves from the environment.
        var context = builder.run(java.util.Arrays.stream(extraProperties).map(property -> "--" + property).toArray(String[]::new));
        return SpringApplication.exit(context);
    }

    static int runSearchCommand(String[] args, String... extraProperties) {
        if (args.length != 1) {
            System.err.println("Usage: " + REBUILD_SEARCH);
            return 2;
        }
        var builder = new SpringApplicationBuilder(TaskManagerApplication.class)
            .web(WebApplicationType.NONE)
            .bannerMode(org.springframework.boot.Banner.Mode.OFF)
            .properties("taskmanager.search-command=" + args[0], "logging.level.root=WARN", "spring.main.log-startup-info=false");
        var context = builder.run(java.util.Arrays.stream(extraProperties).map(property -> "--" + property).toArray(String[]::new));
        return SpringApplication.exit(context);
    }
}
