#!/usr/bin/env bash
# Common parts of the backup and the restore (TASK-131, DEC-161). Both are sourced, never run on their own.

# The settings of the service. They come from the environment; none of them has a default that reaches outside this
# installation, and no password is ever written to a log or to a manifest.
#   TASK_MANAGER_BACKUP_REPOSITORY  the restic repository, for example s3:https://host/bucket or sftp:user@host:/path
#   RESTIC_PASSWORD                 the password of the repository; the operator keeps it outside the backup
#   TASK_MANAGER_BACKUP_ARCHIVE     the directory the database archives its write ahead log into (read only here)
#   TASK_MANAGER_BACKUP_FILES       the directory of the file store (read only here)
#   TASK_MANAGER_BACKUP_STATE       a directory of this service for base backups and reports
#   TASK_MANAGER_BACKUP_INTERVAL    seconds between two backup points, default 900 (rule 7)
#   TASK_MANAGER_BACKUP_BASE_AGE    seconds after which a new base backup is made, default 86400 (rule 7)
#   TASK_MANAGER_BACKUP_RETENTION   days a point stays fully restorable, default 14 (rule 8)
#   PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE  the database of the application
set -euo pipefail

repository=${TASK_MANAGER_BACKUP_REPOSITORY:-}
archive=${TASK_MANAGER_BACKUP_ARCHIVE:-/archive}
files=${TASK_MANAGER_BACKUP_FILES:-/data/files}
state=${TASK_MANAGER_BACKUP_STATE:-/state}
interval=${TASK_MANAGER_BACKUP_INTERVAL:-900}
base_age=${TASK_MANAGER_BACKUP_BASE_AGE:-86400}
retention=${TASK_MANAGER_BACKUP_RETENTION:-14}

log() { printf '%s backup: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
fail() { log "FAILED: $*" >&2; return 1; }

require_settings() {
  local missing=()
  [ -n "$repository" ] || missing+=(TASK_MANAGER_BACKUP_REPOSITORY)
  [ -n "${RESTIC_PASSWORD:-}${RESTIC_PASSWORD_FILE:-}" ] || missing+=(RESTIC_PASSWORD)
  [ -n "${PGHOST:-}" ] || missing+=(PGHOST)
  [ -n "${PGUSER:-}" ] || missing+=(PGUSER)
  [ -n "${PGPASSWORD:-}${PGPASSFILE:-}" ] || missing+=(PGPASSWORD)
  [ -n "${PGDATABASE:-}" ] || missing+=(PGDATABASE)
  if [ ${#missing[@]} -gt 0 ]; then
    fail "required settings are missing: ${missing[*]}"
  fi
  export RESTIC_REPOSITORY=$repository
}

# restic writes its own progress to the standard output; the service keeps the output of a command and shows it only on a
# failure, so that a normal run stays readable in the log of the container.
restic_quiet() {
  local out code
  out=$(restic --quiet "$@" 2>&1) || { code=$?; printf '%s\n' "$out" >&2; return $code; }
  printf '%s' "$out"
}

sql() { psql -X -A -t -q -v ON_ERROR_STOP=1 -c "$1"; }

# The repository exists after the first start; a second init would fail, so it is only initialized when missing.
ensure_repository() {
  if restic --quiet cat config > /dev/null 2>&1; then
    return 0
  fi
  log "initializing the backup repository"
  restic_quiet init > /dev/null
}
