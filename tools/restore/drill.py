#!/usr/bin/env python3
"""The data of the restore drill and its checks after the restore (TASK-135, finding F10 of the review of TASK-131).

  drill.py prepare --url URL --state FILE --mail HOST:PORT    before the backup point
  drill.py reply --url URL --state FILE --name NAME            one more reply to the contact, for the paused worker
  drill.py verify --url URL --state FILE                        against the restored installation

"prepare" creates what the restore has to carry or to stop: attachments and a wiki image with known checksums, a mailbox
of the synthetic mail server with a customer, a contact and a ticket, a reply to the contact, and an open portal session.
It writes the identifiers and checksums into the state file. "reply" confirms one more reply. "verify" logs in to the
restored installation and checks the core flows: open the project, change a ticket, search, download every attachment
and the image byte for byte, and read the replies, which after-restore must have held as uncertain.

The administrator logs in with TASK_MANAGER_DEMO_PASSWORD from the environment, the portal account with the same
password, as tools/demo/demo.py sets them; no password is ever an argument. Only synthetic data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / "demo"))
from demo import Client  # noqa: E402

# A PNG of one pixel, so that the image check of the server accepts it.
PIXEL = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
    "1f15c4890000000d49444154789c6360f8cfc0f01f0005fe02fe0ddb9c"
    "cb0000000049454e44ae426082")


def password() -> str:
    value = os.environ.get("TASK_MANAGER_DEMO_PASSWORD", "")
    if len(value) < 12:
        raise SystemExit("TASK_MANAGER_DEMO_PASSWORD must hold the password of the demo accounts")
    return value


def admin(url: str) -> Client:
    client = Client(url)
    client.login("admin", password())
    return client


def project_id(client: Client, key: str) -> str:
    for project in client.call("GET", "/projects").body:
        if project.get("key") == key:
            return project["id"]
    raise SystemExit(f"the project {key} does not exist")


def prepare(args) -> None:
    client = admin(args.url)
    project = project_id(client, "HELP")
    state: dict = {"project": project, "attachments": [], "replies": []}

    # A ticket of its own for the replies, with the customer and contact of the portal account of Nordwerk.
    ticket = client.call("POST", f"/projects/{project}/tickets",
                         {"type": "task", "title": "Probe der Rücksicherung", "description": "Synthetische Anfrage der Probe"}).body
    number = ticket["number"]
    state["ticket"] = number
    customer = next(item["id"] for item in client.call("GET", "/helpdesk/customers").body
                    if item["name"] == "Nordwerk Maschinenbau")
    contact = next(item["id"] for item in client.call("GET", "/helpdesk/contacts", query={"customer": customer}).body
                   if "lea@nordwerk.example" in item.get("addresses", []))
    client.call("PUT", f"/projects/{project}/tickets/{number}/helpdesk/customer", {"customerId": customer, "contactId": contact})

    # Two attachments with known content.
    for index in (1, 2):
        content = f"Probe der Rücksicherung, Anhang {index}\n".encode() * (200 * index)
        added = client.call("POST", f"/projects/{project}/tickets/{number}/attachments", content=content,
                            query={"name": f"probe-{index}.txt"}, headers={"Content-Type": "application/octet-stream"}).body
        state["attachments"].append({"id": added["id"], "sha256": hashlib.sha256(content).hexdigest(), "size": len(content)})

    # An image of a wiki page of the project.
    tree = client.call("GET", f"/wiki/projects/{project}/pages").body
    pages = tree.get("pages") if isinstance(tree, dict) else tree
    page = pages[0]["id"]
    image = client.call("POST", f"/wiki/pages/{page}/images", content=PIXEL,
                        headers={"Content-Type": "application/octet-stream"}).body
    state["image"] = {"id": image["id"], "sha256": hashlib.sha256(PIXEL).hexdigest()}

    # The mailbox sends through the synthetic mail server; it never fetches anything that exists.
    host, _, port = args.mail.partition(":")
    mailbox = client.call("POST", "/helpdesk/mailboxes", {
        "name": "Probe", "projectId": project, "ticketType": "task", "enabled": True,
        "imapHost": host, "imapPort": 1143, "imapSecurity": "none", "username": "probe",
        "smtpHost": host, "smtpPort": int(port), "smtpSecurity": "none", "address": "support@probe.example",
    }).body
    state["mailbox"] = mailbox["id"]

    # A portal session that the restore must end.
    portal = Client(args.url)
    portal.login("lea.nordwerk", password())
    state["portalSession"] = True

    pathlib.Path(args.state).write_text(json.dumps(state, indent=2), encoding="utf-8")
    print(f"prepared ticket {number} with 2 attachments, a wiki image, a mailbox and a portal session")


def reply(args) -> None:
    state = json.loads(pathlib.Path(args.state).read_text(encoding="utf-8"))
    client = admin(args.url)
    sent = client.call("POST", f"/projects/{state['project']}/tickets/{state['ticket']}/helpdesk/replies", {
        "mailboxId": state["mailbox"], "subject": f"Probe {args.name}", "body": f"Synthetische Antwort {args.name} der Probe.",
    }).body
    state["replies"].append({"id": sent["id"], "name": args.name})
    pathlib.Path(args.state).write_text(json.dumps(state, indent=2), encoding="utf-8")
    print(f"reply {args.name} confirmed as {sent['id']} in state {sent.get('state')}")


def verify(args) -> None:
    state = json.loads(pathlib.Path(args.state).read_text(encoding="utf-8"))
    client = admin(args.url)
    project = state["project"]
    number = state["ticket"]

    def check(condition: bool, text: str) -> None:
        if not condition:
            raise SystemExit(f"FAILED: {text}")
        print(f"ok: {text}")

    check(client.call("GET", f"/projects/{project}").body.get("id") == project, "the project opens")
    current = client.call("GET", f"/projects/{project}/tickets/{number}")
    changed = client.call("PATCH", f"/projects/{project}/tickets/{number}", {"title": "Probe der Rücksicherung, geprüft"},
                          headers={"If-Match": current.headers.get("etag", "")})
    check(changed.body.get("fields", {}).get("title") == "Probe der Rücksicherung, geprüft", "a ticket can be changed")
    found = client.call("GET", "/search/tickets", query={"q": "Rücksicherung"}).body
    check(isinstance(found, dict) and found.get("total", 0) > 0, "the search finds the ticket")
    for attachment in state["attachments"]:
        body = client.call("GET", f"/projects/{project}/tickets/{number}/attachments/{attachment['id']}", as_bytes=True).body
        check(hashlib.sha256(body).hexdigest() == attachment["sha256"], f"attachment {attachment['id']} is the same, byte for byte")
    image = client.call("GET", f"/wiki/images/{state['image']['id']}", as_bytes=True).body
    check(hashlib.sha256(image).hexdigest() == state["image"]["sha256"], "the wiki image is the same, byte for byte")
    replies = client.call("GET", f"/projects/{project}/tickets/{number}/helpdesk").body
    listed = {item["id"]: item for item in replies.get("replies", [])}
    for expected in state["replies"]:
        held = listed.get(expected["id"])
        check(held is not None and held.get("state") == "uncertain" and held.get("errorCode") == "helpdesk.restored",
              f"reply {expected['name']} is held as uncertain with helpdesk.restored")


def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    one = commands.add_parser("prepare")
    one.add_argument("--url", required=True)
    one.add_argument("--state", required=True)
    one.add_argument("--mail", required=True)
    two = commands.add_parser("reply")
    two.add_argument("--url", required=True)
    two.add_argument("--state", required=True)
    two.add_argument("--name", required=True)
    three = commands.add_parser("verify")
    three.add_argument("--url", required=True)
    three.add_argument("--state", required=True)
    args = parser.parse_args(argv)
    {"prepare": prepare, "reply": reply, "verify": verify}[args.command](args)


if __name__ == "__main__":
    main(sys.argv[1:])
