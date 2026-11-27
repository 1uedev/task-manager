package taskmanager.operations.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Component;
import taskmanager.operations.ContainerCommand;
import taskmanager.operations.OperationsStart;
import taskmanager.operations.SchemaVersionException;

/**
 * Decides at every start who may change the schema (TASK-132, DEC-162, rule 3; TASK-136, findings F1 and F2; TASK-137,
 * findings N1, N2 and H1). Three cases, each chosen by the entry point and never by a setting alone:
 *
 * <ul>
 *   <li>The container command {@code migrate}, which only the entry point can select (it registers the
 *       {@link ContainerCommand} of its context; no setting can), is the one schema change of an update. It refuses a
 *       database that is newer than the image or holds a failed migration, and otherwise applies what is pending. Flyway
 *       holds a lock on its history while it migrates, so a second {@code migrate} at the same time waits and then finds
 *       nothing left to do.</li>
 *   <li>Every other process the operations template starts: its entry point puts {@code operations} in front of the
 *       command and registers {@link OperationsStart}. Web process, worker and every other command change nothing and
 *       refuse to start when a migration is missing, when the database is newer than the image or when a migration failed.
 *       A schema mode other than {@code verify} from any source refuses the start as well (N1).</li>
 *   <li>Without that argument the setting {@code taskmanager.schema.mode} decides: {@code migrate} or no value keeps the
 *       demonstration and the tests as before and migrates at the start; {@code verify} checks like the operations
 *       template.</li>
 * </ul>
 *
 * <p>Whenever the schema is checked, the check is a condition of the start, not a side effect of the Flyway configuration:
 * switched off Flyway, settings that change the set of migrations, the target, the history table or the baseline, a bound
 * Flyway configuration that differs from the one the image fixes and an empty set of migrations are refused before anything
 * runs, and a context whose refresh ends without the check is refused as well.
 */
@Component
class SchemaGuard implements FlywayMigrationStrategy, ApplicationListener<ContextRefreshedEvent> {
    static final String MIGRATE_COMMAND = "migrate";

    /**
     * The Flyway settings an image carries in application.yaml, with the one value they may have when the schema is
     * checked. Every other Flyway setting is refused then: it could hide migrations (target, locations, ignore patterns),
     * point to another history or database, or accept a history the image did not write (baseline, out of order).
     */
    private static final String LOCATION = "classpath:db/migration";

    private static final Map<String, String> FIXED = Map.of(
        "locations", LOCATION,
        "cleandisabled", "true",
        "baselineonmigrate", "false",
        "validatemigrationnaming", "true");

    private final String mode;
    private final boolean command;
    private final boolean operations;
    private final SchemaMigration migration;
    private volatile boolean checked;

    SchemaGuard(@Value("${taskmanager.schema.mode:}") String mode, ObjectProvider<ContainerCommand> containerCommand,
            ObjectProvider<OperationsStart> operationsStart, ConfigurableEnvironment environment, SchemaMigration migration) {
        this.mode = mode.strip().toLowerCase(Locale.ROOT);
        ContainerCommand registered = containerCommand.getIfAvailable();
        this.command = registered != null && MIGRATE_COMMAND.equals(registered.name());
        this.operations = operationsStart.getIfAvailable() != null;
        this.migration = migration;
        if (!command && !this.mode.isEmpty() && !this.mode.equals("migrate") && !this.mode.equals("verify")) {
            // The raw value stays out of the message: a misplaced secret must not end up in the log (H1).
            throw new SchemaVersionException("The setting TASK_MANAGER_SCHEMA_MODE (taskmanager.schema.mode) has an unknown "
                + "value; allowed are 'migrate' and 'verify'.");
        }
        if (operations && !command && !this.mode.isEmpty() && !this.mode.equals("verify")) {
            // N1: a process of the operations template never migrates at the start, whatever source sets the mode.
            throw new SchemaVersionException("A process of the operations template only checks the schema; the setting "
                + "TASK_MANAGER_SCHEMA_MODE (taskmanager.schema.mode) may only be 'verify' here. Run the command 'migrate' "
                + "for a schema change and remove the setting.");
        }
        if (enforced()) {
            refuseChangedFlywaySettings(environment);
        }
    }

    private boolean verifying() {
        return operations || mode.equals("verify");
    }

    private boolean enforced() {
        return command || verifying();
    }

    @Override
    public void migrate(Flyway flyway) {
        if (enforced()) {
            refuseChangedConfiguration(flyway.getConfiguration());
            refuseOtherSchema(flyway.getConfiguration().getDataSource());
        }
        if (command) {
            MigrationInfoService info = flyway.info();
            refuseNewerOrFailed(info);
            String before = version(info.current());
            int applied;
            try {
                applied = flyway.migrate().migrationsExecuted;
            } catch (FlywayException failed) {
                // A migration of this run failed (H1). PostgreSQL rolls the failed migration back, so the history ends at
                // the last successful one; the migrations before it in this run stay applied, so the previous version no
                // longer fits the database. The report names the version and the documented way back (N10).
                throw new SchemaVersionException("A migration failed in this run; the schema stays at version "
                    + version(flyway.info().current()) + " and no longer fits the previous version. Do not start any "
                    + "version on this database: return with the backup point taken before the update as described in "
                    + "docs/INSTALL.md, section 'Rückkehr'. Cause: " + firstLine(org.springframework.core.NestedExceptionUtils
                    .getMostSpecificCause(failed).getMessage()));
            }
            migration.record(before, version(flyway.info().current()), applied);
            checked = true;
            return;
        }
        if (verifying()) {
            MigrationInfoService info = flyway.info();
            refuseNewerOrFailed(info);
            List<MigrationInfo> pending = Arrays.asList(info.pending());
            if (!pending.isEmpty()) {
                throw new SchemaVersionException("The database has the schema version " + version(info.current()) + ", this image "
                    + "needs " + version(pending.getLast()) + ": " + pending.size() + " migration(s) are missing ("
                    + versions(pending) + "). Take a backup point, stop web process and worker and run the command "
                    + "'migrate' once; this process never changes the schema itself.");
            }
            flyway.validate();
            checked = true;
            return;
        }
        flyway.migrate();
    }

    /**
     * The getters of the Flyway configuration that may differ from the image (TASK-139, F5 of the review of TASK-137): how
     * the process reaches its database and how Flyway itself is loaded, none of which changes which migrations run, how
     * they are checked or what they contain. Every other value of {@link Configuration}, also one a later Flyway adds, must
     * equal the configuration the image fixes.
     */
    static final Set<String> CONNECTION_ONLY = Set.of("data-source", "cached-data-sources", "url", "user", "password",
        "driver", "connect-retries", "connect-retries-interval", "lock-retry-count", "loggers");

    /**
     * Values that are not compared as values but bound to the image (TASK-140, finding N3 of the review of TASK-139): the
     * class loader Flyway resolves migrations, resolvers and resources with must be the one that loaded this image, and the
     * plugins it finds, with every setting of their configuration extensions, must be the ones of a fresh configuration.
     */
    static final Set<String> BOUND_TO_IMAGE = Set.of("class-loader", "plugin-register");

    /** The one schema the image keeps its tables and its history in; nothing may choose another (N3). */
    static final String SCHEMA = "public";

    /**
     * Compares the Flyway configuration Spring bound with the one the image fixes, value by value and with its type (N2):
     * every value {@link Configuration} offers, except the ones in {@link #CONNECTION_ONLY} (TASK-139, F5; since TASK-140, N3,
     * without JDBC properties, Kerberos file, class loader and plugins), with class loader and plugins bound to the image
     * ({@link #BOUND_TO_IMAGE}): locations in
     * exactly the spelling {@code classpath:db/migration}, history table, schemas and their creation, target, naming,
     * placeholders with their values, prefix and suffix, callbacks with the default callbacks, resolvers, Java migrations,
     * baseline, order and ignore patterns. A programmatic change is refused like the Spring setting of the same name;
     * objects such as callbacks and resolvers are compared by their classes. A configuration that finds no migration at all
     * is refused as well.
     */
    static void refuseChangedConfiguration(Configuration bound) {
        FluentConfiguration fixed = Flyway.configure().locations(LOCATION).cleanDisabled(true).baselineOnMigrate(false)
            .validateMigrationNaming(true);
        Set<String> refused = new TreeSet<>();
        for (java.lang.reflect.Method getter : Configuration.class.getMethods()) {
            String name = settingName(getter);
            if (name == null || CONNECTION_ONLY.contains(name)) {
                continue;
            }
            if (BOUND_TO_IMAGE.contains(name)) {
                if (!boundToImage(name, bound, fixed)) {
                    refused.add(name);
                }
                continue;
            }
            Object mine;
            Object theirs;
            try {
                mine = described(getter.invoke(bound));
                theirs = described(getter.invoke(fixed));
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // A value that cannot be read cannot be shown to be the fixed one.
                refused.add(name);
                continue;
            }
            compare(refused, name, mine, theirs);
        }
        if (!refused.isEmpty()) {
            throw new SchemaVersionException("The schema check refuses changed Flyway settings (" + String.join(", ", refused)
                + "). The image fixes the set of migrations, their history and the target; remove these settings.");
        }
    }

    /**
     * N3: the class loader is the very one of this image, so {@code classpath:db/migration} names the migrations in the
     * image and no other resources; the plugins are the same classes with the same settings as in a configuration that
     * nothing changed. The URL, if Flyway has one of its own, chooses no schema either.
     */
    private static boolean boundToImage(String name, Configuration bound, Configuration fixed) {
        try {
            if (name.equals("class-loader")) {
                return imageLoader(bound.getClassLoader());
            }
            return plugins(bound).equals(plugins(fixed));
        } catch (RuntimeException | ReflectiveOperationException failure) {
            return false;
        }
    }

    /**
     * The class loader of the image, or the class loader of the embedded Tomcat of the web process directly above it: that
     * one has no resources of its own and asks the class loader of the image first, so it finds the same migrations. Any
     * other loader, also one that only adds resources to the one of the image, is refused.
     */
    static boolean imageLoader(ClassLoader loader) {
        ClassLoader image = SchemaGuard.class.getClassLoader();
        if (loader == image) {
            return true;
        }
        if (loader == null || loader.getParent() != image || loader.getClass().getClassLoader() != image
                || !loader.getClass().getName().equals("org.springframework.boot.tomcat.TomcatEmbeddedWebappClassLoader")) {
            return false;
        }
        return !(loader instanceof java.net.URLClassLoader urls) || urls.getURLs().length == 0;
    }

    private static List<Object> plugins(Configuration configuration) throws ReflectiveOperationException {
        List<Object> described = new java.util.ArrayList<>();
        for (var plugin : configuration.getPluginRegister().getPlugins(org.flywaydb.core.extensibility.Plugin.class)) {
            Map<String, Object> values = new java.util.TreeMap<>();
            values.put("", plugin.getClass().getName());
            for (java.lang.reflect.Method getter : plugin.getClass().getMethods()) {
                String setting = settingName(getter);
                if (setting == null || getter.getDeclaringClass() == Object.class || setting.equals("class")) {
                    continue;
                }
                Object value = getter.invoke(plugin);
                Object form = described(value);
                // A nested settings object such as the transactional model counts with its text, not only its class.
                if (value != null && form instanceof String text && text.equals(value.getClass().getName())
                        && value.getClass().getMethod("toString").getDeclaringClass() != Object.class) {
                    form = text + ":" + value;
                }
                values.put(setting, form);
            }
            described.add(values);
        }
        described.sort(java.util.Comparator.comparing(String::valueOf));
        return described;
    }

    /**
     * N3: the connection Flyway works with uses exactly the schema {@value #SCHEMA}. A JDBC property such as
     * {@code currentSchema} or {@code options}, a setting of the role or of the database that changes the search path is
     * refused here, whatever source set it, before Flyway reads or writes anything.
     */
    static void refuseOtherSchema(javax.sql.DataSource dataSource) {
        List<String> schemas = new java.util.ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("select unnest(current_schemas(false))")) {
            while (result.next()) {
                schemas.add(result.getString(1));
            }
        } catch (java.sql.SQLException failure) {
            throw new SchemaVersionException("The schema check cannot read the search path of the database connection: "
                + firstLine(failure.getMessage()));
        }
        if (!schemas.equals(List.of(SCHEMA))) {
            throw new SchemaVersionException("The database connection uses the schema(s) " + schemas + "; the image keeps its "
                + "tables only in '" + SCHEMA + "'. Remove JDBC properties such as currentSchema or options and any changed "
                + "search_path of the role or the database.");
        }
    }

    /** "isBaselineOnMigrate" gives "baseline-on-migrate"; null for a method that is not a plain getter. */
    private static String settingName(java.lang.reflect.Method method) {
        if (method.getParameterCount() != 0 || method.getReturnType() == void.class
                || java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        String name = method.getName();
        String bare;
        if (name.startsWith("get") && name.length() > 3) {
            bare = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2 && (method.getReturnType() == boolean.class
                || method.getReturnType() == Boolean.class)) {
            bare = name.substring(2);
        } else {
            return null;
        }
        return bare.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /** A comparable form: values as they are, arrays and collections element by element, maps by key, objects by class. */
    private static Object described(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?>) {
            return value;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> items = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                items.add(described(java.lang.reflect.Array.get(value, index)));
            }
            return items;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().map(SchemaGuard::described).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), described(item)));
            return sorted;
        }
        if (value instanceof java.nio.charset.Charset || value instanceof org.flywaydb.core.api.Location
                || value instanceof org.flywaydb.core.api.MigrationVersion || value instanceof org.flywaydb.core.api.pattern.ValidatePattern
                || value instanceof java.io.File || value instanceof java.nio.file.Path) {
            return value.getClass().getName() + ":" + value;
        }
        return value.getClass().getName();
    }

    private static void compare(Set<String> refused, String name, Object bound, Object fixed) {
        if (!java.util.Objects.equals(bound, fixed)) {
            refused.add(name);
        }
    }

    /** No process that must check the schema starts without having checked it (F2). */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (enforced() && !checked && event.getApplicationContext().getParent() == null) {
            throw new SchemaVersionException("The schema was not checked at the start; this process refuses to run without "
                + "the check. Do not switch off Flyway or change its settings.");
        }
    }

    /**
     * Refuses every Flyway setting outside the fixed ones, from any source (command line, environment, files), and a fixed
     * one with another value (F2).
     */
    static void refuseChangedFlywaySettings(ConfigurableEnvironment environment) {
        Set<String> refused = new TreeSet<>();
        if (!"true".equalsIgnoreCase(environment.getProperty("spring.flyway.enabled", "true").strip())) {
            refused.add("enabled");
        }
        for (var source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                String key = normalized(name);
                if (!key.startsWith("springflyway")) {
                    continue;
                }
                String setting = key.substring("springflyway".length());
                if (setting.equals("enabled")) {
                    continue;
                }
                if (!FIXED.containsKey(setting)) {
                    refused.add(setting);
                }
            }
        }
        for (var fixed : FIXED.entrySet()) {
            String value = environment.getProperty("spring.flyway." + dashed(fixed.getKey()));
            // The location in exactly this spelling: Spring binds "classpath:DB/migration" as another place (N2).
            boolean same = fixed.getKey().equals("locations") ? fixed.getValue().equals(value == null ? null : value.strip())
                : value != null && fixed.getValue().equalsIgnoreCase(value.strip());
            if (!same) {
                refused.add(fixed.getKey());
            }
        }
        if (!refused.isEmpty()) {
            throw new SchemaVersionException("The schema check refuses changed Flyway settings (" + String.join(", ", refused)
                + "). The image fixes the set of migrations, their history and the target; remove these settings.");
        }
    }

    private static String firstLine(String message) {
        return message == null ? "unknown" : message.strip().lines().findFirst().orElse("unknown");
    }

    private static String normalized(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String dashed(String key) {
        return switch (key) {
            case "cleandisabled" -> "clean-disabled";
            case "baselineonmigrate" -> "baseline-on-migrate";
            case "validatemigrationnaming" -> "validate-migration-naming";
            default -> key;
        };
    }

    static void refuseNewerOrFailed(MigrationInfoService info) {
        if (Arrays.stream(info.all()).noneMatch(m -> m.getState().isResolved())) {
            // N2: a set of migrations that resolves to nothing never stands for this image.
            throw new SchemaVersionException("The image finds none of its migrations; the set of migrations is empty or "
                + "unknown. Do not change the Flyway settings or the image.");
        }
        List<MigrationInfo> applied = Arrays.asList(info.applied());
        List<MigrationInfo> newerFailed = applied.stream().filter(m -> m.getState() == MigrationState.FUTURE_FAILED).toList();
        if (!newerFailed.isEmpty()) {
            // H1: a migration this image does not know failed in an earlier run of a newer image.
            throw new SchemaVersionException("The migration(s) " + versions(newerFailed) + ", unknown to this image, failed in "
                + "an earlier run of a newer image; the schema is not in a known state. Restore the backup point taken before "
                + "that update as described in docs/INSTALL.md, section 'Rückkehr'.");
        }
        List<MigrationInfo> newer = applied.stream().filter(m -> m.getState() == MigrationState.FUTURE_SUCCESS).toList();
        if (!newer.isEmpty()) {
            throw new SchemaVersionException("The database has the schema version " + version(info.current()) + " and is "
                + "newer than this image, which knows migrations up to " + knownUpTo(info) + " (unknown: " + versions(newer)
                + "). Use the image of the version that wrote the schema; an update is never undone by an older image, only "
                + "by restoring the backup point taken before it.");
        }
        List<MigrationInfo> failed = applied.stream().filter(m -> m.getState().isFailed()).toList();
        if (!failed.isEmpty()) {
            throw new SchemaVersionException("The migration(s) " + versions(failed) + " failed in an earlier run. Restore the "
                + "backup point taken before the update as described in docs/INSTALL.md, section 'Rückkehr'; the schema is not "
                + "in a known state.");
        }
    }

    private static String knownUpTo(MigrationInfoService info) {
        return Arrays.stream(info.all())
            .filter(m -> m.getState().isResolved())
            .map(SchemaGuard::version)
            .reduce((first, second) -> second)
            .orElse("none");
    }

    private static String versions(List<MigrationInfo> migrations) {
        return String.join(", ", migrations.stream().map(SchemaGuard::version).toList());
    }

    static String version(MigrationInfo migration) {
        return migration == null || migration.getVersion() == null ? "none" : migration.getVersion().getVersion();
    }
}
