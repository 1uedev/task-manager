#!/usr/bin/env python3
"""The writer of the restore drill (TASK-131, DEC-161).

It logs in as the administrator and creates one ticket after another in a project until it is stopped, and writes a line
for every confirmed write: the time the server answered, the number of the ticket and the time the request left. The drill compares that log with
the state after the restore and so measures the data loss (rule 12): the newest confirmed write that is gone.

Only synthetic data, only against the installation of the drill; it is no load test.
"""
from __future__ import annotations

import argparse
import datetime
import http.client
import json
import os
import signal
import sys
import time
import urllib.parse


class Client:
    """The few calls of the interface this writer needs, with session cookie and CSRF token."""

    def __init__(self, base_url: str) -> None:
        parts = urllib.parse.urlsplit(base_url)
        self.host = parts.hostname or "127.0.0.1"
        self.port = parts.port or 80
        self.prefix = "/api/v1"
        self.cookies: dict[str, str] = {}
        self.csrf: str | None = None

    def call(self, method: str, path: str, body: object = None, headers: dict[str, str] | None = None) -> dict:
        connection = http.client.HTTPConnection(self.host, self.port, timeout=30)
        try:
            request = dict(headers or {})
            payload = None
            if body is not None:
                payload = json.dumps(body).encode()
                request["Content-Type"] = "application/json"
            if self.cookies:
                request["Cookie"] = "; ".join(f"{name}={value}" for name, value in self.cookies.items())
            if self.csrf and method != "GET":
                request["X-CSRF-Token"] = self.csrf
            connection.request(method, self.prefix + path, payload, request)
            response = connection.getresponse()
            raw = response.read()
            for header in response.getheader("set-cookie", "").split(", "):
                if "=" in header:
                    name, _, rest = header.partition("=")
                    self.cookies[name.strip()] = rest.split(";")[0]
            if response.status >= 400:
                raise SystemExit(f"{method} {path} answered {response.status}: {raw[:200]!r}")
            answer = json.loads(raw) if raw else {}
            if isinstance(answer, dict) and answer.get("csrfToken"):
                self.csrf = answer["csrfToken"]
            return answer if isinstance(answer, dict) else {}
        finally:
            connection.close()

    def login(self, username: str, password: str) -> None:
        self.call("GET", "/session")
        self.call("POST", "/session", {"username": username, "password": password})


def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description="writes tickets and logs every confirmed write")
    parser.add_argument("--url", default=os.environ.get("TASK_MANAGER_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--admin", default="admin")
    parser.add_argument("--project", required=True, help="key of the project the tickets go into")
    parser.add_argument("--log", required=True, help="file for one line per confirmed write")
    parser.add_argument("--pause", type=float, default=1.0, help="seconds between two writes")
    args = parser.parse_args(argv)

    password = os.environ.get("TASK_MANAGER_DEMO_PASSWORD", "")
    if len(password) < 12:
        raise SystemExit("TASK_MANAGER_DEMO_PASSWORD must hold the password of the administrator")

    client = Client(args.url)
    client.login(args.admin, password)

    running = True

    def stop(*_: object) -> None:
        nonlocal running
        running = False

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    serial = 0
    with open(args.log, "a", encoding="utf-8", buffering=1) as log:
        while running:
            serial += 1
            title = f"Wiederherstellungsprobe {serial}"
            sent = datetime.datetime.now(datetime.timezone.utc).isoformat()
            created = client.call("POST", f"/projects/{args.project}/tickets",
                                  {"title": title, "description": "Synthetischer Eintrag der Probe", "type": "task"})
            confirmed = datetime.datetime.now(datetime.timezone.utc).isoformat()
            # The line is written and flushed only after the server confirmed the write, so every line in this log is a
            # write the installation acknowledged.
            # Besides the moment of the confirmation it keeps the moment the request left: a write sent after the boundary of
            # a point can never be in that point (TASK-135, finding F10).
            log.write(f"{confirmed} {created.get('number')} {sent} {title}\n")
            time.sleep(args.pause)
    print(f"writer stopped after {serial} confirmed writes", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1:])
