#!/bin/sh
# Step 4 of the restore in docs/BACKUP_RESTORE.md (TASK-135, finding F9 of the review of TASK-131): moves the restored and
# proven data directory and file store from the restore target into the volumes of the database and of the application,
# each with the account that owns it. It refuses when no proven restore lies in the target or when a volume already holds
# data, so it never overwrites an installation.
#   /restore                 the restore target of restore.sh (point, pgdata, files)
#   /var/lib/postgresql      the volume of the database; PostgreSQL 18 keeps its data in 18/docker below it
#   /data/files              the volume of the file store
set -eu
[ -f /restore/point ] && [ -d /restore/pgdata ] && [ -d /restore/files ] \
  || { echo "restore-place: /restore holds no proven restore; run 'backup restore /restore' first" >&2; exit 1; }
[ -z "$(ls -A /var/lib/postgresql)" ] || { echo "restore-place: the database volume is not empty" >&2; exit 1; }
[ -z "$(ls -A /data/files)" ] || { echo "restore-place: the file store is not empty" >&2; exit 1; }
point=$(cat /restore/point)
mkdir -p /var/lib/postgresql/18
cp -a /restore/pgdata /var/lib/postgresql/18/docker
chown -R 999:999 /var/lib/postgresql
chmod 0700 /var/lib/postgresql/18/docker
cp -a /restore/files/. /data/files/
chown -R 10001:10001 /data/files
sync
echo "restore-place: point $point is in place; start the database next"
