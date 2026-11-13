package taskmanager.git.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the operation sets for the Git link (TASK-111, DEC-141).
 *
 * @param secretKey 32 random bytes in Base64 that encrypt the secrets of the applications and the tokens
 *        ({@code TASK_MANAGER_SECRET_KEY}, the same key as the mailbox passwords); empty means nothing can be stored or used
 * @param publicUrl the address under which browsers reach the application ({@code TASK_MANAGER_PUBLIC_URL}); the OAuth
 *        providers send the browser back to it, so without it nobody can connect an account
 * @param timeout how long one call to a provider may take to connect and to answer
 * @param allowHttp whether a GitLab of its own may be reached over plain HTTP; only the tests set it
 * @param githubUrl the address of GitHub.com, replaced only by the tests
 * @param githubApiUrl the address of the API of GitHub.com, replaced only by the tests
 * @param gitlabComUrl the address of GitLab.com, replaced only by the tests
 */
@ConfigurationProperties(prefix = "taskmanager.git")
public record GitProperties(
        @DefaultValue("") String secretKey,
        @DefaultValue("") String publicUrl,
        @DefaultValue("5s") Duration timeout,
        @DefaultValue("false") boolean allowHttp,
        @DefaultValue("https://github.com") String githubUrl,
        @DefaultValue("https://api.github.com") String githubApiUrl,
        @DefaultValue("https://gitlab.com") String gitlabComUrl) {
    public GitProperties {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("taskmanager.git.timeout must be positive");
        }
        publicUrl = publicUrl == null ? "" : publicUrl.strip();
        while (publicUrl.endsWith("/")) {
            publicUrl = publicUrl.substring(0, publicUrl.length() - 1);
        }
        if (!publicUrl.isEmpty() && !publicUrl.matches("https?://[A-Za-z0-9.\\-\\[\\]:]+(/[A-Za-z0-9._~\\-/]*)?")) {
            throw new IllegalArgumentException("taskmanager.git.public-url must be an http or https address without query");
        }
    }

    /** The address the providers send the browser back to, or {@code null} without a public address. */
    public String callbackUrl() {
        return publicUrl.isEmpty() ? null : publicUrl + "/api/v1/git/oauth/callback";
    }
}
