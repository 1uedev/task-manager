#!/usr/bin/env bash
# The restore drill (TASK-131, DEC-161, Abnahmekriterien 1 bis 4). It plays the complete loss of the host through:
#
#   1. starts an installation with the backup service, sets up an administrator and synthetic demo data,
#   2. writes tickets while the backup makes points, and logs every confirmed write,
#   3. refuses a point whose file store lacks a content of the database (prüffall O06),
#   4. destroys database, file store and write ahead log archive,
#   5. restores into a fresh environment, with the newest point damaged so that the older one is taken (prüffall O08),
#   6. runs after-restore, which ends the sessions, holds the open mail replies and checks the file store (O09, O10),
#   7. starts the restored installation and checks the core flows,
#   8. measures the data loss and the time until the installation was usable again and writes the report.
#
# Only synthetic data and throw away passwords; it removes its containers and volumes at the end. The measured values
# belong to the machine of the run; the proof on the reference environment is part of TASK-134.
set -euo pipefail
cd "$(dirname "$0")/../.."

compose=tools/restore/compose.restore.yaml
project=tm-restore-check
export COMPOSE_PROJECT_NAME=$project
export TASK_MANAGER_PORT=${TASK_MANAGER_PORT:-18091}
export TASK_MANAGER_RESTORED_PORT=${TASK_MANAGER_RESTORED_PORT:-18092}
export TASK_MANAGER_DB_PASSWORD="${TASK_MANAGER_DB_PASSWORD:-drill-$(head -c 18 /dev/urandom | base64 | tr -d '/+=')}"
export TASK_MANAGER_DEMO_PASSWORD="${TASK_MANAGER_DEMO_PASSWORD:-drill-$(head -c 18 /dev/urandom | base64 | tr -d '/+=')}"
export TASK_MANAGER_BACKUP_PASSWORD="${TASK_MANAGER_BACKUP_PASSWORD:-drill-$(head -c 18 /dev/urandom | base64 | tr -d '/+=')}"
results=${RESTORE_CHECK_RESULTS:-restore-results}
writes=$results/confirmed-writes.log
report=$results/restore-run.json
mkdir -p "$results"
rm -f "$writes" "$report"

# Every call knows the profile of the fresh environment, so that its services can also be started one by one.
dc() { docker compose -f "$compose" --profile restored "$@"; }
step() { echo; echo "== $*"; }
fail() { echo "FAILED: $*" >&2; exit 1; }
writer_pid=
cleanup() {
  [ -n "$writer_pid" ] && kill "$writer_pid" 2>/dev/null || true
  dc logs app worker backup restored-app > "$results/containers.log" 2>&1 || true
  # RESTORE_CHECK_KEEP=1 keeps containers and volumes of a failed run for a look; the next run removes them.
  [ "${RESTORE_CHECK_KEEP:-0}" = 1 ] || dc down -v --remove-orphans > /dev/null 2>&1 || true
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
db() { dc exec -T db psql -X -A -t -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager -c "$1"; }
restored_db() { dc exec -T restored-db psql -X -A -t -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager -c "$1"; }

step "Build the images and start the installation"
if [ "${RESTORE_CHECK_SKIP_BUILD:-0}" != 1 ]; then
  dc build app backup
fi
# The named volumes of the drill belong to root at first; in an installation the operator gives the archive directory to
# the account of the database and the state and target directory to the account of the backup service
# (docs/BACKUP_RESTORE.md).
dc run --rm --user 0:0 --entrypoint bash db -c 'mkdir -p /archive && chown 999:999 /archive' > /dev/null
dc up -d db app worker
dc run --rm --user 0:0 --entrypoint bash backup -c 'mkdir -p /state /target /restore && chown postgres:postgres /state /target /restore' > /dev/null
await_ready "$TASK_MANAGER_PORT" || { dc logs app; fail "the installation did not become ready"; }

step "Set up the administrator and synthetic demo data"
bootstrap=$(dc run --rm app bootstrap-admin admin)
one_time=$(printf '%s' "$bootstrap" | sed -n 's/^One time password (shown only now): //p' | tr -d '\r')
[ -n "$one_time" ] || fail "bootstrap-admin printed no one time password"
docker run --rm --network "${project}_default" -v "$PWD/tools/demo:/demo:ro" \
  -e TASK_MANAGER_URL=http://app:8080 -e TASK_MANAGER_DEMO_PASSWORD \
  python:3.13-slim python -I -u /demo/demo.py seed --one-time-password "$one_time" > "$results/seed.log" 2>&1 \
  || { tail -20 "$results/seed.log"; fail "the demo data could not be created"; }
project_id=$(db "select id from project order by key limit 1" | tr -d ' \r')
[ -n "$project_id" ] || fail "the demo data created no project"

step "Write tickets while the backup makes points"
python3 -I tools/restore/writer.py --url "http://127.0.0.1:$TASK_MANAGER_PORT" --project "$project_id" --log "$writes" --pause 1 &
writer_pid=$!
sleep 5
dc run --rm backup once > "$results/backup-first.log" 2>&1 || { cat "$results/backup-first.log"; fail "the first point failed"; }
first_point=$(sed -n 's/.*point \([0-9TZ]*\) complete.*/\1/p' "$results/backup-first.log" | tail -n 1)
[ -n "$first_point" ] || fail "the first point was not reported as complete"
sleep 10
dc run --rm backup once > "$results/backup-second.log" 2>&1 || { cat "$results/backup-second.log"; fail "the second point failed"; }
second_point=$(sed -n 's/.*point \([0-9TZ]*\) complete.*/\1/p' "$results/backup-second.log" | tail -n 1)
[ -n "$second_point" ] && [ "$second_point" != "$first_point" ] || fail "the second point was not made"

# A third point with a base backup of its own, so that the damage hits only this point: its base backup disappears from
# the target, exactly as a lost file of the target would do. The points before it stay untouched.
dc run --rm -e TASK_MANAGER_BACKUP_BASE_AGE=0 backup once > "$results/backup-third.log" 2>&1 \
  || { cat "$results/backup-third.log"; fail "the third point failed"; }
third_point=$(sed -n 's/.*point \([0-9TZ]*\) complete.*/\1/p' "$results/backup-third.log" | tail -n 1)
[ -n "$third_point" ] && [ "$third_point" != "$second_point" ] || fail "the third point was not made"

step "O06: a point whose file store lacks a content of the database is refused"
attachment=$(cat /proc/sys/kernel/random/uuid)
ticket=$(db "select id from ticket limit 1" | tr -d ' \r')
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

step "The host is lost"
last_write=$(tail -n 1 "$writes" | cut -d' ' -f1)
last_number=$(tail -n 1 "$writes" | cut -d' ' -f2)
kill "$writer_pid" 2>/dev/null || true
wait "$writer_pid" 2>/dev/null || true
writer_pid=
failure_at=$(date -u +%s)
dc kill db app worker > /dev/null
dc rm -f db app worker > /dev/null
docker volume rm -f "${project}_db-data" "${project}_files-data" "${project}_wal-archive" > /dev/null
echo "database, file store and archive are gone"

step "O08: the newest point is damaged, so the older one is restored"
damaged_base=$(dc run --rm --entrypoint bash backup -c \
  'sed -n "s/.*\"base\":\"\([0-9a-f]*\)\".*/\1/p" /state/manifest.json' | tr -d ' \r\n')
[ -n "$damaged_base" ] || fail "the manifest of the third point names no base backup"
dc run --rm --user 0:0 --entrypoint bash backup -c "rm -f /target/repository/snapshots/$damaged_base" > /dev/null
set +e
dc run --rm --entrypoint /opt/backup/restore.sh backup restore /restore "$third_point" > "$results/restore-damaged.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] || { cat "$results/restore-damaged.log"; fail "the damaged point was restored as if it were fine"; }
grep -q "cannot be read" "$results/restore-damaged.log" \
  || { cat "$results/restore-damaged.log"; fail "the refusal does not name the unreadable part"; }
dc run --rm --entrypoint /opt/backup/restore.sh backup restore /restore > "$results/restore.log" 2>&1 \
  || { cat "$results/restore.log"; fail "no point could be restored"; }
used_point=$(sed -n 's/.*point \([0-9TZ]*\) restored into.*/\1/p' "$results/restore.log" | tail -n 1)
[ "$used_point" = "$second_point" ] || fail "the restore took $used_point instead of the intact point $second_point"
grep -q "is unusable; trying the next older one" "$results/restore.log" \
  || { cat "$results/restore.log"; fail "the restore did not report that it fell back to an older point"; }

# The restore wrote the file store as the account of the backup service; the operator hands it to the account of the
# application, step 4 of the procedure in docs/BACKUP_RESTORE.md.
dc run --rm --user 0:0 --entrypoint bash backup -c 'chown -R 10001:10001 /restore/files' > /dev/null

step "Start the restored database and mark the restore"
dc up -d restored-db
deadline=$((SECONDS + 240))
until dc exec -T restored-db pg_isready -U taskmanager -d taskmanager > /dev/null 2>&1; do
  [ "$SECONDS" -lt "$deadline" ] || { dc logs restored-db; fail "the restored database did not open"; }
  sleep 2
done
dc run --rm --entrypoint /opt/backup/restore.sh -e PGHOST=restored-db backup mark /restore > "$results/mark.log" 2>&1 \
  || { cat "$results/mark.log"; fail "the marker of the restore was not written"; }

step "The restored installation refuses to start before after-restore"
set +e
dc up -d restored-app > /dev/null 2>&1
started=$(await_ready "$TASK_MANAGER_RESTORED_PORT" 60; echo $?)
set -e
[ "$started" -ne 0 ] || fail "the restored installation served requests before after-restore ran"
dc logs restored-app 2>&1 | grep -q "was not released yet" || { dc logs restored-app; fail "the refusal does not name the restore"; }
dc stop restored-app > /dev/null

step "after-restore prepares the installation"
sessions_before=$(restored_db "select count(*) from identity_session where revoked_at is null" | tr -d ' \r')
dc run --rm --entrypoint java restored-app -jar /app/app.jar after-restore "Probe" > "$results/after-restore.log" 2>&1 \
  || { cat "$results/after-restore.log"; fail "after-restore did not end with success"; }
grep -q "The installation is released." "$results/after-restore.log" \
  || { cat "$results/after-restore.log"; fail "after-restore did not release the installation"; }
grep -q "Accounts with company rights: " "$results/after-restore.log" || fail "after-restore did not name the rights holders"
[ "$(restored_db "select count(*) from identity_session where revoked_at is null" | tr -d ' \r')" = 0 ] \
  || fail "after-restore left a session open"
[ "$(restored_db "select count(*) from helpdesk_reply where state in ('pending', 'sending')" | tr -d ' \r')" = 0 ] \
  || fail "after-restore left an open mail reply"
[ "$(restored_db "select count(*) from ai_suggestion" | tr -d ' \r')" = 0 ] || fail "after-restore left an AI suggestion"
[ "$(restored_db "select count(*) from operations_restore_pending" | tr -d ' \r')" = 0 ] || fail "the marker was not removed"

step "The restored installation is usable"
dc up -d restored-app
await_ready "$TASK_MANAGER_RESTORED_PORT" || { dc logs restored-app; fail "the restored installation did not become ready"; }
usable_at=$(date -u +%s)
jar=$(mktemp -d)
cookies=$jar/cookies
# The first call opens the session and hands out the token every writing call has to carry.
token=$(api -c "$cookies" "http://127.0.0.1:$TASK_MANAGER_RESTORED_PORT/api/v1/session" \
  | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p')
[ -n "$token" ] || fail "the restored installation handed out no token"
csrf=$(api -b "$cookies" -c "$cookies" -X POST -H 'Content-Type: application/json' -H "X-CSRF-Token: $token" \
  -d "{\"username\":\"admin\",\"password\":\"$TASK_MANAGER_DEMO_PASSWORD\"}" \
  "http://127.0.0.1:$TASK_MANAGER_RESTORED_PORT/api/v1/session" | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p')
[ -n "$csrf" ] || fail "the administrator could not log in to the restored installation"
tickets=$(api -b "$cookies" "http://127.0.0.1:$TASK_MANAGER_RESTORED_PORT/api/v1/projects/$project_id/tickets?limit=5" \
  | grep -o '"number"' | wc -l | tr -d ' ')
[ "$tickets" -gt 0 ] || fail "the restored installation lists no ticket"
search=$(api -b "$cookies" "http://127.0.0.1:$TASK_MANAGER_RESTORED_PORT/api/v1/search/tickets?q=Wiederherstellungsprobe" \
  | sed -n 's/.*"total":\([0-9]*\).*/\1/p')
[ -n "$search" ] || fail "the search of the restored installation did not answer"
[ "$search" -gt 0 ] || fail "the search of the restored installation finds no ticket of the writer"
rm -rf "$jar"

step "Measure the data loss and the recovery time"
# Every line of the log is a write the installation confirmed. The newest ticket the restored installation holds decides
# how much of that work the backup point carried; the loss is the time between it and the last confirmed write.
present=$(restored_db "select max(number) from ticket where title like 'Wiederherstellungsprobe %'" | tr -d ' \r')
[ -n "$present" ] || fail "the restored installation holds no ticket of the writer"
newest_present=$(awk -v n="$present" '$2 == n { print $1 }' "$writes" | tail -n 1)
[ -n "$newest_present" ] || fail "the writer logged no write for ticket $present"
loss=$(( $(date -u -d "$last_write" +%s) - $(date -u -d "$newest_present" +%s) ))
recovery=$(( usable_at - failure_at ))
confirmed=$(wc -l < "$writes" | tr -d ' ')
cat > "$report" <<JSON
{
  "task": "TASK-131",
  "machine": "$(uname -sm), $(nproc) vCPU",
  "points": {"first": "$first_point", "second": "$second_point", "damaged": "$third_point", "restored": "$used_point"},
  "confirmedWrites": $confirmed,
  "lastConfirmedWrite": {"at": "$last_write", "ticket": $last_number},
  "newestWriteAfterRestore": {"at": "$newest_present", "ticket": $present},
  "dataLossSeconds": $loss,
  "recoverySeconds": $recovery,
  "targets": {"dataLossSeconds": 3600, "recoverySeconds": 14400}
}
JSON
cat "$report"
[ "$loss" -le 3600 ] || fail "the data loss of $loss s is above the target of 3600 s"
[ "$recovery" -le 14400 ] || fail "the recovery of $recovery s is above the target of 14400 s"
echo
echo "Restore drill passed: data loss ${loss} s, usable again after ${recovery} s. Report: $report"
