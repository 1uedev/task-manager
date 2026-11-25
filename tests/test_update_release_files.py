"""The check of the files of an older release in the update check (TASK-137, finding N8 of the review of TASK-136).

Builds a synthetic set of release files the way the workflow "Release" builds them (bundle.py, tar, sha256sum over the
directory) and runs tools/update/release-files.sh against it and against damaged copies.
"""

from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "release"))
import bundle  # noqa: E402

SCRIPT = ROOT / "tools" / "update" / "release-files.sh"
VERSION = "1.2.3"
APP = "sha256:" + "a" * 64
BACKUP = "sha256:" + "b" * 64


class ReleaseFilesTest(unittest.TestCase):
    def setUp(self):
        self.work = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.work)
        self.release = self.work / "release"
        self.release.mkdir()
        digests = {"TASK_MANAGER_IMAGE_DIGEST": APP, "TASK_MANAGER_BACKUP_IMAGE_DIGEST": BACKUP}
        (self.release / f"digests-{VERSION}.env").write_text(
            "".join(f"{key}={value}\n" for key, value in digests.items()), encoding="utf-8")
        built = bundle.build(VERSION, digests, self.work / "bundle")
        with tarfile.open(self.release / f"task-manager-{VERSION}-deploy.tar.gz", "w:gz") as archive:
            archive.add(built, arcname=built.name)
        (self.release / "sbom.spdx.json").write_text("{}", encoding="utf-8")
        self.sums()

    def sums(self, skip=()):
        names = sorted(path.name for path in self.release.iterdir() if path.is_file() and not path.name.startswith("SHA"))
        lines = subprocess.run(["sha256sum", *[name for name in names if name not in skip]], cwd=self.release, check=True,
                               capture_output=True, text=True).stdout
        (self.release / f"SHA256SUMS-{VERSION}").write_text(lines, encoding="utf-8")

    def run_check(self):
        return subprocess.run(["sh", str(SCRIPT), str(self.release), VERSION], capture_output=True, text=True)

    def test_a_complete_release_gives_the_references_and_the_template(self):
        result = self.run_check()
        self.assertEqual(0, result.returncode, result.stderr)
        lines = dict(line.split("=", 1) for line in result.stdout.splitlines())
        self.assertEqual(f"ghcr.io/1uedev/task-manager@{APP}", lines["UPDATE_CHECK_OLD_IMAGE"])
        self.assertEqual(f"ghcr.io/1uedev/task-manager-backup@{BACKUP}", lines["UPDATE_CHECK_OLD_BACKUP_IMAGE"])
        self.assertTrue((Path(lines["UPDATE_CHECK_OLD_TEMPLATE"]) / "compose.yaml").is_file())

    def test_a_missing_checksum_line_is_refused(self):
        for name in (f"digests-{VERSION}.env", f"task-manager-{VERSION}-deploy.tar.gz"):
            with self.subTest(name=name):
                self.sums(skip=(name,))
                result = self.run_check()
                self.assertEqual(1, result.returncode)
                self.assertIn(f"0 lines for {name}", result.stderr)

    def test_a_changed_file_is_refused(self):
        with open(self.release / f"digests-{VERSION}.env", "a", encoding="utf-8") as digests:
            digests.write("# changed\n")
        result = self.run_check()
        self.assertEqual(1, result.returncode)
        self.assertIn("does not match", result.stderr)

    def test_the_counted_line_is_checked_in_text_and_binary_form(self):
        # F3 of the review of TASK-137: a correct line of one file and a wrong line of the other in the other form.
        digests, package = f"digests-{VERSION}.env", f"task-manager-{VERSION}-deploy.tar.gz"
        wrong = "0" * 64
        for valid_binary in (False, True):
            for wrong_name in (digests, package):
                with self.subTest(valid_binary=valid_binary, wrong=wrong_name):
                    lines = []
                    for name in (digests, package):
                        digest = subprocess.run(["sha256sum", name], cwd=self.release, check=True, capture_output=True,
                                                text=True).stdout.split()[0]
                        binary = valid_binary if name != wrong_name else not valid_binary
                        lines.append(f"{wrong if name == wrong_name else digest} {'*' if binary else ' '}{name}\n")
                    (self.release / f"SHA256SUMS-{VERSION}").write_text("".join(lines), encoding="utf-8")
                    result = self.run_check()
                    self.assertEqual(1, result.returncode, result.stdout)
                    self.assertIn(f"the checksum of {wrong_name} in SHA256SUMS-{VERSION} does not match", result.stderr)
                    self.assertFalse((self.release / f"task-manager-{VERSION}").exists())

    def test_binary_lines_with_correct_checksums_are_accepted(self):
        lines = subprocess.run(["sha256sum", "--binary", f"digests-{VERSION}.env", f"task-manager-{VERSION}-deploy.tar.gz"],
                               cwd=self.release, check=True, capture_output=True, text=True).stdout
        self.assertIn(" *digests", lines)
        (self.release / f"SHA256SUMS-{VERSION}").write_text(lines, encoding="utf-8")
        result = self.run_check()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_a_tag_instead_of_a_digest_is_refused(self):
        (self.release / f"digests-{VERSION}.env").write_text(
            f"TASK_MANAGER_IMAGE_DIGEST=v{VERSION}\nTASK_MANAGER_BACKUP_IMAGE_DIGEST={BACKUP}\n", encoding="utf-8")
        self.sums()
        result = self.run_check()
        self.assertEqual(1, result.returncode)
        self.assertIn("TASK_MANAGER_IMAGE_DIGEST in digests-1.2.3.env is not a digest", result.stderr)


if __name__ == "__main__":
    unittest.main()
