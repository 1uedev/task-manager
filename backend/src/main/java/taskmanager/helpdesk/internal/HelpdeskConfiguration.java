package taskmanager.helpdesk.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HelpdeskProperties.class)
class HelpdeskConfiguration {
}
