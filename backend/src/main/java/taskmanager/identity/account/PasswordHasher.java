package taskmanager.identity.account;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id with 19 MiB memory, 2 iterations and 1 lane (OWASP minimum recommendation for Argon2id). The
 * parameters are part of each stored hash, so they can be raised later without invalidating old hashes.
 */
@Component
public class PasswordHasher {
    /** Longest password that is hashed at all. Longer input is refused before any expensive work. */
    public static final int MAX_INPUT_LENGTH = 1024;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    private final String dummyHash = encoder.encode("dummy value for equal timing");

    public String hash(String password) {
        return encoder.encode(password);
    }

    public boolean matches(String password, String hash) {
        return password.length() <= MAX_INPUT_LENGTH && encoder.matches(password, hash);
    }

    /** Spends the time of one verification, so unknown login names cannot be told apart by response time. */
    public void burn(String password) {
        if (password.length() <= MAX_INPUT_LENGTH) {
            encoder.matches(password, dummyHash);
        }
    }
}
