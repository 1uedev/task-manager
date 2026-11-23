#!/usr/bin/env bash
# The restore (TASK-131, DEC-161). It takes a backup point from the target and lays out a fresh installation:
#
#   restore.sh points                      lists the points on the target, newest first
#   restore.sh restore <target> [point]    restores the newest point, or the named one, into <target>
#   restore.sh mark <target>               writes the marker of the restore into the database that was restored
#
# "restore" writes <target>/pgdata (ready to start, it recovers to the moment of the point), <target>/files (the file
# store) and <target>/manifest.json. It never touches a running installation. When the newest point cannot be read or
# does not hold what its manifest names, the next older one is taken and the step is reported, never a wrong success
# (prüffall O08). After the database was started, "mark" writes the row that keeps the web server and the worker from
# starting until the command after-restore ran.
cd "$(dirname "$0")"
# shellcheck source=lib.sh
. ./lib.sh

usage() {
  cat >&2 <<'TEXT'
usage: restore.sh points
       restore.sh restore <target directory> [point]
       restore.sh mark <target directory>
TEXT
  return 2
}

# The points on the target, newest first, as "point snapshot".
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

# Restores one point and proves it: the snapshot must hold the manifest, the base backup it names must exist, and the
# number of contents must match the file store. Returns non zero when the point is unusable.
restore_point() {
  local point=$1 snapshot=$2 target=$3 manifest base contents found
  log "trying point $point (snapshot $snapshot)"
  # Only the content of the target is emptied, because the target itself can be a mount point.
  mkdir -p "$target"
  find "$target" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  restic --quiet restore "$snapshot" --target "$target/raw" > /dev/null || return 1
  manifest=$(find "$target/raw" -name manifest.json -type f | head -n 1)
  [ -n "$manifest" ] || { log "point $point holds no manifest"; return 1; }
  cp "$manifest" "$target/manifest.json"
  base=$(sed -n 's/.*"base":"\([0-9a-f]*\)".*/\1/p' "$target/manifest.json")
  contents=$(sed -n 's/.*"contents":\([0-9]*\).*/\1/p' "$target/manifest.json")
  [ -n "$base" ] && [ -n "$contents" ] || { log "the manifest of $point is incomplete"; return 1; }

  mv "$(dirname "$(find "$target/raw" -type d -name content | head -n 1)")" "$target/files" 2>/dev/null \
    || { log "point $point holds no file store"; return 1; }
  found=$(find "$target/files/content" -type f | wc -l | tr -d ' ')
  [ "$found" -ge "$contents" ] \
    || { log "point $point holds $found contents, its manifest names $contents"; return 1; }

  local archive_dir
  archive_dir=$(find "$target/raw" -type d -name "$(basename "$archive")" | head -n 1)
  [ -n "$archive_dir" ] || { log "point $point holds no write ahead log archive"; return 1; }
  mv "$archive_dir" "$target/archive"

  restic --quiet restore "$base" --target "$target/base" > /dev/null \
    || { log "the base backup $base of point $point cannot be read"; return 1; }
  local data
  data=$(find "$target/base" -type f -name PG_VERSION | head -n 1)
  [ -n "$data" ] || { log "the base backup $base holds no data directory"; return 1; }
  mv "$(dirname "$data")" "$target/pgdata"
  rm -rf "$target/raw" "$target/base"

  local moment lsn
  moment=$(sed -n 's/.*"time":"\([^"]*\)".*/\1/p' "$target/manifest.json")
  lsn=$(sed -n 's/.*"lsn":"\([0-9A-F/]*\)".*/\1/p' "$target/manifest.json")
  [ -n "$moment" ] && [ -n "$lsn" ] || { log "the manifest of $point names no moment"; return 1; }
  # The database recovers exactly to the position of the point and then opens for writing. The position is taken, not the
  # moment: the point knows the position it was written at, and the archive is complete up to it, while a moment can lie
  # a little behind the last archived record and the recovery would then end with an error. The archive lies next to the
  # data directory under the same path the database container must see, and the restored installation archives nothing
  # until its own backup service is set up again.
  local absolute
  absolute=$(cd "$target" && pwd)
  cat >> "$target/pgdata/postgresql.auto.conf" <<CONF
restore_command = 'cp $absolute/archive/%f %p'
recovery_target_lsn = '$lsn'
recovery_target_action = 'promote'
archive_mode = off
CONF
  touch "$target/pgdata/recovery.signal"
  chmod 0700 "$target/pgdata"
  printf '%s\n' "$point" > "$target/point"
  log "point $point restored into $target (database recovers to $moment, position $lsn, $found contents)"
  log "give $target/files to the account of the application before the web server starts (chown -R 10001:10001)"
}

case ${1:-} in
  points)
    require_settings
    points
    ;;
  restore)
    [ $# -ge 2 ] || usage
    require_settings
    target=$2
    wanted=${3:-}
    list=$(points)
    [ -n "$list" ] || fail "the target holds no backup point"
    if [ -n "$wanted" ]; then
      list=$(printf '%s\n' "$list" | awk -v want="$wanted" '$1 == want')
      [ -n "$list" ] || fail "the target holds no point $wanted"
    fi
    while read -r point snapshot; do
      [ -n "$point" ] || continue
      if restore_point "$point" "$snapshot" "$target"; then
        exit 0
      fi
      log "point $point is unusable; trying the next older one"
    done <<< "$list"
    fail "no backup point could be restored"
    ;;
  mark)
    [ $# -ge 2 ] || usage
    point=$(cat "$2/point")
    [ -n "$point" ] || fail "$2 holds no restored point"
    sql "insert into operations_restore_pending (point, restored_at) values ('$point', now()) on conflict (point) do nothing" > /dev/null
    log "the restore of point $point waits for the command after-restore"
    ;;
  *) usage ;;
esac
