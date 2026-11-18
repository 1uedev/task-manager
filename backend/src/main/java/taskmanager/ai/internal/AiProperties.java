package taskmanager.ai.internal;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the operation sets for the AI foundation (TASK-125, DEC-156). The rules of DEC-156 are the defaults; only the tests
 * shorten the times or replace the fixed addresses.
 *
 * @param secretKey 32 random bytes in Base64 that encrypt the API keys ({@code TASK_MANAGER_SECRET_KEY}, the same key as the
 *        mailbox passwords and the Git tokens); empty means no key can be stored or used
 * @param timeout how long one execution may take at most (rule 10: 90 seconds)
 * @param connectTimeout how long building the connection to a provider may take
 * @param checkTimeout how long a check of an access or a model may take
 * @param running how many executions run at the same time in this process (rule 10: two)
 * @param waiting how many more wait for a free place (rule 10: four)
 * @param keep how long a suggestion stays for its person (rule 12: 24 hours)
 * @param contextCharacters the most characters of sources one execution sends (rule 9: 60,000)
 * @param plainHttpHosts the hosts, each optionally with {@code :port}, the operation releases for plain HTTP
 *        ({@code TASK_MANAGER_AI_PLAIN_HTTP_HOSTS}, separated by commas); only an access with the local data path may use
 *        them, every other address needs https (TASK-129, F3)
 * @param openaiUrl the default address of OpenAI
 * @param anthropicUrl the fixed address of Anthropic, replaced only by the tests
 */
@ConfigurationProperties(prefix = "taskmanager.ai")
public record AiProperties(
        @DefaultValue("") String secretKey,
        @DefaultValue("90s") Duration timeout,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("15s") Duration checkTimeout,
        @DefaultValue("2") int running,
        @DefaultValue("4") int waiting,
        @DefaultValue("24h") Duration keep,
        @DefaultValue("60000") int contextCharacters,
        @DefaultValue("") List<String> plainHttpHosts,
        @DefaultValue("https://api.openai.com") String openaiUrl,
        @DefaultValue("https://api.anthropic.com") String anthropicUrl) {
    public AiProperties {
        for (Duration duration : new Duration[] { timeout, connectTimeout, checkTimeout, keep }) {
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("taskmanager.ai times must be positive");
            }
        }
        if (running < 1 || waiting < 0 || contextCharacters < 1000) {
            throw new IllegalArgumentException("taskmanager.ai limits are out of range");
        }
        List<String> entries = new java.util.ArrayList<>();
        for (String raw : plainHttpHosts == null ? List.<String>of() : plainHttpHosts) {
            String host = raw.strip();
            if (host.isEmpty()) {
                continue;
            }
            // An IPv6 literal gets the same one form as an address (TASK-130, H1); names are lower case.
            String entry = AiTargets.releasedEntry(host);
            if (entry == null || !entry.matches("(\\[[0-9a-f:]+\\]|[a-z0-9]([a-z0-9.\\-]*[a-z0-9])?)(:[0-9]{1,5})?")) {
                throw new IllegalArgumentException("TASK_MANAGER_AI_PLAIN_HTTP_HOSTS holds an invalid host: " + host);
            }
            entries.add(entry);
        }
        plainHttpHosts = List.copyOf(entries);
    }
}
