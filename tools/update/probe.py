#!/usr/bin/env python3
"""The data and the core flows of the update check (TASK-136, findings F4 and F10 of the review of TASK-132).

  probe.py first-login --url URL                              the first login of the administrator from bootstrap-admin
  probe.py prepare --url URL --state FILE                     before the backup point of the update
  probe.py flows --url URL --state FILE --phase NAME [--upload]

"first-login" replaces the one time password of the administrator by the demo password. "prepare" creates a ticket of
its own in the project HELP with an attachment and an image on a wiki page, with known checksums, so that the backup point
before the update carries both kinds of stored content (F4). "flows" runs the core flows of docs/INSTALL.md through the
address it gets: open the project, change the ticket, find it in the search, download every attachment and the image byte
for byte, look at the page of the background jobs; with --upload it also uploads one more attachment and downloads it at
once, so that a later phase downloads it again (F10).

Passwords come only from the environment (TASK_MANAGER_ONE_TIME_PASSWORD, TASK_MANAGER_DEMO_PASSWORD), never from an
argument (H2). With an https address the certificate is checked against SSL_CERT_FILE. Only synthetic data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / "demo"))
from demo import Client  # noqa: E402

PIXEL = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
    "1f15c4890000000d49444154789c6360f8cfc0f01f0005fe02fe0ddb9c"
    "cb0000000049454e44ae426082")
TITLE = "Updateprobe"


def secret(name: str) -> str:
    value = os.environ.get(name, "")
    if len(value) < 12:
        raise SystemExit(f"{name} must be set in the environment")
    return value


def admin(url: str) -> Client:
    client = Client(url)
    client.login("admin", secret("TASK_MANAGER_DEMO_PASSWORD"))
    return client


def check(condition: bool, text: str) -> None:
    if not condition:
        raise SystemExit(f"FAILED: {text}")
    print(f"ok: {text}")


def first_login(args) -> None:
    client = Client(args.url)
    client.login("admin", secret("TASK_MANAGER_ONE_TIME_PASSWORD"), new_password=secret("TASK_MANAGER_DEMO_PASSWORD"))
    print("admin: one time password replaced by the demo password")


def prepare(args) -> None:
    client = admin(args.url)
    project = next(item["id"] for item in client.call("GET", "/projects").body if item.get("key") == "HELP")
    ticket = client.call("POST", f"/projects/{project}/tickets",
                         {"type": "task", "title": TITLE, "description": "Synthetische Anfrage der Updateprüfung"}).body
    state: dict = {"project": project, "ticket": ticket["number"], "attachments": []}
    content = ("Anhang der Updateprüfung\n" * 300).encode()
    added = client.call("POST", f"/projects/{project}/tickets/{ticket['number']}/attachments", content=content,
                        query={"name": "update-1.txt"}, headers={"Content-Type": "application/octet-stream"}).body
    state["attachments"].append({"id": added["id"], "sha256": hashlib.sha256(content).hexdigest(), "phase": "prepare"})
    tree = client.call("GET", f"/wiki/projects/{project}/pages").body
    pages = tree.get("pages") if isinstance(tree, dict) else tree
    check(bool(pages), "the project HELP has a wiki page for the image")
    image = client.call("POST", f"/wiki/pages/{pages[0]['id']}/images", content=PIXEL,
                        headers={"Content-Type": "application/octet-stream"}).body
    state["image"] = {"id": image["id"], "sha256": hashlib.sha256(PIXEL).hexdigest()}
    pathlib.Path(args.state).write_text(json.dumps(state, indent=2), encoding="utf-8")
    print(f"prepared ticket {ticket['number']} with an attachment and a wiki image")


def flows(args) -> None:
    path = pathlib.Path(args.state)
    state = json.loads(path.read_text(encoding="utf-8"))
    client = admin(args.url)
    project, number = state["project"], state["ticket"]
    check(client.call("GET", f"/projects/{project}").body.get("id") == project, f"{args.phase}: the project opens")
    current = client.call("GET", f"/projects/{project}/tickets/{number}")
    title = f"{TITLE} {args.phase}"
    changed = client.call("PATCH", f"/projects/{project}/tickets/{number}", {"title": title},
                          headers={"If-Match": current.headers.get("etag", "")})
    check(changed.body.get("fields", {}).get("title") == title, f"{args.phase}: the ticket can be changed")
    # The search finds exactly this ticket with its new title, not just any hit (TASK-137, N9).
    found = client.call("GET", "/search/tickets", query={"q": args.phase}).body
    hits = found.get("items", []) if isinstance(found, dict) else []
    check(any(hit.get("projectId") == project and hit.get("number") == number
              and hit.get("fields", {}).get("title") == title for hit in hits),
          f"{args.phase}: the search finds the changed ticket by project, number and new title")
    if args.upload:
        content = f"Hochgeladen in der Phase {args.phase}\n".encode() * 500
        added = client.call("POST", f"/projects/{project}/tickets/{number}/attachments", content=content,
                            query={"name": f"upload-{args.phase}.txt"},
                            headers={"Content-Type": "application/octet-stream"}).body
        state["attachments"].append({"id": added["id"], "sha256": hashlib.sha256(content).hexdigest(), "phase": args.phase})
        path.write_text(json.dumps(state, indent=2), encoding="utf-8")
    for attachment in state["attachments"]:
        body = client.call("GET", f"/projects/{project}/tickets/{number}/attachments/{attachment['id']}", as_bytes=True).body
        check(hashlib.sha256(body).hexdigest() == attachment["sha256"],
              f"{args.phase}: attachment from {attachment['phase']} downloads byte for byte")
    image = client.call("GET", f"/wiki/images/{state['image']['id']}", as_bytes=True).body
    check(hashlib.sha256(image).hexdigest() == state["image"]["sha256"], f"{args.phase}: the wiki image downloads byte for byte")
    # A worker is alive and has run a recurring task of the installation to its end (TASK-137, N9): a stopped worker or
    # one that fails every job does not pass, however well the endpoint answers.
    deadline = time.monotonic() + 120
    while True:
        summary = client.call("GET", "/jobs/summary").body
        done = client.call("GET", "/jobs", query={"state": "succeeded", "limit": "100"}).body
        recurring = [job for job in done if str(job.get("kind", "")).startswith("recurring.")] if isinstance(done, list) else []
        if isinstance(summary, dict) and summary.get("activeWorkers", 0) >= 1 and recurring:
            break
        if time.monotonic() >= deadline:
            check(False, f"{args.phase}: a worker is active and a recurring task succeeded")
        time.sleep(3)
    check(True, f"{args.phase}: a worker is active and a recurring task succeeded ({recurring[0]['kind']})")


def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    one = commands.add_parser("first-login")
    one.add_argument("--url", required=True)
    two = commands.add_parser("prepare")
    two.add_argument("--url", required=True)
    two.add_argument("--state", required=True)
    three = commands.add_parser("flows")
    three.add_argument("--url", required=True)
    three.add_argument("--state", required=True)
    three.add_argument("--phase", required=True)
    three.add_argument("--upload", action="store_true")
    args = parser.parse_args(argv)
    {"first-login": first_login, "prepare": prepare, "flows": flows}[args.command](args)


if __name__ == "__main__":
    main(sys.argv[1:])
