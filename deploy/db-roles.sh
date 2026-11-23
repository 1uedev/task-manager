#!/bin/sh
# Sets up the database roles of the operations template (TASK-136, finding F3 of the review of TASK-132; db-roles.sql
# describes them). Runs inside the database container as its administration account over the local socket:
#   - once by the database creation (db-init.sh),
#   - after an update of the template that brought the roles: docker compose exec db sh /opt/taskmanager/db-roles.sh
# It can run any number of times. The passwords come from the environment of the container and reach psql through
# \getenv, never as an argument of a process (H2).
set -eu
: "${POSTGRES_USER:?}" "${POSTGRES_DB:?}"
: "${TASK_MANAGER_APP_DB_PASSWORD:?set TASK_MANAGER_APP_DB_PASSWORD}"
: "${TASK_MANAGER_MIGRATE_DB_PASSWORD:?set TASK_MANAGER_MIGRATE_DB_PASSWORD}"
: "${TASK_MANAGER_BACKUP_DB_PASSWORD:?set TASK_MANAGER_BACKUP_DB_PASSWORD}"
psql -X -q -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v owner="$POSTGRES_USER" <<'SQL'
\getenv app_password TASK_MANAGER_APP_DB_PASSWORD
\getenv migrate_password TASK_MANAGER_MIGRATE_DB_PASSWORD
\getenv backup_password TASK_MANAGER_BACKUP_DB_PASSWORD
\i /opt/taskmanager/db-roles.sql
SQL
echo "database roles taskmanager_migrate, taskmanager_app and taskmanager_backup are set up"
