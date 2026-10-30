package taskmanager.helpdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** TASK-064, H2: what the test server counts, driven line by line so that no buffer decides the order. */
class ScriptedSmtpTest {
    private static void say(OutputStream out, String line) throws Exception {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Sends the commands up to DATA and the given lines of the mail; returns the reader for the answers. */
    private static BufferedReader start(Socket socket, String... lines) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = socket.getOutputStream();
        in.readLine();
        for (String command : new String[] {"EHLO test.example", "MAIL FROM:<support@firma.example>", "RCPT TO:<lea@nordwerk.example>", "DATA"}) {
            say(out, command);
            in.readLine();
        }
        for (String line : lines) {
            say(out, line);
        }
        return in;
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        Instant end = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.getAsBoolean() && Instant.now().isBefore(end)) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "the server did not count the mail");
    }

    @Test
    void aConnectionThatEndsBeforeTheEndLineIsNotHandedOver() throws Exception {
        try (ScriptedSmtp server = new ScriptedSmtp(ScriptedSmtp.Mode.ACCEPT)) {
            try (Socket socket = new Socket("localhost", server.port())) {
                start(socket, "Subject: Abbruch", "", "Eins");
            }
            await(() -> server.broken().size() == 1);
            assertEquals(0, server.handedOver().size());
        }
    }

    @Test
    void aDroppedMailIsReadToItsEndAndNotAnswered() throws Exception {
        try (ScriptedSmtp server = new ScriptedSmtp(ScriptedSmtp.Mode.DROP_IN_DATA)) {
            try (Socket socket = new Socket("localhost", server.port())) {
                BufferedReader in = start(socket, "Subject: Abbruch", "", "Eins");
                // Not yet dropped while the mail goes on: the sender can still write.
                Thread.sleep(200);
                assertTrue(socket.getInputStream().available() == 0);
                say(socket.getOutputStream(), "Zwei");
                say(socket.getOutputStream(), ".");
                assertEquals(null, in.readLine(), "no answer to the end line");
            }
            await(() -> server.broken().size() == 1);
            assertEquals(0, server.handedOver().size());
        }
    }

    @Test
    void aMailReadToItsEndLineIsHandedOver() throws Exception {
        try (ScriptedSmtp server = new ScriptedSmtp(ScriptedSmtp.Mode.ACCEPT)) {
            try (Socket socket = new Socket("localhost", server.port())) {
                BufferedReader in = start(socket, "Subject: Ganz", "", "Eins", ".");
                assertTrue(in.readLine().startsWith("250"));
            }
            assertEquals(1, server.handedOver().size());
            assertEquals(0, server.broken().size());
        }
    }
}
