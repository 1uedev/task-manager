#!/usr/bin/env python3
"""The synthetic mail server of the restore drill (TASK-135, finding F10 of the review of TASK-131).

It speaks just enough SMTP for the replies of the helpdesk. What it does with a connection decides the file "mode" in its
directory, read for every connection:

  hold    greets and then never answers, so the sending job waits and its reply stays in "sending"
  accept  takes every mail and stores it as a file of its own under received/

It writes one line per connection and per mail to its log. Only for the drill; it never relays anything.
"""
from __future__ import annotations

import pathlib
import socket
import sys
import threading
import time


def log(directory: pathlib.Path, text: str) -> None:
    with open(directory / "sink.log", "a", encoding="utf-8") as out:
        out.write(f"{time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())} {text}\n")


def serve(connection: socket.socket, directory: pathlib.Path) -> None:
    mode = (directory / "mode").read_text(encoding="utf-8").strip() if (directory / "mode").exists() else "accept"
    log(directory, f"connection in mode {mode}")
    stream = connection.makefile("rwb")

    def reply(line: str) -> None:
        stream.write(line.encode() + b"\r\n")
        stream.flush()

    try:
        reply("220 drill-mail ESMTP")
        if mode == "hold":
            # Read and never answer until the client gives up or the drill ends.
            while stream.readline():
                pass
            return
        while True:
            line = stream.readline()
            if not line:
                return
            command = line.decode(errors="replace").strip()
            verb = command.split(" ", 1)[0].upper()
            if verb == "EHLO":
                reply("250-drill-mail")
                reply("250-AUTH PLAIN LOGIN")
                reply("250 8BITMIME")
            elif verb == "HELO":
                reply("250 drill-mail")
            elif verb == "AUTH":
                reply("235 accepted")
            elif verb in ("MAIL", "RCPT", "RSET", "NOOP"):
                reply("250 ok")
            elif verb == "DATA":
                reply("354 go ahead")
                lines = []
                while True:
                    data = stream.readline()
                    if not data or data in (b".\r\n", b".\n"):
                        break
                    lines.append(data)
                received = directory / "received"
                received.mkdir(exist_ok=True)
                name = f"{time.time_ns()}.eml"
                (received / name).write_bytes(b"".join(lines))
                log(directory, f"mail stored as {name}")
                reply("250 queued")
            elif verb == "QUIT":
                reply("221 bye")
                return
            else:
                reply("502 not implemented")
    except OSError:
        return
    finally:
        connection.close()


def main(argv: list[str]) -> None:
    directory = pathlib.Path(argv[0] if argv else "/var/sink")
    directory.mkdir(parents=True, exist_ok=True)
    server = socket.create_server(("0.0.0.0", 2525))
    log(directory, "listening on 2525")
    while True:
        connection, _ = server.accept()
        threading.Thread(target=serve, args=(connection, directory), daemon=True).start()


if __name__ == "__main__":
    main(sys.argv[1:])
