#!/usr/bin/env bash
# The entry point of the backup image (TASK-135, finding F9): "points" and "restore" go to the restore, everything else
# ("loop", "once") to the backup service, so the commands of docs/BACKUP_RESTORE.md work as printed:
#   docker compose run --rm backup points
#   docker compose run --rm backup restore /restore
#
# Before any command both image settings of the template are checked together, as deploy/check-images.sh does, also when
# Compose starts this container with --no-deps and without the service "images" (TASK-139, finding F7 of the review of
# TASK-137). The template passes them; a container started without the template has neither and skips the check.
if [ -n "${TASK_MANAGER_IMAGE_DIGEST+set}${TASK_MANAGER_BACKUP_IMAGE_DIGEST+set}" ]; then
  refuse() {
    echo "Refused: $1 Copy both lines from digests-VERSION.env of the release into .env." >&2
    exit 1
  }
  if [ -n "${TASK_MANAGER_IMAGE:-}" ] || [ -n "${TASK_MANAGER_BACKUP_IMAGE:-}" ]; then
    refuse "TASK_MANAGER_IMAGE and TASK_MANAGER_BACKUP_IMAGE are no longer read; since TASK-137 the template takes only the digests as TASK_MANAGER_IMAGE_DIGEST and TASK_MANAGER_BACKUP_IMAGE_DIGEST. Remove the two old lines."
  fi
  for name in TASK_MANAGER_IMAGE_DIGEST TASK_MANAGER_BACKUP_IMAGE_DIGEST; do
    value=${!name:-}
    [[ $value =~ ^sha256:[0-9a-f]{64}$ ]] \
      || refuse "$name must be the digest the release names (sha256: and 64 hexadecimal characters); found '$value'."
  done
fi
case ${1:-loop} in
  points|restore) exec /opt/backup/restore.sh "$@" ;;
  *) exec /opt/backup/backup.sh "$@" ;;
esac
