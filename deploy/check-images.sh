#!/bin/sh
# Explains a wrong image reference of the operations template before anything starts (TASK-136, finding F8 of the review
# of TASK-132; TASK-137, finding N7 of the review of TASK-136). compose.yaml itself only knows the references
# ghcr.io/1uedev/task-manager@DIGEST and ghcr.io/1uedev/task-manager-backup@DIGEST, so every command of Compose, pull, up and
# run, also with --no-deps, uses a digest; a tag cannot take its place. This check runs before the database and names the
# mistake: a value that is not a digest of SHA-256, or the settings of the form before TASK-137. The same rule runs at the
# start of every container of both images (the entry point of the backup image and OperationsStart.imageRefusal of the
# application), so a command with --no-deps without this service checks both values together as well (TASK-139, F7).
set -eu
refuse() {
  echo "Refused: $1 Copy both lines from digests-VERSION.env of the release into .env." >&2
  exit 1
}
if [ -n "${TASK_MANAGER_IMAGE:-}" ] || [ -n "${TASK_MANAGER_BACKUP_IMAGE:-}" ]; then
  refuse "TASK_MANAGER_IMAGE and TASK_MANAGER_BACKUP_IMAGE are no longer read; since TASK-137 the template takes only the
 digests as TASK_MANAGER_IMAGE_DIGEST and TASK_MANAGER_BACKUP_IMAGE_DIGEST. Remove the two old lines."
fi
check() {
  name=$1 value=$2
  if ! printf '%s' "$value" | grep -Eqx 'sha256:[0-9a-f]{64}'; then
    refuse "$name must be the digest the release names (sha256: and 64 hexadecimal characters); found '$value'."
  fi
}
check TASK_MANAGER_IMAGE_DIGEST "${TASK_MANAGER_IMAGE_DIGEST:-}"
check TASK_MANAGER_BACKUP_IMAGE_DIGEST "${TASK_MANAGER_BACKUP_IMAGE_DIGEST:-}"
echo "both images are referenced by digest"
