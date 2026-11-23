#!/usr/bin/env bash
# The restore drill (TASK-131, DEC-161; extended by TASK-135, DEC-165, after the review of TASK-131). It plays the complete
# loss of the host through, against a synthetic SFTP target on another container:
#
#   1. starts an installation with the backup service, sets up an administrator, synthetic demo data and the data the
#      restore has to carry or to stop: attachments and a wiki image with known checksums, a portal session, an AI
#      suggestion, and replies to a contact in "sending" and "pending" (F10),
#   2. makes points while nothing writes and restores one of them (F2),
#   3. lets the service in its loop fail at the base backup, at an unreadable file and at the target, and proves that no
#      such run offers a point (F1),
#   4. writes tickets while the backup makes points, with one write before, one transaction open across and one write after
#      the boundary of a point (F2), and refuses a point whose file store lacks a content (prüffall O06),
#   5. damages the three newest points in three ways the backup cannot see: a replaced content of the same size, a missing
#      segment in the middle of the write ahead log and a lost base backup (F5),
#   6. destroys database, file store, archive and the state of the backup service,
#   7. tries a restore into a target that is too small (F5), then restores into a fresh environment, where the three damaged
#      points are dropped one after the other and the intact one is taken (prüffall O08),
#   8. runs after-restore, which ends the sessions, holds the open replies, discards the suggestion and checks the file store
#      (O09, O10), starts web server and worker with the mail server accepting everything and proves that nothing is sent,
#   9. checks the core flows and the boundary of the point, and measures data loss and recovery time at stable limits (F11).
#
# Only synthetic data and throw away passwords and keys; it removes its containers and volumes at the end. The measured
# values belong to the machine of the run; the proof on the reference environment is part of TASK-134.
set -euo pipefail
cd "$(dirname "$0")/../.."

compose=tools/restore/compose.restore.yaml
project=tm-restore-check
export COMPOSE_PROJECT_NAME=$project
export TASK_MANAGER_PORT=${TASK_MANAGER_PORT:-18091}
export TASK_MANAGER_RESTORED_PORT=${TASK_MANAGER_RESTORED_PORT:-18092}
secret() { printf 'drill-%s' "$(head -c 18 /dev/urandom | base64 | tr -d '/+=')"; }
export TASK_MANAGER_DB_PASSWORD="${TASK_MANAGER_DB_PASSWORD:-$(secret)}"
export TASK_MANAGER_DEMO_PASSWORD="${TASK_MANAGER_DEMO_PASSWORD:-$(secret)}"
export TASK_MANAGER_BACKUP_PASSWORD="${TASK_MANAGER_BACKUP_PASSWORD:-$(secret)}"
export TASK_MANAGER_BACKUP_DB_PASSWORD="${TASK_MANAGER_BACKUP_DB_PASSWORD:-$(secret)}"
export TASK_MANAGER_APP_DB_PASSWORD="${TASK_MANAGER_APP_DB_PASSWORD:-$(secret)}"
export TASK_MANAGER_MIGRATE_DB_PASSWORD="${TASK_MANAGER_MIGRATE_DB_PASSWORD:-$(secret)}"
export TASK_MANAGER_SECRET_KEY="${TASK_MANAGER_SECRET_KEY:-$(head -c 32 /dev/urandom | base64)}"
results=${RESTORE_CHECK_RESULTS:-restore-results}
mkdir -p "$results"
results=$(cd "$results" && pwd)
writes=$results/confirmed-writes.log
report=$results/restore-run.json
state=$results/drill-state.json
rm -f "$writes" "$report" "$state"
url=http://127.0.0.1:$TASK_MANAGER_PORT
restored_url=http://127.0.0.1:$TASK_MANAGER_RESTORED_PORT

# Every call knows the profile of the fresh environment, so that its services can also be started one by one.
dc() { docker compose -f "$compose" --profile restored "$@"; }
step() { echo; echo "== $*"; }
fail() { echo "FAILED: $*" >&2; exit 1; }
writer_pid=
open_fd=
cleanup() {
  [ -n "$writer_pid" ] && kill "$writer_pid" 2>/dev/null || true
  [ -n "$open_fd" ] && exec 7>&- 2>/dev/null || true
  dc unpause worker > /dev/null 2>&1 || true
  dc logs app worker backup sftp mail restored-db restored-app restored-worker > "$results/containers.log" 2>&1 || true
  # RESTORE_CHECK_KEEP=1 keeps containers and volumes of a failed run for a look; the next run removes them.
  [ "${RESTORE_CHECK_KEEP:-0}" = 1 ] || dc down -v --remove-orphans > /dev/null 2>&1 || true
  docker volume rm -f "${project}_small-target" > /dev/null 2>&1 || true
}
trap cleanup EXIT

api() { curl -fsS --connect-timeout 5 --max-time 60 "$@"; }
ready() { api "http://127.0.0.1:$1/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; }
await_ready() {
  local port=$1 deadline=$((SECONDS + ${2:-240}))
  until ready "$port"; do
    [ "$SECONDS" -lt "$deadline" ] || return 1
    sleep 2
  done
}
db() { dc exec -T db psql -X -A -t -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager -c "$1" | tr -d '\r'; }
restored_db() { dc exec -T restored-db psql -X -A -t -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager -c "$1" | tr -d '\r'; }
# Root inside a container of the backup image, for the work an operator does once on the host: owners, keys, damage.
as_root() { dc run --rm --no-deps --user 0:0 --entrypoint bash backup -c "$1"; }
# restic as the backup service sees the target, for the checks and the damage of the drill.
restic_in() { dc run --rm --no-deps --entrypoint bash backup -c "export RESTIC_REPOSITORY=\$TASK_MANAGER_BACKUP_REPOSITORY; $1"; }
drill() { python3 -I tools/restore/drill.py "$@"; }
point_of() { sed -n 's/.*point \([0-9TZ]*\) complete.*/\1/p' "$1" | tail -n 1; }
# One point with "once"; prints its name. $1 names the log, the rest goes to docker compose run.
make_point() {
  local name=$1 point
  shift
  dc run --rm "$@" backup once > "$results/backup-$name.log" 2>&1 || { cat "$results/backup-$name.log" >&2; fail "the point $name failed"; }
  point=$(point_of "$results/backup-$name.log")
  [ -n "$point" ] || fail "the point $name was not reported as complete"
  # Two points of the same second would share a name.
  sleep 1
  printf '%s' "$point"
}
offered() { dc run --rm --no-deps backup points 2> "$results/points.err" | awk '{ print $1 }' | tr '\n' ' '; }
manifest_of() { restic_in "restic --quiet dump \$(restic --quiet snapshots --tag point=$1 --json | grep -o '\"id\":\"[0-9a-f]\\{64\\}\"' | head -n 1 | cut -d'\"' -f4) /state/manifest.json"; }
field() { sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p"; }

step "Build the images and set up the hosts"
# A run kept with RESTORE_CHECK_KEEP=1 is removed first.
dc down -v --remove-orphans > /dev/null 2>&1 || true
if [ "${RESTORE_CHECK_SKIP_BUILD:-0}" != 1 ]; then
  dc build app backup
fi
dc create db app worker backup sftp mail > /dev/null 2>&1
# The owners an operator sets once (docs/BACKUP_RESTORE.md, "Einrichten"): the archive belongs to the account of the
# database, state and restore target to the account of the backup service, the file store to the account of the
# application. The client key of the backup service lies in its home with mode 0600.
dc run --rm --no-deps --user 0:0 --entrypoint bash db -c 'chown 999:999 /archive' > /dev/null
as_root 'chown postgres:postgres /state /restore' > /dev/null
# The SFTP target: a key pair for the backup service, its public part with the target, and the host key of the target in
# known_hosts, so that the client never trusts an unknown host (F8).
dc run --rm --no-deps --user 0:0 -v "${project}_ssh-client:/client" -v "${project}_sftp-keys:/keys" --entrypoint bash sftp -c '
  rm -f /client/* && ssh-keygen -q -t ed25519 -N "" -C backup-drill -f /client/id_ed25519 < /dev/null &&
  cp /client/id_ed25519.pub /keys/backup.pub && rm /client/id_ed25519.pub && chown 999:999 /home/drill/upload' > /dev/null
dc up -d sftp mail
deadline=$((SECONDS + 60))
until host_key=$(dc exec -T sftp cat /etc/ssh/ssh_host_ed25519_key.pub 2>/dev/null) && [ -n "$host_key" ]; do
  [ "$SECONDS" -lt "$deadline" ] || fail "the SFTP target did not start"
  sleep 2
done
host_key=$(printf '%s' "$host_key" | awk '{ print $1 " " $2 }')
dc run --rm --no-deps --user 0:0 -v "${project}_ssh-client:/client" --entrypoint bash sftp -c "
  printf 'sftp %s\n' '$host_key' > /client/known_hosts &&
  printf 'Host sftp\n  User drill\n  IdentityFile ~/.ssh/id_ed25519\n  StrictHostKeyChecking yes\n  UserKnownHostsFile ~/.ssh/known_hosts\n  UpdateHostKeys no\n' > /client/config &&
  chown -R 999:999 /client && chmod 0700 /client && chmod 0600 /client/*" > /dev/null
dc exec -T mail sh -c 'echo hold > /var/sink/mode'
dc up -d db app worker
await_ready "$TASK_MANAGER_PORT" || { dc logs app; fail "the installation did not become ready"; }

step "Set up the administrator, synthetic demo data and the data the restore must carry or stop (F10)"
bootstrap=$(dc run --rm app bootstrap-admin admin)
one_time=$(printf '%s' "$bootstrap" | sed -n 's/^One time password (shown only now): //p' | tr -d '\r')
[ -n "$one_time" ] || fail "bootstrap-admin printed no one time password"
# The one time password goes to the demo tool through the environment, never as an argument (H2).
TASK_MANAGER_ONE_TIME_PASSWORD=$one_time python3 -I tools/demo/demo.py --url "$url" seed > "$results/seed.log" 2>&1 \
  || { tail -20 "$results/seed.log"; fail "the demo data could not be created"; }
unset one_time bootstrap
drill prepare --url "$url" --state "$state" --mail mail:2525 | tee "$results/prepare.log"
company=$(db "select id from platform_company")
admin_id=$(db "select id from identity_account where username = 'admin'")
db "insert into ai_run (id, company_id, account_id, kind, use_case, language, state, code, started_at, finished_at)
    values ('00000000-0000-4000-8000-000000000135', '$company', '$admin_id', 'use', 'ticket_summary', 'de', 'refused',
            'drill', now(), now());
    insert into ai_suggestion (run_id, company_id, account_id, sources, content, created_at, expires_at)
    values ('00000000-0000-4000-8000-000000000135', '$company', '$admin_id', '[]', '{\"summary\": \"Synthetischer Vorschlag\"}',
            now(), now() + interval '24 hours')" > /dev/null
db "create table drill_marker (name text primary key, written_at timestamptz not null default clock_timestamp())" > /dev/null
project_id=$(db "select id from project order by key limit 1")
[ -n "$project_id" ] || fail "the demo data created no project"

step "F2: points while nothing writes"
first_point=$(make_point first)
# Web server and worker stop, so that nothing but the backup service writes between the next two points.
dc stop app worker > /dev/null
idle_one=$(make_point idle-one)
idle_two=$(make_point idle-two)
dc run --rm --no-deps backup restore /restore "$idle_two" > "$results/restore-idle.log" 2>&1 \
  || { cat "$results/restore-idle.log"; fail "the point $idle_two without new writes could not be restored"; }
grep -q "point $idle_two restored and proven" "$results/restore-idle.log" || fail "the idle point was not proven"
dc up -d app worker
await_ready "$TASK_MANAGER_PORT" || fail "the installation did not come back"

step "F1: the loop never offers a failed run as a point"
before=$(offered)
loop_run() {
  local name=$1 container
  shift
  container=$(dc run -d -e TASK_MANAGER_BACKUP_INTERVAL=3 "$@" backup loop)
  # Stopped only while it waits between two runs, after the second failed run ended: a run killed in its middle may leave
  # a candidate behind on purpose, which the next run forgets, and that is not what this step checks.
  local deadline=$((SECONDS + 180))
  until [ "$(docker logs "$container" 2>&1 | grep -c "this point was not made")" -ge 2 ]; do
    [ "$SECONDS" -lt "$deadline" ] || break
    sleep 0.5
  done
  docker logs "$container" > "$results/loop-$name.log" 2>&1 || true
  docker rm -f "$container" > /dev/null
  grep -q "this point was not made" "$results/loop-$name.log" || { cat "$results/loop-$name.log"; fail "the loop did not report the failure ($name)"; }
  ! grep -q " complete" "$results/loop-$name.log" || { cat "$results/loop-$name.log"; fail "the loop reported a failed run as complete ($name)"; }
  [ "$(offered)" = "$before" ] || fail "the loop offered a point after the failure ($name)"
}
# The base backup fails: the database refuses the password of the backup account.
loop_run base-refused -e TASK_MANAGER_BACKUP_BASE_AGE=0 -e PGPASSWORD=wrong-password
grep -q "pg_basebackup failed" "$results/loop-base-refused.log" || fail "the loop did not name the failed base backup"
# restic cannot read one file of the file store and ends with status 3; its snapshot is forgotten, never offered.
unreadable=$(db "select id from operations_backup_content order by id limit 1")
# The backup service mounts the file store read only; the change goes through a container of its own.
files_root() { docker run --rm --user 0:0 -v "${project}_files-data:/data/files" --entrypoint "$1" task-manager-backup:drill "${@:2}"; }
files_root chmod 000 "/data/files/content/${unreadable:0:2}/$unreadable"
loop_run unreadable
files_root chmod 640 "/data/files/content/${unreadable:0:2}/$unreadable"
grep -q "restic ended with status 3" "$results/loop-unreadable.log" || fail "the loop did not name status 3 of restic"
[ -z "$(restic_in 'restic --quiet snapshots --tag candidate --json' | grep -o '"id"' || true)" ] \
  || fail "a candidate of a failed run stayed on the target"
# The target refuses the repository password.
loop_run target-refused -e RESTIC_PASSWORD=wrong-password

step "F10: a reply in sending and a reply waiting, both with their jobs"
drill reply --url "$url" --state "$state" --name sending
deadline=$((SECONDS + 120))
until [ "$(db "select state from helpdesk_reply where subject = 'Probe sending'")" = sending ]; do
  [ "$SECONDS" -lt "$deadline" ] || fail "the reply did not reach the state sending"
  sleep 2
done
# The worker freezes in the middle of the delivery; the next reply waits for it.
dc pause worker > /dev/null
drill reply --url "$url" --state "$state" --name pending
[ "$(db "select state from helpdesk_reply where subject = 'Probe pending'")" = pending ] || fail "the second reply does not wait"
[ "$(db "select count(*) from helpdesk_reply r join job j on j.id = r.job_id where r.subject like 'Probe %'")" = 2 ] \
  || fail "the replies have no jobs"
[ "$(db "select count(*) from identity_session s join identity_account a on a.id = s.account_id
         where a.username = 'lea.nordwerk' and s.revoked_at is null")" -ge 1 ] || fail "no portal session is open"
[ "$(db "select count(*) from ai_suggestion")" -ge 1 ] || fail "no AI suggestion exists"
[ "$(db "select count(*) from file_image")" -ge 1 ] || fail "no wiki image exists"

step "F2, F10: write tickets while the backup makes points, with writes around the boundary"
python3 -I tools/restore/writer.py --url "$url" --project "$project_id" --log "$writes" --pause 1 &
writer_pid=$!
sleep 5
db "insert into drill_marker (name) values ('before')" > /dev/null
# A transaction that begins before the boundary and is confirmed after it.
mkfifo "$results/open.fifo"
dc exec -T db psql -X -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager < "$results/open.fifo" > "$results/open.log" 2>&1 &
open_pid=$!
exec 7> "$results/open.fifo"
open_fd=7
echo "begin; insert into drill_marker (name) values ('open');" >&7
sleep 2
second_point=$(make_point second)
db "insert into drill_marker (name) values ('after')" > /dev/null
echo "commit;" >&7
exec 7>&-
open_fd=
wait "$open_pid" || { cat "$results/open.log"; fail "the open transaction was not confirmed"; }
rm -f "$results/open.fifo"
[ "$(db "select count(*) from drill_marker")" = 3 ] || fail "the markers were not all written"
second_manifest=$(manifest_of "$second_point")
[ -z "$(db "select holder from files_purge_hold")" ] || fail "the hold of the clean up stayed after the point"
sleep 5
third_point=$(make_point third)
sleep 5
fourth_point=$(make_point fourth)
fourth_base_start=$(as_root 'cat /state/base-start' | tr -d '\r')
sleep 5
fifth_point=$(make_point fifth -e TASK_MANAGER_BACKUP_BASE_AGE=0)
fifth_base=$(manifest_of "$fifth_point" | field base)
[ -n "$fifth_base" ] || fail "the manifest of the fifth point names no base"

step "O06: a point whose file store lacks a content of the database is refused"
attachment=$(cat /proc/sys/kernel/random/uuid)
ticket=$(db "select id from ticket limit 1")
db "insert into file_attachment (id, company_id, ticket_id, project_id, name, size_bytes, sha256, uploaded_at, uploaded_by)
    select '$attachment', company_id, id, project_id, 'ohne-inhalt.txt', 7, repeat('a', 64), now(), created_by
    from ticket where id = '$ticket'" > /dev/null
set +e
dc run --rm backup once > "$results/backup-incomplete.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] || { cat "$results/backup-incomplete.log"; fail "the point with a missing content was not refused"; }
grep -q "refused: the file store does not hold every content" "$results/backup-incomplete.log" \
  || { cat "$results/backup-incomplete.log"; fail "the refusal does not name the reason"; }
grep -q "missing $attachment" "$results/backup-incomplete.log" || fail "the refusal does not name the content"
db "delete from file_attachment where id = '$attachment'" > /dev/null

step "F11: the writer stops before the last confirmed write is read"
kill "$writer_pid"
wait "$writer_pid" 2>/dev/null || true
writer_pid=
# One read of the last line of the stable log: moment and number belong to the same write.
read -r last_write last_number _ < <(tail -n 1 "$writes")
[ -n "$last_write" ] && [ -n "$last_number" ] || fail "the writer logged no write"

step "F5: three damages the backup cannot see"
# The third point: one referenced content replaced by other bytes of the same size; the number of files stays the same.
victim=$(python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["attachments"][0]["id"])' "$state")
restic_in "set -e
  id=\$(restic --quiet snapshots --tag point=$third_point --json | grep -o '\"id\":\"[0-9a-f]\\{64\\}\"' | head -n 1 | cut -d'\"' -f4)
  work=/state/damage; rm -rf \$work
  restic --quiet restore \$id --target \$work > /dev/null
  file=\$(find \$work -type f -name $victim)
  size=\$(stat -c %s \$file)
  head -c \$size /dev/zero | tr '\\0' 'x' > \$file
  cd \$work
  restic --quiet backup --tag point --tag point=$third_point archive data/files state/manifest.json > /dev/null
  restic --quiet forget \$id > /dev/null
  rm -rf \$work" > "$results/damage-third.log" 2>&1 || { cat "$results/damage-third.log"; fail "the third point could not be damaged"; }
# The fourth point: the segment after the start of its base is missing from its archive.
missing=$(restic_in "restic --quiet ls \$(restic --quiet snapshots --tag point=$fourth_point --json | grep -o '\"id\":\"[0-9a-f]\\{64\\}\"' | head -n 1 | cut -d'\"' -f4)" \
  | sed -n 's#^/archive/\([0-9A-F]\{24\}\)$#\1#p' | sort | awk -v start="$fourth_base_start" '$1 > start' | head -n 1)
[ -n "$missing" ] || fail "the fourth point holds no segment after the start of its base"
restic_in "restic --quiet rewrite --forget --exclude /archive/$missing --tag point=$fourth_point" > "$results/damage-fourth.log" 2>&1 \
  || { cat "$results/damage-fourth.log"; fail "the fourth point could not be damaged"; }
# The fifth point: its own base backup is gone from the target.
restic_in "restic --quiet forget \$(restic --quiet snapshots --tag base=$fifth_base --json | grep -o '\"id\":\"[0-9a-f]\\{64\\}\"' | cut -d'\"' -f4)" \
  > "$results/damage-fifth.log" 2>&1 || { cat "$results/damage-fifth.log"; fail "the base of the fifth point could not be removed"; }

step "The host is lost"
failure_at=$(date -u +%s)
dc unpause worker > /dev/null
dc kill db app worker > /dev/null
dc rm -f -s db app worker backup > /dev/null
docker volume rm -f "${project}_db-data" "${project}_files-data" "${project}_wal-archive" "${project}_backup-state" > /dev/null
echo "database, file store, archive and the state of the backup service are gone"
as_root 'chown postgres:postgres /state /restore' > /dev/null

step "F5: a target that is too small fails every point and says so"
docker volume create --driver local --opt type=tmpfs --opt device=tmpfs --opt o=size=4m,uid=999,gid=999 \
  "${project}_small-target" > /dev/null
set +e
dc run --rm --no-deps -v "${project}_small-target:/small" backup restore /small > "$results/restore-small.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] || { cat "$results/restore-small.log"; fail "the restore into a full target ended with success"; }
grep -q "no backup point could be restored" "$results/restore-small.log" || fail "the failed restore does not say so"

step "O08: the damaged points are dropped one after the other and the intact one is restored"
dc run --rm --no-deps backup restore /restore > "$results/restore.log" 2>&1 \
  || { cat "$results/restore.log"; fail "no point could be restored"; }
used_point=$(sed -n 's/.*point \([0-9TZ]*\) restored and proven.*/\1/p' "$results/restore.log" | tail -n 1)
[ "$used_point" = "$second_point" ] || { cat "$results/restore.log"; fail "the restore took $used_point instead of the intact point $second_point"; }
grep -q "the base backup $fifth_base of point $fifth_point is not on the target" "$results/restore.log" \
  || fail "the lost base of the fifth point was not named"
grep -q "the recovery of $fourth_point" "$results/restore.log" || fail "the missing segment of the fourth point was not found"
grep -q "point $third_point does not hold every content of its database" "$results/restore.log" \
  && grep -q "changed $victim" "$results/restore.log" || fail "the replaced content of the third point was not found"
[ "$(grep -c "is unusable; trying the next older one" "$results/restore.log")" = 3 ] || fail "the restore did not drop exactly three points"
# Step 4 of docs/BACKUP_RESTORE.md: the restored data goes into the volumes of database and file store, with their owners.
dc run --rm restore-place > "$results/restore-place.log" 2>&1 || { cat "$results/restore-place.log"; fail "restore-place failed"; }
grep -q "point $second_point is in place" "$results/restore-place.log" || fail "restore-place did not name the point"

step "The restored installation refuses to start before after-restore"
dc up -d restored-db
deadline=$((SECONDS + 240))
until dc exec -T restored-db pg_isready -U taskmanager -d taskmanager > /dev/null 2>&1; do
  [ "$SECONDS" -lt "$deadline" ] || { dc logs restored-db; fail "the restored database did not open"; }
  sleep 2
done
[ "$(restored_db "select count(*) from operations_restore_pending")" = 1 ] || fail "the restore did not leave its marker"
set +e
dc up -d restored-app restored-worker > /dev/null 2>&1
started=$(await_ready "$TASK_MANAGER_RESTORED_PORT" 60; echo $?)
set -e
[ "$started" -ne 0 ] || fail "the restored installation served requests before after-restore ran"
dc logs restored-app 2>&1 | grep -q "was not released yet" || { dc logs restored-app; fail "the web server does not name the restore"; }
dc logs restored-worker 2>&1 | grep -q "was not released yet" || { dc logs restored-worker; fail "the worker does not name the restore"; }
dc stop restored-app restored-worker > /dev/null

step "F2: the database is exactly at the boundary of the point"
point_time=$(printf '%s' "$second_manifest" | field time)
point_after=$(printf '%s' "$second_manifest" | field timeAfter)
[ "$(restored_db "select string_agg(name, ',' order by name) from drill_marker")" = before ] \
  || fail "the markers around the boundary are not exactly the one before it"
# Every write the installation confirmed before the boundary is there, no write sent after it is.
restored_db "select number from ticket where project_id = '$project_id' and title like 'Wiederherstellungsprobe %'" \
  > "$results/restored-numbers.txt"
checked=$(python3 -I tools/restore/boundary.py "$writes" "$results/restored-numbers.txt" "$point_time" "$point_after") \
  || fail "the restored writes do not match the boundary of the point"

step "after-restore prepares the installation"
dc run --rm restored-app after-restore "Probe" > "$results/after-restore.log" 2>&1 \
  || { cat "$results/after-restore.log"; fail "after-restore did not end with success"; }
grep -q "The installation is released." "$results/after-restore.log" \
  || { cat "$results/after-restore.log"; fail "after-restore did not release the installation"; }
grep -q "Accounts with company rights: " "$results/after-restore.log" || fail "after-restore did not name the rights holders"
[ "$(restored_db "select count(*) from identity_session where revoked_at is null")" = 0 ] || fail "after-restore left a session open"
[ "$(restored_db "select count(*) from helpdesk_reply where state in ('pending', 'sending')")" = 0 ] \
  || fail "after-restore left an open mail reply"
[ "$(restored_db "select count(*) from helpdesk_reply where subject like 'Probe %' and state = 'uncertain'")" = 2 ] \
  || fail "after-restore did not hold both replies as uncertain"
[ "$(restored_db "select count(*) from ai_suggestion")" = 0 ] || fail "after-restore left an AI suggestion"
[ "$(restored_db "select count(*) from operations_restore_pending")" = 0 ] || fail "the marker was not removed"

step "F10: web server and worker start, and nothing is sent again"
dc exec -T mail sh -c 'echo accept > /var/sink/mode'
connections=$(dc exec -T mail sh -c 'grep -c "connection in mode" /var/sink/sink.log || true' | tr -d '\r')
dc up -d restored-app restored-worker
await_ready "$TASK_MANAGER_RESTORED_PORT" || { dc logs restored-app; fail "the restored installation did not become ready"; }
drill verify --url "$restored_url" --state "$state" | tee "$results/verify.log"
usable_at=$(date -u +%s)
# The worker runs its jobs for a while; the mail server must see no delivery of a reply from before the failure.
sleep 30
dc logs restored-worker 2>&1 | grep -q "Started" || { dc logs restored-worker; fail "the restored worker did not start"; }
[ "$(dc exec -T mail sh -c 'grep -c "connection in mode" /var/sink/sink.log || true' | tr -d '\r')" = "$connections" ] \
  || fail "the restored worker opened a connection to the mail server"
[ -z "$(dc exec -T mail sh -c 'ls /var/sink/received 2>/dev/null' | tr -d '\r')" ] || fail "a mail reached the mail server"

step "F11: data loss and recovery time"
present=$(restored_db "select max(number) from ticket where project_id = '$project_id' and title like 'Wiederherstellungsprobe %'")
[ -n "$present" ] || fail "the restored installation holds no ticket of the writer"
newest_present=$(awk -v n="$present" '$2 == n { print $1 }' "$writes" | tail -n 1)
[ -n "$newest_present" ] || fail "the writer logged no write for ticket $present"
[ "$present" -le "$last_number" ] || fail "the restored installation holds ticket $present after the last confirmed one $last_number"
loss=$(( $(date -u -d "$last_write" +%s) - $(date -u -d "$newest_present" +%s) ))
recovery=$(( usable_at - failure_at ))
[ "$loss" -ge 0 ] || fail "the measured data loss is negative ($loss s)"
[ "$recovery" -gt 0 ] || fail "the measured recovery time is not positive ($recovery s)"
confirmed=$(wc -l < "$writes" | tr -d ' ')
cat > "$report" <<JSON
{
  "task": "TASK-135",
  "machine": "$(uname -sm), $(nproc) vCPU",
  "target": "sftp",
  "points": {"first": "$first_point", "idle": ["$idle_one", "$idle_two"], "restored": "$used_point",
             "damaged": {"replacedContent": "$third_point", "missingSegment": "$fourth_point", "lostBase": "$fifth_point"}},
  "boundary": {"time": "$point_time", "timeAfter": "$point_after", "confirmedBeforeChecked": $checked},
  "confirmedWrites": $confirmed,
  "lastConfirmedWrite": {"at": "$last_write", "ticket": $last_number},
  "newestWriteAfterRestore": {"at": "$newest_present", "ticket": $present},
  "dataLossSeconds": $loss,
  "recoverySeconds": $recovery,
  "recoveryEndsAfter": "the core flows of tools/restore/drill.py verify",
  "targets": {"dataLossSeconds": 3600, "recoverySeconds": 14400}
}
JSON
cat "$report"
[ "$loss" -le 3600 ] || fail "the data loss of $loss s is above the target of 3600 s"
[ "$recovery" -le 14400 ] || fail "the recovery of $recovery s is above the target of 14400 s"
echo
echo "Restore drill passed: data loss ${loss} s, usable again after ${recovery} s. Report: $report"
