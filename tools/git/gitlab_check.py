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
    5. When GitLab revokes the tokens (the application is removed), the next refresh shows the links only as
       "Verbindung erneuern" without path or title, and the connection stands on "erneuern".

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
    TASK_MANAGER_RESULTS     optional folder for the result as JSON
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


def setting(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if not value:
        raise SystemExit(f"{name} must be set")
    return value


class Step:
    """Prints each step and collects them for the result file."""

    def __init__(self) -> None:
        self.done: list[dict] = []
        self.started = time.monotonic()

    def ok(self, name: str, detail: str = "") -> None:
        seconds = round(time.monotonic() - self.started, 1)
        print(f"ok  {name}" + (f": {detail}" if detail else ""), flush=True)
        self.done.append({"step": name, "detail": detail, "seconds": seconds})


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAILED: {message}")


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
            raise SystemExit(f"FAILED: GitLab {method} {path} answered {status}: {payload[:300]!r}")
        return json.loads(payload) if payload else None

    def sign_in(self, login: str, password: str) -> None:
        status, _, page = self.request("GET", "/users/sign_in", session=True)
        expect(status == 200, f"GitLab sign in page answered {status}")
        token = re.search(rb'name="authenticity_token" value="([^"]+)"', page)
        expect(token is not None, "the GitLab sign in page has no authenticity token")
        form = urllib.parse.urlencode({"authenticity_token": html.unescape(token.group(1).decode()), "user[login]": login,
                                       "user[password]": password, "user[remember_me]": "0"}).encode()
        status, headers, _ = self.request("POST", "/users/sign_in", form, {"Content-Type": "application/x-www-form-urlencoded"},
                                          session=True)
        expect(status == 302 and not headers.get("location", "").endswith("/users/sign_in"),
               f"GitLab refused the sign in ({status} to {headers.get('location')})")

    def authorize(self, url: str) -> str:
        """Follows the authorize address as the browser does; a trusted application sends straight back with the code."""
        parsed = urllib.parse.urlsplit(url)
        expect(f"{parsed.scheme}://{parsed.netloc}" == self.base, f"the authorize address {url} is not at {self.base}")
        status, headers, page = self.request("GET", parsed.path + "?" + parsed.query, session=True)
        if status == 302:
            return headers["location"]
        # GitLab 16 answers a redirect to an address outside of itself with a page "Redirecting" and a link, which the
        # browser follows; the consent screen of an untrusted application would have a form instead.
        link = re.search(rb'<h1[^>]*>Redirecting</h1>\s*<div>\s*<a href="([^"]+)"', page)
        expect(status == 200 and link is not None, f"GitLab authorize answered {status} instead of the return: {page[:300]!r}")
        return html.unescape(link.group(1).decode())


def links_of(admin: Client, path: str, refresh: bool = False) -> list[dict]:
    body = admin.call("GET", path, query={"refresh": "true"} if refresh else None).body
    expect(body["readable"] and body["writable"], f"{path} is not readable and writable for the administrator")
    return body["links"]


def by_kind(links: list[dict]) -> dict[str, dict]:
    return {link["kind"]: link for link in links}


def main() -> None:
    steps = Step()
    base = setting("TASK_MANAGER_URL")
    gitlab_url = setting("GITLAB_URL").rstrip("/")
    cafile = setting("GITLAB_CA")
    ca = Path(cafile).read_text()
    gitlab = GitLab(gitlab_url, cafile, setting("GITLAB_TOKEN"))
    app_id, app_secret = setting("GITLAB_APP_ID"), setting("GITLAB_APP_SECRET")
    password = setting("TASK_MANAGER_DEMO_PASSWORD")

    # ---------------------------------------------------------------- GitLab: a project in a subgroup with a merge request
    version = gitlab.api("GET", "/version")["version"]
    expect(version.startswith("16."), f"GitLab {version} is not version 16")
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
    steps.ok("GitLab vorbereitet", f"GitLab {version}, Projekt demo/team/werkzeug ({pid}), Commit {sha[:8]}, Merge Request !{change['iid']}")

    # ---------------------------------------------------------------- task manager: administrator and instances
    admin = Client(base)
    try:
        admin.login(setting("TASK_MANAGER_ADMIN", "admin"), password)
    except ApiError:
        admin.login(setting("TASK_MANAGER_ADMIN", "admin"), setting("TASK_MANAGER_ADMIN_OTP"), new_password=password)
    status = admin.call("GET", "/git/status").body
    expect(status["secretKey"] and status["callbackUrl"] == base.rstrip("/") + "/api/v1/git/oauth/callback",
           f"the task manager is not set up for Git: {status}")

    settings = {"kind": "gitlab", "baseUrl": gitlab_url, "clientId": app_id, "clientSecret": app_secret}
    without_ca = admin.call("POST", "/git/admin/providers", {**settings, "name": "GitLab ohne CA"})
    checked = admin.call("POST", f"/git/admin/providers/{without_ca.body['id']}/check").body
    expect(not checked["ok"] and checked["code"] == "git.tls_failed", f"check without the CA answered {checked}")
    admin.call("DELETE", f"/git/admin/providers/{without_ca.body['id']}", headers={"If-Match": without_ca.headers.get("etag")})
    steps.ok("Instanz ohne eigene CA", "Prüfen meldet git.tls_failed")

    provider = admin.call("POST", "/git/admin/providers", {**settings, "name": "GitLab Werkstatt", "caCertificate": ca}).body["id"]
    checked = admin.call("POST", f"/git/admin/providers/{provider}/check").body
    expect(checked["ok"], f"check with the CA answered {checked}")
    steps.ok("Instanz mit eigener CA", f"Prüfen erfolgreich, Version {checked.get('version') or 'ohne Token nicht genannt'}")

    # ---------------------------------------------------------------- OAuth with PKCE as the browser does it
    authorize = admin.call("POST", f"/git/providers/{provider}/connect").body["authorizeUrl"]
    query = urllib.parse.parse_qs(urllib.parse.urlsplit(authorize).query)
    expect(query.get("code_challenge_method") == ["S256"] and query.get("scope") == ["read_api"],
           f"the authorize address asks for {query.get('scope')} with {query.get('code_challenge_method')}")
    gitlab.sign_in("root", setting("GITLAB_ROOT_PASSWORD"))
    back = urllib.parse.urlsplit(gitlab.authorize(authorize))
    expect(back.path == "/api/v1/git/oauth/callback", f"GitLab sent the browser to {back.path}")
    returned = admin.call("GET", "/git/oauth/callback", query=dict(urllib.parse.parse_qsl(back.query)), expect=(303,))
    expect(f"connected={provider}" in returned.headers.get("location", ""), f"the return ended at {returned.headers.get('location')}")
    connection = next(p for p in admin.call("GET", "/git/providers").body if p["id"] == provider)
    expect(connection["connection"] == "active", f"the connection stands on {connection['connection']}")
    steps.ok("Git Konto verbunden", "Anmeldung bei GitLab, Freigabe der Anwendung, Rückleitung mit PKCE")

    # ---------------------------------------------------------------- project, rights, release
    me = account_id(admin, setting("TASK_MANAGER_ADMIN", "admin"))
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
    expect(candidate is not None and candidate["externalId"] == str(pid), f"the candidates are {candidates}")
    release = admin.call("POST", f"/projects/{project_id}/git/repositories", {"providerId": provider, "externalId": str(pid)}).body["id"]
    steps.ok("Repository freigegeben", f"demo/team/werkzeug mit der Kennung {pid}")

    # ---------------------------------------------------------------- links by address and by release
    ticket = admin.call("POST", f"/projects/{project_id}/tickets", {"type": "task", "title": "Werkzeugliste prüfen"}).body["number"]
    path = f"/projects/{project_id}/tickets/{ticket}/git"
    web = f"{gitlab_url}/demo/team/werkzeug"
    for address in (f"{web}.git", f"{web}/-/commit/{sha[:8]}?view=parallel", f"{web}/-/merge_requests/{change['iid']}"):
        admin.call("POST", path, {"url": address})
    duplicate = admin.call("POST", path, {"url": f"{web}/-/commit/{sha}"}, expect=(409,)).body
    expect(duplicate["code"] == "git.link_exists", f"the full SHA answered {duplicate}")
    unknown = admin.call("POST", path, {"url": f"https://gitlab.example/demo/team/werkzeug/-/commit/{sha}"}, expect=(400,)).body
    expect(unknown["code"] == "git.address_unknown", f"an address of another host answered {unknown}")
    links = by_kind(links_of(admin, path))
    expect(set(links) == {"repository", "commit", "merge_request"} and all(link["available"] for link in links.values()),
           f"the ticket shows {links}")
    expect(links["repository"]["repository"]["path"] == "demo/team/werkzeug", "the repository path is not shown")
    expect(links["commit"]["commit"]["sha"] == sha and links["commit"]["commit"]["message"].startswith("Werkzeugliste ergänzen"),
           f"the commit shows {links['commit']['commit']}")
    expect(links["merge_request"]["change"]["state"] == "open", f"the merge request shows {links['merge_request']['change']}")
    expect(links["commit"]["url"] == f"{web}/-/commit/{sha}", f"the commit address is {links['commit']['url']}")
    steps.ok("Ticket verknüpft", "Repository, Commit über kurzen SHA und Merge Request; Duplikat und fremder Rechner abgewiesen")

    requirement = admin.call("POST", f"/requirements/projects/{project_id}/requirements", {"title": "Werkzeuge sind erfasst"}).body["id"]
    admin.call("POST", f"/requirements/{requirement}/git", {"releaseId": release, "kind": "merge_request", "key": str(change["iid"])})
    shown = links_of(admin, f"/requirements/{requirement}/git")
    expect(len(shown) == 1 and shown[0]["available"] and shown[0]["change"]["title"] == "Werkzeugliste für die Werkstatt",
           f"the requirement shows {shown}")
    steps.ok("Anforderung verknüpft", "Merge Request über die Freigabe")

    history = admin.call("GET", f"/projects/{project_id}/tickets/{ticket}/history").body
    entries = [entry for entry in (history["items"] if isinstance(history, dict) else history)
               for change_entry in entry.get("changes", []) if change_entry.get("field") == "git"]
    expect(len(entries) >= 3, "the ticket history names fewer than three Git links")

    # ---------------------------------------------------------------- merge and rename at GitLab, "Aktualisieren"
    for _ in range(30):
        merged = gitlab.api("PUT", f"/projects/{pid}/merge_requests/{change['iid']}/merge", {}, expect_status=(200, 405, 406, 422))
        if isinstance(merged, dict) and merged.get("state") == "merged":
            break
        time.sleep(2)
    else:
        raise SystemExit("FAILED: GitLab did not merge the merge request")
    cached = by_kind(links_of(admin, path))["merge_request"]["change"]["state"]
    refreshed = by_kind(links_of(admin, path, refresh=True))["merge_request"]["change"]["state"]
    expect(refreshed == "merged", f"after the merge and Aktualisieren the state is {refreshed}")
    steps.ok("Zusammengeführt", f"vor dem Aktualisieren {cached}, danach {refreshed}")

    gitlab.api("PUT", f"/projects/{pid}", {"path": "werkzeug-neu", "name": "Werkzeug neu"})
    renamed = by_kind(links_of(admin, path, refresh=True))["repository"]["repository"]
    expect(renamed["path"] == "demo/team/werkzeug-neu" and renamed["url"] == f"{gitlab_url}/demo/team/werkzeug-neu",
           f"after the rename the repository shows {renamed}")
    steps.ok("Umbenannt", "dieselbe Kennung zeigt den neuen Pfad")

    # ---------------------------------------------------------------- tokens revoked at GitLab
    gitlab.api("DELETE", f"/applications/{setting('GITLAB_APP_DB_ID')}")
    revoked = links_of(admin, path, refresh=True)
    expect(all(not link["available"] and link["reason"] == "git.connection_renew" for link in revoked),
           f"after the revocation the ticket shows {revoked}")
    expect(all(link["repository"] is None and link["commit"] is None and link["change"] is None and link["url"] is None
               for link in revoked), "after the revocation a link still shows metadata")
    connection = next(p for p in admin.call("GET", "/git/providers").body if p["id"] == provider)
    expect(connection["connection"] == "renew", f"after the revocation the connection stands on {connection['connection']}")
    steps.ok("Token entzogen", "Verknüpfungen nur mit Art und git.connection_renew, Verbindung auf erneuern")

    print(f"\nGitLab {version} run passed in {round(time.monotonic() - steps.started)} s")
    results = os.environ.get("TASK_MANAGER_RESULTS")
    if results:
        Path(results).mkdir(parents=True, exist_ok=True)
        (Path(results) / "gitlab-run.json").write_text(json.dumps({"gitlab": version, "steps": steps.done}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
