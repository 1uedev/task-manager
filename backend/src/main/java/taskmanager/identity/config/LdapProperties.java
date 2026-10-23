package taskmanager.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the operation provides for the directory connection (TASK-046, DEC-075). The connection itself (address, search base, filter)
 * is an administrator setting in the database; the password of the search account is a secret and therefore only read from the
 * environment ({@code TASK_MANAGER_LDAP_BIND_PASSWORD}), never stored, never returned.
 *
 * @param bindPassword password of the search account; empty when the directory allows an anonymous search
 * @param timeout longest wait for connecting and for each answer of the directory
 * @param trustStore optional file with the certificates the directory's certificate chain must end in (a private authority);
 *        empty uses the trust of the Java runtime
 * @param trustStorePassword password of that file
 * @param trustStoreType type of that file, e.g. {@code PKCS12}
 */
@ConfigurationProperties(prefix = "taskmanager.identity.ldap")
public record LdapProperties(
        @DefaultValue("") String bindPassword,
        @DefaultValue("5s") Duration timeout,
        @DefaultValue("") String trustStore,
        @DefaultValue("") String trustStorePassword,
        @DefaultValue("PKCS12") String trustStoreType) {

    public boolean bindPasswordSet() {
        return !bindPassword.isEmpty();
    }

    @Override
    public String toString() {
        return "LdapProperties[bindPassword=" + (bindPasswordSet() ? "<set>" : "<empty>") + ", timeout=" + timeout + ", trustStore=" + (trustStore.isEmpty() ? "<runtime>" : trustStore) + "]";
    }
}
