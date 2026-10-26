package taskmanager.identity.directory;

import java.time.Instant;

/**
 * The connection to the directory as an administrator stores it. The password of the search account is not part of it.
 *
 * @param enabled whether people unknown to the application may sign in with the directory
 * @param url {@code ldap://host[:port]} or {@code ldaps://host[:port]}
 * @param security {@code none}, {@code starttls} or {@code ldaps}
 * @param bindDn distinguished name of the search account; null for an anonymous search
 * @param userFilter search filter with {@code {0}} for the login name, e.g. {@code (uid={0})}
 * @param idAttribute attribute with the immutable identifier of an entry, e.g. {@code entryUUID}
 * @param nameAttribute attribute with the display name; null for none
 */
public record LdapSettings(
        boolean enabled,
        String url,
        String security,
        String bindDn,
        String searchBase,
        String userFilter,
        String idAttribute,
        String nameAttribute,
        Instant updatedAt) {

    public static final String NONE = "none";
    public static final String STARTTLS = "starttls";
    public static final String LDAPS = "ldaps";
}
