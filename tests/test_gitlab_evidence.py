"""The evidence of the GitLab run keeps the secrets of the run out (TASK-115, F2): failures name step, status and a fixed
code only, a forced failure that carries every secret prints none of them, and the diagnostics drop OAuth parameters,
tokens and passwords."""

from pathlib import Path
import base64
import contextlib
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
import urllib.parse
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "git"))
import gitlab_check  # noqa: E402
import redact  # noqa: E402

SECRETS = {
    "TASK_MANAGER_ADMIN_OTP": "otp-Secret-0001-abcdef",
    "TASK_MANAGER_DEMO_PASSWORD": "run-DemoSecret0002xyz",
    "GITLAB_ROOT_PASSWORD": "Run-RootSecret0003qrs",
    "GITLAB_TOKEN": "glpat-TokenSecret0004uvw",
    "GITLAB_APP_SECRET": "gloas-AppSecret0005mno",
}
RUN_SECRETS = {
    "TASK_MANAGER_DB_PASSWORD": "run-DbSecret0006abc",
    "TASK_MANAGER_DEMO_PASSWORD": SECRETS["TASK_MANAGER_DEMO_PASSWORD"],
    "GITLAB_CHECK_ROOT_PASSWORD": SECRETS["GITLAB_ROOT_PASSWORD"],
    "GITLAB_CHECK_SECRET_KEY": "c2VjcmV0LWtleS1vZi10aGUtcnVuLTAwMDc+/w==",
    "GITLAB_CHECK_TOKEN": SECRETS["GITLAB_TOKEN"],
    "GITLAB_CHECK_APP_SECRET": SECRETS["GITLAB_APP_SECRET"],
    "GITLAB_CHECK_OTP": SECRETS["TASK_MANAGER_ADMIN_OTP"],
}


class FailureTest(unittest.TestCase):
    def test_a_failure_names_status_and_a_known_code_only(self):
        error = gitlab_check.ApiError("GET", "/git/oauth/callback?code=abc&state=def", 500,
                                      {"code": "git.provider_failed", "detail": SECRETS["GITLAB_TOKEN"]})
        self.assertEqual(gitlab_check.failure(error), {"status": 500, "code": "git.provider_failed"})
        odd = gitlab_check.ApiError("GET", "/x", 502, {"code": "Bearer " + SECRETS["GITLAB_TOKEN"]})
        self.assertEqual(gitlab_check.failure(odd), {"status": 502, "code": "api.unexpected"})
        # A code of the right form that the run does not know may be a value: it is not passed on (TASK-116, F4).
        hexed = gitlab_check.ApiError("GET", "/x", 400, {"code": "a3f9c2e1b4d5e6f708192a3b4c5d6e7f"})
        self.assertEqual(gitlab_check.failure(hexed), {"status": 400, "code": "api.unexpected"})
        self.assertEqual(gitlab_check.failure(gitlab_check.Failed("oauth.pkce")), {"status": None, "code": "oauth.pkce"})
        self.assertEqual(gitlab_check.failure(gitlab_check.Failed("code=" + SECRETS["GITLAB_TOKEN"])),
                         {"status": None, "code": "check.failed"})
        self.assertEqual(gitlab_check.failure(ValueError(SECRETS["GITLAB_APP_SECRET"])), {"status": None, "code": "error.unexpected"})
        self.assertEqual(gitlab_check.failure(TimeoutError("x")), {"status": None, "code": "error.timeout"})

    def test_a_forced_failure_prints_and_writes_no_secret(self):
        with tempfile.TemporaryDirectory() as results:
            environment = {**SECRETS, "GITLAB_CHECK_FAIL_AT": "1", "TASK_MANAGER_RESULTS": results}
            out, err = io.StringIO(), io.StringIO()
            with mock.patch.dict(os.environ, environment), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                self.assertEqual(gitlab_check.main(), 1)
            written = (Path(results) / "gitlab-run.json").read_text()
            outcome = json.loads(written)
            self.assertEqual(outcome["failed"], {"step": 1, "name": "GitLab vorbereitet", "status": 500, "code": "forced.failure"})
            self.assertIn("FAILED at step 1 (GitLab vorbereitet): status 500, code forced.failure", err.getvalue())
            for value in SECRETS.values():
                for text in (out.getvalue(), err.getvalue(), written):
                    self.assertNotIn(value, text)
            with mock.patch.dict(os.environ, RUN_SECRETS):
                self.assertEqual(redact.leaks(written, redact.secrets(), "result"), [])

    def test_every_step_ends_with_a_fixed_text(self):
        # TASK-116, F4: no line of a step and no detail of the result file carries a value of an answer.
        source = (ROOT / "tools" / "git" / "gitlab_check.py").read_text()
        import re
        calls = re.findall(r"steps\.ok\((.*)\)", source)
        self.assertEqual(len(calls), 10)
        for call in calls:
            self.assertRegex(call, r'^"[^"{}]*"$', call)
        # TASK-117, F3: the steps of the run are exactly those of evidence.json, in their order.
        names = re.findall(r'steps\.begin\("([^"]*)"\)', source)
        details = [call.strip('"') for call in calls]
        self.assertEqual(list(zip(names, details)), redact.STEPS)
        self.assertEqual(gitlab_check.STEPS, redact.STEPS)

    def test_every_code_of_a_check_is_one_the_upload_allows(self):
        # TASK-117, F3: the codes of the generator and of the check before the upload are the same.
        source = (ROOT / "tools" / "git" / "gitlab_check.py").read_text()
        import re
        used = set(re.findall(r'Failed\("([a-z][a-z0-9_.]*)"', source)) | set(re.findall(r'"([a-z][a-z0-9_.]*)"\)\s*$', source, re.M))
        used -= {"admin", "version", "gitlab.api_"}
        self.assertEqual(used - gitlab_check.CHECK_CODES, set())
        self.assertLessEqual(gitlab_check.CHECK_CODES | gitlab_check.KNOWN_CODES, redact.CODES)


PS = (
    '{"Service":"app","State":"running","Health":"healthy","ExitCode":0,"Name":"x","Command":"java -Dpw=%s"}\n'
    '{"Service":"gitlab","State":"exited","Health":"","ExitCode":137,"Labels":"token=%s"}\n'
    '{"Service":"db","State":"running","Health":"%s","ExitCode":0}\n'
)


class RedactTest(unittest.TestCase):
    def test_diagnostics_hold_only_fixed_fields_of_the_containers(self):
        with mock.patch.dict(os.environ, RUN_SECRETS):
            text = PS % (RUN_SECRETS["TASK_MANAGER_DB_PASSWORD"], RUN_SECRETS["GITLAB_CHECK_TOKEN"], RUN_SECRETS["GITLAB_CHECK_OTP"])
            cleaned = "".join(line + "\n" for line in redact.diagnostics(text))
            self.assertEqual(cleaned, "container app: state running, health healthy, exit 0\n"
                                      "container gitlab: state exited, health -, exit 137\n"
                                      "container state unreadable\n")
            self.assertEqual(redact.leaks(cleaned, redact.secrets()), [])
            self.assertEqual(redact.diagnostics("app-1 | GET /callback?code=abc\n"), ["container state unreadable"])
            array = json.dumps([{"Service": "app", "State": "running", "Health": "", "ExitCode": 0}])
            self.assertEqual(redact.diagnostics(array), ["container app: state running, health -, exit 0"])

    def test_the_check_refuses_other_forms_and_encoded_secrets(self):
        with mock.patch.dict(os.environ, RUN_SECRETS):
            known = redact.secrets()
            # Lines the run never writes, as a filter on raw logs would have let them through (TASK-116, F4).
            for line in ('{"code_verifier": "abcdefghijklmnop"}', '"access_token":"xyz"', "Cookie: __Host-tm-session=abc",
                         "Set-Cookie: _gitlab_session=abc", "app-1  | started"):
                self.assertTrue(redact.leaks(line + "\n", known), line)
            key = RUN_SECRETS["GITLAB_CHECK_SECRET_KEY"]
            for encoded in (urllib.parse.quote(key, safe=""), base64.b64encode(key.encode()).decode(), json.dumps(key)[1:-1]):
                result = json.dumps({"gitlab": None, "passed": False, "steps": [
                    {"step": 1, "name": "GitLab vorbereitet", "detail": encoded[:200], "seconds": 1.0}]})
                self.assertTrue(redact.leaks(result, known, "result"), encoded)
            good = json.dumps({"gitlab": "16.11.10-ce", "passed": False, "steps": [
                {"step": 1, "name": "GitLab vorbereitet", "detail": "GitLab 16, Projekt demo/team/werkzeug mit Commit und Merge Request",
                 "seconds": 3.5}], "failed": {"step": 2, "name": "Instanz ohne eigene CA", "status": 500, "code": "forced.failure"}})
            self.assertEqual(redact.leaks(good, known, "result"), [])
            self.assertTrue(redact.leaks(good.replace('"forced.failure"', '"forced.failure", "extra": 1'), known, "result"))
            self.assertTrue(redact.leaks(good.replace("16.11.10-ce", "17.0.0"), known, "result"))
            # TASK-117, F3: a code of the right form the run never writes, a free detail of the allowed characters and a
            # known value written with Unicode escapes inside a result of the right structure are all refused.
            self.assertTrue(redact.leaks(good.replace('"forced.failure"', '"a3f9c2e1b4d5e6f708192a3b4c5d6e7f"'), known, "result"))
            self.assertTrue(redact.leaks(good.replace("mit Commit und Merge Request", "mit Commit und Merge Request 1"), known, "result"))
            escaped = "".join(f"\\u{ord(c):04x}" for c in RUN_SECRETS["GITLAB_CHECK_TOKEN"])
            self.assertNotIn(RUN_SECRETS["GITLAB_CHECK_TOKEN"], escaped)
            hidden = json.loads(good)
            hidden["steps"][0]["detail"] = "@@"
            text = json.dumps(hidden).replace("@@", escaped)
            self.assertIn("secret of the run", redact.leaks(text, known, "result"))
            # Diagnostics of a service the run does not have are refused.
            self.assertTrue(redact.leaks("container sidecar: state running, health -, exit 0\n", known))
            self.assertEqual(redact.leaks("container gitlab-check: state exited, health -, exit 1\n", known), [])

    def test_the_check_fails_on_a_leak_and_names_no_secret(self):
        with tempfile.TemporaryDirectory() as folder:
            leaked = Path(folder) / "diagnostics.txt"
            leaked.write_text(f"container app: state running, health -, exit 0\nvalue {RUN_SECRETS['GITLAB_CHECK_OTP']}\n")
            clean = Path(folder) / "gitlab-run.json"
            clean.write_text('{"passed": false, "gitlab": "16.11.10-ce", "steps": []}\n')
            environment = {**os.environ, **RUN_SECRETS}
            run = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "git" / "redact.py"), "--check", str(clean), str(leaked)],
                                 env=environment, capture_output=True, text=True, check=False)
            self.assertEqual(run.returncode, 1)
            self.assertNotIn(RUN_SECRETS["GITLAB_CHECK_OTP"], run.stderr + run.stdout)
            run = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "git" / "redact.py"), "--check", str(clean)],
                                 env=environment, capture_output=True, text=True, check=False)
            self.assertEqual(run.returncode, 0, run.stderr)
            # TASK-118, F3: a key twice in one object is refused by the command itself, also when the hidden value is harmless
            # or a secret of the run, at the top and inside a step.
            step = '{"step": 1, "name": "GitLab vorbereitet", "detail": "GitLab 16, Projekt demo/team/werkzeug mit Commit und Merge Request", "seconds": 1.0}'
            for text in ('{"passed": false, "gitlab": "16.11.10-ce", "steps": [], "passed": false}\n',
                         '{"passed": false, "gitlab": "16.11.10-ce", "steps": [' + step.replace('"detail"', '"detail": "frei", "detail"') + ']}\n',
                         '{"passed": false, "gitlab": "16.11.10-ce", "steps": [' + step.replace('"detail"', '"detail": "' + RUN_SECRETS["GITLAB_CHECK_TOKEN"] + '", "detail"') + ']}\n',
                         '{"passed": false, "gitlab": null, "steps": [' + step.replace("1.0", "NaN") + ']}\n'):
                twice = Path(folder) / "gitlab-run.json"
                twice.write_text(text)
                run = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "git" / "redact.py"), "--check", str(twice)],
                                     env=environment, capture_output=True, text=True, check=False)
                self.assertEqual(run.returncode, 1, text)
                self.assertNotIn(RUN_SECRETS["GITLAB_CHECK_TOKEN"], run.stderr + run.stdout)


class RunFolderTest(unittest.TestCase):
    def test_the_run_creates_its_own_folder_and_releases_only_checked_evidence(self):
        # TASK-116, F6: the shell script creates a folder of its own below the given one and names only that one.
        script = (ROOT / "tools" / "git" / "gitlab-check.sh").read_text()
        self.assertIn('run_results=$(mktemp -d "$(cd "$results_base" && pwd)/$COMPOSE_PROJECT_NAME.XXXXXX")', script)
        self.assertIn("printf 'evidence=%s\\n' \"$run_results\" >> \"$GITHUB_OUTPUT\"", script)
        self.assertNotIn("logs --no-color", script, "no log line leaves the run")
        workflow = (ROOT / ".github" / "workflows" / "gitlab.yml").read_text()
        self.assertIn("if: always() && steps.check.outputs.evidence != ''", workflow)
        self.assertIn("${{ steps.check.outputs.evidence }}/gitlab-run.json", workflow)
        self.assertNotIn("gitlab-results/gitlab-run.json", workflow)
        compose = (ROOT / "tools" / "git" / "compose.gitlab.yaml").read_text()
        self.assertIn("${GITLAB_CHECK_RUN_RESULTS:?set by gitlab-check.sh}:/results", compose)


if __name__ == "__main__":
    unittest.main()
