#!/usr/bin/env python3
"""The older version of the update check (TASK-136, finding F9 of the review of TASK-132).

The base is the newest published release, never a draft or a prerelease, whose tag is a version vMAJOR.MINOR.PATCH, lies
on the history of the checked commit and is not that commit itself, so that the check never updates a version to itself.
It must carry the files an operator uses: the digests, the operations template and the checksums; the check then installs
exactly those references. Only while no release exists does the check start from a fixed earlier commit of main.

  choose_base.py RELEASES_JSON HEAD FIXED_COMMIT

RELEASES_JSON is the answer of the GitHub API for the releases of the repository. Prints three lines key=value for
$GITHUB_OUTPUT: kind (release or commit), ref (the tag or the commit) and version (without "v", empty for a commit).
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from typing import Callable

VERSION = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def git_commit(ref: str) -> str | None:
    result = subprocess.run(["git", "rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}"], capture_output=True, text=True)
    return result.stdout.strip() or None


def git_is_ancestor(commit: str, head: str) -> bool:
    return subprocess.run(["git", "merge-base", "--is-ancestor", commit, head]).returncode == 0


def choose(releases: list[dict], head: str, fixed: str, commit_of: Callable[[str], str | None] = git_commit,
           is_ancestor: Callable[[str, str], bool] = git_is_ancestor) -> dict[str, str]:
    candidates = []
    for release in releases:
        tag = release.get("tag_name", "")
        match = VERSION.fullmatch(tag)
        if release.get("draft") or release.get("prerelease") or not match:
            continue
        candidates.append((tuple(int(part) for part in match.groups()), tag, release))
    for _, tag, release in sorted(candidates, reverse=True):
        commit = commit_of(tag)
        if commit is None or commit == head or not is_ancestor(commit, head):
            continue
        version = tag[1:]
        names = {asset.get("name") for asset in release.get("assets", [])}
        needed = {f"digests-{version}.env", f"task-manager-{version}-deploy.tar.gz", f"SHA256SUMS-{version}"}
        if not needed <= names:
            raise SystemExit(f"the release {tag} lacks {', '.join(sorted(needed - names))}; it cannot be the base")
        return {"kind": "release", "ref": tag, "version": version}
    return {"kind": "commit", "ref": fixed, "version": ""}


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    with open(argv[1], encoding="utf-8") as source:
        releases = json.load(source)
    chosen = choose(releases, argv[2], argv[3])
    for key, value in chosen.items():
        print(f"{key}={value}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
