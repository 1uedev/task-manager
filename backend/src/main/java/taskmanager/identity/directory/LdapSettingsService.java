package taskmanager.identity.directory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.account.AccountRepository;
import taskmanager.platform.ApiProblemException;

/**
 * The administrator's setting of the directory connection (TASK-046, DEC-075): validation, storage with an audit entry and the
 * connection check. The methods do not decide who may call them; the web layer asks for the right {@code accounts.manage} in the
 * transaction of the change.
 * The password of the search account is not handled here at all.
 */
@Service
public class LdapSettingsService {
    private static final Pattern ATTRIBUTE = Pattern.compile("^[A-Za-z][A-Za-z0-9-]{0,99}$");

    /** What an administrator sends. Fields that are blank count as absent. */
    public record Input(Boolean enabled, String url, String security, String bindDn, String searchBase, String userFilter,
            String idAttribute, String nameAttribute) {
    }

    private final LdapSettingsRepository repository;
    private final AccountRepository accounts;
    private final DirectoryClient client;
    private final Clock clock;

    LdapSettingsService(LdapSettingsRepository repository, AccountRepository accounts, DirectoryClient client, Clock clock) {
        this.repository = repository;
        this.accounts = accounts;
        this.client = client;
        this.clock = clock;
    }

    public Optional<LdapSettings> find() {
        return repository.find();
    }

    /** Whether the environment of the operation provides the password of the search account. */
    public boolean bindPasswordSet() {
        return client.bindPasswordSet();
    }

    @Transactional
    public LdapSettings change(String actor, Input input) {
        LdapSettings settings = validate(input);
        Instant now = clock.instant();
        repository.save(settings, now);
        accounts.audit(actor, "ldap.configured", null, "enabled=" + settings.enabled() + " security=" + settings.security(), now);
        return repository.find().orElseThrow();
    }

    /** The reason the check of the connection fails with, or null when it succeeds. Nothing is stored. */
    public String check(Input input, String sampleLoginName) {
        LdapSettings settings = input == null
            ? repository.find().orElseThrow(() -> ApiProblemException.of(409, "ldap.not_configured")) : validate(input);
        try {
            client.check(settings, sampleLoginName);
            return null;
        } catch (DirectoryUnavailableException e) {
            return e.reason();
        }
    }

    LdapSettings validate(Input input) {
        if (input == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("ldap.required"));
        }
        List<String> violations = new ArrayList<>();
        URI uri = uri(blank(input.url()), violations);
        String security = blank(input.security());
        if (security == null || !List.of(LdapSettings.NONE, LdapSettings.STARTTLS, LdapSettings.LDAPS).contains(security)) {
            violations.add("ldap.security.invalid");
        } else if (uri != null && LdapSettings.LDAPS.equals(security) != "ldaps".equals(uri.getScheme())) {
            violations.add("ldap.security.mismatch");
        }
        String bindDn = blank(input.bindDn());
        if (bindDn != null && !validDn(bindDn)) {
            violations.add("ldap.bindDn.invalid");
        }
        String base = blank(input.searchBase());
        if (base == null || !validDn(base)) {
            violations.add("ldap.searchBase.invalid");
        }
        String filter = blank(input.userFilter());
        if (!validFilter(filter)) {
            violations.add("ldap.userFilter.invalid");
        }
        String idAttribute = blank(input.idAttribute());
        if (idAttribute == null || !ATTRIBUTE.matcher(idAttribute).matches()) {
            violations.add("ldap.idAttribute.invalid");
        }
        String nameAttribute = blank(input.nameAttribute());
        if (nameAttribute != null && !ATTRIBUTE.matcher(nameAttribute).matches()) {
            violations.add("ldap.nameAttribute.invalid");
        }
        boolean enabled = Boolean.TRUE.equals(input.enabled());
        if (enabled && bindDn != null && !client.bindPasswordSet()) {
            violations.add("ldap.bindPassword.missing");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        return new LdapSettings(enabled, endpoint(uri), security, bindDn, base, filter, idAttribute, nameAttribute, null);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static URI uri(String url, List<String> violations) {
        try {
            URI uri = url == null || url.length() > 300 ? null : new URI(url);
            boolean usable = uri != null && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                && uri.getFragment() == null && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                && ("ldap".equals(uri.getScheme()) || "ldaps".equals(uri.getScheme())) && uri.getPort() != 0 && uri.getPort() <= 65535;
            if (usable) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
            // reported below
        }
        violations.add("ldap.url.invalid");
        return null;
    }

    private static String endpoint(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    private static boolean validDn(String dn) {
        if (dn.length() > 500 || dn.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        try {
            return !new LdapName(dn).isEmpty();
        } catch (InvalidNameException e) {
            return false;
        }
    }

    /** One filter in parentheses with the login name as {@code {0}} and no other place holder; the parentheses are balanced. */
    static boolean validFilter(String filter) {
        if (filter == null || filter.length() > 500 || filter.chars().anyMatch(Character::isISOControl)
                || !filter.startsWith("(") || !filter.endsWith(")") || !filter.contains("{0}")) {
            return false;
        }
        if (filter.replace("{0}", "").contains("{") || filter.replace("{0}", "").contains("}")) {
            return false;
        }
        int depth = 0;
        for (int i = 0; i < filter.length(); i++) {
            char c = filter.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i != filter.length() - 1) {
                    return false;
                }
            }
            if (depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }
}
