package taskmanager.files.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import taskmanager.platform.ApiProblemException;

/** TASK-051: the cleaned name and the refusal of executable content. */
class UploadRulesTest {
    private final UploadRules rules = new UploadRules(1000, List.of("exe", "SH", "html"));

    @Test
    void aNameLosesItsPathControlAndFormatCharacters() {
        assertEquals("passwd", UploadRules.clean("../../etc/passwd"));
        assertEquals("secret.txt", UploadRules.clean("C:\\Users\\me\\secret.txt"));
        assertEquals("bericht.pdf", UploadRules.clean("be\u0000ri\tcht\n.pdf"));
        assertEquals("rechnungexe.txt", UploadRules.clean("rechnung\u202Etxt.exe".replace("txt.exe", "exe.txt")), "a right to left mark cannot disguise an extension");
        assertEquals("Über uns.docx", UploadRules.clean("  Über uns.docx. . "));
        assertEquals("é.txt", UploadRules.clean("e\u0301.txt"), "normalized to composed form");
    }

    @Test
    void aNameThatStaysEmptyOrTooLongIsRefused() {
        for (String name : new String[] {null, "", "   ", "/", "..", ".", "\u202E", "a/", "dir\\"}) {
            ApiProblemException refused = assertThrows(ApiProblemException.class, () -> UploadRules.clean(name), String.valueOf(name));
            assertEquals(400, refused.status());
            assertEquals(List.of("name.invalid"), refused.violations());
        }
        assertEquals(255, UploadRules.clean("a".repeat(255)).length());
        assertThrows(ApiProblemException.class, () -> UploadRules.clean("a".repeat(256)));
    }

    @Test
    void blockedExtensionsAreRefusedWhateverTheCase() {
        for (String name : new String[] {"setup.exe", "SETUP.EXE", "run.Sh", "page.html", "archive.tar.exe"}) {
            ApiProblemException refused = assertThrows(ApiProblemException.class, () -> rules.checkName(name), name);
            assertEquals(415, refused.status());
            assertEquals("attachment.type_blocked", refused.code());
        }
        rules.checkName("bericht.pdf");
        rules.checkName("exe");
        rules.checkName("notes.txt.bak");
        rules.checkName("noextension");
    }

    @Test
    void contentThatStartsLikeAProgramIsRefused() {
        byte[][] programs = {{0x4D, 0x5A, 0, 0}, {0x7F, 0x45, 0x4C, 0x46}, {0x23, 0x21, 0x2F}, {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
            {(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}, {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCE}};
        for (byte[] head : programs) {
            assertEquals(415, assertThrows(ApiProblemException.class, () -> UploadRules.checkHead(head)).status());
        }
        UploadRules.checkHead(new byte[] {'%', 'P', 'D', 'F'});
        UploadRules.checkHead(new byte[] {'P', 'K', 3, 4});
        UploadRules.checkHead(new byte[] {'M'});
        UploadRules.checkHead(new byte[0]);
    }
}
