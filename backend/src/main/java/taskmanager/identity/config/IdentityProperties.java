package taskmanager.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable values with the decided defaults (TASK-024). They exist mainly so that tests can shorten
 * the periods; the session duration itself is an administrator setting stored in the database.
 *
 * @param oneTimePasswordValidity validity of a one time password from its issue
 * @param restrictedSessionValidity longest lifetime of the restricted session that only allows the forced password change
 * @param userFailureThreshold failed attempts per login name before the waiting time starts
 * @param sourceFailureThreshold failed attempts per source address before the waiting time starts
 * @param baseDelay first waiting time, doubled with every further failure
 * @param maxDelay upper bound of the waiting time
 */
@ConfigurationProperties(prefix = "taskmanager.identity")
public record IdentityProperties(
        @DefaultValue("72h") Duration oneTimePasswordValidity,
        @DefaultValue("30m") Duration restrictedSessionValidity,
        @DefaultValue("5") int userFailureThreshold,
        @DefaultValue("5") int sourceFailureThreshold,
        @DefaultValue("5s") Duration baseDelay,
        @DefaultValue("15m") Duration maxDelay) {
}
