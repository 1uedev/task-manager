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

    /** Registers the marker in the context the application starts. */
    public static void register(SpringApplication application) {
        OperationsStart start = new OperationsStart();
        application.addInitializers((ConfigurableApplicationContext context) -> context.getBeanFactory().registerSingleton(BEAN, start));
    }
}
