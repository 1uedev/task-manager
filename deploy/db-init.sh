#!/bin/sh
# Runs once when the database of the operations template is created (TASK-132, DEC-162). It sets up the role of the
# backup service that docs/BACKUP_RESTORE.md describes: it may connect as a replication client for the base backup, close a
# write ahead log segment, write the named restore point that bounds a backup point (TASK-135) and read the tables. A
# replication role can read the whole database through the base backup anyway, so reading the tables widens nothing; the
# backup needs it for the list of contents and the schema version.
set -eu
: "${TASK_MANAGER_BACKUP_DB_PASSWORD:?set TASK_MANAGER_BACKUP_DB_PASSWORD}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v password="$TASK_MANAGER_BACKUP_DB_PASSWORD" -v owner="$POSTGRES_USER" <<'SQL'
create role taskmanager_backup with login replication password :'password';
grant execute on function pg_switch_wal() to taskmanager_backup;
grant execute on function pg_create_restore_point(text) to taskmanager_backup;
grant usage on schema public to taskmanager_backup;
alter default privileges for role :"owner" in schema public grant select on tables to taskmanager_backup;
SQL
printf 'host replication taskmanager_backup all scram-sha-256\n' >> "$PGDATA/pg_hba.conf"
