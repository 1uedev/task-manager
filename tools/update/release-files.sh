#!/bin/sh
# Checks the files of an older release for the update check (TASK-137, finding N8 of the review of TASK-136) and prints
# the references the check installs.
#
#   release-files.sh DIR VERSION
#
# DIR holds digests-VERSION.env, task-manager-VERSION-deploy.tar.gz and SHA256SUMS-VERSION as the release offers them.
# SHA256SUMS must carry exactly one line for each of the two files and exactly that line must match; a missing line is
# a refusal, not a skipped file. The package is unpacked into DIR, the digests must be digests of SHA-256. Prints for $GITHUB_ENV:
#   UPDATE_CHECK_OLD_IMAGE, UPDATE_CHECK_OLD_BACKUP_IMAGE (registry and digest) and UPDATE_CHECK_OLD_TEMPLATE.
# Ends with 1 and a reason on any deviation.
set -eu
dir=${1:?directory of the release files}
version=${2:?version without v}
digests="digests-$version.env"
package="task-manager-$version-deploy.tar.gz"
sums="SHA256SUMS-$version"
refuse() { echo "release $version refused: $1" >&2; exit 1; }
cd "$dir"
for file in "$digests" "$package" "$sums"; do
  [ -f "$file" ] || refuse "$file is missing"
done
# Each file is checked against exactly the one line that was counted for it: text ("HASH  name") and binary
# ("HASH *name") lines count and are checked the same way, by comparing the hash of the line with the hash of the file
# (TASK-139, finding F3 of the review of TASK-137).
for file in "$digests" "$package"; do
  lines=$(awk -v name="$file" '$2 == name || $2 == "*" name' "$sums" | wc -l)
  [ "$lines" -eq 1 ] || refuse "$sums has $lines lines for $file instead of exactly one"
  line=$(awk -v name="$file" '$2 == name || $2 == "*" name' "$sums")
  expected=$(printf '%s\n' "$line" | awk 'NF == 2 { print $1 }')
  printf '%s' "$expected" | grep -Eqx '[0-9a-f]{64}' || refuse "the line for $file in $sums is not a SHA-256 checksum line"
  actual=$(sha256sum < "$file")
  actual=${actual%% *}
  [ "$actual" = "$expected" ] || refuse "the checksum of $file in $sums does not match"
done
value() {
  found=$(sed -n "s/^$1=//p" "$digests")
  [ "$(printf '%s\n' "$found" | grep -c .)" -eq 1 ] || refuse "$digests must name $1 exactly once"
  printf '%s' "$found" | grep -Eqx 'sha256:[0-9a-f]{64}' || refuse "$1 in $digests is not a digest of SHA-256"
  printf '%s' "$found"
}
app=$(value TASK_MANAGER_IMAGE_DIGEST)
backup=$(value TASK_MANAGER_BACKUP_IMAGE_DIGEST)
tar -xzf "$package"
[ -f "task-manager-$version/deploy/compose.yaml" ] || refuse "the package has no task-manager-$version/deploy/compose.yaml"
echo "UPDATE_CHECK_OLD_IMAGE=ghcr.io/1uedev/task-manager@$app"
echo "UPDATE_CHECK_OLD_BACKUP_IMAGE=ghcr.io/1uedev/task-manager-backup@$backup"
echo "UPDATE_CHECK_OLD_TEMPLATE=$(pwd)/task-manager-$version/deploy"
