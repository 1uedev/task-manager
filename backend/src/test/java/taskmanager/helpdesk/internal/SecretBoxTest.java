package taskmanager.helpdesk.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TASK-057: the mailbox passwords are encrypted with the key of the operation and bound to their mailbox. */
class SecretBoxTest {
    private static SecretBox box(String key) {
        return new SecretBox(new HelpdeskProperties(key, Duration.ofSeconds(60), 25_000_000L, 50, Duration.ofSeconds(20), Duration.ofMinutes(10)));
    }

    private static String key(char c) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) c);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void aSealedPasswordOpensOnlyForItsMailboxAndKey() {
        SecretBox box = box(key('a'));
        UUID mailbox = UUID.randomUUID();
        byte[] sealed = box.seal(mailbox, "Geheim-ä");
        assertFalse(new String(sealed, StandardCharsets.ISO_8859_1).contains("Geheim"));
        assertEquals("Geheim-ä", box.open(mailbox, sealed));
        assertFalse(Arrays.equals(sealed, box.seal(mailbox, "Geheim-ä")), "a new nonce each time");
        assertThrows(SecretBox.Unreadable.class, () -> box.open(UUID.randomUUID(), sealed));
        assertThrows(SecretBox.Unreadable.class, () -> box(key('b')).open(mailbox, sealed));
        byte[] damaged = sealed.clone();
        damaged[damaged.length - 1] ^= 1;
        assertThrows(SecretBox.Unreadable.class, () -> box.open(mailbox, damaged));
        assertThrows(SecretBox.Unreadable.class, () -> box.open(mailbox, new byte[4]));
    }

    @Test
    void withoutAKeyNothingIsEncryptedAndAWrongKeyStopsTheStart() {
        SecretBox none = box("");
        assertFalse(none.available());
        assertThrows(SecretBox.KeyMissing.class, () -> none.seal(UUID.randomUUID(), "x"));
        assertThrows(SecretBox.KeyMissing.class, () -> none.open(UUID.randomUUID(), new byte[40]));
        assertThrows(IllegalArgumentException.class, () -> box(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalArgumentException.class, () -> box("kein base64!"));
    }
}
