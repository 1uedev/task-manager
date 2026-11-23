#!/usr/bin/env bash
# The backup service (TASK-131, DEC-161; corrected by TASK-135, DEC-165). It makes a backup point every
# TASK_MANAGER_BACKUP_INTERVAL seconds and a base backup every TASK_MANAGER_BACKUP_BASE_AGE seconds. A point is one restic
# snapshot of the write ahead log archive, the file store and its manifest, together with the base backup the manifest
# names. Without arguments the service runs until it is stopped; "once" makes a single point.
#
# What makes a point complete (TASK-135):
#   - its boundary is a named restore point the database wrote (pg_create_restore_point); the restore recovers exactly to
#     it, and the segment that holds it is in the archive before the point is stored (F2);
#   - every segment from the start of its base backup up to that segment lies complete in the archive (F3);
#   - the clean up of the file store deletes nothing from the moment the hold is set until the snapshot is stored, so every
#     content the database names at the boundary is in the file store the snapshot reads (F4);
#   - restic ended with status 0 and the stored manifest is the one written; only then the snapshot gets the tag "point"
#     and is offered for a restore. Until then it is a "candidate", and a candidate that failed is forgotten (F1).
# Every step is checked for itself. In the loop every point runs as a process of its own, so a failure in one step ends
# exactly that point and the next one is tried.
#
# A point is refused, and the former point stays the newest one, when a content the database refers to is missing in the
# file store (prüffall O06): the monitoring of the age of the newest point then shows the backlog instead of a green
# backup that cannot be restored.
cd "$(dirname "$0")" || exit 1
# shellcheck source=lib.sh
. ./lib.sh

mode=${1:-loop}
base_name_file=$state/base-name
base_start_file=$state/base-start
base_time_file=$state/base-time
latest_file=$state/latest-point
holder=backup-$(hostname)

# The base backup: the data directory of the database at a moment, with the write ahead log it needs to become
# consistent. The archive carries everything after it, so one base serves every later point.
make_base() {
  local dir=$state/base name start
  name=$(date -u +%Y%m%dT%H%M%SZ)
  log "base backup $name"
  rm -rf "$dir" "$dir.new" || return 1
  mkdir -p "$dir.new" || return 1
  pg_basebackup --pgdata="$dir.new" --format=plain --wal-method=fetch --checkpoint=fast --no-password \
    || { fail "pg_basebackup failed"; return 1; }
  start=$(sed -n 's/^START WAL LOCATION: .* (file \([0-9A-F]\{24\}\))$/\1/p' "$dir.new/backup_label")
  [ -n "$start" ] || { fail "the base backup names no start segment"; return 1; }
  mv "$dir.new" "$dir" || return 1
  store_snapshot base "$name" "$dir" || return 1
  # The state is replaced only after the base is stored for good.
  printf '%s' "$name" > "$base_name_file.new" && mv "$base_name_file.new" "$base_name_file" || return 1
  printf '%s' "$start" > "$base_start_file.new" && mv "$base_start_file.new" "$base_start_file" || return 1
  date -u +%s > "$base_time_file" || return 1
  rm -rf "$dir"
  log "base backup $name stored, it starts at segment $start"
}

# A new base is due after TASK_MANAGER_BACKUP_BASE_AGE seconds, without a base, and when the database runs on another
# timeline than its base (after a restore), because the archive of the new timeline does not continue the old one.
base_due() {
  [ -s "$base_name_file" ] && [ -s "$base_start_file" ] || return 0
  local age timeline
  age=$(( $(date -u +%s) - $(cat "$base_time_file" 2>/dev/null || echo 0) ))
  [ "$age" -lt "$base_age" ] || return 0
  timeline=$(sql "select substr(pg_walfile_name(pg_current_wal_lsn()), 1, 8)") || return 0
  [ "$timeline" = "$(cut -c1-8 "$base_start_file")" ] || return 0
  return 1
}

# Stores one directory as a snapshot of the given kind ("base" or "point") with the name of the base or point. The
# snapshot is a candidate until restic reported success and, for a point, the stored manifest is the one written; then it
# gets the tag of its kind. Prints nothing; fails with the reason.
store_snapshot() {
  local kind=$1 name=$2 output code=0 id
  shift 2
  output=$(restic backup --tag candidate --tag "$kind=$name" --json "$@" 2> "$state/restic.err") || code=$?
  id=$(printf '%s\n' "$output" | sed -n 's/.*"snapshot_id":"\([0-9a-f]*\)".*/\1/p' | tail -n 1)
  if [ "$code" -ne 0 ] || [ -z "$id" ]; then
    sed -n '1,20p' "$state/restic.err" >&2
    # Status 3 means restic stored a snapshot although it could not read everything; it is no point.
    [ -z "$id" ] || restic_quiet forget "$id" > /dev/null || true
    fail "restic ended with status $code for the $kind $name"
    return 1
  fi
  if [ "$kind" = point ]; then
    if ! restic --quiet dump "$id" "$state/manifest.json" 2> /dev/null | cmp -s - "$state/manifest.json"; then
      restic_quiet forget "$id" > /dev/null || true
      fail "the stored manifest of point $name differs from the one written"
      return 1
    fi
  fi
  restic_quiet tag --add "$kind" --remove candidate "$id" > /dev/null \
    || { fail "the $kind $name could not be marked as complete"; return 1; }
}

# Snapshots that stayed candidates (a run that ended in the middle) are never offered; they are forgotten here.
forget_candidates() {
  local ids
  ids=$(restic --quiet snapshots --tag candidate --json | { grep -o '"id":"[0-9a-f]\{64\}"' || true; } | cut -d'"' -f4) || return 1
  [ -z "$ids" ] && return 0
  # shellcheck disable=SC2086
  restic_quiet forget $ids > /dev/null
}

# Waits until the segment is complete in the archive. The archive command publishes a segment only under its final name
# when it is complete, so the name is enough; the size is checked as well.
wait_for_segment() {
  local wal=$1 size=$2 deadline
  deadline=$(( $(date -u +%s) + 120 ))
  until [ -f "$archive/$wal" ] && [ "$(stat -c %s "$archive/$wal")" = "$size" ]; do
    if [ "$(date -u +%s)" -ge "$deadline" ]; then
      fail "the segment $wal did not reach the archive within 120 s"
      return 1
    fi
    sleep 2
  done
}

# Every segment from the start of the base up to the last one the point needs must lie complete in the archive.
check_chain() {
  local first=$1 last=$2 size=$3 per_id timeline from to n name
  timeline=${first:0:8}
  [ "${last:0:8}" = "$timeline" ] || { fail "the point lies on timeline ${last:0:8}, its base on $timeline"; return 1; }
  per_id=$(( 0x100000000 / size ))
  from=$(( 16#${first:8:8} * per_id + 16#${first:16:8} ))
  to=$(( 16#${last:8:8} * per_id + 16#${last:16:8} ))
  [ "$from" -le "$to" ] || { fail "the segment $last lies before the start $first of the base"; return 1; }
  for (( n = from; n <= to; n++ )); do
    name=$(printf '%s%08X%08X' "$timeline" $(( n / per_id )) $(( n % per_id )))
    if [ ! -f "$archive/$name" ] || [ "$(stat -c %s "$archive/$name")" != "$size" ]; then
      fail "the archive lacks the complete segment $name between $first and $last"
      return 1
    fi
  done
}

# Compares the contents the database refers to with the file store. Prints the reason why the point is refused.
check_contents() {
  local list=$state/contents.list present=$state/contents.present
  sql "select id || ' ' || size from operations_backup_content order by id" > "$list" || return 1
  find "$files/content" -type f -printf '%f %s\n' 2>/dev/null | sort > "$present" || true
  # The file name decides which list a line belongs to, because an empty file store would otherwise make the first line
  # of the list of the database look like a line of the file store.
  awk 'FILENAME == ARGV[1] { size[$1] = $2; next }
       { if (!($1 in size)) { print "missing " $1; bad++ } else if (size[$1] != $2) { print "changed " $1; bad++ } }
       END { if (bad) exit 1 }' "$present" "$list"
}

make_point() {
  local name rp lines t_before rp_lsn t_after wal size contents manifest
  name=$(date -u +%Y%m%dT%H%M%SZ)
  rp=tm-$name
  # From here until the snapshot is stored the clean up deletes no content (F4). When this call returns, no deletion
  # runs any more.
  # A database from before V0071 knows no hold (its clean up does not ask for one either); the point says so and goes on.
  if [ "$(sql "select to_regprocedure('files_purge_hold_begin(text)') is not null")" = t ]; then
    sql "select files_purge_hold_begin('$holder')" > /dev/null || { fail "the clean up of the file store could not be held"; return 1; }
  else
    log "the database predates the hold of the clean up (V0071); the point is made without it"
  fi
  # The boundary: the moment before, the restore point and the moment after, in one session. Everything confirmed before
  # the first moment is in the point; nothing committed after the second is.
  lines=$(psql -X -A -t -q -v ON_ERROR_STOP=1 \
    -c "select to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')" \
    -c "select pg_create_restore_point('$rp')" \
    -c "select to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')") \
    || { fail "the restore point $rp could not be written"; return 1; }
  t_before=$(sed -n 1p <<< "$lines"); rp_lsn=$(sed -n 2p <<< "$lines"); t_after=$(sed -n 3p <<< "$lines")
  [ -n "$t_before" ] && [ -n "$rp_lsn" ] && [ -n "$t_after" ] || { fail "the database did not confirm the restore point"; return 1; }
  wal=$(sql "select pg_walfile_name('$rp_lsn')") || return 1
  size=$(sql "select setting::bigint * (case unit when '8kB' then 8192 when 'MB' then 1048576 else 1 end) from pg_settings where name = 'wal_segment_size'") || return 1
  # The segment with the restore point is closed now; the point waits until it is complete in the archive.
  sql "select pg_switch_wal()" > /dev/null || return 1
  wait_for_segment "$wal" "$size" || return 1
  check_chain "$(cat "$base_start_file")" "$wal" "$size" || return 1
  if ! check_contents > "$state/contents.report" 2>&1; then
    log "point $name refused: the file store does not hold every content of the database"
    sed -n '1,20p' "$state/contents.report" >&2
    return 1
  fi
  contents=$(wc -l < "$state/contents.list" | tr -d ' ')
  manifest=$state/manifest.json
  printf '{"point":"%s","time":"%s","timeAfter":"%s","restorePoint":"%s","lsn":"%s","segment":"%s","base":"%s","schema":"%s","contents":%s}\n' \
    "$name" "$t_before" "$t_after" "$rp" "$rp_lsn" "$wal" "$(cat "$base_name_file")" \
    "$(sql "select coalesce(max(version), 'none') from flyway_schema_history where success")" \
    "$contents" > "$manifest" || return 1
  store_snapshot point "$name" "$archive" "$files" "$manifest" || return 1
  printf '%s %s\n' "$name" "$t_before" > "$latest_file" || return 1
  log "point $name complete (database up to $t_before, restore point $rp, $contents contents)"
}

# A point stays restorable for the retention period. The base backups it needs are kept for one day longer, and the
# newest base is always kept, so the newest point can be restored even when no base was made within the period.
forget_old() {
  restic_quiet forget --tag point --keep-within "${retention}d" --keep-last 1 --prune > /dev/null || return 1
  restic_quiet forget --tag base --keep-within "$((retention + 1))d" --keep-last 1 --prune > /dev/null || return 1
}

# The local archive needs only the segments from the start of the current base on (H3): every later point names that
# base, and every older point carries the segments it needs in its own snapshot.
clean_archive() {
  [ -w "$archive" ] || { log "the archive is read only here; it is not cleaned"; return 0; }
  pg_archivecleanup "$archive" "$(cat "$base_start_file")" || { fail "the archive could not be cleaned"; return 1; }
}

run_once() {
  require_settings || return 1
  mkdir -p "$state" || return 1
  [ -d "$archive" ] || { fail "the archive directory $archive does not exist"; return 1; }
  [ -d "$files" ] || { fail "the file store $files does not exist"; return 1; }
  ensure_repository || return 1
  # One run at a time, also when the operator starts "once" next to the service.
  exec 9> "$state/lock" || return 1
  flock 9 || return 1
  # A run that was killed (a stopped container, a lost connection) leaves its lock on the target, and every later run would
  # fail on it. Under the lock above no other run of this installation uses the target, and every run of the service has
  # the same host name, so restic removes exactly the locks of processes that no longer exist and those older than 30
  # minutes.
  restic_quiet unlock > /dev/null || log "the target could not be checked for stale locks"
  forget_candidates || { fail "left over candidates could not be forgotten"; return 1; }
  if base_due; then
    make_base || return 1
  fi
  local code=0
  make_point || code=$?
  if [ "$(sql "select to_regprocedure('files_purge_hold_end(text)') is not null" 2> /dev/null)" = t ]; then
    sql "select files_purge_hold_end('$holder')" > /dev/null || log "the hold of the clean up could not be removed; it expires after 6 hours"
  fi
  [ "$code" -eq 0 ] || return "$code"
  forget_old || { fail "the retention could not be applied; the point itself is complete"; return 1; }
  clean_archive || return 1
}

case $mode in
  once)
    run_once
    ;;
  loop)
    require_settings || exit 1
    log "service started: a point every ${interval} s, a base backup every ${base_age} s, retention ${retention} days"
    while true; do
      # Each point is a process of its own: its failure ends exactly this point, and the next one is tried; the age of the
      # newest point shows the backlog.
      if ! "$PWD/backup.sh" once; then
        log "this point was not made; the next one follows in ${interval} s"
      fi
      sleep "$interval"
    done
    ;;
  *)
    fail "usage: backup.sh [once|loop]"
    exit 2
    ;;
esac
