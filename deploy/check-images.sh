#!/bin/sh
# Refuses to start the operations template with an image reference that is not a digest of the release (TASK-136,
# finding F8 of the review of TASK-132). A tag can point to another state at the next pull; a digest cannot. The service
# "images" of compose.yaml runs this before the database, and with it before every process of the application, and
# docs/INSTALL.md runs it before every pull. The expected forms come from compose.yaml; only the update check of the
# repository replaces them with local image identifiers, in a file of its own (tools/update/compose.test.yaml).
set -eu
check() {
  name=$1 value=$2 pattern=$3
  if ! printf '%s' "$value" | grep -Eqx "$pattern"; then
    echo "Refused: $name must be the digest the release names ($pattern); found '$value'. Copy both lines from" \
      "digests-VERSION.env of the release into .env." >&2
    exit 1
  fi
}
check TASK_MANAGER_IMAGE "${TASK_MANAGER_IMAGE:-}" "${TASK_MANAGER_IMAGE_FORM:?}"
check TASK_MANAGER_BACKUP_IMAGE "${TASK_MANAGER_BACKUP_IMAGE:-}" "${TASK_MANAGER_BACKUP_IMAGE_FORM:?}"
echo "both images are referenced by digest"
