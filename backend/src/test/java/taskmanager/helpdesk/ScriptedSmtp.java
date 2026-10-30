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
 * or it reads the whole mail and ends the connection before it answers, so the sender cannot know whether it was taken. For
 * TASK-062 it also ends the connection before the mail and accepts a mail only after a long check.
 * Every mail it read to its end line counts as handed over; a mail it dropped or whose connection ended before counts as
 * broken.
 */
final class ScriptedSmtp implements AutoCloseable {
    /** How long the server checks a mail in {@link Mode#SLOW_ACCEPT}. */
    static final java.time.Duration SLOW = java.time.Duration.ofSeconds(6);

    enum Mode {
        /** Answers 250 to everything and takes the mail. */
        ACCEPT,
        /** Reads the whole mail and closes the connection without an answer to its end. */
        DROP_AFTER_DATA,
        /** Closes the connection on the command DATA, before any of the mail is sent (TASK-062). */
        DROP_AT_DATA,
        /**
         * Keeps only the first line of the mail, reads the rest without keeping it and closes the connection before it answers
         * the end line, so the server holds nothing (TASK-063). It waits for the end line so that the sender has always
         * written the whole mail when it learns of the end, whatever its buffers do (TASK-064, H2).
         */
        DROP_IN_DATA,
        /** Reads the whole mail and accepts it only after {@link #SLOW} (TASK-062). */
        SLOW_ACCEPT,
        /**
         * Reads the whole mail and answers only after {@link #release()}: it accepts the mail, unless the mode changed to
         * {@link #DROP_AFTER_DATA} meanwhile (TASK-062).
         */
        HELD,
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
    private final List<String> broken = new CopyOnWriteArrayList<>();
    private volatile Socket current;
    private final java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);

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

    /** Lets a {@link Mode#HELD} server answer. */
    void release() {
        gate.countDown();
    }

    /** The mails whose connection ended before their end line; the server holds none of them. */
    List<String> broken() {
        return broken;
    }

    /** The mails the server read to their end line, whatever it answered. */
    List<String> handedOver() {
        return handedOver;
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                current = socket;
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
                if (mode == Mode.DROP_AT_DATA) {
                    return;
                }
                say(out, "354 End data with <CR><LF>.<CR><LF>");
                StringBuilder mail = new StringBuilder();
                boolean first = true;
                while ((line = in.readLine()) != null && !line.equals(".")) {
                    mail.append(line).append("\r\n");
                    if (first && mode == Mode.DROP_IN_DATA) {
                        // Drops the mail after its first line: it was never handed over (TASK-063, F4).
                        broken.add(mail.toString());
                        while ((line = in.readLine()) != null && !line.equals(".")) {
                            // The rest of the mail is read and dropped.
                        }
                        return;
                    }
                    first = false;
                }
                if (line == null) {
                    // The connection ended before the end of the mail: not handed over either.
                    broken.add(mail.toString());
                    return;
                }
                handedOver.add(mail.toString());
                if (mode == Mode.DROP_AFTER_DATA) {
                    return;
                }
                if (mode == Mode.SLOW_ACCEPT || mode == Mode.HELD) {
                    try {
                        if (mode == Mode.SLOW_ACCEPT) {
                            Thread.sleep(SLOW.toMillis());
                        } else {
                            gate.await();
                        }
                    } catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (mode == Mode.DROP_AFTER_DATA) {
                        return;
                    }
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
        gate.countDown();
        server.close();
        Socket open = current;
        if (open != null) {
            open.close();
        }
        try {
            thread.join(5_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
