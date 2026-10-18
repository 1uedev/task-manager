package taskmanager.identity.account;

import java.util.Locale;
import java.util.regex.Pattern;

/** Login names are stored in lower case, so uniqueness ignores case. */
public final class UsernameRules {
    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9._@+-]{2,63}$");

    private UsernameRules() {
    }

    public static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return VALID.matcher(normalized).matches();
    }
}
