"""The choice of the older version of the update check (TASK-136, finding F9 of the review of TASK-132; TASK-137, N8)."""

from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "update"))
import choose_base  # noqa: E402

HEAD = "h" * 40
FIXED = "f" * 40
COMMITS = {"v1.0.0": "a" * 40, "v1.1.0": "b" * 40, "v1.2.0": HEAD, "v2.0.0": "c" * 40, "v1.10.0": "d" * 40}
ANCESTORS = {"a" * 40, "b" * 40, "d" * 40}


def release(tag, draft=False, prerelease=False, complete=True):
    version = tag[1:]
    assets = [f"digests-{version}.env", f"task-manager-{version}-deploy.tar.gz", f"SHA256SUMS-{version}"]
    return {"tag_name": tag, "draft": draft, "prerelease": prerelease,
            "assets": [{"name": name} for name in (assets if complete else assets[:1])]}


def choose(releases):
    return choose_base.choose(releases, HEAD, FIXED, commit_of=COMMITS.get, is_ancestor=lambda c, h: c in ANCESTORS)


class ChooseBaseTest(unittest.TestCase):
    def test_without_a_release_the_fixed_commit_is_the_base(self):
        self.assertEqual({"kind": "commit", "ref": FIXED, "commit": FIXED, "version": ""}, choose([]))

    def test_drafts_prereleases_and_other_tags_alone_still_allow_the_fixed_commit(self):
        chosen = choose([release("v1.1.0", draft=True), release("v1.10.0", prerelease=True), release("v1.9")])
        self.assertEqual("commit", chosen["kind"])

    def test_a_published_release_on_the_target_itself_is_no_reason_for_the_fixed_commit(self):
        # The counter test of N8: exactly one published release, and it is the checked commit.
        with self.assertRaises(choose_base.NoSuitableRelease) as refused:
            choose([release("v1.2.0")])
        self.assertIn("v1.2.0 (this commit itself)", str(refused.exception))

    def test_releases_outside_the_history_or_without_tag_stop_the_check(self):
        with self.assertRaises(choose_base.NoSuitableRelease) as refused:
            choose([release("v2.0.0"), release("v3.0.0")])
        self.assertIn("v2.0.0 (not in the history of this commit)", str(refused.exception))
        self.assertIn("v3.0.0 (tag not found)", str(refused.exception))

    def test_the_command_ends_with_3_without_a_suitable_release(self):
        import json, tempfile
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as source:
            json.dump([release("v1.2.0")], source)
        original = choose_base.choose
        choose_base.choose = lambda releases, head, fixed: original(
            releases, head, fixed, commit_of=COMMITS.get, is_ancestor=lambda c, h: c in ANCESTORS)
        try:
            self.assertEqual(3, choose_base.main(["choose_base.py", source.name, HEAD, FIXED]))
        finally:
            choose_base.choose = original
            Path(source.name).unlink()

    def test_the_newest_published_release_by_version_is_the_base(self):
        chosen = choose([release("v1.0.0"), release("v1.10.0"), release("v1.1.0")])
        self.assertEqual({"kind": "release", "ref": "v1.10.0", "commit": "d" * 40, "version": "1.10.0"}, chosen)

    def test_the_target_itself_is_never_the_base(self):
        # v1.2.0 is the checked commit: the update goes from the release before it.
        self.assertEqual("v1.1.0", choose([release("v1.1.0"), release("v1.2.0")])["ref"])

    def test_a_release_outside_the_history_drafts_prereleases_and_other_tags_are_skipped(self):
        chosen = choose([release("v1.0.0"), release("v2.0.0"), release("v1.1.0", draft=True),
                         release("v1.10.0", prerelease=True), release("v1.9"), {"tag_name": "nightly", "assets": []}])
        self.assertEqual("v1.0.0", chosen["ref"])

    def test_a_release_without_its_files_refuses_the_check(self):
        with self.assertRaises(SystemExit):
            choose([release("v1.1.0", complete=False)])


if __name__ == "__main__":
    unittest.main()
