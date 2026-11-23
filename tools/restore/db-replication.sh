#!/bin/sh
# TASK-131 (DEC-161): the base backup of the backup service connects as a replication client. The image of the database
# writes no such line into pg_hba.conf, so the drill adds one for the account of the application. An installation adds
# the same line for its own backup role, see docs/BACKUP_RESTORE.md.
set -eu
printf 'host replication %s all scram-sha-256\n' "$POSTGRES_USER" >> "$PGDATA/pg_hba.conf"
