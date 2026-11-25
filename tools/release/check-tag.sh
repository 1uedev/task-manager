#!/usr/bin/env bash
# The check of a release tag (TASK-132, DEC-162, rule 2; TASK-136, F7 of the review of TASK-132; TASK-139, F1 of the
# review of TASK-137). A version is published only for a tag vMAJOR.MINOR.PATCH on a commit that lies in the history of
# the fixed reference commit of main. Prints "VERSION COMMIT" (version without "v", the commit the tag names) when both
# hold and refuses otherwise; publishes nothing itself and runs no code of the tagged commit.
#
#   check-tag.sh TAG REFERENCE
#
# TAG must be fetched as refs/tags/TAG. REFERENCE is the full hash of the commit of main the workflow "Release" runs on
# (its GITHUB_SHA when started on main): the reference point is fixed at the start of the run, a commit that reaches main
# later is refused, and a moving name such as origin/main is not accepted. The workflow and this script come from that
# commit of main, never from the tag. tests/test_release_tools.py proves allowed and refused cases without a release.
# The protection against a tag or a run that is not Holger's is not here: the tag ruleset and the environment "release"
# with Holger as required reviewer (docs/INSTALL.md, "Veröffentlichen einer Version").
set -euo pipefail
tag=${1:?tag}
reference=${2:?full hash of the reference commit of main}
if ! [[ "$tag" =~ ^v(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})$ ]]; then
  echo "Refused: the tag is not a version vMAJOR.MINOR.PATCH; nothing is published." >&2
  exit 1
fi
if ! [[ "$reference" =~ ^[0-9a-f]{40}$ ]] || ! git cat-file -e "$reference^{commit}" 2>/dev/null; then
  echo "Refused: the reference must be the full hash of a known commit of main; nothing is published." >&2
  exit 1
fi
if ! commit=$(git rev-parse --verify --quiet "refs/tags/$tag^{commit}"); then
  echo "Refused: the tag $tag is not known; nothing is published." >&2
  exit 1
fi
if ! git merge-base --is-ancestor "$commit" "$reference"; then
  echo "Refused: the tag $tag is not on main at the reference commit; nothing is published." >&2
  exit 1
fi
echo "${tag#v} $commit"
