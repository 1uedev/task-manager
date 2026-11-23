#!/usr/bin/env bash
# The backup service (TASK-131, DEC-161). It makes a backup point every TASK_MANAGER_BACKUP_INTERVAL seconds and a base
# backup every TASK_MANAGER_BACKUP_BASE_AGE seconds. A point is one restic snapshot of the write ahead log archive, the
# file store and its manifest, together with the base backup the manifest names; it is complete only when restic
# confirmed it on the target. Without arguments the service runs until it is stopped; "once" makes a single point, which
# the tests and the restore drill use.
#
# A point is refused, and the former point stays the newest one, when a content the database refers to is missing in the
# file store (prüffall O06): the monitoring of the age of the newest point then shows the backlog instead of a green
# backup that cannot be restored.
cd "$(dirname "$0")"
# shellcheck source=lib.sh
. ./lib.sh

mode=${1:-loop}
base_snapshot_file=$state/base-snapshot
base_time_file=$state/base-time
latest_file=$state/latest-point

# The base backup: the data directory of the database at a moment, with the write ahead log it needs to become
# consistent. The archive carries everything after it, so one base serves every later point.
make_base() {
  local dir=$state/base id
  log "base backup"
  rm -rf "$dir" "$dir.new"
  mkdir -p "$dir.new"
  pg_basebackup --pgdata="$dir.new" --format=plain --wal-method=fetch --checkpoint=fast --no-password
  mv "$dir.new" "$dir"
  id=$(restic backup --tag base --json "$dir" | sed -n 's/.*"snapshot_id":"\([0-9a-f]*\)".*/\1/p' | tail -n 1)
  [ -n "$id" ] || fail "restic did not report a snapshot for the base backup"
  printf '%s' "$id" > "$base_snapshot_file"
  date -u +%s > "$base_time_file"
  rm -rf "$dir"
  log "base backup stored as snapshot $id"
}

base_due() {
  [ -s "$base_snapshot_file" ] || return 0
  local age
  age=$(( $(date -u +%s) - $(cat "$base_time_file" 2>/dev/null || echo 0) ))
  [ "$age" -ge "$base_age" ]
}

# Everything committed up to the returned moment is in the archive afterwards: the moment and the position are read
# first, then the segment that holds the position is closed and awaited in the archive.
wait_for_archive() {
  local lsn=$1 wal deadline now
  wal=$(sql "select pg_walfile_name('$lsn')" | tr -d ' ')
  [ -n "$wal" ] || fail "the database did not name the write ahead log segment of $lsn"
  sql "select pg_switch_wal()" > /dev/null
  deadline=$(( $(date -u +%s) + 120 ))
  until [ -f "$archive/$wal" ]; do
    if [ "$(date -u +%s)" -ge "$deadline" ]; then
      now=$(sql "select pg_current_wal_lsn()" | tr -d ' ')
      # Nothing was written since: the segment stays open and holds no commit after the moment, so the point is covered
      # by the segments that are archived already.
      [ "$now" = "$lsn" ] || fail "the segment $wal did not reach the archive within 120 s"
      log "no write since the last point; $wal stays open"
      return 0
    fi
    sleep 2
  done
}

# Compares the contents the database refers to with the file store. Prints the number of contents, or the reason why the
# point is refused.
check_contents() {
  local list=$state/contents.list present=$state/contents.present
  sql "select id || ' ' || size from operations_backup_content order by id" > "$list"
  find "$files/content" -type f -printf '%f %s\n' 2>/dev/null | sort > "$present" || true
  # The file name decides which list a line belongs to, because an empty file store would otherwise make the first line
  # of the list of the database look like a line of the file store.
  awk 'FILENAME == ARGV[1] { size[$1] = $2; next }
       { if (!($1 in size)) { print "missing " $1; bad++ } else if (size[$1] != $2) { print "changed " $1; bad++ } }
       END { if (bad) exit 1 }' "$present" "$list"
}

make_point() {
  local name t lsn contents manifest id
  name=$(date -u +%Y%m%dT%H%M%SZ)
  t=$(sql "select to_char(now() at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.USOF')" | tr -d ' ')
  lsn=$(sql "select pg_current_wal_lsn()" | tr -d ' ')
  wait_for_archive "$lsn"
  if ! check_contents > "$state/contents.report" 2>&1; then
    log "point $name refused: the file store does not hold every content of the database"
    sed -n '1,20p' "$state/contents.report" >&2
    return 1
  fi
  contents=$(wc -l < "$state/contents.list" | tr -d ' ')
  manifest=$state/manifest.json
  printf '{"point":"%s","time":"%s","lsn":"%s","base":"%s","schema":"%s","contents":%s}\n' \
    "$name" "$t" "$lsn" "$(cat "$base_snapshot_file")" \
    "$(sql "select coalesce(max(version), 'none') from flyway_schema_history where success" | tr -d ' ')" \
    "$contents" > "$manifest"
  id=$(restic backup --tag point --tag "point=$name" --json "$archive" "$files" "$manifest" \
    | sed -n 's/.*"snapshot_id":"\([0-9a-f]*\)".*/\1/p' | tail -n 1)
  [ -n "$id" ] || fail "restic did not report a snapshot for the point"
  printf '%s %s %s\n' "$name" "$id" "$t" > "$latest_file"
  log "point $name complete as snapshot $id (database up to $t, $contents contents)"
}

# A point stays restorable for the retention period. The base backups it needs are kept for one day longer, and the
# newest base is always kept, so the newest point can be restored even when no base was made within the period.
forget_old() {
  restic_quiet forget --tag point --keep-within "${retention}d" --keep-last 1 --prune > /dev/null
  restic_quiet forget --tag base --keep-within "$((retention + 1))d" --keep-last 1 --prune > /dev/null
}

require_settings
mkdir -p "$state"
[ -d "$archive" ] || fail "the archive directory $archive does not exist"
[ -d "$files" ] || fail "the file store $files does not exist"
ensure_repository

run_once() {
  if base_due; then
    make_base
  fi
  make_point
  forget_old
}

case $mode in
  once) run_once ;;
  loop)
    log "service started: a point every ${interval} s, a base backup every ${base_age} s, retention ${retention} days"
    while true; do
      # A failed point must not end the service: the next one is tried, and the age of the newest point shows the backlog.
      run_once || log "this point was not made; the next one follows in ${interval} s"
      sleep "$interval"
    done
    ;;
  *) fail "usage: backup.sh [once|loop]" ;;
esac
