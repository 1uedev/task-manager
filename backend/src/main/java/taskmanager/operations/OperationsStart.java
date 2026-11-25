package taskmanager.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Marks a process that the operations template started (TASK-137, finding N1 of the review of TASK-136). The template puts
 * the argument {@code operations} in front of every command through its entry point; the entry point then registers this
 * bean in the context it starts. A setting cannot create it. A process that carries it never migrates at the start: it
 * checks the schema and refuses a schema mode other than {@code verify}, whatever source sets it. Only the container
 * command {@code migrate} changes the schema. Without the argument the demonstration keeps its own start, which migrates.
 */
public record OperationsStart() {
    /** The argument the operations template puts in front of every command. */
    public static final String ARGUMENT = "operations";
    /** The name of the bean. */
    public static final String BEAN = "taskmanager.operationsStart";

    private static final java.util.regex.Pattern DIGEST = java.util.regex.Pattern.compile("sha256:[0-9a-f]{64}");

    /**
     * Both image settings of the template, checked together at the start of every process of the template, also one that
     * Compose starts with {@code run --no-deps} without the service "images" (TASK-139, finding F7 of the review of
     * TASK-137); the same rule as deploy/check-images.sh. Returns the reason of a refusal, or null.
     */
    public static String imageRefusal(java.util.Map<String, String> environment) {
        String hint = " Copy both lines from digests-VERSION.env of the release into .env.";
        if (!environment.getOrDefault("TASK_MANAGER_IMAGE", "").isEmpty()
                || !environment.getOrDefault("TASK_MANAGER_BACKUP_IMAGE", "").isEmpty()) {
            return "TASK_MANAGER_IMAGE and TASK_MANAGER_BACKUP_IMAGE are no longer read; since TASK-137 the template takes "
                + "only the digests as TASK_MANAGER_IMAGE_DIGEST and TASK_MANAGER_BACKUP_IMAGE_DIGEST. Remove the two old "
                + "lines." + hint;
        }
        for (String name : java.util.List.of("TASK_MANAGER_IMAGE_DIGEST", "TASK_MANAGER_BACKUP_IMAGE_DIGEST")) {
            String value = environment.getOrDefault(name, "");
            if (!DIGEST.matcher(value).matches()) {
                return name + " must be the digest the release names (sha256: and 64 hexadecimal characters); found '"
                    + value.replaceAll("[^\\x21-\\x7e]", "?") + "'." + hint;
            }
        }
        return null;
    }

    /** Registers the marker in the context the application starts. */
    public static void register(SpringApplication application) {
        OperationsStart start = new OperationsStart();
        application.addInitializers((ConfigurableApplicationContext context) -> context.getBeanFactory().registerSingleton(BEAN, start));
    }
}
