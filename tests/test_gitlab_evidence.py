"""The evidence of the GitLab run keeps the secrets of the run out (TASK-115, F2): failures name step, status and a fixed
code only, a forced failure that carries every secret prints none of them, and the diagnostics drop OAuth parameters,
tokens and passwords."""

from pathlib import Path
import contextlib
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
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
    def test_a_failure_names_status_and_code_only(self):
        error = gitlab_check.ApiError("GET", "/git/oauth/callback?code=abc&state=def", 500,
                                      {"code": "git.provider_failed", "detail": SECRETS["GITLAB_TOKEN"]})
        self.assertEqual(gitlab_check.failure(error), {"status": 500, "code": "git.provider_failed"})
        odd = gitlab_check.ApiError("GET", "/x", 502, {"code": "Bearer " + SECRETS["GITLAB_TOKEN"]})
        self.assertEqual(gitlab_check.failure(odd), {"status": 502, "code": "api.unexpected"})
        self.assertEqual(gitlab_check.failure(gitlab_check.Failed("oauth.pkce")), {"status": None, "code": "oauth.pkce"})
        self.assertEqual(gitlab_check.failure(gitlab_check.Failed("code=" + SECRETS["GITLAB_TOKEN"])),
                         {"status": None, "code": "check.failed"})
        self.assertEqual(gitlab_check.failure(ValueError(SECRETS["GITLAB_APP_SECRET"])), {"status": None, "code": "error.valueerror"})

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
                self.assertEqual(redact.leaks(written, redact.secrets()), [])


class RedactTest(unittest.TestCase):
    LOG = (
        "app-1  | started on port 8080\n"
        "app-1  | GET /api/v1/git/oauth/callback?code=abc123&state=xyz 303\n"
        "gitlab-1  | Started POST \"/oauth/token\" with access_token=xyz\n"
        f"gitlab-1  | personal token {RUN_SECRETS['GITLAB_CHECK_TOKEN']}\n"
        f"app-1  | the key {RUN_SECRETS['GITLAB_CHECK_SECRET_KEY']} was read\n"
        "gitlab-1  | Authorization: Bearer abc\n"
        "db-1  | database system is ready\n"
    )

    def test_diagnostics_lose_every_secret_and_sensitive_line(self):
        with mock.patch.dict(os.environ, RUN_SECRETS):
            cleaned = "".join(redact.clean(self.LOG.splitlines(keepends=True), redact.secrets()))
            self.assertEqual(cleaned, "app-1  | started on port 8080\napp-1  | the key [entfernt] was read\n"
                                      "db-1  | database system is ready\n")
            self.assertEqual(redact.leaks(cleaned, redact.secrets()), [])
            self.assertTrue(redact.leaks(self.LOG, redact.secrets()))

    def test_the_check_fails_on_a_leak_and_names_no_secret(self):
        with tempfile.TemporaryDirectory() as folder:
            leaked = Path(folder) / "diagnostics.txt"
            leaked.write_text(f"value {RUN_SECRETS['GITLAB_CHECK_OTP']}\n")
            clean = Path(folder) / "gitlab-run.json"
            clean.write_text('{"passed": true}\n')
            environment = {**os.environ, **RUN_SECRETS}
            run = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "git" / "redact.py"), "--check", str(clean), str(leaked)],
                                 env=environment, capture_output=True, text=True, check=False)
            self.assertEqual(run.returncode, 1)
            self.assertNotIn(RUN_SECRETS["GITLAB_CHECK_OTP"], run.stderr + run.stdout)
            run = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "git" / "redact.py"), "--check", str(clean)],
                                 env=environment, capture_output=True, text=True, check=False)
            self.assertEqual(run.returncode, 0)


if __name__ == "__main__":
    unittest.main()
