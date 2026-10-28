package taskmanager.helpdesk.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TASK-057: only the topmost result of the own receiving server confirms a sender, and only with DMARC pass for its domain. */
class SenderCheckTest {
    private static final String OWN = "mx.firma.example";

    @Test
    void aPassOfTheOwnServerForTheFromDomainConfirms() {
        assertTrue(SenderCheck.confirmed(OWN, List.of("mx.firma.example; spf=pass; dmarc=pass (p=reject) header.from=Nordwerk.example"),
            "lea@nordwerk.example"));
        assertTrue(SenderCheck.confirmed(OWN, List.of("MX.FIRMA.EXAMPLE 1; dkim=pass header.d=nordwerk.example;\r\n dmarc=pass header.from=\"nordwerk.example\""),
            "lea@nordwerk.example"));
    }

    @Test
    void everythingElseConfirmsNothing() {
        String pass = "mx.firma.example; dmarc=pass header.from=nordwerk.example";
        assertFalse(SenderCheck.confirmed(null, List.of(pass), "lea@nordwerk.example"), "no authserv-id configured");
        assertFalse(SenderCheck.confirmed(OWN, List.of(), "lea@nordwerk.example"), "no header");
        assertFalse(SenderCheck.confirmed(OWN, List.of("mx.fremd.example; dmarc=pass header.from=nordwerk.example"), "lea@nordwerk.example"),
            "another server");
        assertFalse(SenderCheck.confirmed(OWN, List.of("mx.firma.example; dmarc=fail header.from=nordwerk.example", pass), "lea@nordwerk.example"),
            "the topmost header of the own server decides");
        assertFalse(SenderCheck.confirmed(OWN, List.of("mx.firma.example; dmarc=pass header.from=andere.example"), "lea@nordwerk.example"),
            "another domain");
        assertFalse(SenderCheck.confirmed(OWN, List.of("mx.firma.example; dmarc=none header.from=nordwerk.example"), "lea@nordwerk.example"));
        assertFalse(SenderCheck.confirmed(OWN, List.of("mx.firma.example; spf=pass (dmarc=pass header.from=nordwerk.example)"), "lea@nordwerk.example"),
            "a comment is no result");
        assertFalse(SenderCheck.confirmed(OWN, List.of("mx.firma.example; dmarc=pass"), "lea@nordwerk.example"), "no header.from");
        assertFalse(SenderCheck.confirmed(OWN, List.of("mx.firma.example.attacker.example; dmarc=pass header.from=nordwerk.example"),
            "lea@nordwerk.example"), "a longer id is another id");
    }
}
