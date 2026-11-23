package taskmanager.operations.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import taskmanager.operations.ContainerCommand;

/**
 * A bean that exists only in the context of the named container command, which only the entry point registers (TASK-136,
 * finding F1 of the review of TASK-132). A setting cannot create it, so a web process or a worker never runs the work of a
 * command.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Conditional(ConditionalOnContainerCommand.OnContainerCommand.class)
@interface ConditionalOnContainerCommand {
    /** The name of the command. */
    String value();

    final class OnContainerCommand implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var attributes = metadata.getAnnotationAttributes(ConditionalOnContainerCommand.class.getName());
            var factory = context.getBeanFactory();
            if (attributes == null || factory == null || !factory.containsSingleton(ContainerCommand.BEAN)) {
                return false;
            }
            return factory.getSingleton(ContainerCommand.BEAN) instanceof ContainerCommand command
                && command.name().equals(attributes.get("value"));
        }
    }
}
