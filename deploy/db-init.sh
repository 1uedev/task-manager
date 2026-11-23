#!/bin/sh
# Runs once when the database of the operations template is created (TASK-132, DEC-162). It sets up the roles of
# db-roles.sql (TASK-136, F3): the migration role that owns the schema, the runtime role of web process, worker and
# commands, and the role of the backup service, which may connect as a replication client for the base backup.
set -eu
sh /opt/taskmanager/db-roles.sh
printf 'host replication taskmanager_backup all scram-sha-256\n' >> "$PGDATA/pg_hba.conf"
