package taskmanager.identity.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RulesTest {
    @Test
    void passwordsOfAnyCompositionPassWhenLongEnough() {
        assertEquals(List.of(), PasswordPolicy.violations("correct horse battery", "anna"));
        assertEquals(List.of(), PasswordPolicy.violations("Ünïcödé-Pässwörter-sind-ok", "anna"));
        assertEquals(List.of(), PasswordPolicy.violations("a".repeat(11) + "b", "anna"));
    }

    @Test
    void lengthIsCountedInCharactersNotBytes() {
        assertTrue(PasswordPolicy.violations("ääääääääääää".substring(0, 11), "anna").contains("password.too_short"));
        assertEquals(List.of(), PasswordPolicy.violations("äöüäöüäöüäöüx", "anna"));
        assertTrue(PasswordPolicy.violations("x".repeat(129) + "y", "anna").contains("password.too_long"));
        assertEquals(List.of(), PasswordPolicy.violations("xy".repeat(64), "anna"));
    }

    @Test
    void controlCharactersAreRefused() {
        assertTrue(PasswordPolicy.violations("good-password-1\n", "anna").contains("password.invalid_characters"));
        assertTrue(PasswordPolicy.violations("good-password-\u0000", "anna").contains("password.invalid_characters"));
    }

    @Test
    void theLoginNameAndVeryCommonPasswordsAreRefused() {
        assertTrue(PasswordPolicy.violations("Anna.Mustermann", "anna.mustermann").contains("password.equals_username"));
        assertTrue(PasswordPolicy.violations("password1234", "anna").contains("password.too_common"));
        assertTrue(PasswordPolicy.violations("Password123456", "anna").contains("password.too_common"));
        assertTrue(PasswordPolicy.violations("aaaaaaaaaaaaaa", "anna").contains("password.too_common"));
        assertFalse(PasswordPolicy.violations("Password-with-more-thought", "anna").contains("password.too_common"));
    }

    @Test
    void loginNamesAreLowerCaseAndLimited() {
        assertEquals("anna.m", UsernameRules.normalize("  Anna.M "));
        for (String ok : List.of("abc", "a.b-c_d@example.org", "user+tag", "x".repeat(64), "007")) {
            assertTrue(UsernameRules.isValid(UsernameRules.normalize(ok)), ok);
        }
        for (String bad : List.of("ab", "", " ", "x".repeat(65), "-abc", ".abc", "a b c", "ünicode", "a/b", "a;b", "a\nb")) {
            assertFalse(UsernameRules.isValid(UsernameRules.normalize(bad)), bad);
        }
    }

    @Test
    void oneTimePasswordsAreLongRandomAndWithoutLookAlikes() {
        OneTimePasswords generator = new OneTimePasswords();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String password = generator.next();
            assertEquals(OneTimePasswords.LENGTH, password.length());
            assertTrue(password.matches("[A-HJ-NP-Za-km-z2-9]+"), password);
            assertTrue(seen.add(password), "duplicate " + password);
        }
    }

    @Test
    void hasherUsesArgon2idAndChecksConstantly() {
        PasswordHasher hasher = new PasswordHasher();
        String hash = hasher.hash("a long enough secret");
        assertTrue(hash.startsWith("$argon2id$"), hash);
        assertTrue(hasher.matches("a long enough secret", hash));
        assertFalse(hasher.matches("a long enough secreT", hash));
        assertFalse(hasher.hash("a long enough secret").equals(hash), "salted");
    }
}
