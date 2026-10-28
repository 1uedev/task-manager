#!/usr/bin/env bash
# Checks the packaged backend against a real, initially empty PostgreSQL database:
# refusal without settings, first start with migration, graceful stop, restart without
# schema changes. Expects the TASK_MANAGER_DB_* settings for the application and the
# libpq variables PGHOST, PGPORT, PGUSER, PGPASSWORD and PGDATABASE for the same database.
set -euo pipefail

cd "$(dirname "$0")/.."
jar=$(ls target/task-manager-backend-*.jar | grep -v -- '-plain' | head -n 1)
port=${TASK_MANAGER_HTTP_PORT:-18080}
export TASK_MANAGER_HTTP_PORT=$port
logs=target/lifecycle
mkdir -p "$logs"
# The file store of the attachments (TASK-051) is a setting of the web application; it lies in a directory of its own.
export TASK_MANAGER_FILES_DIR=$PWD/$logs/files
rm -rf "$TASK_MANAGER_FILES_DIR"

pid=
phase=setup
fail() { echo "FAILED in phase '$phase': $*" >&2; exit 1; }
# Never leave the application running when the check stops early. A forced kill here is cleanup
# only; a successful stop is proven separately by the exit code and the graceful shutdown log.
cleanup() {
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    echo "Cleanup: stopping leftover process $pid after phase '$phase'" >&2
    kill -KILL "$pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT
# An exited child stays a zombie until it is reaped by wait, and kill -0 still succeeds for it.
alive() { kill -0 "$pid" 2>/dev/null && ! ps -o stat= -p "$pid" | grep -q Z; }
ready() { curl -fsS --connect-timeout 2 --max-time 5 "http://localhost:$port/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; }
sql() { psql -X -A -t -q -v ON_ERROR_STOP=1 -c "$1"; }
history() { sql "select version || ' ' || success || ' ' || installed_on from flyway_schema_history order by installed_rank"; }

[ "$(sql "select count(*) from information_schema.tables where table_schema = 'public'")" = 0 ] \
  || fail "database is not empty before the first start"

phase=missing-settings
echo "== Refusal without database settings"
set +e
env -u TASK_MANAGER_DB_URL -u TASK_MANAGER_DB_USER -u TASK_MANAGER_DB_PASSWORD \
  timeout 60 java -jar "$jar" > "$logs/missing-settings.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] && [ "$code" -ne 124 ] || fail "start without settings ended with $code"
grep -q "required settings are missing: TASK_MANAGER_DB_PASSWORD (spring.datasource.password), TASK_MANAGER_DB_URL (spring.datasource.url), TASK_MANAGER_DB_USER (spring.datasource.username)" \
  "$logs/missing-settings.log" || fail "refusal does not name the missing settings"
! grep -q "Tomcat initialized" "$logs/missing-settings.log" || fail "server started before the settings check"
[ "$(sql "select count(*) from information_schema.tables where table_schema = 'public'")" = 0 ] \
  || fail "refused start changed the database"

run() {
  local name=$1 code deadline watchdog
  phase="$name: start"
  java -jar "$jar" > "$logs/$name.log" 2>&1 &
  pid=$!
  deadline=$((SECONDS + 120))
  until ready; do
    alive || { cat "$logs/$name.log"; fail "process ended before it was ready"; }
    [ "$SECONDS" -lt "$deadline" ] || { cat "$logs/$name.log"; fail "not ready within 120 s"; }
    sleep 1
  done
  phase="$name: stop"
  kill -TERM "$pid"
  # A stop that hangs is killed after 60 s; the resulting exit code 137 fails the check below.
  ( sleep 60; kill -KILL "$pid" ) 2>/dev/null &
  watchdog=$!
  set +e
  wait "$pid"
  code=$?
  set -e
  pkill -P "$watchdog" 2>/dev/null || true
  kill "$watchdog" 2>/dev/null || true
  wait "$watchdog" 2>/dev/null || true
  pid=
  [ "$code" -ne 137 ] || { cat "$logs/$name.log"; fail "no exit within 60 s after SIGTERM"; }
  # 143 = terminated by SIGTERM after the JVM shutdown hooks ran.
  [ "$code" -eq 143 ] || fail "$name: exit code $code after SIGTERM"
  grep -q "Graceful shutdown complete" "$logs/$name.log" || fail "$name: no graceful shutdown"
  ! grep -E " ERROR " "$logs/$name.log" || fail "$name: error in log"
}

echo "== First start on the empty database"
run first-start
phase="first-start: history"
after_first=$(history)
echo "$after_first"
[ "$(sql "select string_agg(version || ':' || success, ',' order by installed_rank) from flyway_schema_history")" = '0001:true,0002:true,0003:true,0004:true,0005:true,0007:true,0008:true,0009:true,0010:true,0011:true,0012:true,0013:true,0014:true,0015:true,0016:true,0017:true,0018:true,0019:true,0020:true,0021:true,0022:true,0023:true,0024:true,0025:true' ] \
  || fail "unexpected history after first start"
grep -q 'Successfully applied 24 migrations' "$logs/first-start.log" || fail "first start did not migrate"

phase=restart
echo "== Restart"
run restart
[ "$(history)" = "$after_first" ] || fail "restart changed the migration history"
grep -q 'Schema "public" is up to date. No migration necessary.' "$logs/restart.log" || fail "restart did not report an up-to-date schema"
[ "$(sql "select string_agg(table_name, ',' order by table_name) from information_schema.tables where table_schema = 'public'")" = 'appearance_audit_event,appearance_setting,file_attachment,file_audit_event,flyway_schema_history,helpdesk_audit_event,helpdesk_contact,helpdesk_contact_address,helpdesk_customer,helpdesk_customer_project,helpdesk_mailbox,helpdesk_message,helpdesk_ticket_customer,identity_account,identity_audit_event,identity_ldap_setting,identity_login_throttle,identity_session,identity_setting,job,job_audit_event,job_schedule,job_worker,permissions_action,permissions_audit,permissions_field,permissions_group,permissions_membership,permissions_revision,platform_company,project,project_audit_event,project_configuration,project_configuration_draft,project_member,search_project,search_project_tag,search_state,search_tag,search_ticket,search_ticket_custom,search_ticket_tag,tag,tag_audit_event,tag_project,tag_ticket,ticket,ticket_change,ticket_counter' ] \
  || fail "unexpected schema objects"
[ "$(sql "select (select count(*) from platform_company) + (select count(*) from permissions_revision) + (select count(*) from permissions_action) + (select count(*) from permissions_membership)")" = 0 ] \
  || fail "unexpected default identities or grants"
[ "$(sql "select count(*) from identity_account")" = 0 ] || fail "a migration or start created an account"

phase=bootstrap-admin
echo "== Container command: first administrator"
set +e
java -jar "$jar" bootstrap-admin lifecycle-admin > "$logs/bootstrap.out" 2> "$logs/bootstrap.err"
code=$?
set -e
[ "$code" -eq 0 ] || { cat "$logs/bootstrap.out" "$logs/bootstrap.err"; fail "bootstrap-admin exited with $code"; }
grep -q '^Account: lifecycle-admin$' "$logs/bootstrap.out" || fail "bootstrap-admin did not name the account"
secret=$(sed -n 's/^One time password (shown only now): //p' "$logs/bootstrap.out")
[ "${#secret}" -eq 20 ] || fail "bootstrap-admin did not print a one time password"
! grep -q "$secret" "$logs/bootstrap.err" || fail "one time password reached the error output"
[ "$(sql "select count(*) from identity_account where password_change_required and password_hash like '\$argon2id\$%'")" = 1 ] \
  || fail "first account is not stored as expected"
[ "$(sql "select count(*) from identity_account where password_hash like '%$secret%'")" = 0 ] || fail "one time password stored in clear"
# TASK-039: the same transaction created the company and the initial rights, and nothing beyond them.
[ "$(sql "select (select count(*) from platform_company) || ',' || (select count(*) from permissions_group) || ',' || (select count(*) from permissions_action where action_key in ('permissions.manage', 'accounts.manage', 'session_duration.manage', 'appearance.manage', 'projects.manage', 'tags.manage', 'jobs.manage', 'helpdesk.manage') and project_id is null) || ',' || (select count(*) from permissions_membership m join identity_account a on a.id = m.user_id where a.username = 'lifecycle-admin') || ',' || (select count(*) from permissions_field)")" = '1,1,8,1,0' ] \
  || fail "bootstrap-admin did not create the company and the initial administration as expected"

phase=bootstrap-refused
set +e
java -jar "$jar" bootstrap-admin second-admin > "$logs/bootstrap-second.out" 2> "$logs/bootstrap-second.err"
code=$?
set -e
[ "$code" -eq 2 ] || fail "second bootstrap-admin exited with $code instead of 2"
grep -q 'bootstrap.accounts_exist' "$logs/bootstrap-second.err" || fail "second bootstrap-admin did not name the reason"
! grep -q 'One time password' "$logs/bootstrap-second.out" || fail "second bootstrap-admin printed a password"
[ "$(sql "select count(*) from identity_account")" = 1 ] || fail "second bootstrap-admin created an account"
[ "$(sql "select count(*) from platform_company")" = 1 ] || fail "second bootstrap-admin created a company"

phase=rebuild-search
set +e
java -jar "$jar" rebuild-search > "$logs/rebuild-search.out" 2> "$logs/rebuild-search.err"
code=$?
set -e
[ "$code" -eq 0 ] || { cat "$logs/rebuild-search.out" "$logs/rebuild-search.err"; fail "rebuild-search exited with $code"; }
grep -q '^Search projection rebuilt.$' "$logs/rebuild-search.out" || fail "rebuild-search did not report the rebuild"
[ "$(sql "select count(*) from search_state")" = 1 ] || fail "rebuild-search did not record the built projection"

phase=started-with-admin
echo "== Start with an existing account"
run with-account
[ -d "$TASK_MANAGER_FILES_DIR/staging" ] && [ -d "$TASK_MANAGER_FILES_DIR/content" ] || fail "the web application did not prepare the file store"

phase=worker
echo "== Separate worker process (TASK-054)"
# The web runs above executed jobs themselves; the worker starts from a queue without schedules and must create them.
sql "delete from job; delete from job_schedule; delete from job_worker"
java -jar "$jar" worker > "$logs/worker.log" 2>&1 &
pid=$!
deadline=$((SECONDS + 120))
until [ "$(sql "select count(*) from job_worker")" = 1 ] \
    && [ "$(sql "select count(*) from job where kind = 'recurring.files.cleanup' and state = 'succeeded'")" = 1 ]; do
  alive || { cat "$logs/worker.log"; fail "the worker ended before it ran the clean up"; }
  [ "$SECONDS" -lt "$deadline" ] || { cat "$logs/worker.log"; fail "the worker did not run the clean up within 120 s"; }
  sleep 1
done
! curl -fsS --connect-timeout 2 --max-time 5 "http://localhost:$port/actuator/health" > /dev/null 2>&1 || fail "the worker serves HTTP"
kill -TERM "$pid"
( sleep 60; kill -KILL "$pid" ) 2>/dev/null &
watchdog=$!
set +e
wait "$pid"
code=$?
set -e
pkill -P "$watchdog" 2>/dev/null || true
kill "$watchdog" 2>/dev/null || true
wait "$watchdog" 2>/dev/null || true
pid=
[ "$code" -ne 137 ] || { cat "$logs/worker.log"; fail "the worker did not exit within 60 s after SIGTERM"; }
[ "$code" -eq 143 ] || [ "$code" -eq 0 ] || fail "worker: exit code $code after SIGTERM"
grep -q "Job executor .* stopped." "$logs/worker.log" || fail "worker: the executor did not stop in order"
! grep -E " ERROR " "$logs/worker.log" || fail "worker: error in log"
[ "$(sql "select count(*) from job_worker")" = 0 ] || fail "worker: the executor did not sign off"

phase=missing-files-dir
echo "== Refusal without a file store"
set +e
env -u TASK_MANAGER_FILES_DIR timeout 60 java -jar "$jar" > "$logs/missing-files-dir.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] && [ "$code" -ne 124 ] || fail "start without a file store ended with $code"
grep -q "required settings are missing: TASK_MANAGER_FILES_DIR (taskmanager.files.directory)" "$logs/missing-files-dir.log" \
  || fail "refusal does not name the missing file store"

echo "Lifecycle check passed"
