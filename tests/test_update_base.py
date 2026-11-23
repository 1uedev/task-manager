"""The choice of the older version of the update check (TASK-136, finding F9 of the review of TASK-132)."""

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
        self.assertEqual({"kind": "commit", "ref": FIXED, "version": ""}, choose([]))

    def test_the_newest_published_release_by_version_is_the_base(self):
        chosen = choose([release("v1.0.0"), release("v1.10.0"), release("v1.1.0")])
        self.assertEqual({"kind": "release", "ref": "v1.10.0", "version": "1.10.0"}, chosen)

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
