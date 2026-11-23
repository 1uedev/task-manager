package taskmanager.operations;

import java.util.Objects;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The container command a process runs instead of the web server or the worker (TASK-135, finding F6 of the review of
 * TASK-131). The entry point registers it as a bean of the context it starts for that command; a setting such as
 * {@code taskmanager.process=command} cannot create it. Only a process without a web server that carries it may skip the
 * refusal of a restore that was not released yet.
 *
 * @param name the command, for example {@code after-restore}
 */
public record ContainerCommand(String name) {
    /** The name of the bean. */
    public static final String BEAN = "taskmanager.containerCommand";

    public ContainerCommand {
        Objects.requireNonNull(name, "name");
    }

    /** Registers the command in the context the application starts. */
    public static void register(SpringApplication application, String name) {
        ContainerCommand command = new ContainerCommand(name);
        application.addInitializers((ConfigurableApplicationContext context) -> context.getBeanFactory().registerSingleton(BEAN, command));
    }
}
