"""The checks of a release without publishing anything (TASK-136, findings F7 and F12 of the review of TASK-132)."""

from pathlib import Path
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "release"))
import bundle  # noqa: E402

CHECK_TAG = ROOT / "tools" / "release" / "check-tag.sh"
DIGESTS = {
    "TASK_MANAGER_IMAGE": "ghcr.io/1uedev/task-manager@sha256:" + "a" * 64,
    "TASK_MANAGER_BACKUP_IMAGE": "ghcr.io/1uedev/task-manager-backup@sha256:" + "b" * 64,
}


def git(directory, *arguments):
    environment = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.org", GIT_COMMITTER_NAME="t",
                       GIT_COMMITTER_EMAIL="t@example.org", GIT_CONFIG_GLOBAL=os.devnull)
    return subprocess.run(["git", *arguments], cwd=directory, env=environment, check=True, capture_output=True,
                          text=True).stdout.strip()


class CheckTagTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.mkdtemp()
        git(cls.directory, "init", "-q", "-b", "main")
        git(cls.directory, "commit", "-q", "--allow-empty", "-m", "first")
        cls.on_main = git(cls.directory, "rev-parse", "HEAD")
        git(cls.directory, "checkout", "-q", "-b", "side")
        git(cls.directory, "commit", "-q", "--allow-empty", "-m", "side")
        cls.off_main = git(cls.directory, "rev-parse", "HEAD")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.directory)

    def check(self, tag, commit):
        return subprocess.run(["bash", str(CHECK_TAG), tag, commit, "main"], cwd=self.directory, capture_output=True,
                              text=True)

    def test_a_version_on_main_is_allowed(self):
        for tag, version in (("v1.0.0", "1.0.0"), ("v0.12.3", "0.12.3"), ("v10.0.100", "10.0.100")):
            result = self.check(tag, self.on_main)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(version, result.stdout.strip())

    def test_other_tags_are_refused(self):
        for tag in ("v1.0", "1.0.0", "v1.0.0-rc1", "v01.0.0", "v1.0.0 ", "vx.y.z", "v1.0.0\nv2.0.0", "release"):
            result = self.check(tag, self.on_main)
            self.assertEqual(1, result.returncode, tag)
            self.assertIn("not a version", result.stderr)
            self.assertEqual("", result.stdout)

    def test_a_tag_outside_main_is_refused(self):
        result = self.check("v1.0.0", self.off_main)
        self.assertEqual(1, result.returncode)
        self.assertIn("not on main", result.stderr)

    def test_an_unknown_main_is_refused(self):
        result = subprocess.run(["bash", str(CHECK_TAG), "v1.0.0", self.on_main, "origin/main"], cwd=self.directory,
                                capture_output=True, text=True)
        self.assertEqual(1, result.returncode)

    def test_the_release_workflow_uses_the_check_and_the_protected_environment(self):
        workflow = (ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")
        self.assertIn("tools/release/check-tag.sh", workflow)
        self.assertRegex(workflow, r"(?m)^    environment: release$")
        self.assertIn("tools/release/bundle.py", workflow)


class BundleTest(unittest.TestCase):
    def setUp(self):
        self.target = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.target)

    def test_the_bundle_holds_every_mounted_file_and_every_link_works(self):
        result = bundle.build("1.2.3", DIGESTS, self.target)
        for name in bundle.DEPLOY:
            self.assertTrue((result / "deploy" / name).is_file(), name)
        example = (result / "deploy" / ".env.example").read_text(encoding="utf-8")
        self.assertIn("TASK_MANAGER_IMAGE=" + DIGESTS["TASK_MANAGER_IMAGE"] + "\n", example)
        self.assertIn("TASK_MANAGER_BACKUP_IMAGE=" + DIGESTS["TASK_MANAGER_BACKUP_IMAGE"] + "\n", example)
        for guide in ("INSTALL.md", "BACKUP_RESTORE.md", "OPERATIONS.md"):
            self.assertTrue((result / "docs" / guide).is_file(), guide)
        self.assertTrue((result / "tools" / "restore" / "restore-check.sh").is_file())
        checked, rewritten = 0, 0
        for guide in (result / "docs").glob("*.md"):
            for target in re.findall(r"\]\(([^)\s]+)\)", guide.read_text(encoding="utf-8")):
                path = target.split("#", 1)[0]
                if not path or "://" in path or path.startswith("mailto:"):
                    rewritten += bool(re.match(r"https://github\.com/1uedev/task-manager/(blob|tree)/v1\.2\.3/", path))
                    continue
                self.assertTrue((guide.parent / path).exists(), f"{guide.name}: {target}")
                checked += 1
        self.assertGreater(checked, 10)
        # Links to the source of the demonstration lead to the repository at the tag.
        self.assertGreater(rewritten, 0)
        self.assertIn("/blob/v1.2.3/tools/demo/demo.py", (result / "docs" / "DEMO.md").read_text(encoding="utf-8"))

    def test_a_tag_or_a_foreign_registry_is_refused(self):
        for value in ("ghcr.io/1uedev/task-manager:1.2.3", "docker.io/1uedev/task-manager@sha256:" + "a" * 64):
            with self.assertRaises(bundle.BundleError):
                bundle.build("1.2.3", dict(DIGESTS, TASK_MANAGER_IMAGE=value), self.target / value.replace("/", "_"))

    def test_a_dead_link_refuses_the_bundle(self):
        root = self.target / "repository"
        shutil.copytree(ROOT / "deploy", root / "deploy")
        shutil.copytree(ROOT / "tools" / "restore", root / "tools" / "restore")
        (root / "docs").mkdir()
        for guide in bundle.GUIDES:
            (root / "docs" / guide).write_text("# " + guide + "\n", encoding="utf-8")
        (root / "docs" / "OPERATIONS.md").write_text("See [the plan](../PLAN.md).\n", encoding="utf-8")
        with self.assertRaisesRegex(bundle.BundleError, "does not exist"):
            bundle.build("1.2.3", DIGESTS, self.target / "out", root)


if __name__ == "__main__":
    unittest.main()
