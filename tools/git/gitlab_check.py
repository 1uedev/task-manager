#!/usr/bin/env python3
"""Run of the Git link against a real GitLab 16 in a container (TASK-113, DEC-143, rule 1).

The automatic tests of TASK-111 and TASK-112 talk to stand ins of GitHub and GitLab. This run checks the same paths once
against a real GitLab Community Edition 16 that serves HTTPS with a certificate of a CA of its own, which neither the JVM
nor the system knows:

    1. "Prüfen" of an instance without the CA names git.tls_failed; with the CA it answers with the version of GitLab.
    2. A person connects the own GitLab account through OAuth with PKCE (sign in at GitLab, consent of a trusted
       application, return to the task manager).
    3. The project releases a repository of a subgroup; a ticket links repository, commit (by a short SHA) and merge
       request by their addresses, a requirement links the merge request through the release; the same commit by its
       full SHA is refused as a duplicate, an address of another host as unknown.
    4. The links show path, message and state live; after the merge in GitLab "Aktualisieren" shows "merged", after a
       rename of the project the new path under the same identifier.
    5. When GitLab revokes the tokens (the application is removed), the next refresh shows the same links of ticket and
       requirement (same removal keys, kinds and counts) only as "Verbindung erneuern" without instance, path or title,
       and the connection stands on "erneuern".

A failure names only the step, the HTTP status and a fixed code (TASK-115, F2): never an answer body, an address with
its query, a token or a password. GITLAB_CHECK_FAIL_AT=<step number> forces a failure at the start of that step with an
error that carries the secrets of the run, to show that none of them reaches the output or the result file.

Everything runs through the public HTTP API of the task manager, as the interface does it, and through the API of GitLab
with a token of its administrator for the preparation. Only the Python standard library is used.

Settings come from the environment:
    TASK_MANAGER_URL         address of the task manager, e.g. http://app:8080
    TASK_MANAGER_ADMIN       login name of the administrator from bootstrap-admin (default admin)
    TASK_MANAGER_ADMIN_OTP   its one time password
    TASK_MANAGER_DEMO_PASSWORD  the password the run gives the administrator
    GITLAB_URL               https://gitlab.test
    GITLAB_CA                file with the CA certificate in PEM
    GITLAB_ROOT_PASSWORD     password of root at GitLab, for the sign in of the OAuth flow
    GITLAB_TOKEN             personal access token of root (scope api) for the preparation
    GITLAB_APP_ID, GITLAB_APP_SECRET  the OAuth application (scope read_api, trusted, redirect to the callback)
    GITLAB_APP_DB_ID         its identifier in GitLab, used to remove it at the end
    TASK_MANAGER_RESULTS     optional folder for the result as JSON (also written when the run fails)
    GITLAB_CHECK_FAIL_AT     optional number of the step that fails on purpose
"""

from __future__ import annotations

import html
import http.client
import json
import os
import re
import ssl
import sys
import time
import urllib.parse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "demo"))
from demo import ApiError, Client, account_id  # noqa: E402  (the HTTP client of the demo tool, standard library only)


CODE = re.compile(r"^[a-z][a-z0-9_.]{0,79}$")
SECRET_SETTINGS = ("TASK_MANAGER_ADMIN_OTP", "TASK_MANAGER_DEMO_PASSWORD", "GITLAB_ROOT_PASSWORD", "GITLAB_TOKEN", "GITLAB_APP_SECRET")


def setting(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if not value:
        raise Failed("setting.missing")
    return value


class Failed(Exception):
    """A check that did not hold. It carries only a fixed code and, for an answer, its HTTP status (TASK-115, F2)."""

    def __init__(self, code: str, status: int | None = None):
        super().__init__(code)
        self.code = code if CODE.match(code) else "check.failed"
        self.status = status


class Step:
    """Numbers and names each step, prints it and collects the steps for the result file."""

    def __init__(self) -> None:
        self.done: list[dict] = []
        self.started = time.monotonic()
        self.number = 0
        self.name = "Start"
        self.fail_at = os.environ.get("GITLAB_CHECK_FAIL_AT", "")

    def begin(self, name: str) -> None:
        self.number += 1
        self.name = name
        if self.fail_at == str(self.number):
            # A failure on purpose with the secrets of the run in its text and its answer body (TASK-115, F2).
            secrets = {key: os.environ.get(key, "") for key in SECRET_SETTINGS}
            raise ApiError("GET", "/forced?" + urllib.parse.urlencode(secrets), 500, {"code": "forced.failure", "detail": secrets})

    def ok(self, detail: str = "") -> None:
        seconds = round(time.monotonic() - self.started, 1)
        print(f"ok  {self.number}. {self.name}" + (f": {detail}" if detail else ""), flush=True)
        self.done.append({"step": self.number, "name": self.name, "detail": detail, "seconds": seconds})


def failure(error: BaseException) -> dict:
    """What a failure may tell: the step is added by the caller, here only the status and a fixed code (TASK-115, F2)."""
    if isinstance(error, Failed):
        return {"status": error.status, "code": error.code}
    if isinstance(error, ApiError):
        code = error.code if isinstance(error.code, str) and CODE.match(error.code) else "api.unexpected"
        return {"status": error.status, "code": code}
    return {"status": None, "code": "error." + type(error).__name__.lower()[:60]}


def expect(condition: bool, code: str) -> None:
    if not condition:
        raise Failed(code)


class GitLab:
    """A browser like session at GitLab with its cookies, and API calls with the token of the administrator."""

    def __init__(self, base: str, cafile: str, token: str):
        parsed = urllib.parse.urlsplit(base)
        self.base = base.rstrip("/")
        self.host = parsed.hostname
        self.port = parsed.port or 443
        self.context = ssl.create_default_context(cafile=cafile)
        self.token = token
        self.cookies: dict[str, str] = {}

    def request(self, method: str, target: str, body: bytes | None = None, headers: dict[str, str] | None = None,
                session: bool = False) -> tuple[int, dict[str, str], bytes]:
        connection = http.client.HTTPSConnection(self.host, self.port, context=self.context, timeout=60)
        sent = dict(headers or {})
        if session and self.cookies:
            sent["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        if not session:
            sent["PRIVATE-TOKEN"] = self.token
        try:
            connection.request(method, target, body=body, headers=sent)
            answer = connection.getresponse()
            payload = answer.read()
        finally:
            connection.close()
        received: dict[str, str] = {}
        for name, value in answer.getheaders():
            received[name.lower()] = value
            if session and name.lower() == "set-cookie":
                first = value.split(";", 1)[0]
                key, _, content = first.partition("=")
                self.cookies[key.strip()] = content
        return answer.status, received, payload

    def api(self, method: str, path: str, fields: dict | None = None, expect_status: tuple[int, ...] = (200, 201, 202, 204)) -> object:
        body = json.dumps(fields).encode() if fields is not None else None
        status, _, payload = self.request(method, "/api/v4" + path, body, {"Content-Type": "application/json"} if body else None)
        if status not in expect_status:
            raise Failed("gitlab.api_" + method.lower(), status)
        return json.loads(payload) if payload else None

    def sign_in(self, login: str, password: str) -> None:
        status, _, page = self.request("GET", "/users/sign_in", session=True)
        if status != 200:
            raise Failed("gitlab.sign_in_page", status)
        token = re.search(rb'name="authenticity_token" value="([^"]+)"', page)
        expect(token is not None, "gitlab.sign_in_token")
        form = urllib.parse.urlencode({"authenticity_token": html.unescape(token.group(1).decode()), "user[login]": login,
                                       "user[password]": password, "user[remember_me]": "0"}).encode()
        status, headers, _ = self.request("POST", "/users/sign_in", form, {"Content-Type": "application/x-www-form-urlencoded"},
                                          session=True)
        if status != 302 or headers.get("location", "").endswith("/users/sign_in"):
            raise Failed("gitlab.sign_in_refused", status)

    def authorize(self, url: str) -> str:
        """Follows the authorize address as the browser does; a trusted application sends straight back with the code."""
        parsed = urllib.parse.urlsplit(url)
        expect(f"{parsed.scheme}://{parsed.netloc}" == self.base, "oauth.authorize_host")
        status, headers, page = self.request("GET", parsed.path + "?" + parsed.query, session=True)
        if status == 302:
            return headers["location"]
        # GitLab 16 answers a redirect to an address outside of itself with a page "Redirecting" and a link, which the
        # browser follows; the consent screen of an untrusted application would have a form instead.
        link = re.search(rb'<h1[^>]*>Redirecting</h1>\s*<div>\s*<a href="([^"]+)"', page)
        if status != 200 or link is None:
            raise Failed("oauth.no_return", status)
        return html.unescape(link.group(1).decode())


def links_of(admin: Client, path: str, refresh: bool = False) -> list[dict]:
    body = admin.call("GET", path, query={"refresh": "true"} if refresh else None).body
    expect(body["readable"] and body["writable"], "links.not_writable")
    return body["links"]


def by_kind(links: list[dict]) -> dict[str, dict]:
    """The links by kind; each kind exactly once (TASK-115, H1)."""
    kinds = [link["kind"] for link in links]
    expect(len(kinds) == len(set(kinds)), "links.kind_twice")
    return {link["kind"]: link for link in links}


def shape(links: list[dict]) -> list[tuple[str, str]]:
    """Removal key and kind of each link in their order: what has to stay the same when the token is revoked (F1)."""
    expect(all(isinstance(link.get("key"), str) and link["key"] for link in links), "links.key_missing")
    return [(link["key"], link["kind"]) for link in links]


def hidden(link: dict) -> bool:
    """A placeholder after TASK-114: kind, key and reason only."""
    return (not link["available"] and link["reason"] == "git.connection_renew"
            and all(link[name] is None for name in ("providerId", "providerName", "providerKind", "linkedAt", "linkedBy",
                                                    "repository", "commit", "change", "url")))


def main() -> int:
    steps = Step()
    results = os.environ.get("TASK_MANAGER_RESULTS")
    version = None
    try:
        version = run(steps)
        outcome = {"gitlab": version, "passed": True, "steps": steps.done}
        print(f"\nGitLab {version} run passed in {round(time.monotonic() - steps.started)} s")
    except BaseException as error:  # noqa: BLE001  (every failure is reported the same short way)
        if isinstance(error, KeyboardInterrupt):
            raise
        told = failure(error)
        outcome = {"gitlab": version, "passed": False, "steps": steps.done,
                   "failed": {"step": steps.number, "name": steps.name, **told}}
        status = told["status"] if told["status"] is not None else "-"
        print(f"FAILED at step {steps.number} ({steps.name}): status {status}, code {told['code']}", file=sys.stderr, flush=True)
    if results:
        Path(results).mkdir(parents=True, exist_ok=True)
        (Path(results) / "gitlab-run.json").write_text(json.dumps(outcome, ensure_ascii=False, indent=2))
    return 0 if outcome["passed"] else 1


def run(steps: Step) -> str:
    steps.begin("GitLab vorbereitet")
    base = setting("TASK_MANAGER_URL").rstrip("/")
    callback = base + "/api/v1/git/oauth/callback"
    gitlab_url = setting("GITLAB_URL").rstrip("/")
    cafile = setting("GITLAB_CA")
    ca = Path(cafile).read_text()
    gitlab = GitLab(gitlab_url, cafile, setting("GITLAB_TOKEN"))
    app_id, app_secret = setting("GITLAB_APP_ID"), setting("GITLAB_APP_SECRET")
    password = setting("TASK_MANAGER_DEMO_PASSWORD")
    login = setting("TASK_MANAGER_ADMIN", "admin")

    # ---------------------------------------------------------------- GitLab: a project in a subgroup with a merge request
    version = gitlab.api("GET", "/version")["version"]
    expect(version.startswith("16."), "gitlab.not_16")
    group = gitlab.api("POST", "/groups", {"name": "Demo", "path": "demo", "visibility": "private"})
    team = gitlab.api("POST", "/groups", {"name": "Team", "path": "team", "parent_id": group["id"], "visibility": "private"})
    project = gitlab.api("POST", "/projects", {"name": "Werkzeug", "path": "werkzeug", "namespace_id": team["id"],
                                                "visibility": "private", "initialize_with_readme": True, "default_branch": "main"})
    pid = project["id"]
    commit = gitlab.api("POST", f"/projects/{pid}/repository/commits", {
        "branch": "werkzeugliste", "start_branch": "main", "commit_message": "Werkzeugliste ergänzen\n\nSynthetischer Beispielinhalt.",
        "actions": [{"action": "create", "file_path": "werkzeuge.md", "content": "# Werkzeuge\n\n- Hammer\n- Zange\n"}]})
    sha = commit["id"]
    change = gitlab.api("POST", f"/projects/{pid}/merge_requests", {"source_branch": "werkzeugliste", "target_branch": "main",
                                                                     "title": "Werkzeugliste für die Werkstatt"})
    steps.ok(f"GitLab {version}, Projekt demo/team/werkzeug ({pid}), Commit {sha[:8]}, Merge Request !{change['iid']}")

    # ---------------------------------------------------------------- task manager: administrator and instances
    steps.begin("Instanz ohne eigene CA")
    admin = Client(base)
    try:
        admin.login(login, password)
    except ApiError:
        admin.login(login, setting("TASK_MANAGER_ADMIN_OTP"), new_password=password)
    status = admin.call("GET", "/git/status").body
    expect(status["secretKey"] and status["callbackUrl"] == callback, "taskmanager.git_not_set_up")
    settings = {"kind": "gitlab", "baseUrl": gitlab_url, "clientId": app_id, "clientSecret": app_secret}
    without_ca = admin.call("POST", "/git/admin/providers", {**settings, "name": "GitLab ohne CA"})
    checked = admin.call("POST", f"/git/admin/providers/{without_ca.body['id']}/check").body
    expect(not checked["ok"] and checked["code"] == "git.tls_failed", "check.without_ca_not_refused")
    admin.call("DELETE", f"/git/admin/providers/{without_ca.body['id']}", headers={"If-Match": without_ca.headers.get("etag")})
    steps.ok("Prüfen meldet git.tls_failed")

    steps.begin("Instanz mit eigener CA")
    provider = admin.call("POST", "/git/admin/providers", {**settings, "name": "GitLab Werkstatt", "caCertificate": ca}).body["id"]
    checked = admin.call("POST", f"/git/admin/providers/{provider}/check").body
    expect(checked["ok"], "check.with_ca_failed")
    steps.ok(f"Prüfen erfolgreich, Version {checked.get('version') or 'ohne Token nicht genannt'}")

    # ---------------------------------------------------------------- OAuth with PKCE as the browser does it
    steps.begin("Git Konto verbunden")
    authorize = admin.call("POST", f"/git/providers/{provider}/connect").body["authorizeUrl"]
    sent = urllib.parse.urlsplit(authorize)
    query = dict(urllib.parse.parse_qsl(sent.query))
    # The full authorize address (TASK-115, H1): the endpoint, the application, the return and PKCE with a challenge.
    expect(f"{sent.scheme}://{sent.netloc}{sent.path}" == gitlab_url + "/oauth/authorize", "oauth.authorize_endpoint")
    expect(query.get("response_type") == "code" and query.get("client_id") == app_id, "oauth.authorize_client")
    expect(query.get("redirect_uri") == callback, "oauth.redirect_uri")
    expect(query.get("scope") == "read_api", "oauth.scope")
    expect(query.get("code_challenge_method") == "S256" and re.fullmatch(r"[A-Za-z0-9_-]{43}", query.get("code_challenge", "")) is not None,
           "oauth.pkce")
    expect(len(query.get("state", "")) >= 32, "oauth.state")
    gitlab.sign_in("root", setting("GITLAB_ROOT_PASSWORD"))
    back = urllib.parse.urlsplit(gitlab.authorize(authorize))
    returned_query = dict(urllib.parse.parse_qsl(back.query))
    expect(f"{back.scheme}://{back.netloc}{back.path}" == callback, "oauth.return_target")
    expect(returned_query.get("state") == query["state"] and bool(returned_query.get("code")), "oauth.return_query")
    returned = admin.call("GET", "/git/oauth/callback", query=returned_query, expect=(303,))
    expect(f"connected={provider}" in returned.headers.get("location", ""), "oauth.not_connected")
    connection = next(p for p in admin.call("GET", "/git/providers").body if p["id"] == provider)
    expect(connection["connection"] == "active", "oauth.connection_not_active")
    steps.ok("Anmeldung bei GitLab, Freigabe der Anwendung, Rückleitung an die Rückleitungsadresse mit Zustand und PKCE S256")

    # ---------------------------------------------------------------- project, rights, release
    steps.begin("Repository freigegeben")
    me = account_id(admin, login)
    created = admin.call("POST", "/projects", {"key": "WERK", "name": "Werkstatt", "description": "Lauf gegen GitLab 16"})
    project_id = created.body["id"]
    admin.call("PUT", f"/projects/{project_id}/members/{me}", headers={"If-Match": created.headers.get("etag")})
    rights = admin.call("POST", "/permissions/groups", {"name": "Git Lauf"}).body["id"]
    for action in ("project.read", "project.manage", "ticket.read", "ticket.create", "ticket.edit", "requirement.read", "requirement.edit"):
        admin.call("PUT", f"/permissions/groups/{rights}/actions", {"action": action, "projectId": None, "enabled": True})
    for resource, fields in (("ticket", ("title", "type", "status", "git")), ("requirement", ("title", "description", "criteria", "git"))):
        for name in fields:
            for operation in ("read", "write"):
                admin.call("PUT", f"/permissions/groups/{rights}/fields", {"resourceType": resource, "field": name, "projectId": None,
                                                                           "operation": operation, "effect": "allow"})
    admin.call("PUT", f"/permissions/groups/{rights}/members/{me}")
    candidates = admin.call("GET", f"/projects/{project_id}/git/candidates", query={"provider": provider, "q": "werkzeug"}).body
    candidate = next((c for c in candidates if c["path"] == "demo/team/werkzeug"), None)
    expect(candidate is not None and candidate["externalId"] == str(pid), "release.no_candidate")
    release = admin.call("POST", f"/projects/{project_id}/git/repositories", {"providerId": provider, "externalId": str(pid)}).body["id"]
    steps.ok(f"demo/team/werkzeug mit der Kennung {pid}")

    # ---------------------------------------------------------------- links by address and by release
    steps.begin("Ticket verknüpft")
    ticket = admin.call("POST", f"/projects/{project_id}/tickets", {"type": "task", "title": "Werkzeugliste prüfen"}).body["number"]
    ticket_path = f"/projects/{project_id}/tickets/{ticket}"
    path = f"{ticket_path}/git"
    ticket_version = admin.call("GET", ticket_path).body["version"]
    web = f"{gitlab_url}/demo/team/werkzeug"
    for address in (f"{web}.git", f"{web}/-/commit/{sha[:8]}?view=parallel", f"{web}/-/merge_requests/{change['iid']}"):
        admin.call("POST", path, {"url": address})
    duplicate = admin.call("POST", path, {"url": f"{web}/-/commit/{sha}"}, expect=(409,)).body
    expect(duplicate["code"] == "git.link_exists", "link.duplicate_not_refused")
    unknown = admin.call("POST", path, {"url": f"https://gitlab.example/demo/team/werkzeug/-/commit/{sha}"}, expect=(400,)).body
    expect(unknown["code"] == "git.address_unknown", "link.other_host_not_refused")
    shown = links_of(admin, path)
    expect(len(shown) == 3, "ticket.link_count")
    links = by_kind(shown)
    expect(set(links) == {"repository", "commit", "merge_request"} and all(link["available"] for link in shown), "ticket.links")
    expect(links["repository"]["repository"]["path"] == "demo/team/werkzeug", "ticket.repository_path")
    expect(links["commit"]["commit"]["sha"] == sha and links["commit"]["commit"]["message"].startswith("Werkzeugliste ergänzen"),
           "ticket.commit")
    expect(links["merge_request"]["change"]["state"] == "open", "ticket.merge_request")
    expect(links["commit"]["url"] == f"{web}/-/commit/{sha}", "ticket.commit_url")
    expect(admin.call("GET", ticket_path).body["version"] == ticket_version, "ticket.version_changed")
    history = admin.call("GET", f"{ticket_path}/history").body
    entries = [change_entry.get("to") for entry in (history["items"] if isinstance(history, dict) else history)
               for change_entry in entry.get("changes", []) if change_entry.get("field") == "git"]
    # The history names the kind only (TASK-114, F6), once per link.
    expect(sorted(entries) == ["commit", "merge_request", "repository"], "ticket.history")
    steps.ok("Repository, Commit über kurzen SHA und Merge Request, genau drei; Duplikat und fremder Rechner abgewiesen; Version unverändert")

    steps.begin("Anforderung verknüpft")
    requirement = admin.call("POST", f"/requirements/projects/{project_id}/requirements", {"title": "Werkzeuge sind erfasst"}).body["id"]
    requirement_path = f"/requirements/{requirement}"
    before = admin.call("GET", requirement_path).body
    admin.call("POST", f"{requirement_path}/git", {"releaseId": release, "kind": "merge_request", "key": str(change["iid"])})
    linked = links_of(admin, f"{requirement_path}/git")
    expect(len(linked) == 1 and linked[0]["kind"] == "merge_request" and linked[0]["available"]
           and linked[0]["change"]["title"] == "Werkzeugliste für die Werkstatt", "requirement.links")
    events = admin.call("GET", f"{requirement_path}/history").body["events"]
    expect([event.get("to") for event in events if event.get("kind") == "git_linked"] == ["merge_request"], "requirement.history")
    steps.ok("Merge Request über die Freigabe, Historie nennt die Art")

    # ---------------------------------------------------------------- merge and rename at GitLab, "Aktualisieren"
    steps.begin("Zusammengeführt")
    for _ in range(30):
        merged = gitlab.api("PUT", f"/projects/{pid}/merge_requests/{change['iid']}/merge", {}, expect_status=(200, 405, 406, 422))
        if isinstance(merged, dict) and merged.get("state") == "merged":
            break
        time.sleep(2)
    else:
        raise Failed("gitlab.not_merged")
    cached = by_kind(links_of(admin, path))["merge_request"]["change"]["state"]
    refreshed = by_kind(links_of(admin, path, refresh=True))["merge_request"]["change"]["state"]
    expect(refreshed == "merged", "merge.state_not_shown")
    requirement_state = links_of(admin, f"{requirement_path}/git", refresh=True)[0]["change"]["state"]
    expect(requirement_state == "merged", "merge.requirement_state")
    # A merged merge request changes neither the ticket nor the requirement (TASK-115, H1).
    expect(admin.call("GET", ticket_path).body["version"] == ticket_version, "merge.ticket_version")
    after = admin.call("GET", requirement_path).body
    expect((after["version"], after["revision"], after["status"]) == (before["version"], before["revision"], before["status"]),
           "merge.requirement_changed")
    steps.ok(f"vor dem Aktualisieren {cached}, danach {refreshed}; Ticketversion, Revision und Status der Anforderung unverändert")

    steps.begin("Umbenannt")
    gitlab.api("PUT", f"/projects/{pid}", {"path": "werkzeug-neu", "name": "Werkzeug neu"})
    renamed = by_kind(links_of(admin, path, refresh=True))["repository"]["repository"]
    expect(renamed["path"] == "demo/team/werkzeug-neu" and renamed["url"] == f"{gitlab_url}/demo/team/werkzeug-neu", "rename.not_shown")
    steps.ok("dieselbe Kennung zeigt den neuen Pfad")

    # ---------------------------------------------------------------- tokens revoked at GitLab
    steps.begin("Token entzogen")
    ticket_before = shape(links_of(admin, path))
    requirement_before = shape(links_of(admin, f"{requirement_path}/git"))
    expect(len(ticket_before) == 3 and len(requirement_before) == 1, "revoke.links_before")
    gitlab.api("DELETE", f"/applications/{setting('GITLAB_APP_DB_ID')}")
    ticket_after = links_of(admin, path, refresh=True)
    requirement_after = links_of(admin, f"{requirement_path}/git", refresh=True)
    # The same links, by removal key and kind, with the same counts, only without their Git data (TASK-115, F1).
    expect(shape(ticket_after) == ticket_before, "revoke.ticket_links_changed")
    expect(shape(requirement_after) == requirement_before, "revoke.requirement_links_changed")
    expect(all(hidden(link) for link in ticket_after + requirement_after), "revoke.metadata_shown")
    connection = next(p for p in admin.call("GET", "/git/providers").body if p["id"] == provider)
    expect(connection["connection"] == "renew", "revoke.connection_not_renew")
    steps.ok("dieselben drei Verknüpfungen am Ticket und eine an der Anforderung nur mit Art und git.connection_renew, Verbindung auf erneuern")
    return version


if __name__ == "__main__":
    sys.exit(main())
