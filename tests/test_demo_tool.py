"""Regressions of the demonstration tool from the review of TASK-031 (PR #135): lost answers, session time, complex searches."""

from pathlib import Path
import datetime
import http.server
import os
import random
import sys
import threading
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "demo"))
import demo  # noqa: E402

UTC = datetime.timezone.utc


class _Handler(http.server.BaseHTTPRequestHandler):
    """Records every request; drops the connection without an answer while the server's drop counter is positive."""

    protocol_version = "HTTP/1.1"

    def _handle(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        server = self.server
        with server.lock:
            server.seen.append((self.command, self.path))
            drop = server.drops > 0
            if drop:
                server.drops -= 1
        if drop:
            # The request was received and, for a change, carried out; only the answer gets lost.
            self.close_connection = True
            self.connection.close()
            return
        if server.behavior == "truncate":
            # Saved, but the answer stops in the middle of its announced body.
            self.send_response(201)
            self.send_header("Content-Length", "100")
            self.end_headers()
            self.wfile.write(b'{"id": "12')
            self.wfile.flush()
            self.close_connection = True
            self.connection.close()
            return
        if server.behavior == "slow":
            # Saved, but the answer comes later than the client waits.
            time.sleep(1.5)
        body = b'{"ok": true}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    do_GET = do_POST = do_PATCH = do_PUT = do_DELETE = _handle

    def log_message(self, *args):
        pass


class LostAnswerTests(unittest.TestCase):
    def setUp(self):
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        self.server.seen, self.server.drops, self.server.lock, self.server.behavior = [], 0, threading.Lock(), None
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.client = demo.Client(f"http://127.0.0.1:{self.server.server_address[1]}", timeout=5)

    def tearDown(self):
        self.client.close()
        self.server.shutdown()
        self.server.server_close()

    def test_change_saved_but_answer_lost_is_not_sent_again(self):
        self.server.drops = 1
        with self.assertRaises(demo.UnclearOutcome):
            self.client.call("POST", "/projects/p/tickets", {"title": "Einmal"})
        self.assertEqual(self.server.seen, [("POST", "/api/v1/projects/p/tickets")])

    def test_every_changing_method_is_left_unclear(self):
        for method in ("POST", "PATCH", "PUT", "DELETE"):
            with self.subTest(method=method):
                self.server.seen.clear()
                self.server.drops = 1
                with self.assertRaises(demo.UnclearOutcome):
                    self.client.call(method, "/x", {"a": 1} if method != "DELETE" else None)
                self.assertEqual(len(self.server.seen), 1)

    def test_change_with_cut_off_answer_is_unclear_and_not_sent_again(self):
        self.server.behavior = "truncate"
        with self.assertRaises(demo.UnclearOutcome):
            self.client.call("POST", "/tags", {"name": "einmal"})
        self.assertEqual(self.server.seen, [("POST", "/api/v1/tags")])

    def test_change_answered_too_late_is_unclear_and_not_sent_again(self):
        self.server.behavior = "slow"
        self.client.timeout = 0.5
        with self.assertRaises(demo.UnclearOutcome):
            self.client.call("PATCH", "/projects/p/tickets/1", {"title": "einmal"})
        self.assertEqual(self.server.seen, [("PATCH", "/api/v1/projects/p/tickets/1")])

    def test_change_that_could_not_connect_stays_a_plain_error(self):
        # Nothing left the client, so the outcome is certain: nothing was saved.
        port = self.server.server_address[1]
        self.server.shutdown()
        self.server.server_close()
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
        self.server.seen, self.server.drops, self.server.lock, self.server.behavior = [], 0, threading.Lock(), None
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        closed = demo.Client(f"http://127.0.0.1:{port}", timeout=2)
        with self.assertRaises(ConnectionRefusedError):
            closed.call("POST", "/tags", {"name": "nie"})

    def test_reading_request_is_repeated_once(self):
        self.server.drops = 1
        response = self.client.call("GET", "/session")
        self.assertEqual(response.status, 200)
        self.assertEqual([m for m, _ in self.server.seen], ["GET", "GET"])

    def test_reading_request_gives_up_after_the_second_loss(self):
        self.server.drops = 2
        with self.assertRaises((ConnectionError, OSError)):
            self.client.call("GET", "/session")
        self.assertEqual(len(self.server.seen), 2)

    def test_idle_connection_is_renewed_before_a_change(self):
        self.client.call("GET", "/session")
        first = self.client.connection
        self.client.last_used = time.monotonic() - demo.IDLE_RECONNECT_SECONDS - 1
        self.client.call("POST", "/tags", {"name": "neu"})
        self.assertIsNot(self.client.connection, first)


class SessionTimeTests(unittest.TestCase):
    def test_parses_api_instants(self):
        expected = datetime.datetime(2026, 10, 22, 6, 35, 12, 123456, tzinfo=UTC)
        self.assertEqual(demo.parse_instant("2026-10-22T06:35:12.123456789Z"), expected)
        self.assertEqual(demo.parse_instant("2026-10-22T06:35:12.123456Z"), expected)
        self.assertEqual(demo.parse_instant("2026-10-22T08:35:12.123456+02:00"), expected)
        self.assertEqual(demo.parse_instant("2026-10-22T06:35:12Z"), expected.replace(microsecond=0))
        with self.assertRaises(ValueError):
            demo.parse_instant("2026-10-22T06:35:12")

    def test_full_day_across_daylight_saving_change(self):
        # Europe ends summer time on 25 October 2026; 24 hours in UTC stay 24 hours.
        now = datetime.datetime(2026, 10, 24, 6, 0, tzinfo=UTC)
        self.assertAlmostEqual(demo.hours_left("2026-10-25T06:00:00.000000001Z", now), 24.0, places=4)
        spring = datetime.datetime(2026, 3, 28, 22, 30, tzinfo=UTC)
        self.assertAlmostEqual(demo.hours_left("2026-03-29T22:30:00Z", spring), 24.0, places=4)

    @unittest.skipUnless(hasattr(time, "tzset"), "time zones can only be switched on Unix")
    def test_local_time_zone_plays_no_role(self):
        saved = os.environ.get("TZ")
        try:
            for zone in ("UTC", "Europe/Berlin", "America/New_York", "Asia/Kolkata"):
                with self.subTest(zone=zone):
                    os.environ["TZ"] = zone
                    time.tzset()
                    now = datetime.datetime.now(UTC)
                    end = (now + datetime.timedelta(hours=24)).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
                    self.assertAlmostEqual(demo.hours_left(end, now), 24.0, places=4)
        finally:
            if saved is None:
                os.environ.pop("TZ", None)
            else:
                os.environ["TZ"] = saved
            time.tzset()


class ComplexSearchTests(unittest.TestCase):
    def state(self):
        state = demo.UserState(client=None)
        state.projects = [{"id": f"p{i}", "key": f"R{i:02d}"} for i in range(5)]
        state.tags = [f"t{i}" for i in range(100)]
        state.cases = [
            {"project": "p1", "title": "Rechnung prüft langsam (Kunde)", "status": "done", "type": "bug",
             "tags": [f"c{i}" for i in range(12)]},
            {"project": "p3", "title": "Drucker fehlt", "status": "new", "type": "task", "tags": ["c1"]},
        ]
        return state

    def test_case_fits_its_ticket(self):
        state = self.state()
        rng = random.Random(7)
        for _ in range(500):
            search = demo.complex_case(state, rng)
            source = next(c for c in state.cases if c["project"] == search["project"][0])
            self.assertEqual(search["q"], source["title"].split()[0].lower())
            self.assertLessEqual(len(search["project"]), 3)
            self.assertEqual(len(set(search["project"])), len(search["project"]))
            self.assertIn(source["status"], search["status"])
            self.assertIn(source["type"], search["type"])
            self.assertTrue(set(search["tag"]) <= set(source["tags"]))
            self.assertTrue(1 <= len(search["tag"]) <= 10)

    def test_hits_are_reported_per_class(self):
        samples = [
            demo.Sample("Komplexe Suche mit Treffern", 10.0, "ok", 3),
            demo.Sample("Komplexe Suche mit Treffern", 12.0, "ok", 0),
            demo.Sample("Komplexe Suche mit Treffern", 11.0, "ok", 1),
            demo.Sample("Komplexe Suche selektiv", 5.0, "ok", 0),
            demo.Sample("Ticketänderung Speichern", 20.0, "ok"),
            demo.Sample("Ticketänderung Speichern", float("nan"), "unclear"),
        ]
        rows = {row["class"]: row for row in demo.summarize(samples)}
        self.assertEqual(rows["Komplexe Suche mit Treffern"]["hits"], {"with_hits": 2, "without_hits": 1, "p50": 1, "max": 3})
        self.assertEqual(rows["Komplexe Suche selektiv"]["hits"]["with_hits"], 0)
        self.assertNotIn("hits", rows["Ticketänderung Speichern"])
        self.assertEqual(rows["Ticketänderung Speichern"]["unclear"], 1)
        self.assertEqual(rows["Ticketänderung Speichern"]["target_p95_ms"], 1000)
        self.assertIsNone(rows["Ticketänderung gesamt"]["target_p95_ms"])


if __name__ == "__main__":
    unittest.main()
