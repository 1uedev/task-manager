"""Independent probes against the unmodified TASK-031 demo module.

Run from the repository root. Uses only synthetic in-process data and a local
HTTP server. No performance numbers for the application are derived here.
"""
import importlib.util
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import random
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("task031_demo", ROOT / "tools/demo/demo.py")
demo = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = demo
spec.loader.exec_module(demo)


def check_percentiles_and_published_tables():
    values = list(range(1, 101))
    assert [demo.percentile(values, q) for q in (.5, .95, .99)] == [50, 95, 99]
    print("PERCENTILES: nearest-rank p50/p95/p99 => 50/95/99 for 1..100")
    for path in sorted((ROOT / "docs/measurements/task-031").glob("*.json")):
        result = json.loads(path.read_text(encoding="utf-8"))
        assert sum(row["samples"] for row in result["classes"]) == result["samples"]
        for row in result["classes"]:
            assert row["ok"] + row["errors"] + row["rejected"] == row["samples"]
            assert row["p50_ms"] <= row["p95_ms"] <= row["p99_ms"] <= row["max_ms"]
            if row["met"] is not None:
                assert row["met"] == (row["p95_ms"] <= row["target_p95_ms"])
        expected_rate = round(sum(row["errors"] for row in result["classes"]) / result["samples"], 5)
        assert result["error_rate"] == expected_rate
        assert result["throughput_per_second"] == round(result["samples"] / result["duration_seconds"], 2)
        print("AGGREGATES CONSISTENT:", path.name, result["samples"], "samples;")
    print("NOTE: published files contain aggregates, not individual latency observations")


class CaptureClient:
    def __init__(self):
        self.query = None

    def call(self, method, path, body=None, query=None, **kwargs):
        self.query = query
        return demo.Response(200, {"etag": '"1"'}, {}, .001)


def check_complex_query_construction():
    # Exact shape prepared for ref001 under the default reference profile:
    # five membership projects; first 50 tags from each plus 50 company tags.
    projects = [{"id": f"p{i}", "key": f"R{1 + 4*i:02d}"} for i in range(5)]
    origin = {f"p{i}-tag{j}": f"p{i}" for i in range(5) for j in range(50)}
    origin.update({f"company-tag{j}": None for j in range(50)})
    client = CaptureClient()
    state = demo.UserState(client, projects, {p["id"]: list(range(4451, 4501)) for p in projects}, list(origin), True)
    rng = random.Random(1000)
    impossible = 0
    cross_catalog = 0
    outside_scope = 0
    example = None
    count = 10000
    for _ in range(count):
        demo.run_action(state, "Komplexe Suche", rng)
        query = client.query
        selected = set(query["project"])
        tag_projects = {origin[tag] for tag in query["tag"] if origin[tag] is not None}
        multiple = len(tag_projects) > 1
        outside = bool(tag_projects - selected)
        cross_catalog += multiple
        outside_scope += outside
        if multiple or outside:
            impossible += 1
            example = example or {"projects": sorted(selected), "tag_project_origins": sorted(tag_projects)}
    print("COMPLEX QUERY CONSTRUCTION:", json.dumps({"queries": count, "structurally_zero_matches": impossible,
          "tags_from_different_project_catalogs": cross_catalog, "tag_outside_selected_projects": outside_scope,
          "example": example}))
    assert impossible > count * .8
    print("Reason: ticket tags are AND filters; a ticket cannot carry tags from two project catalogs")


def check_write_timing():
    class TimedClient:
        def call(self, method, path, *args, **kwargs):
            return demo.Response(200, {"etag": '"1"'}, {}, .9 if method == "GET" else .01)
    state = demo.UserState(TimedClient(), [{"id": "p", "key": "P"}], {"p": [1]}, [], True)
    sample = demo.run_action(state, "Ticketänderung", random.Random(1))
    assert sample.ms == 10
    print("WRITE TIMING: controlled GET=900 ms, PATCH=10 ms => sample=10 ms; GET is not included")
    print("WRITE TARGET:", demo.TARGETS_MS["Ticketänderung"])


def check_post_retry():
    calls = []
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"
        def log_message(self, *args):
            pass
        def do_POST(self):
            calls.append(json.loads(self.rfile.read(int(self.headers["Content-Length"]))))
            if len(calls) == 1:
                # Application accepted and committed the synthetic creation;
                # connection dies before the success response can be delivered.
                self.connection.shutdown(socket.SHUT_RDWR)
                self.connection.close()
                self.close_connection = True
                return
            data = json.dumps({"id": len(calls)}).encode()
            self.send_response(201)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    client = demo.Client(f"http://127.0.0.1:{server.server_port}")
    try:
        response = client.call("POST", "/projects/synthetic/tickets", {"title": "Synthetic retry probe"})
        assert response.status == 201 and len(calls) == 2
        print("POST RETRY: one Client.call =>", len(calls), "accepted creations; caller sees success id", response.body["id"])
    finally:
        client.close()
        server.shutdown()
        server.server_close()
        thread.join()


def check_session_time_calculation():
    started = datetime(2026, 10, 22, 12, tzinfo=timezone.utc)
    end = started + timedelta(hours=24)
    reported = (time.mktime(time.strptime(end.isoformat()[:19], "%Y-%m-%dT%H:%M:%S"))
                - time.mktime(time.gmtime(started.timestamp()))) / 3600
    print("SESSION TIME CALCULATION:", json.dumps({"timezone": time.tzname, "actual_hours": 24,
          "verify_formula_hours": reported}, ensure_ascii=False))


if __name__ == "__main__":
    check_percentiles_and_published_tables()
    check_complex_query_construction()
    check_write_timing()
    check_post_retry()
    check_session_time_calculation()
