package taskmanager.helpdesk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small SMTP server for the failures a real one does not show on demand (TASK-058): it refuses the recipient with a code,
 * or it reads the whole mail and ends the connection before it answers, so the sender cannot know whether it was taken.
 * Every mail it read to the end counts as handed over.
 */
final class ScriptedSmtp implements AutoCloseable {
    enum Mode {
        /** Answers 250 to everything and takes the mail. */
        ACCEPT,
        /** Reads the whole mail and closes the connection without an answer to its end. */
        DROP_AFTER_DATA,
        /** Refuses the recipient with 550. */
        REFUSE_RECIPIENT,
        /** Refuses the recipient for now with 451. */
        DEFER_RECIPIENT
    }

    private final ServerSocket server;
    private final Thread thread;
    private volatile Mode mode;
    private final AtomicInteger connections = new AtomicInteger();
    private final List<String> handedOver = new CopyOnWriteArrayList<>();

    ScriptedSmtp(Mode mode) throws IOException {
        this.mode = mode;
        this.server = new ServerSocket(0);
        this.thread = new Thread(this::serve, "scripted-smtp");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    int port() {
        return server.getLocalPort();
    }

    void mode(Mode value) {
        mode = value;
    }

    int connections() {
        return connections.get();
    }

    /** The mails the server read to their end, whatever it answered. */
    List<String> handedOver() {
        return handedOver;
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                connections.incrementAndGet();
                talk(socket);
            } catch (IOException ended) {
                // The next connection or the end of the server.
            }
        }
    }

    private void talk(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = socket.getOutputStream();
        say(out, "220 scripted.example ESMTP");
        String line;
        while ((line = in.readLine()) != null) {
            String command = line.toUpperCase();
            if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                say(out, "250 scripted.example");
            } else if (command.startsWith("MAIL FROM")) {
                say(out, "250 OK");
            } else if (command.startsWith("RCPT TO")) {
                say(out, switch (mode) {
                    case REFUSE_RECIPIENT -> "550 5.1.1 No such user";
                    case DEFER_RECIPIENT -> "451 4.3.0 Try again later";
                    default -> "250 OK";
                });
            } else if (command.equals("DATA")) {
                say(out, "354 End data with <CR><LF>.<CR><LF>");
                StringBuilder mail = new StringBuilder();
                while ((line = in.readLine()) != null && !line.equals(".")) {
                    mail.append(line).append("\r\n");
                }
                handedOver.add(mail.toString());
                if (mode == Mode.DROP_AFTER_DATA) {
                    return;
                }
                say(out, "250 OK queued");
            } else if (command.startsWith("RSET") || command.startsWith("NOOP")) {
                say(out, "250 OK");
            } else if (command.startsWith("QUIT")) {
                say(out, "221 Bye");
                return;
            } else {
                say(out, "502 Command not implemented");
            }
        }
    }

    private static void say(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
