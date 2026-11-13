#!/usr/bin/env python3
"""Synthetic demonstration data and the search measurement of TASK-031 (DEC-071).

Everything goes through the public HTTP API of a running task manager, with the same rights checks, history and
search projection as the web interface. Only the Python standard library is used, so the tool runs in the plain
python image of the compose file or on any machine with Python 3.11 or newer.

    seed --profile demo        small presentation data set with four people of different rights
    seed --profile reference   reference stock from OPERATIONS.md: 20 projects, 90,000 tickets, 100 accounts
    measure                    15 concurrent users with the action mix from OPERATIONS.md, p50/p95 per class
    verify                     checks rights, conflicts, search and the session on the presentation data set

All names, texts and accounts are synthetic. The password of every demo account comes from the environment
variable TASK_MANAGER_DEMO_PASSWORD; the tool never writes it anywhere.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import datetime
import hashlib
import http.client
import json
import math
import os
import platform
import random
import statistics
import sys
import threading
import time
import urllib.parse
from dataclasses import dataclass, field

API = "/api/v1"


class ApiError(Exception):
    def __init__(self, method: str, path: str, status: int, body: object):
        code = body.get("code") if isinstance(body, dict) else None
        super().__init__(f"{method} {path} answered {status} {code or ''}".strip())
        self.status = status
        self.code = code
        self.body = body


class UnclearOutcome(Exception):
    """A change was sent but its answer was lost; the server may or may not have carried it out.

    Such a request is never sent a second time: creating a ticket or a tag twice would change the data set, so the
    caller has to look at the current state instead of guessing."""

    def __init__(self, method: str, path: str, cause: BaseException):
        super().__init__(f"{method} {path}: answer lost ({cause.__class__.__name__}); the change may or may not have been saved")
        self.method = method
        self.path = path


# Only these requests change nothing on the server and may be repeated after a lost answer.
SAFE_METHODS = ("GET", "HEAD")
# A kept-alive connection idle for longer is opened anew before a change, so that a connection the server closed in
# the meantime does not cost an answer. The server keeps idle connections for far longer than this.
IDLE_RECONNECT_SECONDS = 5.0


@dataclass
class Response:
    status: int
    headers: dict[str, str]
    body: object
    seconds: float


class Client:
    """One browser-like session: keeps its cookies and CSRF token, reuses one connection, never shares it between threads."""

    def __init__(self, base_url: str, timeout: float = 60.0):
        parsed = urllib.parse.urlsplit(base_url)
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise SystemExit(f"invalid server address: {base_url}")
        self.scheme = parsed.scheme
        self.host = parsed.hostname
        self.port = parsed.port or (443 if parsed.scheme == "https" else 80)
        self.timeout = timeout
        self.connection: http.client.HTTPConnection | None = None
        self.last_used = 0.0
        self.cookies: dict[str, str] = {}
        self.csrf: str | None = None
        self.username: str | None = None

    def _connect(self) -> http.client.HTTPConnection:
        if self.connection is None:
            kind = http.client.HTTPSConnection if self.scheme == "https" else http.client.HTTPConnection
            self.connection = kind(self.host, self.port, timeout=self.timeout)
        return self.connection

    def close(self) -> None:
        if self.connection is not None:
            self.connection.close()
            self.connection = None

    def call(self, method: str, path: str, body: object = None, query: dict | list | None = None,
             headers: dict[str, str] | None = None, expect: tuple[int, ...] | None = (200, 201, 204),
             content: bytes | None = None, as_bytes: bool = False) -> Response:
        """`content` is sent as it is (an upload); `as_bytes` returns the answer as bytes instead of reading it as JSON (a download)."""
        target = API + path
        if query:
            target += "?" + urllib.parse.urlencode(query, doseq=True)
        sent = {"Accept": "application/json"}
        if self.cookies:
            sent["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        if method not in ("GET", "HEAD") and self.csrf:
            sent["X-CSRF-Token"] = self.csrf
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            sent["Content-Type"] = "application/json"
        if content is not None:
            data = content
        sent.update(headers or {})
        safe = method in SAFE_METHODS
        if not safe and self.connection is not None and time.monotonic() - self.last_used > IDLE_RECONNECT_SECONDS:
            self.close()
        for attempt in (1, 2):
            started = time.perf_counter()
            connection = self._connect()
            if connection.sock is None:
                # Opening the connection sends nothing; if it fails, the outcome is certain and the error stays as it is.
                try:
                    connection.connect()
                except OSError:
                    self.close()
                    raise
            try:
                connection.request(method, target, body=data, headers=sent)
                raw = connection.getresponse()
                payload = raw.read()
                seconds = time.perf_counter() - started
                self.last_used = time.monotonic()
                break
            except (OSError, http.client.HTTPException) as error:
                # Usually a kept-alive connection the server already closed, but also a cut off answer or a timeout. A
                # reading request on a closed connection is tried once more on a fresh one. A change is never sent again:
                # once it may have left, the server may have saved it, whatever went wrong afterwards.
                self.close()
                if not safe:
                    raise UnclearOutcome(method, path, error) from error
                retry = isinstance(error, (http.client.RemoteDisconnected, ConnectionResetError, BrokenPipeError,
                                           http.client.CannotSendRequest))
                if attempt == 2 or not retry:
                    raise
        received = {k.lower(): v for k, v in raw.getheaders()}
        for name, value in raw.getheaders():
            if name.lower() == "set-cookie":
                self._cookie(value)
        parsed: object = None
        if as_bytes:
            parsed = payload
        elif payload:
            try:
                parsed = json.loads(payload)
            except ValueError:
                parsed = payload.decode(errors="replace")
        if isinstance(parsed, dict) and isinstance(parsed.get("csrfToken"), str):
            self.csrf = parsed["csrfToken"]
        response = Response(raw.status, received, parsed, seconds)
        if expect is not None and raw.status not in expect:
            raise ApiError(method, path, raw.status, parsed)
        return response

    def _cookie(self, header: str) -> None:
        first, _, attributes = header.partition(";")
        name, _, value = first.strip().partition("=")
        expired = "max-age=0" in attributes.lower().replace(" ", "")
        if expired or value == "":
            self.cookies.pop(name, None)
        else:
            self.cookies[name] = value

    # ------------------------------------------------------------------ session

    def login(self, username: str, password: str, new_password: str | None = None) -> dict:
        self.cookies.clear()
        self.csrf = None
        self.call("GET", "/session")
        session = self.call("POST", "/session", {"username": username, "password": password}).body
        if isinstance(session, dict) and session.get("passwordChangeBy"):
            if new_password is None:
                raise SystemExit(f"{username} must change the password first")
            session = self.call("POST", "/session/password", {"currentPassword": password, "newPassword": new_password}).body
        self.username = username
        return session if isinstance(session, dict) else {}


def demo_password() -> str:
    value = os.environ.get("TASK_MANAGER_DEMO_PASSWORD", "")
    if len(value) < 12:
        raise SystemExit("TASK_MANAGER_DEMO_PASSWORD must be set to a password of at least 12 characters")
    return value


def admin_client(args) -> Client:
    """The administrator from bootstrap-admin. With --one-time-password the first login sets the demo password."""
    password = demo_password()
    client = Client(args.url)
    try:
        client.login(args.admin, password)
    except ApiError as error:
        if error.status != 401 or not args.one_time_password:
            raise SystemExit(f"login of {args.admin} failed ({error}); pass --one-time-password from bootstrap-admin "
                             "or set TASK_MANAGER_DEMO_PASSWORD to the administrator's password")
        client.login(args.admin, args.one_time_password, new_password=password)
        print(f"{args.admin}: one time password replaced by TASK_MANAGER_DEMO_PASSWORD")
    return client


# ---------------------------------------------------------------------- data building blocks

ALL_FIELDS = ["title", "description", "type", "status", "assignee", "tags", "attachments", "customer", "contact", "priority"]
PROJECT_ACTIONS_FULL = ["project.read", "project.manage", "ticket.read", "ticket.create", "ticket.edit", "tag.create"]
PROJECT_ACTIONS_EDIT = ["project.read", "ticket.read", "ticket.create", "ticket.edit", "tag.create"]
PROJECT_ACTIONS_READ = ["project.read", "ticket.read"]
# The wiki of the demo projects (TASK-093, TASK-094): project leads publish, development edits, everybody else reads.
WIKI_ACTIONS_FULL = ["wiki.read", "wiki.edit", "wiki.publish"]
WIKI_ACTIONS_EDIT = ["wiki.read", "wiki.edit"]
WIKI_ACTIONS_READ = ["wiki.read"]


@dataclass
class GroupSpec:
    name: str
    actions: list[str]
    read: list[str]
    write: list[str]
    resource: str = "ticket"


class Builder:
    def __init__(self, admin: Client, workers: int):
        self.admin = admin
        self.workers = workers
        self.local = threading.local()
        self.credentials: tuple[str, str] | None = None

    def worker(self) -> Client:
        """Each thread works in its own administrator session, like several open browser windows."""
        client = getattr(self.local, "client", None)
        if client is None:
            client = Client(f"{self.admin.scheme}://{self.admin.host}:{self.admin.port}")
            assert self.credentials is not None
            client.login(*self.credentials)
            self.local.client = client
        return client

    def group(self, spec: GroupSpec, members: list[str]) -> str:
        group = self.admin.call("POST", "/permissions/groups", {"name": spec.name}).body["id"]
        for action in spec.actions:
            self.admin.call("PUT", f"/permissions/groups/{group}/actions", {"action": action, "projectId": None, "enabled": True})
        for operation, fields in (("read", spec.read), ("write", spec.write)):
            for name in fields:
                self.admin.call("PUT", f"/permissions/groups/{group}/fields",
                                {"resourceType": spec.resource, "field": name, "projectId": None, "operation": operation, "effect": "allow"})
        for account in members:
            self.admin.call("PUT", f"/permissions/groups/{group}/members/{account}")
        return group

    def account(self, username: str, display: str, password: str) -> str:
        issued = self.worker().call("POST", "/accounts", {"username": username, "displayName": display}).body
        person = Client(f"{self.admin.scheme}://{self.admin.host}:{self.admin.port}")
        person.login(username, issued["oneTimePassword"], new_password=password)
        person.call("DELETE", "/session")
        person.close()
        return issued["account"]["id"]

    def project(self, key: str, name: str, description: str, members: list[str]) -> str:
        created = self.admin.call("POST", "/projects", {"key": key, "name": name, "description": description})
        project = created.body["id"]
        etag = created.headers.get("etag")
        for account in members:
            etag = self.admin.call("PUT", f"/projects/{project}/members/{account}", headers={"If-Match": etag}).headers.get("etag", etag)
        return project

    def company_tag(self, name: str) -> str:
        return self.admin.call("POST", "/tags", {"name": name}).body["id"]

    def project_tag(self, project: str, name: str) -> str:
        return self.admin.call("POST", f"/projects/{project}/catalog/tags", {"name": name}).body["id"]

    def ticket(self, project: str, draft: dict, status: str, tags: list[str]) -> int:
        client = self.worker()
        created = client.call("POST", f"/projects/{project}/tickets", draft)
        number = created.body["number"]
        etag = created.headers.get("etag")
        path = f"/projects/{project}/tickets/{number}"
        steps = {"new": [], "in_progress": ["in_progress"], "done": ["in_progress", "done"]}[status]
        for step in steps:
            etag = client.call("PATCH", path, {"status": step}, headers={"If-Match": etag}).headers.get("etag")
        for tag in tags:
            client.call("POST", f"{path}/tags", {"tagId": tag})
        return number

    def tickets(self, jobs: list[tuple[str, dict, str, list[str]]], label: str) -> None:
        started = time.monotonic()
        done = 0
        failures = 0
        with concurrent.futures.ThreadPoolExecutor(self.workers) as pool:
            for future in concurrent.futures.as_completed(pool.submit(self.ticket, *job) for job in jobs):
                try:
                    future.result()
                except (ApiError, UnclearOutcome) as error:
                    failures += 1
                    if failures <= 5:
                        print(f"  ticket failed: {error}", file=sys.stderr)
                done += 1
                if done % 2000 == 0 or done == len(jobs):
                    rate = done / max(time.monotonic() - started, 0.001)
                    print(f"  {label}: {done}/{len(jobs)} tickets, {rate:.0f} per second", flush=True)
        if failures:
            raise SystemExit(f"{failures} tickets could not be created")


def account_id(client: Client, username: str) -> str:
    for entry in client.call("GET", "/directory/accounts").body:
        if entry["username"] == username:
            return entry["id"]
    raise SystemExit(f"account {username} not found")


def refuse_second_run(admin: Client, keys: set[str], usernames: set[str]) -> None:
    existing = {item["key"] for item in admin.call("GET", "/projects").body}
    existing |= {entry["username"] for entry in admin.call("GET", "/directory/accounts").body}
    taken = sorted((keys | usernames) & existing)
    if taken:
        raise SystemExit(f"{', '.join(taken[:5])} already exist; the data set was created before. "
                         "Start with an empty database (docker compose down -v) to create it again.")


# ---------------------------------------------------------------------- presentation data set

DEMO_PEOPLE = [
    # username, display name, group, projects
    ("anna", "Anna Albers", "Projektleitung", ["WEB", "APP", "HELP"]),
    ("ben", "Ben Becker", "Entwicklung", ["WEB", "APP"]),
    ("carla", "Carla Cordes", "Lesen ohne Beschreibung", ["WEB"]),
    ("david", "David Dorn", "Entwicklung", ["HELP"]),
]

DEMO_PROJECTS = [
    ("WEB", "Webportal Relaunch", "Neues Kundenportal mit überarbeiteter Startseite, Formularen und Suche.",
     ["Startseite", "Formular", "Suchmaschine"],
     ["Startseite lädt auf Mobilgeräten zu langsam", "Kontaktformular verliert Eingaben nach Fehler",
      "Navigation für Tastaturbedienung verbessern", "Neue Bildergalerie für Referenzen", "Cookie Hinweis überarbeiten",
      "Fehlerseite 404 gestalten", "Suchfeld schlägt keine Begriffe vor", "Impressum und Datenschutz aktualisieren",
      "Kontrast der Schaltflächen prüfen", "Login Formular zeigt keine Fehlermeldung", "Newsletter Anmeldung doppelt bestätigen",
      "Ladeanimation entfernen", "Übersetzung der Produktseiten ins Englische", "Metadaten für Suchmaschinen ergänzen",
      "Bilder im Webformat ausliefern", "Footer Links prüfen"]),
    ("APP", "Mobile App", "Begleit App für Außendienst mit Offline Modus.",
     ["iOS", "Android", "Offline"],
     ["Absturz beim Öffnen der Kamera", "Offline Modus synchronisiert nicht", "Push Mitteilungen für neue Aufträge",
      "Anmeldung mit Fingerabdruck", "Dunkle Darstellung für alle Ansichten", "Kartenansicht zeigt falschen Standort",
      "Fotos vor dem Hochladen verkleinern", "Speicherverbrauch im Hintergrund senken", "Formular für Arbeitsbericht",
      "Unterschrift des Kunden erfassen", "Akkuverbrauch der Standortabfrage", "Liste der Aufträge sortieren"]),
    ("HELP", "Interner Helpdesk", "Anfragen der Kolleginnen und Kollegen zu Arbeitsplatz und Software.",
     ["Hardware", "Zugang", "Drucker"],
     ["Drucker im zweiten Stock druckt nicht", "Neuer Laptop für Einarbeitung", "Zugang zum Projektlaufwerk fehlt",
      "Bildschirm flackert", "Passwort für Testsystem zurücksetzen", "Headset knackt in Videokonferenzen",
      "Software für Bildbearbeitung installieren", "VPN trennt nach wenigen Minuten", "Tastatur mit Schweizer Belegung"]),
    ("INF", "Infrastruktur", "Server, Netz und Betrieb der internen Dienste.",
     ["Netzwerk", "Monitoring"],
     ["Zertifikat des Testservers erneuern", "Speicherplatz der Datenbank überwachen", "Firewall Regeln dokumentieren",
      "Neuen Switch im Lager einbinden", "Protokolle zentral sammeln", "Notstrom Test planen"]),
]

DEMO_COMPANY_TAGS = ["Sicherheit", "Performance", "Barrierefreiheit", "Dokumentation", "Kundenwunsch"]

DEMO_DESCRIPTIONS = [
    "Gemeldet aus der Abstimmung am Montag. Bitte mit dem Fachbereich klären.",
    "Tritt nur unter bestimmten Bedingungen auf. Schritte zum Nachstellen stehen im Kommentar der Meldung.",
    "Kleiner Umfang, kann zusammen mit verwandten Aufgaben erledigt werden.",
    "Abhängig von der Freigabe durch die Projektleitung.",
    None,
]


def seed_demo(args) -> None:
    password = demo_password()
    admin = admin_client(args)
    refuse_second_run(admin, {key for key, *_ in DEMO_PROJECTS}, {name for name, *_ in DEMO_PEOPLE} | {name for name, *_ in DEMO_PORTAL})
    builder = Builder(admin, workers=4)
    builder.credentials = (args.admin, password)
    rng = random.Random(31)
    admin_id = account_id(admin, args.admin)

    print("accounts")
    people = {username: builder.account(username, display, password) for username, display, _, _ in DEMO_PEOPLE}
    print("groups and rights")
    specs = {
        "Projektleitung": GroupSpec("Projektleitung", PROJECT_ACTIONS_FULL + WIKI_ACTIONS_FULL, ALL_FIELDS, ALL_FIELDS),
        "Entwicklung": GroupSpec("Entwicklung", PROJECT_ACTIONS_EDIT + WIKI_ACTIONS_EDIT, ALL_FIELDS,
                                 ["title", "description", "status", "assignee", "tags", "attachments"]),
        "Lesen ohne Beschreibung": GroupSpec("Lesen ohne Beschreibung", PROJECT_ACTIONS_READ + WIKI_ACTIONS_READ, ["title", "type", "status", "tags"], []),
    }
    builder.group(GroupSpec("Verwaltung Demo", PROJECT_ACTIONS_FULL + WIKI_ACTIONS_FULL, ALL_FIELDS, ALL_FIELDS), [admin_id])
    for name, spec in specs.items():
        builder.group(spec, [people[u] for u, _, group, _ in DEMO_PEOPLE if group == name])

    print("tags")
    company = [builder.company_tag(name) for name in DEMO_COMPANY_TAGS]
    print("projects and tickets")
    jobs = []
    project_ids: dict[str, str] = {}
    for key, name, description, local_tags, titles in DEMO_PROJECTS:
        members = [admin_id] + [people[u] for u, _, _, projects in DEMO_PEOPLE if key in projects]
        project = builder.project(key, name, description, members)
        project_ids[key] = project
        local = [builder.project_tag(project, tag) for tag in local_tags]
        assignable = [people[u] for u, _, group, projects in DEMO_PEOPLE if key in projects and group != "Lesen ohne Beschreibung"]
        for title in titles:
            draft = {"type": rng.choice(["task", "task", "bug"]), "title": title}
            text = rng.choice(DEMO_DESCRIPTIONS)
            if text:
                draft["description"] = text
            if assignable and rng.random() < 0.7:
                draft["assignee"] = rng.choice(assignable)
            status = rng.choices(["new", "in_progress", "done"], [5, 3, 2])[0]
            tags = rng.sample(local + company, k=rng.choice([0, 1, 1, 2, 3]))
            jobs.append((project, draft, status, tags))
    builder.tickets(jobs, "demo")
    nordwerk = seed_deadlines(admin, project_ids["HELP"])
    seed_portal(admin, project_ids["HELP"], nordwerk, password)
    seed_mail_template(admin)
    page = seed_wiki(admin, project_ids["HELP"])
    leads = [admin_id] + [people[u] for u, _, group, _ in DEMO_PEOPLE if group == "Projektleitung"]
    seed_requirements(admin, project_ids["HELP"], page, leads)
    print(f"done: {len(DEMO_PEOPLE)} accounts, {len(DEMO_PORTAL)} portal accounts, {len(DEMO_PROJECTS)} projects, {len(jobs)} tickets. "
          "Every demo account uses TASK_MANAGER_DEMO_PASSWORD.")


def _wiki_doc(*blocks: tuple[str, str]) -> dict:
    """A wiki document of headings ("h") and paragraphs ("p")."""
    content = []
    for kind, text in blocks:
        node = {"type": "paragraph", "content": [{"type": "text", "text": text}]}
        if kind == "h":
            node = {"type": "heading", "attrs": {"level": 2}, "content": [{"type": "text", "text": text}]}
        content.append(node)
    return {"type": "doc", "content": content}


WIKI_HANDBOOK = _wiki_doc(("p", "Wie wir Anfragen im internen Helpdesk annehmen, ergänzen und beantworten."))
WIKI_REQUEST_DE = _wiki_doc(
    ("h", "Was fehlt oft?"),
    ("p", "Kunde, Kontakt und Priorität. Ohne Kunde läuft keine Servicefrist."),
    ("h", "So geht es"),
    ("p", "Ticket öffnen, Kunde und Kontakt wählen, Priorität setzen und speichern."),
)
WIKI_REQUEST_EN = _wiki_doc(
    ("h", "What is often missing?"),
    ("p", "Customer, contact and priority. Without a customer no service deadline runs."),
    ("h", "How to do it"),
    ("p", "Open the ticket, choose customer and contact, set the priority and save."),
)


def seed_wiki(admin: Client, project: str) -> str:
    """A small page tree in the helpdesk project (TASK-094): "Helpdesk Handbuch" with the page "Serviceanfrage ergänzen",
    both published in German; the second page has an English draft that readers do not see. Returns the second page."""
    print("wiki")
    handbook = admin.call("POST", f"/wiki/projects/{project}/pages",
                          {"title": "Helpdesk Handbuch", "parentId": None, "content": WIKI_HANDBOOK, "language": "de"}).body["id"]
    admin.call("POST", f"/wiki/pages/{handbook}/publish", {"revision": 1}, headers={"If-Match": '"1"'})
    request = admin.call("POST", f"/wiki/projects/{project}/pages",
                         {"title": "Serviceanfrage ergänzen", "parentId": handbook, "content": WIKI_REQUEST_DE, "language": "de"}).body["id"]
    admin.call("POST", f"/wiki/pages/{request}/publish", {"revision": 1}, headers={"If-Match": '"1"'})
    added = admin.call("POST", f"/wiki/pages/{request}/languages", {"language": "en"})
    admin.call("PUT", f"/wiki/pages/{request}/draft", {"title": "Complete a service request", "content": WIKI_REQUEST_EN},
               query={"language": "en"}, headers={"If-Match": added.headers.get("etag")})
    return request


REQUIREMENT_ACTIONS = ["requirement.read", "requirement.edit", "requirement.approve"]
REQUIREMENT_FIELDS = ["title", "description", "criteria", "priority", "owner"]
# The custom fields of requirements in the helpdesk project (TASK-098): a required choice and a whole number.
REQUIREMENT_CUSTOM_FIELDS = [
    {"key": "bereich", "name": "Bereich", "type": "select", "required": True, "active": True,
     "options": [{"key": "portal", "name": "Kundenportal", "active": True}, {"key": "intern", "name": "Interne Bearbeitung", "active": True}]},
    {"key": "aufwand", "name": "Aufwand in Tagen", "type": "integer", "required": False, "active": True, "options": []},
]
REQUIREMENT_EXAMPLE = {
    "title": "Kunden sehen den Stand ihrer freigegebenen Supportanfragen",
    "priority": "high",
    "description": _wiki_doc(("p", "Im Kundenportal sehen berechtigte Kunden den Bearbeitungsstand ihrer Anfragen, ohne nachfragen zu müssen.")),
    "criteria": [{"id": None, "text": "Die Portalliste zeigt den Status jeder freigegebenen Anfrage der eigenen Organisation."},
                 {"id": None, "text": "Anfragen anderer Organisationen erscheinen weder in der Liste noch in der Suche."},
                 {"id": None, "text": "Ein Statuswechsel im Ticket ist nach dem Neuladen der Portalseite sichtbar."}],
    "customFields": {"bereich": "portal", "aufwand": 3},
}


def seed_requirements(admin: Client, project: str, page: str, members: list[str]) -> None:
    """The example requirement in the helpdesk project (TASK-098): two custom fields, three criteria, revision 1 approved and
    linked with ticket 1 and the wiki page "Serviceanfrage ergänzen", then a changed effort that opens revision 2 as draft, so
    that the ticket shows the hint on the newer revision."""
    print("requirements")
    current = admin.call("GET", f"/projects/{project}")
    configuration = current.body["configuration"]
    configuration["requirementFields"] = REQUIREMENT_CUSTOM_FIELDS
    admin.call("PUT", f"/projects/{project}/configuration", configuration, headers={"If-Match": current.headers.get("etag")})
    group = admin.call("POST", "/permissions/groups", {"name": "Anforderungen"}).body["id"]
    for action in REQUIREMENT_ACTIONS:
        admin.call("PUT", f"/permissions/groups/{group}/actions", {"action": action, "projectId": None, "enabled": True})
    rules = [(name, None) for name in REQUIREMENT_FIELDS] + [(f"custom.{field['key']}", project) for field in REQUIREMENT_CUSTOM_FIELDS]
    for operation in ("read", "write"):
        for name, scope in rules:
            admin.call("PUT", f"/permissions/groups/{group}/fields",
                       {"resourceType": "requirement", "field": name, "projectId": scope, "operation": operation, "effect": "allow"})
    for account in members:
        admin.call("PUT", f"/permissions/groups/{group}/members/{account}")
    created = admin.call("POST", f"/requirements/projects/{project}/requirements", REQUIREMENT_EXAMPLE)
    requirement = created.body["id"]
    path = f"/requirements/{requirement}"
    submitted = admin.call("POST", f"{path}/submit", headers={"If-Match": created.headers.get("etag")})
    approved = admin.call("POST", f"{path}/approve", {"revision": 1}, headers={"If-Match": submitted.headers.get("etag")})
    admin.call("POST", f"{path}/tickets", {"number": 1})
    admin.call("POST", f"{path}/pages", {"pageId": page})
    admin.call("PATCH", path, {"customFields": {"aufwand": 5}}, headers={"If-Match": approved.headers.get("etag")})


def seed_mail_template(admin: Client) -> None:
    """The mail template of the replies (TASK-082): the built-in greeting and footer with a demo signature per language."""
    print("mail template")
    current = admin.call("GET", "/appearance/mail-template")
    texts = {code: {"greeting": current.body[code]["builtIn"]["greeting"], "signature": DEMO_SIGNATURE[code],
                    "footer": current.body[code]["builtIn"]["footer"]} for code in ("de", "en")}
    admin.call("PUT", "/appearance/mail-template", {"applicationUrl": None, **texts}, headers={"If-Match": current.headers.get("etag")})


def seed_deadlines(admin: Client, project: str) -> str:
    """Service deadlines in the helpdesk project (TASK-068): office hours, one rule with 120 and 480 business minutes for
    every priority and two escalation stages per clock (TASK-070), "Erledigt" as the solved status, and a customer on three
    tickets so that their clocks run."""
    print("service deadlines")
    current = admin.call("GET", f"/projects/{project}")
    configuration = current.body["configuration"]
    for status in configuration["statuses"]:
        if status["key"] == "done":
            status["sla"] = "resolved"
    admin.call("PUT", f"/projects/{project}/configuration", configuration, headers={"If-Match": current.headers.get("etag")})
    customer = admin.call("POST", "/helpdesk/customers", {"name": "Nordwerk Maschinenbau", "projects": [project]}).body["id"]
    office = [{"from": "08:00", "to": "17:00"}]
    calendar = admin.call("POST", "/sla/calendars", {
        "name": "Bürozeiten", "timeZone": "Europe/Berlin", "week": [{"day": day, "hours": office} for day in range(1, 6)],
        "exceptions": [{"date": "2026-12-24", "hours": [{"from": "08:00", "to": "12:00"}]}, {"date": "2026-12-25", "hours": []},
                       {"date": "2026-12-26", "hours": []}],
    }).body["id"]
    budgets = {priority: {"firstResponse": 120, "resolution": 480} for priority in ("low", "normal", "high", "critical")}
    # Escalation stages (TASK-070): a warning at 80 % for the project leads, the due stage at 100 % for the administration.
    groups = {group["name"]: group["id"] for group in admin.call("GET", "/permissions/groups").body}
    stages = [{"clock": clock, "percent": percent, "groupId": groups[name]}
              for clock in ("first_response", "resolution") for percent, name in ((80, "Projektleitung"), (100, "Verwaltung Demo"))]
    admin.call("POST", "/sla/rules", {"name": "Helpdesk Standard", "projectId": project, "calendarId": calendar, "budgets": budgets,
                                      "escalations": stages})
    for number in (1, 2, 3):
        admin.call("PUT", f"/projects/{project}/tickets/{number}/helpdesk/customer", {"customerId": customer, "contactId": None})
    admin.call("PUT", f"/projects/{project}/tickets/1/sla/priority", {"priority": "high"})
    return customer


# Portal accounts of two synthetic customers (TASK-076): username, display name, contact address, language of the mails
# (TASK-082): the contact of Südtechnik reads English, so both languages of the mail template can be shown.
DEMO_PORTAL = [
    ("lea.nordwerk", "Lea Lang", "lea@nordwerk.example", "de"),
    ("tom.suedtechnik", "Tom Tal", "tom@suedtechnik.example", "en"),
]

# The mail template "Antwort an Kunden" of the demonstration (TASK-082): an own signature in both languages, the built-in
# greeting and footer.
DEMO_SIGNATURE = {
    "de": "Viele Grüße\n{{sender.name}}\nSupport {{company.name}}\nTelefon 0000 000000 (Beispiel)",
    "en": "Kind regards\n{{sender.name}}\n{{company.name}} support\nPhone +00 0000 000000 (example)",
}


def seed_portal(admin: Client, project: str, nordwerk: str, password: str) -> None:
    """The customer portal (TASK-076): the type "task" of the helpdesk project offered in the portal, a second customer
    Südtechnik, one portal account with contact for each customer, a request of Südtechnik, and on the first request of
    Nordwerk an answer of the support through the portal and a post of the customer."""
    print("customer portal")
    current = admin.call("GET", f"/projects/{project}")
    configuration = current.body["configuration"]
    for ticket_type in configuration["ticketTypes"]:
        ticket_type["portal"] = ticket_type["key"] == "task"
    admin.call("PUT", f"/projects/{project}/configuration", configuration, headers={"If-Match": current.headers.get("etag")})
    suedtechnik = admin.call("POST", "/helpdesk/customers", {"name": "Südtechnik", "projects": [project]}).body["id"]
    base = f"{admin.scheme}://{admin.host}:{admin.port}"
    for (username, display, address, language), customer in zip(DEMO_PORTAL, (nordwerk, suedtechnik)):
        contact = admin.call("POST", "/helpdesk/contacts", {"customerId": customer, "name": display, "addresses": [address],
                                                            "language": language}).body["id"]
        issued = admin.call("POST", "/portal-accounts", {"username": username, "displayName": display, "organizationId": customer,
                                                         "contactId": contact, "projectIds": [project]}).body
        person = Client(base)
        person.login(username, issued["oneTimePassword"], new_password=password)
        person.call("DELETE", "/session")
        person.close()
    admin.call("PUT", f"/projects/{project}/tickets/4/helpdesk/customer", {"customerId": suedtechnik, "contactId": None})
    # The administration answers as support: helpdesk.reply with the read rule of the mails in its group.
    group = next(item["id"] for item in admin.call("GET", "/permissions/groups").body if item["name"] == "Verwaltung Demo")
    admin.call("PUT", f"/permissions/groups/{group}/actions", {"action": "helpdesk.reply", "projectId": None, "enabled": True})
    admin.call("PUT", f"/permissions/groups/{group}/fields",
               {"resourceType": "ticket", "field": "mail", "projectId": None, "operation": "read", "effect": "allow"})
    admin.call("POST", f"/projects/{project}/tickets/1/helpdesk/portal-posts",
               {"body": "Danke für die Meldung. Ein Techniker sieht sich den Drucker heute Nachmittag an."})
    lea = Client(base)
    lea.login(DEMO_PORTAL[0][0], password)
    lea.call("POST", f"/portal/projects/{project}/tickets/1/conversation", {"body": "Vielen Dank, wir sind bis 16 Uhr im Büro."})
    lea.call("DELETE", "/session")
    lea.close()

# ---------------------------------------------------------------------- reference data set

# Synthetic vocabulary. The weights make some words very common and most rare, like real ticket text.
COMMON_WORDS = ["system", "fehler", "kunde", "anzeige", "daten"]
TOPIC_WORDS = [
    "abrechnung", "anmeldung", "archiv", "auftrag", "auswertung", "bericht", "bestellung", "datenbank", "dokument",
    "drucker", "export", "filter", "formular", "freigabe", "import", "kalender", "kasse", "katalog", "konto", "lager",
    "liste", "mahnung", "maske", "menue", "nachricht", "oberflaeche", "planung", "portal", "protokoll", "rechnung",
    "rechte", "schnittstelle", "server", "sicherung", "sortierung", "speicher", "statistik", "suche", "tabelle",
    "termin", "upload", "vertrag", "vorlage", "warenkorb", "webseite", "zahlung", "zeitplan", "zugang", "zertifikat",
]
VERBS = ["zeigt", "speichert", "laedt", "berechnet", "meldet", "sortiert", "exportiert", "verliert", "blockiert", "aktualisiert"]
QUALIFIERS = ["falsch", "langsam", "doppelt", "nicht", "unvollstaendig", "verzoegert", "sporadisch", "nach dem update",
              "bei grossen mengen", "im englischen", "fuer neue kunden", "am monatsende"]


def zipf_word(rng: random.Random) -> str:
    rank = min(int(rng.paretovariate(1.1)), len(TOPIC_WORDS)) - 1
    return TOPIC_WORDS[rank % len(TOPIC_WORDS)]


def reference_text(rng: random.Random, serial: int) -> tuple[str, str]:
    title = f"{zipf_word(rng).capitalize()} {rng.choice(VERBS)} {rng.choice(QUALIFIERS)}"
    if rng.random() < 0.6:
        title += f" ({rng.choice(COMMON_WORDS)})"
    sentences = []
    for _ in range(rng.randint(1, 4)):
        sentences.append(f"Die {zipf_word(rng)} {rng.choice(VERBS)} {rng.choice(QUALIFIERS)}, betroffen ist {rng.choice(COMMON_WORDS)} {zipf_word(rng)}.")
    # A rare marker word per ticket, so that exact single hits exist as well.
    sentences.append(f"Merkmal m{serial:06d}.")
    return title[:200], " ".join(sentences)


def seed_reference(args) -> None:
    password = demo_password()
    admin = admin_client(args)
    projects_n, tickets_n, accounts_n = args.projects, args.tickets, args.accounts
    keys = [f"R{i:02d}" for i in range(1, projects_n + 1)]
    names = [f"ref{i:03d}" for i in range(1, accounts_n + 1)]
    refuse_second_run(admin, set(keys), set(names))
    builder = Builder(admin, workers=args.workers)
    builder.credentials = (args.admin, password)
    rng = random.Random(90000)
    admin_id = account_id(admin, args.admin)
    started = time.monotonic()

    print(f"{accounts_n} accounts")
    with concurrent.futures.ThreadPoolExecutor(min(args.workers, 8)) as pool:
        ids = list(pool.map(lambda n: builder.account(n, f"Referenz Person {n[3:]}", password), names))
    accounts = dict(zip(names, ids))

    # Three group sizes with different field rules: everyone in a project sees at least title and status.
    third = max(1, accounts_n // 3)
    full = ids[:third]
    edit = ids[third:2 * third]
    read = ids[2 * third:]
    print("groups and rights")
    builder.group(GroupSpec("Referenz Verwaltung", PROJECT_ACTIONS_FULL, ALL_FIELDS, ALL_FIELDS), [admin_id])
    builder.group(GroupSpec("Referenz Projektleitung", PROJECT_ACTIONS_FULL, ALL_FIELDS, ALL_FIELDS), full)
    builder.group(GroupSpec("Referenz Bearbeitung", PROJECT_ACTIONS_EDIT, ALL_FIELDS, ["title", "description", "status", "assignee", "tags", "attachments"]), edit)
    builder.group(GroupSpec("Referenz Lesen ohne Beschreibung", PROJECT_ACTIONS_READ, ["title", "type", "status", "tags"], []), read)

    print(f"{args.company_tags} company tags")
    company = [builder.company_tag(f"bereich {i:04d}") for i in range(1, args.company_tags + 1)]

    print(f"{projects_n} projects")
    project_ids: dict[str, str] = {}
    local_tags: dict[str, list[str]] = {}
    project_members: dict[str, list[str]] = {}
    for index, key in enumerate(keys):
        # Each account belongs to about five projects; membership decides what the search may show.
        members = [admin_id] + [aid for i, aid in enumerate(ids) if (i + index) % max(1, projects_n // 5) == 0]
        project_members[key] = members
        project = builder.project(key, f"Referenzprojekt {key}", f"Synthetisches Messprojekt {key} für TASK-031.", members)
        project_ids[key] = project
        local_tags[key] = [builder.project_tag(project, f"{key.lower()} thema {i:03d}") for i in range(1, args.project_tags + 1)]
        # Projects carry tags of the company catalog as well (ten each in TAGS_SEARCH.md).
        for tag in rng.sample(company, k=min(10, len(company))):
            admin.call("POST", f"/projects/{project}/tags", {"tagId": tag})

    print(f"{tickets_n} tickets")
    jobs = []
    for serial in range(tickets_n):
        key = keys[serial % projects_n]
        title, description = reference_text(rng, serial)
        assignable = [m for m in project_members[key] if m in set(full) | set(edit)]
        draft = {"type": "bug" if rng.random() < 0.35 else "task", "title": title, "description": description}
        if assignable and rng.random() < 0.8:
            draft["assignee"] = rng.choice(assignable)
        status = rng.choices(["new", "in_progress", "done"], [3, 2, 5])[0]
        count = round(rng.triangular(0, 2 * args.tags_per_ticket, args.tags_per_ticket))
        pool = local_tags[key] + company
        tags = rng.sample(pool, k=min(count, len(pool)))
        jobs.append((project_ids[key], draft, status, tags))
    builder.tickets(jobs, "reference")
    total_tags = sum(len(job[3]) for job in jobs)
    minutes = (time.monotonic() - started) / 60
    print(f"done in {minutes:.1f} minutes: {accounts_n} accounts, {projects_n} projects, {tickets_n} tickets, "
          f"{args.company_tags + projects_n * args.project_tags} tags, {total_tags} ticket tag assignments.")
    seed_reference_content(args, admin, builder)


# ---------------------------------------------------------------------- wiki pages and requirements of the reference set

WIKI_TITLE_WORDS = ["Leitfaden", "Anleitung", "Hinweise", "Checkliste", "Ablauf", "Uebersicht", "Regeln", "Notizen"]


def reference_doc(rng: random.Random, serial: int, marker: str, sentences: int) -> dict:
    """A wiki document of one to three short sections from the synthetic vocabulary and a rare marker word."""
    blocks = []
    for section in range(rng.randint(1, 3)):
        blocks.append(("h", f"{zipf_word(rng).capitalize()} {rng.choice(QUALIFIERS)}"))
        text = " ".join(f"Die {zipf_word(rng)} {rng.choice(VERBS)} {rng.choice(QUALIFIERS)}, betroffen ist {rng.choice(COMMON_WORDS)} {zipf_word(rng)}."
                        for _ in range(rng.randint(1, sentences)))
        if section == 0:
            text += f" Merkmal {marker}{serial:06d}."
        blocks.append(("p", text))
    return _wiki_doc(*blocks)


def wiki_title(rng: random.Random) -> str:
    return f"{rng.choice(WIKI_TITLE_WORDS)} {zipf_word(rng)} {rng.choice(QUALIFIERS)}"[:200]


class ContentBuilder:
    """Creates the wiki pages and requirements of the reference set through the public API, each project in one thread."""

    def __init__(self, builder: Builder):
        self.builder = builder
        self.counts = {"pages": 0, "english": 0, "drafts": 0, "unpublished": 0, "archived": 0,
                       "requirements": 0, "criteria": 0, "review": 0, "approved": 0, "revised": 0}
        self.lock = threading.Lock()

    def count(self, name: str, n: int = 1) -> None:
        with self.lock:
            self.counts[name] += n

    def wiki(self, key: str, project: str, pages: int, seed: int) -> None:
        """A page tree of at most four levels: ten roots, the other pages below an earlier one. About a third of the pages
        has a published English version, about one in eight an open draft after the publication, one in twenty was never
        published and one in twenty of the pages without children is archived at the end."""
        client = self.builder.worker()
        rng = random.Random(seed)
        depth: dict[str, int] = {}
        children: dict[str, int] = {}
        published: list[str] = []
        for index in range(pages):
            parents = [page for page in published if depth[page] < 4]
            parent = None if index < 10 or not parents else rng.choice(parents)
            serial = seed * 1000 + index
            created = client.call("POST", f"/wiki/projects/{project}/pages",
                                  {"title": wiki_title(rng), "parentId": parent, "content": reference_doc(rng, serial, "w", 4), "language": "de"})
            page = created.body["id"]
            depth[page] = 1 if parent is None else depth[parent] + 1
            children[page] = 0
            if parent is not None:
                children[parent] += 1
            self.count("pages")
            if index >= 10 and rng.random() < 0.05:
                self.count("unpublished")
                continue
            client.call("POST", f"/wiki/pages/{page}/publish", {"revision": 1}, headers={"If-Match": '"1"'})
            published.append(page)
            if rng.random() < 0.125:
                client.call("PUT", f"/wiki/pages/{page}/draft", {"title": wiki_title(rng), "content": reference_doc(rng, serial, "w", 4)},
                            query={"language": "de"}, headers={"If-Match": '"1"'})
                self.count("drafts")
            if rng.random() < 0.33:
                added = client.call("POST", f"/wiki/pages/{page}/languages", {"language": "en"})
                saved = client.call("PUT", f"/wiki/pages/{page}/draft",
                                    {"title": f"Guide {zipf_word(rng)} {rng.choice(['notes', 'steps', 'rules', 'checklist'])}",
                                     "content": reference_doc(rng, serial, "e", 3)},
                                    query={"language": "en"}, headers={"If-Match": added.headers.get("etag")})
                revision = saved.headers.get("etag")
                client.call("POST", f"/wiki/pages/{page}/publish", {"revision": int(revision.strip('W/"'))},
                            query={"language": "en"}, headers={"If-Match": revision})
                self.count("english")
        leaves = [page for page in published if children[page] == 0]
        for page in rng.sample(leaves, k=len(leaves) // 20):
            version = client.call("GET", f"/wiki/pages/{page}", query={"language": "de"}).body["version"]
            client.call("POST", f"/wiki/pages/{page}/archive", {"version": version}, query={"language": "de"})
            self.count("archived")

    def requirement(self, project: str, serial: int, seed: int) -> None:
        """One requirement with one to eight criteria. Three in ten are approved, a third of them changed again into a
        new revision; one in ten waits in review."""
        client = self.builder.worker()
        rng = random.Random(seed)
        title, _ = reference_text(rng, serial)
        criteria = [{"id": None, "text": f"Die {zipf_word(rng)} {rng.choice(VERBS)} {rng.choice(QUALIFIERS)} ({rng.choice(COMMON_WORDS)})."}
                    for _ in range(rng.randint(1, 8))]
        body = {"title": title, "priority": rng.choices(["low", "normal", "high", "critical"], [2, 5, 2, 1])[0],
                "description": reference_doc(rng, serial, "a", 2), "criteria": criteria}
        created = client.call("POST", f"/requirements/projects/{project}/requirements", body)
        path = f"/requirements/{created.body['id']}"
        self.count("requirements")
        self.count("criteria", len(criteria))
        roll = rng.random()
        if roll < 0.4:
            submitted = client.call("POST", f"{path}/submit", headers={"If-Match": created.headers.get("etag")})
            if roll < 0.1:
                self.count("review")
                return
            approved = client.call("POST", f"{path}/approve", {"revision": 1}, headers={"If-Match": submitted.headers.get("etag")})
            self.count("approved")
            if rng.random() < 1 / 3:
                again, _ = reference_text(rng, serial)
                client.call("PATCH", path, {"title": again}, headers={"If-Match": approved.headers.get("etag")})
                self.count("revised")


def seed_reference_content(args, admin: Client, builder: Builder) -> None:
    """Part of TASK-104 (DEC-134, rule 4): wiki pages and requirements in the reference projects, with the rights of the
    three reference groups. Runs after the tickets or alone on an existing reference set (profile reference-content)."""
    started = time.monotonic()
    names = [f"ref{i:03d}" for i in range(1, args.accounts + 1)]
    directory = {entry["username"]: entry["id"] for entry in admin.call("GET", "/directory/accounts").body}
    groups = {item["name"] for item in admin.call("GET", "/permissions/groups").body}
    if "Referenz Wiki Verwaltung" in groups:
        raise SystemExit("the wiki pages and requirements of the reference set exist already")
    ids = [directory[name] for name in names]
    third = max(1, len(ids) // 3)
    full, edit, read = ids[:third], ids[third:2 * third], ids[2 * third:]
    admin_id = directory[args.admin]
    print("wiki and requirement rights")
    everything = ["title", "description", "criteria", "priority", "owner"]
    builder.group(GroupSpec("Referenz Wiki Verwaltung", WIKI_ACTIONS_FULL, [], []), [admin_id] + full)
    builder.group(GroupSpec("Referenz Wiki Bearbeitung", WIKI_ACTIONS_EDIT, [], []), edit)
    builder.group(GroupSpec("Referenz Wiki Lesen", WIKI_ACTIONS_READ, [], []), read)
    builder.group(GroupSpec("Referenz Anforderungen Verwaltung", REQUIREMENT_ACTIONS, everything, everything, "requirement"), [admin_id] + full)
    builder.group(GroupSpec("Referenz Anforderungen Bearbeitung", ["requirement.read", "requirement.edit"], everything,
                            ["title", "description", "criteria", "priority"], "requirement"), edit)
    builder.group(GroupSpec("Referenz Anforderungen Lesen ohne Beschreibung", ["requirement.read"], ["title", "criteria", "priority", "owner"], [],
                            "requirement"), read)
    projects = sorted((item for item in admin.call("GET", "/projects").body if item["key"].startswith("R")), key=lambda item: item["key"])
    if not projects:
        raise SystemExit("no reference projects; create the reference set first")
    content = ContentBuilder(builder)
    pages_each = max(1, args.wiki_pages // len(projects))
    print(f"{pages_each * len(projects)} wiki pages")
    with concurrent.futures.ThreadPoolExecutor(builder.workers) as pool:
        for future in [pool.submit(content.wiki, item["key"], item["id"], pages_each, 104000 + index) for index, item in enumerate(projects)]:
            future.result()
    print(f"  {content.counts}")
    print(f"{args.requirements} requirements")
    with concurrent.futures.ThreadPoolExecutor(builder.workers) as pool:
        futures = [pool.submit(content.requirement, projects[serial % len(projects)]["id"], serial, 204000 + serial)
                   for serial in range(args.requirements)]
        for done, future in enumerate(concurrent.futures.as_completed(futures), start=1):
            future.result()
            if done % 2000 == 0:
                print(f"  {done}/{len(futures)} requirements", flush=True)
    minutes = (time.monotonic() - started) / 60
    print(f"wiki pages and requirements done in {minutes:.1f} minutes: {content.counts}")
    results = os.environ.get("TASK_MANAGER_RESULTS", "results")
    os.makedirs(results, exist_ok=True)
    with open(os.path.join(results, "reference-content.json"), "w") as out:
        json.dump({"minutes": round(minutes, 1), **content.counts}, out, indent=2)


# ---------------------------------------------------------------------- checks of the presentation data set

def parse_instant(text: str) -> datetime.datetime:
    """Reads an ISO 8601 instant from the API, for example 2026-10-22T06:35:12.123456789Z, as an aware UTC time.

    Python before 3.11 accepts neither the Z nor more than six fraction digits, so both are normalized first. Digits
    beyond microseconds are cut off; for a duration in hours they do not matter."""
    head, sep, tail = text.strip().partition(".")
    zone = ""
    if sep:
        digits = ""
        for position, char in enumerate(tail):
            if not char.isdigit():
                zone = tail[position:]
                break
            digits += char
        text = f"{head}.{digits[:6].ljust(6, '0')}{zone}"
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    value = datetime.datetime.fromisoformat(text)
    if value.tzinfo is None:
        raise ValueError(f"instant without time zone: {text}")
    return value.astimezone(datetime.timezone.utc)


def hours_left(expires_at: str, now: datetime.datetime) -> float:
    """Remaining session time in hours, computed entirely in UTC so that the local zone and daylight saving play no role."""
    return (parse_instant(expires_at) - now).total_seconds() / 3600


def verify(args) -> None:
    """Checks the presentation data set the way a person would see it; used by the image check in CI.

    With --save the session of anna is kept in a file, with --resume that session is used again after a restart:
    it must still be valid and keep its fixed end."""
    password = demo_password()
    failures: list[str] = []

    def check(condition: bool, text: str) -> None:
        print(("ok      " if condition else "FAILED  ") + text)
        if not condition:
            failures.append(text)

    if args.resume:
        with open(args.resume) as saved:
            state = json.load(saved)
        anna = Client(args.url)
        anna.cookies, anna.csrf = state["cookies"], state["csrf"]
        session = anna.call("GET", "/session").body
        check(session.get("authenticated") is True, "session of anna is still valid after the restart")
        same = "expiresAt" in session and parse_instant(session["expiresAt"]) == parse_instant(state["expiresAt"])
        check(same, f"fixed end unchanged ({state['expiresAt']} before, {session.get('expiresAt')} after)")
        keys = sorted(p["key"] for p in anna.call("GET", "/projects").body)
        check(keys == ["APP", "HELP", "WEB"], f"data kept, anna sees {keys}")
        if "attachment" in state:
            kept = anna.call("GET", state["attachment"]["path"], as_bytes=True, expect=None)
            check(kept.status == 200 and hashlib.sha256(kept.body).hexdigest() == state["attachment"]["sha256"],
                  "the attachment is still there after the restart, byte for byte")
    else:
        anna = Client(args.url)
        session = anna.login("anna", password)
        hours = hours_left(session["expiresAt"], datetime.datetime.now(datetime.timezone.utc))
        check(23.5 < hours <= 24.01, f"session ends after the default of 24 hours ({hours:.2f} h left)")
        keys = sorted(p["key"] for p in anna.call("GET", "/projects").body)
        check(keys == ["APP", "HELP", "WEB"], f"anna sees her three projects {keys}")

        carla = Client(args.url)
        carla.login("carla", password)
        projects = carla.call("GET", "/projects").body
        check([p["key"] for p in projects] == ["WEB"], "carla sees only WEB")
        web = projects[0]["id"]
        ticket = carla.call("GET", f"/projects/{web}/tickets", query={"limit": 50}).body[0]
        check(sorted(ticket["fields"]) == ["status", "title", "type"], f"carla reads no description or assignee {sorted(ticket['fields'])}")
        check(ticket["writableFields"] == [], "carla may change no field")
        refused = carla.call("PATCH", f"/projects/{web}/tickets/{ticket['number']}", {"title": "Versuch"},
                             headers={"If-Match": f'"{ticket["version"]}"'}, expect=None)
        check(refused.status == 403, f"a change by carla is refused ({refused.status})")
        hits = carla.call("GET", "/search/tickets", query={"q": "Drucker"}).body
        check(hits["total"] == 0, "carla does not find the HELP ticket about the printer")

        david = Client(args.url)
        david.login("david", password)
        hits = david.call("GET", "/search/tickets", query={"q": "Drucker"}).body
        check(hits["total"] >= 1 and all(h["key"].startswith("HELP-") for h in hits["items"]), "david finds the printer ticket in HELP")
        hits = david.call("GET", "/search/tickets", query={"q": "Startseite"}).body
        check(hits["total"] == 0, "david finds nothing from WEB")

        ben = Client(args.url)
        ben.login("ben", password)
        first = ben.call("GET", "/projects").body[0]["id"]
        current = ben.call("GET", f"/projects/{first}/tickets", query={"limit": 1}).body[0]
        path = f"/projects/{first}/tickets/{current['number']}"
        etag = ben.call("GET", path).headers["etag"]
        changed = ben.call("PATCH", path, {"title": current["fields"]["title"] + " (geprüft)"}, headers={"If-Match": etag}, expect=None)
        check(changed.status == 200, "ben changes a ticket")
        stale = ben.call("PATCH", path, {"title": "Veraltete Fassung"}, headers={"If-Match": etag}, expect=None)
        check(stale.status in (409, 412), f"an outdated change is a conflict ({stale.status})")
        found = ben.call("GET", "/search/tickets", query={"q": "geprüft"}).body
        check(any(h["key"] == current["key"] for h in found["items"]), "the search finds the new title right away")

        # Attachments (TASK-051): the same bytes come back, programs are refused, a reader without the field is refused.
        content = ("Synthetischer Prüfbericht für die Vorführung.\n" * 50).encode()
        added = ben.call("POST", f"{path}/attachments", content=content, query={"name": "Prüfbericht.txt"},
                         headers={"Content-Type": "application/octet-stream"}, expect=None)
        check(added.status == 201, f"ben attaches a file ({added.status})")
        attachment = added.body if isinstance(added.body, dict) else {}
        digest = hashlib.sha256(content).hexdigest()
        check(attachment.get("sha256") == digest and attachment.get("size") == len(content), "the server names the size and checksum of the file")
        saved = ben.call("GET", f"{path}/attachments/{attachment.get('id')}", as_bytes=True, expect=None)
        check(saved.status == 200 and saved.body == content, "the download returns exactly the same bytes")
        check("attachment" in saved.headers.get("content-disposition", "") and saved.headers.get("x-content-type-options") == "nosniff",
              "the download is a file to save, never shown by the browser")
        blocked = ben.call("POST", f"{path}/attachments", content=b"MZ", query={"name": "tool.exe"},
                           headers={"Content-Type": "application/octet-stream"}, expect=None)
        check(blocked.status == 415, f"a program is refused ({blocked.status})")
        unseen = carla.call("GET", f"/projects/{web}/tickets/{ticket['number']}/attachments", expect=None)
        check(unseen.status == 403, f"carla has no read rule for attachments ({unseen.status})")

        if args.save:
            with open(args.save, "w") as out:
                json.dump({"cookies": anna.cookies, "csrf": anna.csrf, "expiresAt": session["expiresAt"],
                           "attachment": {"path": f"{path}/attachments/{attachment.get('id')}", "sha256": digest}}, out)
    if failures:
        raise SystemExit(f"{len(failures)} checks failed")
    print("all checks passed")


# ---------------------------------------------------------------------- measurement

# Shares of OPERATIONS.md. Time booking (10) and wiki editing do not take part; the remaining shares are scaled up.
# The complex search is split: most cases are built from a ticket the person can see, so that text, projects, status,
# type and tags fit together and at least that ticket matches; the rest combines random filters and mostly finds
# nothing, which is the cheap selective case and is reported on its own.
# TASK-104 (DEC-134, rule 5) adds the wiki share of OPERATIONS.md (5) as reading of pages and the searches over wiki pages
# and requirements, the overview "Alle" and the quick search with a key; the classes of TASK-031 keep their shares.
MIX = {
    "Ticketliste": 20.0,
    "Ticketdetail": 25.0,
    "Ticketänderung": 15.0,
    "Suche Kennung": 3.0,
    "Suche ein Wort": 4.0,
    "Suche zwei Wörter": 3.0,
    "Suche häufiger Begriff": 3.0,
    "Suche kurzer Präfix": 2.0,
    "Suche ohne Treffer": 2.0,
    "Suche mit Filtern": 4.0,
    "Komplexe Suche mit Treffern": 1.5,
    "Komplexe Suche selektiv": 0.5,
    "Tagvorschlag": 2.0,
    "Wikiseite lesen": 5.0,
    "Wiki ein Wort": 1.5,
    "Wiki Entwürfe": 0.5,
    "Anforderung ein Wort": 1.0,
    "Anforderung Kennung": 0.5,
    "Anforderung mit Filtern": 0.5,
    "Übersicht Alle": 2.0,
    "Schnellsuche mit Kennung": 1.0,
}
# One ticket change yields two values: the save alone (the PATCH, compared with the 1 s for a regular save in
# OPERATIONS.md) and the whole action of reading the current version and saving, which has no target of its own.
# The search targets are those of TASK-104 rule 6 (part of OPEN-014): a key and suggestions at most 200 ms, a regular
# search with its first page at most 500 ms, a complex search and the overview at most 1 s, each as p95 on the server.
TARGETS_MS = {
    "Ticketliste": 500, "Ticketdetail": 500, "Ticketänderung Speichern": 1000, "Ticketänderung gesamt": None,
    "Suche Kennung": 200, "Suche ein Wort": 500, "Suche zwei Wörter": 500, "Suche häufiger Begriff": 500,
    "Suche kurzer Präfix": 500, "Suche ohne Treffer": 500, "Suche mit Filtern": 500,
    "Komplexe Suche mit Treffern": 1000, "Komplexe Suche selektiv": 1000, "Tagvorschlag": 200,
    "Wikiseite lesen": 500, "Wiki ein Wort": 500, "Wiki Entwürfe": 500, "Anforderung ein Wort": 500, "Anforderung Kennung": 200,
    "Anforderung mit Filtern": 500, "Übersicht Alle": 1000, "Schnellsuche mit Kennung": 200,
}


@dataclass
class Sample:
    kind: str
    ms: float
    outcome: str  # ok, rejected (expected business refusal such as a version conflict), unclear or error
    hits: int | None = None  # total of a search answer


@dataclass
class UserState:
    client: Client
    projects: list[dict] = field(default_factory=list)
    tickets: dict[str, list[int]] = field(default_factory=dict)
    tags: list[str] = field(default_factory=list)
    cases: list[dict] = field(default_factory=list)
    can_edit: bool = False
    wiki: bool = False
    wiki_drafts: bool = False
    pages: list[str] = field(default_factory=list)
    requirements: dict[str, int] = field(default_factory=dict)


def prepare_user(base: str, username: str, password: str) -> UserState:
    client = Client(base)
    client.login(username, password)
    state = UserState(client)
    state.projects = client.call("GET", "/projects").body
    for project in state.projects:
        listed = client.call("GET", f"/projects/{project['id']}/tickets", query={"limit": 50}).body
        state.tickets[project["id"]] = [t["number"] for t in listed] or [1]
        catalog = client.call("GET", f"/projects/{project['id']}/catalog/tags", query={"limit": 50}).body
        state.tags.extend(tag["id"] for tag in catalog)
        # Visible tickets with tags are the starting points of complex searches that must find something.
        offset = random.Random(username + project["id"]).randrange(0, 1000)
        found = client.call("GET", "/search/tickets", query={"project": [project["id"]], "limit": 50, "offset": offset}).body
        for hit in found["items"]:
            fields = hit.get("fields", {})
            if hit.get("tags") and fields.get("title") and fields.get("status") and fields.get("type"):
                state.cases.append({"project": hit["projectId"], "title": fields["title"], "status": fields["status"],
                                    "type": fields["type"], "tags": [tag["id"] for tag in hit["tags"]]})
    state.tags.extend(tag["id"] for tag in client.call("GET", "/tags", query={"limit": 50}).body)
    first = state.projects[0]["id"]
    sample = client.call("GET", f"/projects/{first}/tickets/{state.tickets[first][0]}").body
    state.can_edit = "title" in sample.get("writableFields", [])
    # The areas of the person come from the overview itself: an area without a right is null.
    overview = client.call("GET", "/search/all", query={"q": "xqprobe"}).body
    state.wiki = overview["wiki"] is not None
    if state.wiki:
        state.wiki_drafts = client.call("GET", "/search/wiki", query={"drafts": "true", "limit": 1}, expect=None).status == 200
        for word in TOPIC_WORDS[:5]:
            found = client.call("GET", "/search/wiki", query={"q": word, "limit": 50}).body
            state.pages.extend(hit["id"] for hit in found["items"])
    if overview["requirements"] is not None:
        # The highest number per project, from the list without text that starts with the newest.
        for project in state.projects:
            found = client.call("GET", "/search/requirements", query={"project": [project["id"]], "limit": 1}).body
            if found["items"]:
                state.requirements[project["id"]] = found["items"][0]["number"]
    return state


def complex_case(state: UserState, rng: random.Random) -> dict:
    """Search parameters that fit one visible ticket: a word of its title, its project among up to three, its status
    and type among others, and a part of its own tags. The tags of one ticket come from its project catalog and the
    company catalog only, so every combination is one that exists in the data."""
    case = rng.choice(state.cases)
    others = [p["id"] for p in state.projects if p["id"] != case["project"]]
    projects = [case["project"]] + rng.sample(others, k=min(rng.randint(0, 2), len(others)))
    statuses = sorted({case["status"], rng.choice(["new", "in_progress", "done"])})
    types = sorted({case["type"], rng.choice(["bug", "task"])})
    tags = rng.sample(case["tags"], k=rng.randint(1, min(10, len(case["tags"]))))
    word = case["title"].split()[0].lower()
    return {"q": word, "project": projects, "status": statuses, "type": types, "tag": tags}


def selective_case(state: UserState, rng: random.Random) -> dict:
    """Random projects and up to ten random visible tags; such combinations rarely exist and mostly find nothing."""
    search: dict = {"q": zipf_word(rng),
                    "project": [p["id"] for p in rng.sample(state.projects, k=min(3, len(state.projects)))],
                    "status": ["new", "in_progress"], "type": ["bug", "task"]}
    if state.tags:
        search["tag"] = rng.sample(state.tags, k=min(rng.randint(1, 10), len(state.tags)))
    return search


def outcome_of(kind: str, status: int) -> str:
    if status in (200, 201, 204):
        return "ok"
    if status in (409, 412, 428) or (status == 403 and kind.startswith("Ticketänderung")):
        return "rejected"
    return "error"


def run_action(state: UserState, kind: str, rng: random.Random) -> list[Sample]:
    """Runs one action of the mix and returns its measured values; a ticket change yields two."""
    client = state.client
    project = rng.choice(state.projects)
    pid = project["id"]
    number = rng.choice(state.tickets[pid])
    search = None
    if kind == "Ticketliste":
        response = client.call("GET", f"/projects/{pid}/tickets", query={"limit": 50}, expect=None)
    elif kind == "Ticketdetail":
        response = client.call("GET", f"/projects/{pid}/tickets/{number}", expect=None)
    elif kind == "Ticketänderung":
        started = time.perf_counter()
        current = client.call("GET", f"/projects/{pid}/tickets/{number}", expect=None)
        if current.status != 200:
            # Nothing was saved; both values of the action count as failed.
            return [Sample("Ticketänderung Speichern", float("nan"), "error"), Sample("Ticketänderung gesamt", current.seconds * 1000, "error")]
        title = f"{zipf_word(rng).capitalize()} {rng.choice(VERBS)} {rng.choice(QUALIFIERS)}"
        saved = client.call("PATCH", f"/projects/{pid}/tickets/{number}", {"title": title},
                            headers={"If-Match": current.headers.get("etag", "")}, expect=None)
        whole = (time.perf_counter() - started) * 1000
        outcome = outcome_of(kind, saved.status)
        return [Sample("Ticketänderung Speichern", saved.seconds * 1000, outcome), Sample("Ticketänderung gesamt", whole, outcome)]
    elif kind == "Tagvorschlag":
        response = client.call("GET", "/tags", query={"q": rng.choice(["ber", "bere", "bereich 0", "the"]), "limit": 10}, expect=None)
    elif kind == "Wikiseite lesen":
        response = client.call("GET", f"/wiki/pages/{rng.choice(state.pages)}", query={"language": rng.choice(["de", "en"])}, expect=None)
    elif kind in ("Wiki ein Wort", "Wiki Entwürfe"):
        search = {"q": zipf_word(rng), "limit": 20, "preferred": "de"}
        if kind == "Wiki Entwürfe":
            search["drafts"] = "true"
        response = client.call("GET", "/search/wiki", query=search, expect=None)
    elif kind.startswith("Anforderung"):
        search = {"limit": 20}
        if kind == "Anforderung Kennung":
            rpid = rng.choice(list(state.requirements))
            key = next(p["key"] for p in state.projects if p["id"] == rpid)
            search["q"] = f"{key} ANF-{rng.randint(1, state.requirements[rpid])}"
        else:
            search["q"] = zipf_word(rng)
        if kind == "Anforderung mit Filtern":
            search["project"] = [rng.choice(list(state.requirements))]
            search["status"] = [rng.choice(["draft", "review", "approved"])]
            search["priority"] = rng.sample(["low", "normal", "high", "critical"], k=2)
        response = client.call("GET", "/search/requirements", query=search, expect=None)
    elif kind == "Übersicht Alle":
        search = {"q": rng.choice([zipf_word(rng), f"{zipf_word(rng)} {rng.choice(VERBS)}", rng.choice(COMMON_WORDS)]), "preferred": "de"}
        response = client.call("GET", "/search/all", query=search, expect=None)
    elif kind == "Schnellsuche mit Kennung":
        # A ticket key or, where the person reads requirements, a requirement key; the answer names the object in "direct".
        search = {"q": f"{project['key']}-{number}", "preferred": "de"}
        if state.requirements and rng.random() < 0.3:
            rpid = rng.choice(list(state.requirements))
            key = next(p["key"] for p in state.projects if p["id"] == rpid)
            search["q"] = f"{key} ANF-{rng.randint(1, state.requirements[rpid])}"
        response = client.call("GET", "/search/all", query=search, expect=None)
    else:
        search = {"limit": 20}
        if kind == "Suche Kennung":
            search["q"] = f"{project['key']}-{number}"
        elif kind == "Suche ein Wort":
            search["q"] = zipf_word(rng)
        elif kind == "Suche zwei Wörter":
            search["q"] = f"{zipf_word(rng)} {rng.choice(VERBS)}"
        elif kind == "Suche häufiger Begriff":
            search["q"] = rng.choice(COMMON_WORDS)
        elif kind == "Suche kurzer Präfix":
            search["q"] = rng.choice(TOPIC_WORDS)[:2]
        elif kind == "Suche ohne Treffer":
            search["q"] = f"xq{rng.randrange(10**6)}"
        elif kind == "Suche mit Filtern":
            search["q"] = zipf_word(rng)
            search["project"] = [pid]
            search["status"] = [rng.choice(["new", "in_progress", "done"])]
        elif kind == "Komplexe Suche mit Treffern":
            search.update(complex_case(state, rng))
        elif kind == "Komplexe Suche selektiv":
            search.update(selective_case(state, rng))
        response = client.call("GET", "/search/tickets", query=search, expect=None)
    hits = None
    outcome = outcome_of(kind, response.status)
    if search is not None and response.status == 200 and isinstance(response.body, dict):
        if kind == "Übersicht Alle":
            hits = sum(area["total"] for area in response.body.values() if isinstance(area, dict) and "total" in area)
        elif kind == "Schnellsuche mit Kennung":
            hits = 0 if response.body.get("direct") is None else 1
            # The class succeeds only with the direct hit (TASK-104, rule 5); an answer without it is no valid value (TASK-110, H4).
            if hits == 0:
                outcome = "error"
        else:
            hits = response.body.get("total")
    return [Sample(kind, response.seconds * 1000, outcome, hits)]


def percentile(values: list[float], share: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return float("nan")
    rank = max(0, math.ceil(share * len(ordered)) - 1)
    return ordered[rank]


def machine() -> dict:
    memory = None
    try:
        with open("/proc/meminfo") as info:
            for line in info:
                if line.startswith("MemTotal:"):
                    memory = round(int(line.split()[1]) / 1024 / 1024, 1)
    except OSError:
        pass
    cpu = None
    try:
        with open("/proc/cpuinfo") as info:
            cpu = next((line.split(":", 1)[1].strip() for line in info if line.startswith("model name")), None)
    except OSError:
        pass
    return {"cpus": os.cpu_count(), "cpu": cpu, "memory_gb": memory, "system": platform.platform(), "python": platform.python_version()}


def measure(args) -> None:
    password = demo_password()
    # Spread over the whole account list, so that all three reference groups and their field rules take part.
    users = [f"ref{1 + i * args.accounts // args.users:03d}" for i in range(args.users)]
    print(f"preparing {len(users)} sessions")
    with concurrent.futures.ThreadPoolExecutor(len(users)) as pool:
        states = list(pool.map(lambda name: prepare_user(args.url, name, password), users))
    admin = Client(args.url)
    admin.login(args.admin, password)
    stock = admin.call("GET", "/search/tickets", query={"limit": 1}).body["total"]
    projects = len(admin.call("GET", "/projects").body)
    accounts = len(admin.call("GET", "/accounts").body)
    pages = admin.call("GET", "/search/wiki", query={"limit": 1}).body["total"]
    drafts = admin.call("GET", "/search/wiki", query={"limit": 1, "drafts": "true"}).body["total"]
    requirements = admin.call("GET", "/search/requirements", query={"limit": 1}).body["total"]
    print(f"stock visible to {args.admin}: {projects} projects, {stock} tickets, {accounts} accounts, {pages} published and "
          f"{drafts} draft wiki versions, {requirements} requirements")

    kinds = list(MIX)
    weights = [MIX[k] for k in kinds]
    samples: list[Sample] = []
    actions = [0]
    lock = threading.Lock()
    begin = time.monotonic()
    measure_from = begin + args.warmup
    stop_at = measure_from + args.duration

    def user_loop(index: int, state: UserState) -> None:
        rng = random.Random(1000 + index)
        # A person without write rights does not try to change tickets, and one who sees no ticket with tags has no
        # complex search with hits; their shares go to the other actions.
        # The same holds for the wiki and requirement classes of a person without these rights.
        missing = {"Ticketänderung": not state.can_edit, "Komplexe Suche mit Treffern": not state.cases,
                   "Wikiseite lesen": not state.pages, "Wiki ein Wort": not state.wiki, "Wiki Entwürfe": not state.wiki_drafts,
                   "Anforderung ein Wort": not state.requirements, "Anforderung Kennung": not state.requirements,
                   "Anforderung mit Filtern": not state.requirements}
        own = [0 if missing.get(k, False) else w for k, w in zip(kinds, weights)]
        while True:
            pause = rng.expovariate(1 / args.think) if args.think > 0 else 0
            if time.monotonic() + pause >= stop_at:
                return
            time.sleep(pause)
            kind = rng.choices(kinds, own)[0]
            try:
                measured = run_action(state, kind, rng)
            except UnclearOutcome as error:
                measured = [Sample("Ticketänderung Speichern" if kind == "Ticketänderung" else kind, float("nan"), "unclear")]
                print(f"  {kind}: {error}", file=sys.stderr)
            except (OSError, http.client.HTTPException) as error:
                measured = [Sample("Ticketänderung Speichern" if kind == "Ticketänderung" else kind, float("nan"), "error")]
                print(f"  {kind}: {error}", file=sys.stderr)
            if time.monotonic() >= measure_from:
                with lock:
                    samples.extend(measured)
                    actions[0] += 1

    print(f"warm up {args.warmup} s, measure {args.duration} s, {args.users} users, think time {args.think} s")
    threads = [threading.Thread(target=user_loop, args=(i, s), daemon=True) for i, s in enumerate(states)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    rows = summarize(samples)
    total = sum(r["samples"] for r in rows if r["class"] != "Ticketänderung gesamt")
    errors = sum(r["errors"] + r["unclear"] for r in rows if r["class"] != "Ticketänderung gesamt")
    result = {
        "started": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() - args.duration - args.warmup)),
        "users": args.users, "think_seconds": args.think, "warmup_seconds": args.warmup, "duration_seconds": args.duration,
        "commit": args.commit, "build": args.build,
        "stock": {"projects": projects, "tickets": stock, "accounts": accounts, "wiki_published": pages, "wiki_drafts": drafts,
                  "requirements": requirements},
        "client_machine": machine(), "server": args.url, "samples": total,
        "error_rate": round(errors / total, 5) if total else None,
        "actions": actions[0], "throughput_per_second": round(actions[0] / args.duration, 2), "classes": rows,
    }
    os.makedirs(args.out, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    with open(os.path.join(args.out, f"measurement-{stamp}.json"), "w") as out:
        json.dump(result, out, indent=2, ensure_ascii=False)
    lines = [
        f"Messung {result['started']}: {args.users} Benutzer, Denkzeit {args.think} s, {args.warmup} s Aufwärmen, {args.duration} s Messung",
        f"Bestand: {projects} Projekte, {stock} Tickets, {accounts} Konten, {pages} veröffentlichte und {drafts} Entwurfsfassungen im Wiki, "
        f"{requirements} Anforderungen; {actions[0]} Aktionen, Fehlerquote {result['error_rate']}",
        f"Gemessener Stand: Commit {args.commit or 'nicht angegeben'}, Build {args.build or 'nicht angegeben'}",
        "",
    ]
    report = "\n".join(lines + report_table(rows)) + "\n"
    with open(os.path.join(args.out, f"measurement-{stamp}.md"), "w") as out:
        out.write(report)
    print(report)


def summarize(samples: list[Sample]) -> list[dict]:
    """One row per measuring class with latency percentiles of the successful requests and, for searches, the hits."""
    rows = []
    for kind in TARGETS_MS:
        chosen = [s for s in samples if s.kind == kind]
        ok = [s.ms for s in chosen if s.outcome == "ok"]
        target = TARGETS_MS[kind]
        p95 = percentile(ok, 0.95)
        row = {
            "class": kind, "samples": len(chosen), "ok": len(ok),
            "rejected": sum(1 for s in chosen if s.outcome == "rejected"),
            "errors": sum(1 for s in chosen if s.outcome == "error"),
            "unclear": sum(1 for s in chosen if s.outcome == "unclear"),
            "p50_ms": round(percentile(ok, 0.50), 1), "p95_ms": round(p95, 1), "p99_ms": round(percentile(ok, 0.99), 1),
            "max_ms": round(max(ok), 1) if ok else None, "target_p95_ms": target,
            "met": None if target is None or not ok else p95 <= target,
        }
        hits = [s.hits for s in chosen if s.outcome == "ok" and s.hits is not None]
        if hits:
            row["hits"] = {"with_hits": sum(1 for h in hits if h > 0), "without_hits": sum(1 for h in hits if h == 0),
                           "p50": percentile(hits, 0.50), "max": max(hits)}
        rows.append(row)
    return rows


def report_table(rows: list[dict]) -> list[str]:
    lines = [
        "| Messklasse | Messwerte | abgewiesen | Fehler | p50 ms | p95 ms | p99 ms | Ziel p95 ms | erfüllt | mit Treffern | Treffer Median | Treffer max |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|",
    ]
    for r in rows:
        met = "" if r["met"] is None else ("ja" if r["met"] else "nein")
        hits = r.get("hits")
        share = f"{hits['with_hits']} von {hits['with_hits'] + hits['without_hits']}" if hits else ""
        lines.append(f"| {r['class']} | {r['samples']} | {r['rejected']} | {r['errors'] + r['unclear']} | {r['p50_ms']} | "
                     f"{r['p95_ms']} | {r['p99_ms']} | {r['target_p95_ms'] or ''} | {met} | {share} | "
                     f"{hits['p50'] if hits else ''} | {hits['max'] if hits else ''} |")
    return lines


# ---------------------------------------------------------------------- command line

def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default=os.environ.get("TASK_MANAGER_URL", "http://localhost:8080"))
    parser.add_argument("--admin", default=os.environ.get("TASK_MANAGER_DEMO_ADMIN", "admin"),
                        help="login name given to bootstrap-admin")
    commands = parser.add_subparsers(dest="command", required=True)

    seed = commands.add_parser("seed", help="create a synthetic data set")
    seed.add_argument("--profile", choices=["demo", "reference", "reference-content"], default="demo",
                      help="reference-content adds only the wiki pages and requirements to an existing reference set")
    seed.add_argument("--one-time-password", help="one time password printed by bootstrap-admin, for the first login")
    seed.add_argument("--workers", type=int, default=8, help="parallel sessions for creating tickets")
    seed.add_argument("--projects", type=int, default=20)
    seed.add_argument("--tickets", type=int, default=90000)
    seed.add_argument("--accounts", type=int, default=100)
    seed.add_argument("--company-tags", type=int, default=1000)
    seed.add_argument("--project-tags", type=int, default=200)
    seed.add_argument("--tags-per-ticket", type=float, default=5.0, help="mean number of tags per ticket")
    seed.add_argument("--wiki-pages", type=int, default=2000)
    seed.add_argument("--requirements", type=int, default=10000)

    run = commands.add_parser("measure", help="measure with concurrent users on the reference data set")
    run.add_argument("--users", type=int, default=15)
    run.add_argument("--accounts", type=int, default=100, help="size of the reference account list")
    run.add_argument("--think", type=float, default=5.0, help="mean pause between two actions of one user, seconds")
    run.add_argument("--warmup", type=int, default=600)
    run.add_argument("--duration", type=int, default=1800)
    run.add_argument("--out", default=os.environ.get("TASK_MANAGER_RESULTS", "results"))
    # The measured build, written into the result so a run names exactly what it measured (TASK-110, H5).
    run.add_argument("--commit", default=os.environ.get("TASK_MANAGER_COMMIT"), help="commit the measured application was built from")
    run.add_argument("--build", default=os.environ.get("TASK_MANAGER_BUILD"), help="how the measured application was built and run")

    check = commands.add_parser("verify", help="check rights, conflict, search and session on the presentation data set")
    check.add_argument("--save", help="keep the session of anna in this file")
    check.add_argument("--resume", help="check the kept session again after a restart")

    args = parser.parse_args(argv)
    if args.command == "seed":
        if args.profile == "demo":
            seed_demo(args)
        elif args.profile == "reference":
            seed_reference(args)
        else:
            admin = admin_client(args)
            builder = Builder(admin, workers=args.workers)
            builder.credentials = (args.admin, demo_password())
            seed_reference_content(args, admin, builder)
    elif args.command == "verify":
        verify(args)
    else:
        measure(args)


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (ApiError, UnclearOutcome) as error:
        raise SystemExit(f"stopped: {error}")
    except KeyboardInterrupt:
        raise SystemExit("interrupted")
