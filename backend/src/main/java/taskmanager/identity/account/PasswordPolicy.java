package taskmanager.identity.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rules for chosen passwords: 12 to 128 characters, no required character classes, not the login name,
 * not a very common password, no control characters. The list of common passwords is small and ships with
 * the application; it is a basic barrier, not a breached password check.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    private static final Set<String> COMMON = load();

    private PasswordPolicy() {
    }

    /** Stable reason codes, empty when the password is acceptable. */
    public static List<String> violations(String password, String username) {
        List<String> violations = new ArrayList<>();
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            violations.add("password.too_short");
        }
        if (length > MAX_LENGTH) {
            violations.add("password.too_long");
        }
        if (password.codePoints().anyMatch(Character::isISOControl)) {
            violations.add("password.invalid_characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (username != null && lower.equals(username.toLowerCase(Locale.ROOT))) {
            violations.add("password.equals_username");
        }
        if (isCommon(lower)) {
            violations.add("password.too_common");
        }
        return violations;
    }

    private static boolean isCommon(String lower) {
        if (lower.codePoints().distinct().count() == 1) {
            return true;
        }
        String stripped = lower.replaceAll("[0-9!?.#*_\\-]+$", "");
        return COMMON.contains(lower) || (stripped.length() >= 4 && COMMON.contains(stripped));
    }

    private static Set<String> load() {
        try (InputStream in = PasswordPolicy.class.getResourceAsStream("/identity/common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("common-passwords.txt is missing");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
