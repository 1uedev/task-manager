package taskmanager.files.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FilesProperties.class)
class FilesConfiguration {
    /** The file store the application uses. A later object store replaces this adapter. */
    @Bean
    FileStorage fileStorage(FilesProperties properties) {
        return new FileSystemStorage(properties.directory());
    }
}
