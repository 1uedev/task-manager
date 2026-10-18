package taskmanager.identity.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokensTest {
    @Test
    void tokensAreUniqueUrlSafeAndFortythreeCharactersLong() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = Tokens.random();
            assertEquals(43, token.length());
            assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
            assertTrue(seen.add(token));
        }
    }

    @Test
    void theStoredFormIsAFixedLengthHash() {
        byte[] hash = Tokens.hash("abc");
        assertEquals(32, hash.length);
        assertTrue(Tokens.sameValue(hash, Tokens.hash("abc")));
        assertFalse(Tokens.sameValue(hash, Tokens.hash("abd")));
        assertTrue(Tokens.sameValue("x", "x"));
        assertFalse(Tokens.sameValue("x", "xy"));
    }

    @Test
    void throttleSubjectsDoNotRevealNamesAndIgnoreCase() {
        assertEquals(LoginThrottle.userSubject("Anna"), LoginThrottle.userSubject(" anna "));
        assertFalse(LoginThrottle.userSubject("anna").contains("anna"));
        assertEquals(LoginThrottle.userSubject("x".repeat(300)), LoginThrottle.userSubject("x".repeat(256) + "different"));
    }
}
