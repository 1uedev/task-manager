"""The job and file checks of the update probe (TASK-139, finding F8 of the review of TASK-137)."""

from pathlib import Path
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "update"))
import probe  # noqa: E402

FINGERPRINT = ROOT / "tools" / "update" / "files-fingerprint.sh"
KIND = "recurring.files.cleanup"
SINCE = probe.moment("2026-11-25T10:00:00.000000Z")


def job(state, created, finished, kind=KIND):
    return {"kind": kind, "state": state, "createdAt": created, "finishedAt": finished}


class FreshJobTest(unittest.TestCase):
    def test_only_a_run_created_and_finished_after_the_start_of_the_phase_counts(self):
        self.assertEqual("succeeded", probe.fresh_outcome(
            [job("succeeded", "2026-11-25T10:00:01Z", "2026-11-25T10:00:02Z")], KIND, SINCE))
        self.assertEqual("succeeded", probe.fresh_outcome(
            [job("succeeded", "2026-11-25T11:00:01+01:00", "2026-11-25T11:00:02+01:00")], KIND, SINCE))

    def test_an_old_success_with_a_failing_current_worker_does_not_pass(self):
        # The database before the update or the restored point carries an old success; the current worker fails the job.
        old = job("succeeded", "2026-11-24T09:00:00Z", "2026-11-24T09:00:01Z")
        self.assertIsNone(probe.fresh_outcome([old], KIND, SINCE))
        self.assertEqual("failed", probe.fresh_outcome(
            [job("failed", "2026-11-25T10:00:01Z", "2026-11-25T10:00:05Z"), old], KIND, SINCE))
        # A failure of the phase wins also over a success of the same phase.
        self.assertEqual("failed", probe.fresh_outcome(
            [job("failed", "2026-11-25T10:00:01Z", "2026-11-25T10:00:05Z"),
             job("succeeded", "2026-11-25T10:00:06Z", "2026-11-25T10:00:07Z")], KIND, SINCE))

    def test_other_kinds_running_jobs_and_runs_created_before_do_not_count(self):
        self.assertIsNone(probe.fresh_outcome([
            job("succeeded", "2026-11-25T10:00:01Z", "2026-11-25T10:00:02Z", kind="recurring.ai.cleanup"),
            job("running", "2026-11-25T10:00:01Z", None),
            job("succeeded", "2026-11-25T09:59:59Z", "2026-11-25T10:00:02Z"),
            "no job", {}], KIND, SINCE))


class FilesFingerprintTest(unittest.TestCase):
    def setUp(self):
        self.work = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.work)
        self.files = self.work / "content"
        (self.files / "ab").mkdir(parents=True)
        (self.files / "ab" / "one").write_bytes(b"x" * 10)
        (self.files / "two").write_bytes(b"y" * 5)

    def run_script(self, failing=None):
        environment = dict(os.environ)
        if failing:
            # A stand in for one tool that fails as a read of a damaged file store would.
            bin_dir = self.work / "bin"
            bin_dir.mkdir(exist_ok=True)
            tool = bin_dir / failing
            tool.write_text("#!/bin/sh\necho synthetic read failure >&2\nexit 1\n", encoding="utf-8")
            tool.chmod(tool.stat().st_mode | stat.S_IXUSR)
            environment["PATH"] = f"{bin_dir}{os.pathsep}{environment['PATH']}"
        return subprocess.run(["bash", str(FINGERPRINT), str(self.files)], capture_output=True, text=True, env=environment)

    def test_count_bytes_and_sum_of_every_file(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        count, size, digest = result.stdout.split()
        self.assertEqual(("2", "15"), (count, size))
        self.assertRegex(digest, r"^[0-9a-f]{64}$")

    def test_every_failed_read_ends_the_script_without_output(self):
        for failing in ("find", "sort", "sha256sum", "cat", "wc"):
            with self.subTest(failing=failing):
                result = self.run_script(failing)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertEqual("", result.stdout)


if __name__ == "__main__":
    unittest.main()
