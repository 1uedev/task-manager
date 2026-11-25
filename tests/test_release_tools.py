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
    "TASK_MANAGER_IMAGE_DIGEST": "sha256:" + "a" * 64,
    "TASK_MANAGER_BACKUP_IMAGE_DIGEST": "sha256:" + "b" * 64,
}


def git(directory, *arguments):
    environment = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.org", GIT_COMMITTER_NAME="t",
                       GIT_COMMITTER_EMAIL="t@example.org", GIT_CONFIG_GLOBAL=os.devnull)
    return subprocess.run(["git", *arguments], cwd=directory, env=environment, check=True, capture_output=True,
                          text=True).stdout.strip()


class CheckTagTest(unittest.TestCase):
    """check-tag.sh against a fixed reference commit of main (TASK-139, finding F1 of the review of TASK-137)."""

    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.mkdtemp()
        git(cls.directory, "init", "-q", "-b", "main")
        git(cls.directory, "commit", "-q", "--allow-empty", "-m", "first")
        cls.on_main = git(cls.directory, "rev-parse", "HEAD")
        # The reference: main when the run starts.
        cls.reference = cls.on_main
        # A commit that reaches main only after the start of the run.
        git(cls.directory, "commit", "-q", "--allow-empty", "-m", "later")
        cls.later = git(cls.directory, "rev-parse", "HEAD")
        git(cls.directory, "checkout", "-q", "-b", "side", cls.on_main)
        git(cls.directory, "commit", "-q", "--allow-empty", "-m", "side")
        cls.off_main = git(cls.directory, "rev-parse", "HEAD")
        git(cls.directory, "checkout", "-q", "main")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.directory)

    def check(self, tag, commit, reference=None):
        if commit is not None and re.fullmatch(r"v\d+\.\d+\.\d+", tag):
            git(self.directory, "tag", "-f", tag, commit)
        return subprocess.run(["bash", str(CHECK_TAG), tag, reference or self.reference], cwd=self.directory,
                              capture_output=True, text=True)

    def test_a_version_on_main_is_allowed(self):
        for tag, version in (("v1.0.0", "1.0.0"), ("v0.12.3", "0.12.3"), ("v10.0.100", "10.0.100")):
            result = self.check(tag, self.on_main)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(f"{version} {self.on_main}", result.stdout.strip())

    def test_other_tags_are_refused(self):
        for tag in ("v1.0", "1.0.0", "v1.0.0-rc1", "v01.0.0", "v1.0.0 ", "vx.y.z", "v1.0.0\nv2.0.0", "release"):
            result = self.check(tag, None)
            self.assertEqual(1, result.returncode, tag)
            self.assertIn("not a version", result.stderr)
            self.assertEqual("", result.stdout)

    def test_a_tag_outside_main_is_refused(self):
        result = self.check("v1.0.1", self.off_main)
        self.assertEqual(1, result.returncode)
        self.assertIn("not on main at the reference commit", result.stderr)

    def test_a_commit_that_reaches_main_after_the_start_is_refused(self):
        # The reference point is fixed at the start of the run: the later commit is on main now, but not at the reference.
        result = self.check("v1.0.2", self.later)
        self.assertEqual(1, result.returncode)
        self.assertIn("not on main at the reference commit", result.stderr)
        self.assertEqual(0, self.check("v1.0.2", self.later, reference=self.later).returncode)

    def test_a_moving_or_unknown_reference_is_refused(self):
        git(self.directory, "tag", "-f", "v1.0.3", self.on_main)
        for reference in ("main", "origin/main", "HEAD", self.on_main[:12], "0" * 40):
            with self.subTest(reference=reference):
                result = subprocess.run(["bash", str(CHECK_TAG), "v1.0.3", reference], cwd=self.directory,
                                        capture_output=True, text=True)
                self.assertEqual(1, result.returncode)
                self.assertIn("full hash of a known commit", result.stderr)

    def test_an_unknown_tag_is_refused(self):
        result = subprocess.run(["bash", str(CHECK_TAG), "v9.9.9", self.reference], cwd=self.directory,
                                capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn("not known", result.stderr)

    def test_the_release_workflow_uses_the_check_and_the_protected_environment(self):
        workflow = (ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")
        self.assertIn("tools/release/check-tag.sh", workflow)
        self.assertRegex(workflow, r"(?m)^    environment: release$")
        self.assertIn("tools/release/bundle.py", workflow)
        self.assertIn("tools/release/check-package.sh", workflow)

    def test_the_check_runs_from_main_before_any_code_of_the_tag_and_binds_the_build(self):
        # F1 of the review of TASK-137: no push trigger (that would run this file from the tag); the run starts on main,
        # the check job checks out exactly that commit, fetches only the git objects of the tag and compares against
        # GITHUB_SHA; a run on another ref is refused. The release job waits for it and builds exactly the checked commit.
        text = (ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")
        trigger = text[text.index("\non:\n"):text.index("\npermissions:\n")]
        self.assertIn("workflow_dispatch:", trigger)
        self.assertNotIn("push", trigger)
        self.assertNotIn("GITHUB_REF_NAME", text)
        jobs = text[text.index("\njobs:\n"):]
        check = jobs[jobs.index("\n  check:\n"):jobs.index("\n  release:\n")]
        release = jobs[jobs.index("\n  release:\n"):]
        self.assertIn("\n    if: github.ref == 'refs/heads/main'\n", check)
        self.assertRegex(check, r"\n    permissions:\n      contents: read\n    outputs:")
        self.assertNotIn("environment:", check)
        self.assertIn("ref: ${{ github.sha }}", check)
        self.assertIn('[ "$GITHUB_REF" = refs/heads/main ]', check)
        self.assertIn('check-tag.sh "$TAG" "$GITHUB_SHA"', check)
        self.assertIn('TAG: ${{ inputs.tag }}', check)
        self.assertNotIn("${{ inputs.tag }}\"", check)
        # Before check-tag.sh only the checkout of main and the fetch of the tag objects happen.
        before = check[:check.index("check-tag.sh")]
        for runs_tag_code in ("docker", "python", "sh tools", "bash tools/release/bundle", "make", "./"):
            self.assertNotIn(runs_tag_code, before.replace("tools/release/check-tag.sh", ""))
        self.assertIn("\n    needs: check\n", release)
        self.assertIn("\n    environment: release\n", release)
        self.assertIn("ref: ${{ needs.check.outputs.commit }}", release)
        self.assertNotIn("check-tag.sh", release)
        self.assertNotIn("inputs.tag", release)


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
        self.assertIn("TASK_MANAGER_IMAGE_DIGEST=" + DIGESTS["TASK_MANAGER_IMAGE_DIGEST"] + "\n", example)
        self.assertIn("TASK_MANAGER_BACKUP_IMAGE_DIGEST=" + DIGESTS["TASK_MANAGER_BACKUP_IMAGE_DIGEST"] + "\n", example)
        for guide in ("INSTALL.md", "BACKUP_RESTORE.md", "OPERATIONS.md"):
            self.assertTrue((result / "docs" / guide).is_file(), guide)
        # The checks of the repository stay in the repository; the guide links the restore check at the tag (N11).
        self.assertFalse((result / "tools").exists())
        self.assertIn("/blob/v1.2.3/tools/restore/restore-check.sh", (result / "docs" / "BACKUP_RESTORE.md").read_text(encoding="utf-8"))
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

    def test_a_tag_or_a_whole_reference_is_refused(self):
        for value in ("1.2.3", "ghcr.io/1uedev/task-manager@sha256:" + "a" * 64, "sha256:" + "a" * 63, "sha512:" + "a" * 64):
            with self.assertRaises(bundle.BundleError):
                bundle.build("1.2.3", dict(DIGESTS, TASK_MANAGER_IMAGE_DIGEST=value),
                             self.target / value.replace("/", "_").replace(":", "_"))

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


class PackageCheckTest(unittest.TestCase):
    """tools/release/check-package.sh against a bundle in a fresh directory (TASK-137, N11)."""

    def setUp(self):
        if shutil.which("docker") is None or subprocess.run(["docker", "compose", "version"], capture_output=True).returncode:
            self.skipTest("needs Docker Compose")
        self.target = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.target)
        self.built = bundle.build("1.2.3", DIGESTS, self.target)

    def check(self):
        archive = self.target / "package.tar.gz"
        subprocess.run(["tar", "-C", str(self.target), "-czf", str(archive), self.built.name], check=True)
        return subprocess.run(["sh", str(ROOT / "tools" / "release" / "check-package.sh"), str(archive), "1.2.3"],
                              capture_output=True, text=True)

    def test_the_unpacked_package_resolves_with_and_without_the_proxy_file(self):
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("template resolved with and without the proxy file", result.stdout)

    def test_a_missing_mounted_file_refuses_the_package(self):
        (self.built / "deploy" / "db-roles.sql").unlink()
        result = self.check()
        self.assertEqual(1, result.returncode)
        self.assertIn("db mounts db-roles.sql, which the package lacks", result.stderr)


if __name__ == "__main__":
    unittest.main()
