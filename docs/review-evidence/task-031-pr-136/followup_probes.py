"""Independent, synthetic follow-up probes; run with Python 3.11+ from any directory."""
from pathlib import Path
import datetime
import http.server
import json
import random
import sys
import threading
import time
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools" / "demo"))
import demo


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.server.committed += 1  # Synthetic commit before losing the answer.
        self.close_connection = True
        if self.server.mode == "drop":
            return
        if self.server.mode == "timeout":
            time.sleep(0.3)
            return
        self.send_response(201)
        self.send_header("Content-Length", "100")
        self.end_headers()
        self.wfile.write(b'{"id":')  # Truncate the success body after the commit.
        self.wfile.flush()

    def log_message(self, *args):
        pass


def transport():
    for mode, expected in (("drop", "UnclearOutcome"), ("partial", "IncompleteRead"), ("timeout", "TimeoutError")):
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        server.mode, server.committed = mode, 0
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        client = demo.Client(f"http://127.0.0.1:{server.server_port}", timeout=0.1)
        try:
            try:
                client.call("POST", "/projects/synthetic/tickets", {"title": "Synthetic"})
                raise AssertionError("the answer must fail")
            except Exception as error:
                actual = type(error).__name__
                assert actual == expected, (mode, actual)
                assert server.committed == 1, (mode, server.committed)
                classification = "unclear" if isinstance(error, demo.UnclearOutcome) else "error"
                print(f"POST {mode}: commits={server.committed}, exception={actual}, measurement classification={classification}")
        finally:
            client.close()
            server.shutdown()
            server.server_close()
            thread.join()


def measurement():
    path = ROOT / "docs/measurements/task-031/measurement-20261022-073217"
    result = json.loads(path.with_suffix(".json").read_text(encoding="utf-8"))
    rows = result["classes"]
    md = path.with_suffix(".md").read_text(encoding="utf-8")
    assert md.splitlines()[4:] == demo.report_table(rows)[1:]
    assert sum(r["samples"] for r in rows if r["class"] != "Ticketänderung gesamt") == result["samples"] == result["actions"] == 5385
    assert result["error_rate"] == 0 and all(r["rejected"] + r["errors"] + r["unclear"] == 0 for r in rows)
    assert result["stock"] == {"projects": 20, "tickets": 90000, "accounts": 101}
    assert (result["users"], result["warmup_seconds"], result["duration_seconds"], result["think_seconds"]) == (15, 600, 1800, 5.0)
    for row in rows:
        assert row["samples"] == row["ok"] + row["rejected"] + row["errors"] + row["unclear"]
        assert row["p50_ms"] <= row["p95_ms"] <= row["p99_ms"] <= row["max_ms"]
        assert row["target_p95_ms"] == demo.TARGETS_MS[row["class"]]
        assert row["met"] == (None if row["target_p95_ms"] is None else row["p95_ms"] <= row["target_p95_ms"])
        if "hits" in row:
            assert row["hits"]["with_hits"] + row["hits"]["without_hits"] == row["ok"]
        if row["class"].startswith("Komplexe"):
            print(f"{row['class']}: samples={row['samples']}, p95_ms={row['p95_ms']}, hits={row['hits']}")
    print("Published JSON/Markdown: exact generated table match, totals/targets/counts consistent; raw percentiles not independently reconstructed")


def generators_and_timing():
    state = demo.UserState(None)
    state.projects = [{"id": f"p{i}", "key": f"P{i}"} for i in range(5)]
    state.cases = [{"project": f"p{i}", "title": "Rechnung langsam", "status": "new", "type": "bug",
                    "tags": ["company1", f"project{i}"]} for i in range(5)]
    rng = random.Random(136)
    for _ in range(10000):
        query = demo.complex_case(state, rng)
        source = next(c for c in state.cases if c["project"] == query["project"][0])
        assert source["status"] in query["status"] and source["type"] in query["type"]
        assert set(query["tag"]) <= set(source["tags"]) and query["q"] == "rechnung"
        assert 1 <= len(query["project"]) <= 3 and 1 <= len(query["tag"]) <= 10
    print("Positive generator: 10000/10000 queries match their synthetic source ticket")

    class Client:
        def call(self, method, path, *args, **kwargs):
            return demo.Response(200, {"etag": '"1"'}, {}, 0.9 if method == "GET" else 0.01)

    state.client = Client()
    state.tickets = {p["id"]: [1] for p in state.projects}
    with patch.object(demo.time, "perf_counter", side_effect=[10.0, 10.91]):
        samples = demo.run_action(state, "Ticketänderung", rng)
    assert [(s.kind, round(s.ms)) for s in samples] == [("Ticketänderung Speichern", 10), ("Ticketänderung gesamt", 910)]
    assert demo.TARGETS_MS[samples[0].kind] == 1000 and demo.TARGETS_MS[samples[1].kind] is None
    print("Controlled write: GET 900ms + PATCH 10ms => save 10ms, complete action 910ms; target only on save")
    for start, end in (("2026-03-28T22:30:00Z", "2026-03-29T22:30:00Z"),
                       ("2026-10-24T06:00:00Z", "2026-10-25T06:00:00Z")):
        assert demo.hours_left(end, demo.parse_instant(start)) == 24
    print("UTC expiry arithmetic: 24 hours across both DST changes")


if __name__ == "__main__":
    transport()
    measurement()
    generators_and_timing()
    print("All independent probe assertions passed")
