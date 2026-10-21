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
             headers: dict[str, str] | None = None, expect: tuple[int, ...] | None = (200, 201, 204)) -> Response:
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
        sent.update(headers or {})
        for attempt in (1, 2):
            started = time.perf_counter()
            try:
                connection = self._connect()
                connection.request(method, target, body=data, headers=sent)
                raw = connection.getresponse()
                payload = raw.read()
                seconds = time.perf_counter() - started
                break
            except (http.client.RemoteDisconnected, ConnectionResetError, BrokenPipeError, http.client.CannotSendRequest):
                # A kept-alive connection the server already closed; one fresh attempt.
                self.close()
                if attempt == 2:
                    raise
        received = {k.lower(): v for k, v in raw.getheaders()}
        for name, value in raw.getheaders():
            if name.lower() == "set-cookie":
                self._cookie(value)
        parsed: object = None
        if payload:
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

ALL_FIELDS = ["title", "description", "type", "status", "assignee", "tags"]
PROJECT_ACTIONS_FULL = ["project.read", "project.manage", "ticket.read", "ticket.create", "ticket.edit", "tag.create"]
PROJECT_ACTIONS_EDIT = ["project.read", "ticket.read", "ticket.create", "ticket.edit", "tag.create"]
PROJECT_ACTIONS_READ = ["project.read", "ticket.read"]


@dataclass
class GroupSpec:
    name: str
    actions: list[str]
    read: list[str]
    write: list[str]


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
                                {"resourceType": "ticket", "field": name, "projectId": None, "operation": operation, "effect": "allow"})
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
                except ApiError as error:
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
    refuse_second_run(admin, {key for key, *_ in DEMO_PROJECTS}, {name for name, *_ in DEMO_PEOPLE})
    builder = Builder(admin, workers=4)
    builder.credentials = (args.admin, password)
    rng = random.Random(31)
    admin_id = account_id(admin, args.admin)

    print("accounts")
    people = {username: builder.account(username, display, password) for username, display, _, _ in DEMO_PEOPLE}
    print("groups and rights")
    specs = {
        "Projektleitung": GroupSpec("Projektleitung", PROJECT_ACTIONS_FULL, ALL_FIELDS, ALL_FIELDS),
        "Entwicklung": GroupSpec("Entwicklung", PROJECT_ACTIONS_EDIT, ALL_FIELDS, ["title", "description", "status", "assignee", "tags"]),
        "Lesen ohne Beschreibung": GroupSpec("Lesen ohne Beschreibung", PROJECT_ACTIONS_READ, ["title", "type", "status", "tags"], []),
    }
    builder.group(GroupSpec("Verwaltung Demo", PROJECT_ACTIONS_FULL, ALL_FIELDS, ALL_FIELDS), [admin_id])
    for name, spec in specs.items():
        builder.group(spec, [people[u] for u, _, group, _ in DEMO_PEOPLE if group == name])

    print("tags")
    company = [builder.company_tag(name) for name in DEMO_COMPANY_TAGS]
    print("projects and tickets")
    jobs = []
    for key, name, description, local_tags, titles in DEMO_PROJECTS:
        members = [admin_id] + [people[u] for u, _, _, projects in DEMO_PEOPLE if key in projects]
        project = builder.project(key, name, description, members)
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
    print(f"done: {len(DEMO_PEOPLE)} accounts, {len(DEMO_PROJECTS)} projects, {len(jobs)} tickets. "
          "Every demo account uses TASK_MANAGER_DEMO_PASSWORD.")


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
    builder.group(GroupSpec("Referenz Bearbeitung", PROJECT_ACTIONS_EDIT, ALL_FIELDS, ["title", "description", "status", "assignee", "tags"]), edit)
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


# ---------------------------------------------------------------------- checks of the presentation data set

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
        same = session.get("expiresAt", "")[:26].rstrip("Z") == state["expiresAt"][:26].rstrip("Z")
        check(same, f"fixed end unchanged ({state['expiresAt']} before, {session.get('expiresAt')} after)")
        keys = sorted(p["key"] for p in anna.call("GET", "/projects").body)
        check(keys == ["APP", "HELP", "WEB"], f"data kept, anna sees {keys}")
    else:
        anna = Client(args.url)
        session = anna.login("anna", password)
        hours = (time.mktime(time.strptime(session["expiresAt"][:19], "%Y-%m-%dT%H:%M:%S")) - time.mktime(time.gmtime())) / 3600
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

        if args.save:
            with open(args.save, "w") as out:
                json.dump({"cookies": anna.cookies, "csrf": anna.csrf, "expiresAt": session["expiresAt"]}, out)
    if failures:
        raise SystemExit(f"{len(failures)} checks failed")
    print("all checks passed")


# ---------------------------------------------------------------------- measurement

# Shares of OPERATIONS.md. Time booking (10) and wiki editing (5) do not exist yet; the remaining 85 are scaled up.
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
    "Komplexe Suche": 2.0,
    "Tagvorschlag": 2.0,
}
TARGETS_MS = {
    "Ticketliste": 500, "Ticketdetail": 500, "Ticketänderung": None, "Suche Kennung": 500, "Suche ein Wort": 500,
    "Suche zwei Wörter": 500, "Suche häufiger Begriff": 500, "Suche kurzer Präfix": 500, "Suche ohne Treffer": 500,
    "Suche mit Filtern": 500, "Komplexe Suche": 1000, "Tagvorschlag": 200,
}


@dataclass
class Sample:
    kind: str
    ms: float
    outcome: str  # ok, rejected (expected business refusal such as a version conflict) or error


@dataclass
class UserState:
    client: Client
    projects: list[dict] = field(default_factory=list)
    tickets: dict[str, list[int]] = field(default_factory=dict)
    tags: list[str] = field(default_factory=list)
    can_edit: bool = False


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
    state.tags.extend(tag["id"] for tag in client.call("GET", "/tags", query={"limit": 50}).body)
    first = state.projects[0]["id"]
    sample = client.call("GET", f"/projects/{first}/tickets/{state.tickets[first][0]}").body
    state.can_edit = "title" in sample.get("writableFields", [])
    return state


def run_action(state: UserState, kind: str, rng: random.Random) -> Sample:
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
        current = client.call("GET", f"/projects/{pid}/tickets/{number}", expect=None)
        if current.status != 200:
            return Sample(kind, current.seconds * 1000, "error")
        title = f"{zipf_word(rng).capitalize()} {rng.choice(VERBS)} {rng.choice(QUALIFIERS)}"
        response = client.call("PATCH", f"/projects/{pid}/tickets/{number}", {"title": title},
                               headers={"If-Match": current.headers.get("etag", "")}, expect=None)
    elif kind == "Tagvorschlag":
        response = client.call("GET", "/tags", query={"q": rng.choice(["ber", "bere", "bereich 0", "the"]), "limit": 10}, expect=None)
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
        elif kind == "Komplexe Suche":
            search["q"] = zipf_word(rng)
            search["project"] = [p["id"] for p in rng.sample(state.projects, k=min(3, len(state.projects)))]
            search["status"] = ["new", "in_progress"]
            search["type"] = ["bug", "task"]
            if state.tags:
                search["tag"] = rng.sample(state.tags, k=min(rng.randint(1, 10), len(state.tags)))
        response = client.call("GET", "/search/tickets", query=search, expect=None)
    if response.status in (200, 201, 204):
        outcome = "ok"
    elif response.status in (409, 412, 428) or (response.status == 403 and kind == "Ticketänderung"):
        outcome = "rejected"
    else:
        outcome = "error"
    return Sample(kind, response.seconds * 1000, outcome)


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
    print(f"stock visible to {args.admin}: {projects} projects, {stock} tickets, {accounts} accounts")

    kinds = list(MIX)
    weights = [MIX[k] for k in kinds]
    samples: list[Sample] = []
    lock = threading.Lock()
    begin = time.monotonic()
    measure_from = begin + args.warmup
    stop_at = measure_from + args.duration

    def user_loop(index: int, state: UserState) -> None:
        rng = random.Random(1000 + index)
        # A person without write rights does not try to change tickets; their share goes to the other actions.
        own = [w if state.can_edit or k != "Ticketänderung" else 0 for k, w in zip(kinds, weights)]
        while True:
            pause = rng.expovariate(1 / args.think) if args.think > 0 else 0
            if time.monotonic() + pause >= stop_at:
                return
            time.sleep(pause)
            kind = rng.choices(kinds, own)[0]
            try:
                sample = run_action(state, kind, rng)
            except (OSError, http.client.HTTPException) as error:
                sample = Sample(kind, float("nan"), "error")
                print(f"  {kind}: {error}", file=sys.stderr)
            if time.monotonic() >= measure_from:
                with lock:
                    samples.append(sample)

    print(f"warm up {args.warmup} s, measure {args.duration} s, {args.users} users, think time {args.think} s")
    threads = [threading.Thread(target=user_loop, args=(i, s), daemon=True) for i, s in enumerate(states)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    rows = []
    for kind in kinds:
        chosen = [s for s in samples if s.kind == kind]
        ok = [s.ms for s in chosen if s.outcome == "ok"]
        errors = sum(1 for s in chosen if s.outcome == "error")
        rejected = sum(1 for s in chosen if s.outcome == "rejected")
        target = TARGETS_MS[kind]
        p95 = percentile(ok, 0.95)
        rows.append({
            "class": kind, "samples": len(chosen), "ok": len(ok), "rejected": rejected, "errors": errors,
            "p50_ms": round(percentile(ok, 0.50), 1), "p95_ms": round(p95, 1), "p99_ms": round(percentile(ok, 0.99), 1),
            "max_ms": round(max(ok), 1) if ok else None, "target_p95_ms": target,
            "met": None if target is None or not ok else p95 <= target,
        })
    total = sum(r["samples"] for r in rows)
    errors = sum(r["errors"] for r in rows)
    result = {
        "started": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() - args.duration - args.warmup)),
        "users": args.users, "think_seconds": args.think, "warmup_seconds": args.warmup, "duration_seconds": args.duration,
        "stock": {"projects": projects, "tickets": stock, "accounts": accounts},
        "client_machine": machine(), "server": args.url, "samples": total,
        "error_rate": round(errors / total, 5) if total else None,
        "throughput_per_second": round(total / args.duration, 2), "classes": rows,
    }
    os.makedirs(args.out, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    with open(os.path.join(args.out, f"measurement-{stamp}.json"), "w") as out:
        json.dump(result, out, indent=2, ensure_ascii=False)
    lines = [
        f"Messung {result['started']}: {args.users} Benutzer, Denkzeit {args.think} s, {args.warmup} s Aufwärmen, {args.duration} s Messung",
        f"Bestand: {projects} Projekte, {stock} Tickets, {accounts} Konten; {total} Messwerte, Fehlerquote {result['error_rate']}",
        "",
        "| Messklasse | Messwerte | abgewiesen | Fehler | p50 ms | p95 ms | p99 ms | Ziel p95 ms | erfüllt |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---|",
    ]
    for r in rows:
        met = "" if r["met"] is None else ("ja" if r["met"] else "nein")
        lines.append(f"| {r['class']} | {r['samples']} | {r['rejected']} | {r['errors']} | {r['p50_ms']} | {r['p95_ms']} | "
                     f"{r['p99_ms']} | {r['target_p95_ms'] or ''} | {met} |")
    report = "\n".join(lines) + "\n"
    with open(os.path.join(args.out, f"measurement-{stamp}.md"), "w") as out:
        out.write(report)
    print(report)


# ---------------------------------------------------------------------- command line

def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default=os.environ.get("TASK_MANAGER_URL", "http://localhost:8080"))
    parser.add_argument("--admin", default=os.environ.get("TASK_MANAGER_DEMO_ADMIN", "admin"),
                        help="login name given to bootstrap-admin")
    commands = parser.add_subparsers(dest="command", required=True)

    seed = commands.add_parser("seed", help="create a synthetic data set")
    seed.add_argument("--profile", choices=["demo", "reference"], default="demo")
    seed.add_argument("--one-time-password", help="one time password printed by bootstrap-admin, for the first login")
    seed.add_argument("--workers", type=int, default=8, help="parallel sessions for creating tickets")
    seed.add_argument("--projects", type=int, default=20)
    seed.add_argument("--tickets", type=int, default=90000)
    seed.add_argument("--accounts", type=int, default=100)
    seed.add_argument("--company-tags", type=int, default=1000)
    seed.add_argument("--project-tags", type=int, default=200)
    seed.add_argument("--tags-per-ticket", type=float, default=5.0, help="mean number of tags per ticket")

    run = commands.add_parser("measure", help="measure with concurrent users on the reference data set")
    run.add_argument("--users", type=int, default=15)
    run.add_argument("--accounts", type=int, default=100, help="size of the reference account list")
    run.add_argument("--think", type=float, default=5.0, help="mean pause between two actions of one user, seconds")
    run.add_argument("--warmup", type=int, default=600)
    run.add_argument("--duration", type=int, default=1800)
    run.add_argument("--out", default=os.environ.get("TASK_MANAGER_RESULTS", "results"))

    check = commands.add_parser("verify", help="check rights, conflict, search and session on the presentation data set")
    check.add_argument("--save", help="keep the session of anna in this file")
    check.add_argument("--resume", help="check the kept session again after a restart")

    args = parser.parse_args(argv)
    if args.command == "seed":
        (seed_demo if args.profile == "demo" else seed_reference)(args)
    elif args.command == "verify":
        verify(args)
    else:
        measure(args)


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except ApiError as error:
        raise SystemExit(f"stopped: {error}")
    except KeyboardInterrupt:
        raise SystemExit("interrupted")
