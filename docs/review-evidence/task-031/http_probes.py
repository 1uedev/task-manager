"""Real HTTP probes using the unmodified demo client, on own synthetic data.

Usage from repo root: python http_probes.py DEMO_URL SMALL_REFERENCE_URL
TASK_MANAGER_DEMO_PASSWORD must match the independently seeded disposable DBs.
No cookies or credentials are printed or saved by these probes.
"""
from datetime import datetime, timezone
import importlib.util
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import random
import socket
import sys
import time
import threading
import urllib.parse
import uuid

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("task031_demo_http", ROOT / "tools/demo/demo.py")
demo = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = demo
spec.loader.exec_module(demo)
password = os.environ["TASK_MANAGER_DEMO_PASSWORD"]

client = demo.Client(sys.argv[1])
session = client.login("anna", password)
correct_hours = (datetime.fromisoformat(session["expiresAt"].replace("Z", "+00:00")) - datetime.now(timezone.utc)).total_seconds() / 3600
wrong_hours = (time.mktime(time.strptime(session["expiresAt"][:19], "%Y-%m-%dT%H:%M:%S")) - time.mktime(time.gmtime())) / 3600
print("REAL SESSION:", json.dumps({"timezone": time.tzname, "UTC_hours_left": round(correct_hours, 3),
      "verify_formula_hours_left": round(wrong_hours, 3)}, ensure_ascii=False))
assert 23.5 < correct_hours <= 24.01
client.close()

state = demo.prepare_user(sys.argv[2], "ref001", password)

class RecordingClient:
    def __init__(self, wrapped):
        self.wrapped = wrapped
        self.answers = []
    def call(self, method, path, body=None, **kwargs):
        result = self.wrapped.call(method, path, body, **kwargs)
        if path == "/search/tickets":
            self.answers.append({"status": result.status, "total": result.body["total"]})
        return result
    def close(self):
        self.wrapped.close()

state.client = RecordingClient(state.client)
for i in range(100):
    demo.run_action(state, "Komplexe Suche", random.Random(1000 + i))
zero = sum(answer["total"] == 0 for answer in state.client.answers)
print("SMALL REFERENCE COMPLEX SEARCH:", json.dumps({"projects": 4, "initial_tickets": 200, "accounts_plus_admin": 13,
      "queries": len(state.client.answers), "zero_matches": zero,
      "nonzero_matches": len(state.client.answers)-zero, "all_HTTP_200": all(x["status"] == 200 for x in state.client.answers)}))

# Deliberately construct one positive complex-filter case from a real ticket:
# permitted fields, a word from its title and one of its assigned tags.
found = False
for project in state.projects:
    for number in state.tickets[project["id"]]:
        ticket = state.client.call("GET", f"/projects/{project['id']}/tickets/{number}").body
        tags = state.client.call("GET", f"/projects/{project['id']}/tickets/{number}/tags").body
        if ticket["fields"]["status"] not in ("new", "in_progress") or not tags:
            continue
        query = {"q": ticket["fields"]["title"].split()[0], "project": [project["id"]],
                 "status": ["new", "in_progress"], "type": ["bug", "task"], "tag": [tags[0]["id"]], "limit": 20}
        answer = state.client.call("GET", "/search/tickets", query=query)
        assert answer.status == 200 and answer.body["total"] >= 1
        print("CONTROL: positive complex filter from an actual ticket returns at least one hit")
        found = True
        break
    if found:
        break
assert found
# Drop a real success response after a committed ticket creation. The original
# Client.call retries the POST. Count the resulting real tickets through GET.
target = urllib.parse.urlsplit(sys.argv[2])
assert target.hostname in ("127.0.0.1", "localhost") and target.scheme == "http"
responses = []

class DropResponse(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *args):
        pass
    def do_POST(self):
        payload = self.rfile.read(int(self.headers["Content-Length"]))
        upstream = http.client.HTTPConnection(target.hostname, target.port)
        headers = {key: value for key, value in self.headers.items() if key.lower() not in ("host", "connection")}
        upstream.request("POST", self.path, payload, headers)
        answer = upstream.getresponse()
        body = answer.read()
        responses.append(answer.status)
        if len(responses) == 1:
            assert answer.status == 201
            # The full real response has arrived; the database commit happened.
            self.connection.shutdown(socket.SHUT_RDWR)
            self.connection.close()
            self.close_connection = True
        else:
            self.send_response(answer.status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        upstream.close()

proxy = ThreadingHTTPServer(("127.0.0.1", 0), DropResponse)
thread = threading.Thread(target=proxy.serve_forever, daemon=True)
thread.start()
retry = demo.Client(f"http://127.0.0.1:{proxy.server_port}")
retry.cookies = dict(state.client.wrapped.cookies)
retry.csrf = state.client.wrapped.csrf
title = "Synthetic retry probe " + uuid.uuid4().hex[:8]
project_id = state.projects[0]["id"]
try:
    retry.call("POST", f"/projects/{project_id}/tickets", {"title": title, "type": "task"})
    listed = state.client.call("GET", f"/projects/{project_id}/tickets", query={"limit": 50}).body
    created = [item for item in listed if item["fields"].get("title") == title]
    assert responses == [201, 201] and len(created) == 2
    print("REAL POST RETRY: one Client.call produced two committed tickets; backend statuses [201, 201]")
finally:
    retry.close()
    proxy.shutdown()
    proxy.server_close()
    thread.join()
state.client.close()
