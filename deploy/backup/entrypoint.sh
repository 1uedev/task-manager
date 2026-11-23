#!/usr/bin/env bash
# The entry point of the backup image (TASK-135, finding F9): "points" and "restore" go to the restore, everything else
# ("loop", "once") to the backup service, so the commands of docs/BACKUP_RESTORE.md work as printed:
#   docker compose run --rm backup points
#   docker compose run --rm backup restore /restore
case ${1:-loop} in
  points|restore) exec /opt/backup/restore.sh "$@" ;;
  *) exec /opt/backup/backup.sh "$@" ;;
esac
