package taskmanager.platform.config;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Stops the start before any server or database bean is created when a required setting is
 * missing. Without this check a missing database URL only surfaces as an unrelated driver error.
 * A bean factory post-processor runs after all property sources, including test overrides, exist.
 */
@Component
class RequiredSettings implements BeanFactoryPostProcessor, EnvironmentAware {
    /** Spring property and the environment variable that application.yaml maps to it. */
    static final Map<String, String> REQUIRED = Map.of(
        "spring.datasource.url", "TASK_MANAGER_DB_URL",
        "spring.datasource.username", "TASK_MANAGER_DB_USER",
        "spring.datasource.password", "TASK_MANAGER_DB_PASSWORD");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> missing = REQUIRED.entrySet().stream()
            .filter(entry -> isBlank(entry.getKey()))
            .map(entry -> entry.getValue() + " (" + entry.getKey() + ")")
            .sorted()
            .toList();
        if (!missing.isEmpty()) {
            throw new MissingSettingsException(missing);
        }
    }

    private boolean isBlank(String property) {
        try {
            String value = environment.getProperty(property);
            return value == null || value.isBlank();
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return true;
        }
    }
}
