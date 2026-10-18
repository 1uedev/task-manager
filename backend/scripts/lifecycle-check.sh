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

fail() { echo "FAILED: $*" >&2; exit 1; }
sql() { psql -X -A -t -q -v ON_ERROR_STOP=1 -c "$1"; }
history() { sql "select version || ' ' || success || ' ' || installed_on from flyway_schema_history order by installed_rank"; }

[ "$(sql "select count(*) from information_schema.tables where table_schema = 'public'")" = 0 ] \
  || fail "database is not empty before the first start"

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
  local name=$1 pid code
  java -jar "$jar" > "$logs/$name.log" 2>&1 &
  pid=$!
  for _ in $(seq 120); do
    if curl -fsS "http://localhost:$port/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then
      break
    fi
    kill -0 "$pid" 2>/dev/null || { cat "$logs/$name.log"; fail "$name: process ended before it was ready"; }
    sleep 1
  done
  curl -fsS "http://localhost:$port/actuator/health/readiness" | grep -q '"UP"' || fail "$name: not ready"
  kill -TERM "$pid"
  set +e
  wait "$pid"
  code=$?
  set -e
  # 143 = terminated by SIGTERM after the JVM shutdown hooks ran.
  [ "$code" -eq 143 ] || fail "$name: exit code $code after SIGTERM"
  grep -q "Graceful shutdown complete" "$logs/$name.log" || fail "$name: no graceful shutdown"
  ! grep -E " ERROR " "$logs/$name.log" || fail "$name: error in log"
}

echo "== First start on the empty database"
run first-start
after_first=$(history)
echo "$after_first"
[ "$(echo "$after_first" | wc -l)" -eq 1 ] && [[ "$after_first" == "0001 true "* ]] || fail "unexpected history after first start"
grep -q 'Successfully applied 1 migration' "$logs/first-start.log" || fail "first start did not migrate"

echo "== Restart"
run restart
[ "$(history)" = "$after_first" ] || fail "restart changed the migration history"
grep -q 'Schema "public" is up to date. No migration necessary.' "$logs/restart.log" || fail "restart did not report an up-to-date schema"
[ "$(sql "select string_agg(table_name, ',') from information_schema.tables where table_schema = 'public'")" = flyway_schema_history ] \
  || fail "unexpected schema objects"

echo "Lifecycle check passed"
