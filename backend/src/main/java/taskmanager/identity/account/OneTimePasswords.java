package taskmanager.identity.account;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Random one time passwords without look alike characters (no 0/O, 1/l/I). 20 characters carry about 115 bits. */
@Component
public class OneTimePasswords {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    static final int LENGTH = 20;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
