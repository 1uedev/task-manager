#!/usr/bin/env bash
# The fingerprint of a file store for the update check (TASK-139, finding F8 of the review of TASK-137): prints
# "COUNT BYTES SUM" over every file below DIR, where SUM is the SHA-256 of the sorted list of "SHA-256  path" lines. Every
# read is a step of its own whose failure ends the script with a status other than 0 and without output; a file that
# cannot be read never turns into a smaller count or sum. Tested in tests/test_update_probe.py.
#
#   files-fingerprint.sh DIR
set -euo pipefail
cd "${1:?directory of the file store}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
find . -type f -print0 > "$work/found"
sort -z < "$work/found" > "$work/list"
xargs -0r sha256sum < "$work/list" > "$work/sums"
count=$(wc -l < "$work/sums")
listed=$(tr -cd '\0' < "$work/list" | wc -c)
[ "$count" -eq "$listed" ] || { echo "the file store lists $listed files, but $count were read" >&2; exit 1; }
# pipefail: a failed cat fails the substitution, and set -e ends the script at the assignment.
bytes=$(xargs -0r cat < "$work/list" | wc -c)
sum=$(sha256sum < "$work/sums")
printf '%s %s %s\n' "$count" "$bytes" "${sum%% *}"
