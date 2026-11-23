#!/usr/bin/env bash
# The restore (TASK-131, DEC-161; corrected by TASK-135, DEC-165). It takes a backup point from the target and lays out a
# fresh installation:
#
#   restore.sh points                      lists the complete points on the target, newest first
#   restore.sh restore <target> [point]    restores the newest point, or the named one, into <target>
#
# "restore" writes <target>/pgdata, <target>/files and <target>/manifest.json and never touches a running installation. A
# point counts as restored only when it is proven (finding F5 of the review of TASK-131): an isolated PostgreSQL in this
# container recovered the data directory exactly to the restore point of the point and opened it, and every content the
# recovered database names lies in the file store with its size and SHA-256. That instance then writes the marker that
# keeps the web server and the worker from starting until the command after-restore ran, and stops. When anything fails
# on the way, the point is dropped and the next older one is tried, and the step is reported; there is no wrong success
# (prüffall O08). The data directory that results is an ordinary, already recovered one: the database container starts
# it without replaying anything.
#
# Settings besides lib.sh:
#   TASK_MANAGER_RESTORE_DB_USER   the owner role of the database, which writes the marker (default taskmanager)
#   TASK_MANAGER_RESTORE_TIMEOUT   seconds the recovery of one point may take (default 3600)
cd "$(dirname "$0")" || exit 1
# shellcheck source=lib.sh
. ./lib.sh

db_user=${TASK_MANAGER_RESTORE_DB_USER:-taskmanager}
recovery_timeout=${TASK_MANAGER_RESTORE_TIMEOUT:-3600}

usage() {
  cat >&2 <<'TEXT'
usage: restore.sh points
       restore.sh restore <target directory> [point]
TEXT
  exit 2
}

# The complete points on the target, newest first, as "point snapshot". Only snapshots with the tag "point" count; a
# candidate whose backup did not finish never has it.
points() {
  # The summary of a snapshot is an object of its own and is dropped first, so that every snapshot stays one line and
  # keeps its tag next to its identifier; both fields are read for themselves, because their order belongs to restic.
  restic --quiet snapshots --tag point --json \
    | sed 's/"summary":{[^{}]*}//g' \
    | tr '{' '\n' \
    | awk 'match($0, /"point=[0-9TZ]+"/) {
             p = substr($0, RSTART + 7, RLENGTH - 8)
             if (match($0, /"short_id":"[0-9a-f]+"/)) { print p " " substr($0, RSTART + 12, RLENGTH - 13) }
           }' \
    | sort -r
}

field() { sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p" "$2"; }

# Restores and proves one point. Runs as a process of its own with errexit, so any failing step ends it; every step is
# also checked for itself to name the reason.
try_point() {
  local point=$1 snapshot=$2 target=$3 manifest base contents restore_point base_id data absolute socket log pid
  log "trying point $point (snapshot $snapshot)"
  # Only the content of the target is emptied, because the target itself can be a mount point.
  mkdir -p "$target" || exit 1
  find "$target" -mindepth 1 -maxdepth 1 -exec rm -rf {} + || exit 1
  restic --quiet restore "$snapshot" --target "$target/raw" > /dev/null || { log "point $point cannot be read from the target or written to $target"; exit 1; }
  manifest=$(find "$target/raw" -name manifest.json -type f | head -n 1)
  [ -n "$manifest" ] || { log "point $point holds no manifest"; exit 1; }
  cp "$manifest" "$target/manifest.json" || exit 1
  base=$(field base "$target/manifest.json")
  restore_point=$(field restorePoint "$target/manifest.json")
  contents=$(sed -n 's/.*"contents":\([0-9]*\).*/\1/p' "$target/manifest.json")
  [ -n "$base" ] && [ -n "$restore_point" ] && [ -n "$contents" ] || { log "the manifest of $point is incomplete"; exit 1; }

  data=$(find "$target/raw" -type d -name content | head -n 1)
  [ -n "$data" ] || { log "point $point holds no file store"; exit 1; }
  mv "$(dirname "$data")" "$target/files" || { log "the file store of $point could not be moved"; exit 1; }
  data=$(find "$target/raw" -type d -name "$(basename "$archive")" | head -n 1)
  [ -n "$data" ] || { log "point $point holds no write ahead log archive"; exit 1; }
  mv "$data" "$target/archive" || { log "the archive of $point could not be moved"; exit 1; }

  base_id=$(restic --quiet snapshots --tag "base,base=$base" --json | { grep -o '"id":"[0-9a-f]\{64\}"' || true; } | head -n 1 | cut -d'"' -f4)
  [ -n "$base_id" ] || { log "the base backup $base of point $point is not on the target"; exit 1; }
  restic --quiet restore "$base_id" --target "$target/base" > /dev/null \
    || { log "the base backup $base of point $point cannot be read"; exit 1; }
  # The data directory is the one that holds the directory "global" and PG_VERSION; databases below it have neither.
  data=$(find "$target/base" -type d -name global | head -n 1)
  [ -n "$data" ] && [ -f "$(dirname "$data")/PG_VERSION" ] || { log "the base backup $base holds no data directory"; exit 1; }
  mv "$(dirname "$data")" "$target/pgdata" || { log "the data directory of $point could not be moved"; exit 1; }
  rm -rf "$target/raw" "$target/base" || exit 1
  chmod 0700 "$target/pgdata" || exit 1
  touch "$target/pgdata/recovery.signal" || exit 1

  # The isolated recovery: no network, a socket of its own, nothing archived, and the settings only on the command line,
  # so the data directory keeps no trace of them.
  absolute=$(cd "$target" && pwd) || exit 1
  socket=$absolute/socket
  log=$absolute/recovery.log
  mkdir -p "$socket" || exit 1
  postgres -D "$absolute/pgdata" \
    -c listen_addresses='' -c unix_socket_directories="$socket" -c archive_mode=off \
    -c restore_command="cp $absolute/archive/%f %p" \
    -c recovery_target_name="$restore_point" -c recovery_target_action=promote \
    > "$log" 2>&1 &
  pid=$!
  local deadline=$(( $(date -u +%s) + recovery_timeout )) open=""
  while [ -z "$open" ]; do
    if ! kill -0 "$pid" 2> /dev/null; then
      log "the recovery of $point ended without opening the database:"
      grep -E 'FATAL|PANIC|ERROR' "$log" | tail -n 5 >&2 || true
      exit 1
    fi
    if [ "$(date -u +%s)" -ge "$deadline" ]; then
      kill "$pid" 2> /dev/null || true
      log "the recovery of $point did not finish within $recovery_timeout s"
      exit 1
    fi
    open=$(psql -X -A -t -q -h "$socket" -U "$db_user" -d "${PGDATABASE:-taskmanager}" \
      -c "select 1 where not pg_is_in_recovery()" 2> /dev/null || true)
    [ -n "$open" ] || sleep 2
  done
  stop_database() { pg_ctl -D "$absolute/pgdata" -m fast -w stop > /dev/null 2>&1 || kill "$pid" 2> /dev/null || true; }
  if ! grep -q "recovery stopping at restore point \"$restore_point\"" "$log"; then
    stop_database
    log "the recovery of $point did not stop at its restore point $restore_point"
    exit 1
  fi

  # Every content the recovered database names, with size and SHA-256 (F5): a missing, changed or replaced file drops the
  # point, whatever the number of files.
  local expected="$absolute/contents.expected" present="$absolute/contents.present"
  if ! psql -X -A -t -q -v ON_ERROR_STOP=1 -F ' ' -h "$socket" -U "$db_user" -d "${PGDATABASE:-taskmanager}" \
      -c "select id, size, sha256 from operations_backup_content order by id" > "$expected"; then
    stop_database
    log "the recovered database of $point cannot list its contents"
    exit 1
  fi
  ( cd "$target/files/content" && find . -type f -print0 | xargs -0 -r sha256sum ) > "$present.sums" || { stop_database; exit 1; }
  ( cd "$target/files/content" && find . -type f -printf '%f %s\n' ) > "$present.sizes" || { stop_database; exit 1; }
  if ! awk 'FILENAME == ARGV[1] { n = split($2, part, "/"); sum[part[n]] = $1; next }
            FILENAME == ARGV[2] { size[$1] = $2; next }
            { if (!($1 in size)) { print "missing " $1; bad++ }
              else if (size[$1] != $2 || sum[$1] != $3) { print "changed " $1; bad++ } }
            END { if (bad) exit 1 }' "$present.sums" "$present.sizes" "$expected" > "$absolute/contents.report"; then
    stop_database
    log "point $point does not hold every content of its database:"
    sed -n '1,20p' "$absolute/contents.report" >&2
    exit 1
  fi
  found=$(wc -l < "$expected" | tr -d ' ')

  # The marker: the web server and the worker refuse to start until after-restore ran (rule 10 of TASK-131).
  if ! psql -X -A -t -q -v ON_ERROR_STOP=1 -h "$socket" -U "$db_user" -d "${PGDATABASE:-taskmanager}" \
      -c "insert into operations_restore_pending (point, restored_at) values ('$point', now()) on conflict (point) do nothing" > /dev/null; then
    stop_database
    log "the marker of the restore could not be written"
    exit 1
  fi
  pg_ctl -D "$absolute/pgdata" -m fast -w stop > /dev/null || { log "the recovered database did not stop"; exit 1; }
  rm -rf "$socket" "$present.sums" "$present.sizes" "$expected" || exit 1
  printf '%s\n' "$point" > "$target/point" || exit 1
  log "point $point restored and proven in $target: database at restore point $restore_point, $found contents checked"
}

case ${1:-} in
  points)
    require_settings || exit 1
    points
    ;;
  restore)
    [ $# -ge 2 ] || usage
    require_settings || exit 1
    target=$2
    wanted=${3:-}
    list=$(points) || fail "the points cannot be listed" || exit 1
    [ -n "$list" ] || { fail "the target holds no complete backup point"; exit 1; }
    if [ -n "$wanted" ]; then
      list=$(printf '%s\n' "$list" | awk -v want="$wanted" '$1 == want')
      [ -n "$list" ] || { fail "the target holds no complete point $wanted"; exit 1; }
    fi
    while read -r point snapshot; do
      [ -n "$point" ] || continue
      # A process of its own per point, so that errexit holds inside it and a failure drops exactly this point.
      if "$PWD/restore.sh" _try "$point" "$snapshot" "$target"; then
        log "next: restore-place moves $target/pgdata and $target/files into the volumes of database and file store (docs/BACKUP_RESTORE.md, step 4)"
        exit 0
      fi
      log "point $point is unusable; trying the next older one"
    done <<< "$list"
    fail "no backup point could be restored"
    exit 1
    ;;
  _try)
    [ $# -eq 4 ] || usage
    try_point "$2" "$3" "$4"
    ;;
  *) usage ;;
esac
