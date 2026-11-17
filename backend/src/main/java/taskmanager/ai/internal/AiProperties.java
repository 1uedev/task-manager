package taskmanager.ai.internal;

import java.time.Duration;
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
 * @param allowHttp whether every access may use plain HTTP; only the tests set it, otherwise only a local data path may
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
        @DefaultValue("false") boolean allowHttp,
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
    }
}
