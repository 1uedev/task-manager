package taskmanager.identity.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import org.junit.jupiter.api.Test;

/** The key an account is assigned by (TASK-048): different values never share a key, and text identifiers keep their form. */
class DirectoryIdentifierTest {
    private static String key(Object value) throws NamingException {
        return DirectoryClient.identifier(new BasicAttribute("id", value));
    }

    @Test
    void aTextIdentifierStaysTheText() throws NamingException {
        assertEquals("0b7a6a1e-6f2c-4e0b-9c53-2f9a6a1d3c11", key("0b7a6a1e-6f2c-4e0b-9c53-2f9a6a1d3c11".getBytes(StandardCharsets.UTF_8)));
        assertEquals("0b7a6a1e-6f2c-4e0b-9c53-2f9a6a1d3c11", key("0b7a6a1e-6f2c-4e0b-9c53-2f9a6a1d3c11"));
        assertEquals("Müller-17", key("Müller-17".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void differentBinaryValuesGetDifferentKeys() throws NamingException {
        byte[] first = new byte[16];
        byte[] second = new byte[16];
        java.util.Arrays.fill(first, (byte) 0x80);
        java.util.Arrays.fill(second, (byte) 0x81);
        first[6] = 0x40;
        second[6] = 0x40;
        String a = key(first);
        String b = key(second);
        assertTrue(a.startsWith("hex:"), a);
        assertEquals("hex:80808080808040808080808080808080", a);
        assertEquals("hex:81818181818140818181818181818181", b);
        assertNotEquals(a, b);
    }

    @Test
    void bytesThatAreNotPlainTextAreStoredAsHexadecimal() throws NamingException {
        assertEquals("hex:0102", key(new byte[] { 1, 2 }));
        assertEquals("hex:2020", key("  ".getBytes(StandardCharsets.UTF_8)), "blanks only are no identifier text");
        assertEquals("hex:6865783a6162", key("hex:ab".getBytes(StandardCharsets.UTF_8)), "a text can never look like a binary key");
        assertEquals("hex:c328", key(new byte[] { (byte) 0xC3, 0x28 }), "invalid UTF-8 is binary");
    }

    @Test
    void lossyOrMissingValuesNameNobody() throws NamingException {
        assertNull(key("���@�"), "a text that already lost its bytes");
        assertNull(key(new byte[0]));
        assertNull(DirectoryClient.identifier(null));
        BasicAttribute two = new BasicAttribute("id");
        two.add("a");
        two.add("b");
        assertNull(DirectoryClient.identifier(two));
        assertNull(key(new byte[300]), "too long for the stored key");
    }
}
