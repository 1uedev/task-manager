#!/usr/bin/env bash
# The check of a release tag (TASK-132, DEC-162, rule 2; TASK-136, finding F7 of the review of TASK-132). A version is
# published only for a tag vMAJOR.MINOR.PATCH on a commit of main. Prints the version without "v" when both hold and
# refuses otherwise; publishes nothing itself. tests/test_release_tools.py proves allowed and refused cases without a
# release. The protection against a tag that is not Holger's is not here: the tag ruleset of the repository and the
# environment "release" with Holger as required reviewer (docs/INSTALL.md, "Veröffentlichen einer Version").
#
#   check-tag.sh TAG COMMIT [MAIN_REF]     MAIN_REF defaults to origin/main
set -euo pipefail
tag=${1:?tag}
commit=${2:?commit}
main=${3:-origin/main}
if ! [[ "$tag" =~ ^v(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})$ ]]; then
  echo "Refused: the tag is not a version vMAJOR.MINOR.PATCH; nothing is published." >&2
  exit 1
fi
if ! git rev-parse --verify --quiet "$main^{commit}" >/dev/null; then
  echo "Refused: $main is not known; fetch main first." >&2
  exit 1
fi
if ! git merge-base --is-ancestor "$commit" "$main"; then
  echo "Refused: the tag $tag is not on main; nothing is published." >&2
  exit 1
fi
echo "${tag#v}"
