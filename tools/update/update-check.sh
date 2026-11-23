#!/usr/bin/env bash
# The update check (TASK-132, DEC-162, rule 4 and Abnahmekriterien 1 to 3). It runs the operations template
# deploy/compose.yaml exactly as docs/INSTALL.md describes:
#
#   1. installs the older version behind TLS with its own certificate, an administrator and synthetic demo data,
#   2. records a fingerprint of the data,
#   3. updates the way the guide says: backup point, stop, new image, the start before 'migrate' is refused (O11),
#      'migrate' once, a second 'migrate' changes nothing, start,
#   4. checks that every recorded value is still there and the core flows work through TLS,
#   5. replaces all containers and checks again that data and configuration stay (O01),
#   6. checks that the image holds no secret and that no container runs privileged, with the Docker socket, writable
#      or with more capabilities than the template gives it.
#
# Inputs (the workflow "Update" builds them):
#   UPDATE_CHECK_OLD_IMAGE     image of the older version
#   UPDATE_CHECK_OLD_SOURCE    checkout of the older version, for its demo data
#   UPDATE_CHECK_NEW_IMAGE     image of this change
#   UPDATE_CHECK_BACKUP_IMAGE  image of the backup service of this change
# Only synthetic data and throw away passwords; it removes its containers and volumes at the end.
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${UPDATE_CHECK_OLD_IMAGE:?set UPDATE_CHECK_OLD_IMAGE}"
: "${UPDATE_CHECK_OLD_SOURCE:?set UPDATE_CHECK_OLD_SOURCE}"
: "${UPDATE_CHECK_NEW_IMAGE:?set UPDATE_CHECK_NEW_IMAGE}"
: "${UPDATE_CHECK_BACKUP_IMAGE:?set UPDATE_CHECK_BACKUP_IMAGE}"
old_source=$(cd "$UPDATE_CHECK_OLD_SOURCE" && pwd)

results=${UPDATE_CHECK_RESULTS:-update-results}
mkdir -p "$results"
results=$(cd "$results" && pwd)
work=$(mktemp -d)
project=tm-update-check
https_port=${UPDATE_CHECK_HTTPS_PORT:-18443}
python_image=python:3.13.16-slim@sha256:cf50a4f6ac4f026458b08d5222023d700ac806410dff5e9ca8728fa44f2b850f

secret() { head -c 24 /dev/urandom | base64 | tr -d '/+='; }
db_password=$(secret)
backup_db_password=$(secret)
backup_password=$(secret)
secret_key=$(head -c 32 /dev/urandom | base64)
demo_password="update-$(secret)"

# The settings of the installation, written like deploy/.env of an operator; only the image changes during the update.
mkdir -p "$work/tls"
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost" \
  -keyout "$work/tls/key.pem" -out "$work/tls/certificate.pem" 2> /dev/null
chmod 0644 "$work/tls/key.pem" "$work/tls/certificate.pem"
write_env() {
  cat > "$work/env" <<ENV
TASK_MANAGER_IMAGE=$1
TASK_MANAGER_BACKUP_IMAGE=$UPDATE_CHECK_BACKUP_IMAGE
TASK_MANAGER_DOMAIN=localhost
TASK_MANAGER_TLS=own
TASK_MANAGER_TLS_DIR=$work/tls
TASK_MANAGER_HTTP_BIND=127.0.0.1:18180
TASK_MANAGER_HTTPS_BIND=127.0.0.1:$https_port
TASK_MANAGER_DB_PASSWORD=$db_password
TASK_MANAGER_BACKUP_DB_PASSWORD=$backup_db_password
TASK_MANAGER_SECRET_KEY=$secret_key
TASK_MANAGER_BACKUP_PASSWORD=$backup_password
TASK_MANAGER_BACKUP_REPOSITORY=/state/repository
TASK_MANAGER_BACKUP_SSH_DIR=$work/ssh
TASK_MANAGER_APP_MEMORY=1536m
TASK_MANAGER_DB_MEMORY=1g
ENV
}
mkdir -p "$work/ssh"

dc() { docker compose -p "$project" -f deploy/compose.yaml --env-file "$work/env" "$@"; }
step() { echo; echo "== $*"; }
fail() { echo "FAILED: $*" >&2; exit 1; }
cleanup() {
  [ -f "$work/env" ] && { dc logs > "$results/containers.log" 2>&1 || true; dc --profile maintenance down -v --remove-orphans > /dev/null 2>&1 || true; }
  rm -rf "$work"
}
trap cleanup EXIT

db() { dc exec -T db psql -X -A -t -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager -c "$1" | tr -d '\r'; }
https() { curl -fsS --connect-timeout 5 --max-time 60 --cacert "$work/tls/certificate.pem" --resolve "localhost:$https_port:127.0.0.1" "$@"; }
base="https://localhost:$https_port"
await_tls() {
  local deadline=$((SECONDS + 300))
  until https "$base/actuator/health/readiness" 2> /dev/null | grep -q '"UP"'; do
    [ "$SECONDS" -lt "$deadline" ] || { dc ps; dc logs app caddy | tail -60; fail "the installation did not answer through TLS"; }
    sleep 3
  done
}
newest_migration() { find "$1" -name 'V*__*.sql' -printf '%f\n' | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n | tail -n 1; }

# The data a person would miss after an update: counts of every kind of record and a checksum over the tickets.
fingerprint() {
  db "select (select count(*) from project) || ' projects, ' || (select count(*) from ticket) || ' tickets, '
    || (select count(*) from tag) || ' tags, ' || (select count(*) from identity_account) || ' accounts, '
    || (select count(*) from permissions_membership) || ' memberships, ' || (select count(*) from ticket_change) || ' changes, '
    || coalesce((select md5(string_agg(project_id || ':' || number || ':' || title || ':' || status, ',' order by project_id, number))
       from ticket), '-')"
}

# Logs in as the administrator through TLS and checks the core flows; prints the number of tickets the list shows.
core_flows() {
  local jar token csrf tickets
  jar=$(mktemp)
  token=$(https -c "$jar" "$base/api/v1/session" | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p')
  [ -n "$token" ] || fail "the installation handed out no token"
  csrf=$(https -b "$jar" -c "$jar" -X POST -H 'Content-Type: application/json' -H "X-CSRF-Token: $token" \
    -d "{\"username\":\"admin\",\"password\":\"$demo_password\"}" "$base/api/v1/session" \
    | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p')
  [ -n "$csrf" ] || fail "the administrator could not log in through TLS"
  tickets=$(https -b "$jar" "$base/api/v1/projects/$project_id/tickets?limit=5" | grep -o '"number"' | wc -l | tr -d ' ')
  [ "$tickets" -gt 0 ] || fail "the ticket list is empty"
  https -b "$jar" "$base/api/v1/search/tickets?q=Ticket" | grep -q '"total"' || fail "the search did not answer"
  rm -f "$jar"
  echo "$tickets"
}

old_newest=$(newest_migration "$old_source/backend/src/main/resources/db/migration")
new_newest=$(newest_migration backend/src/main/resources/db/migration)
old_has_migrate=0
grep -q 'static final String MIGRATE = "migrate"' "$old_source/backend/src/main/java/taskmanager/TaskManagerApplication.java" \
  && old_has_migrate=1
echo "older version: migrations up to V$old_newest, command migrate: $old_has_migrate; this change: up to V$new_newest"

step "Install the older version with the operations template"
write_env "$UPDATE_CHECK_OLD_IMAGE"
if [ "$old_has_migrate" = 1 ]; then
  dc run --rm migrate > "$results/old-migrate.log" 2>&1 || { cat "$results/old-migrate.log"; fail "migrate of the older version failed"; }
fi
# A version from before TASK-132 migrates at its first start, as the demonstration still does.
dc up -d > /dev/null
await_tls
bootstrap=$(dc run --rm -T app bootstrap-admin admin)
one_time=$(printf '%s' "$bootstrap" | sed -n 's/^One time password (shown only now): //p' | tr -d '\r')
[ -n "$one_time" ] || fail "bootstrap-admin printed no one time password"
docker run --rm --network "${project}_default" -v "$old_source/tools/demo:/demo:ro" \
  -e TASK_MANAGER_URL=http://app:8080 -e TASK_MANAGER_DEMO_PASSWORD="$demo_password" \
  "$python_image" python -I -u /demo/demo.py seed --one-time-password "$one_time" > "$results/seed.log" 2>&1 \
  || { tail -20 "$results/seed.log"; fail "the demo data of the older version could not be created"; }
project_id=$(db "select id from project order by key limit 1")
[ -n "$project_id" ] || fail "the demo data created no project"
before=$(fingerprint)
echo "before the update: $before"
core_flows > /dev/null

step "Update: backup point, stop, new image"
# The backup service reads the list of contents that V0070 (TASK-131) brought; an older state of main from before it has
# no backup point to wait for. Every released version has it.
if [ "$((10#$old_newest))" -ge 70 ]; then
  dc run --rm backup once > "$results/backup.log" 2>&1 || { cat "$results/backup.log"; fail "no backup point before the update"; }
  grep -q "point [0-9TZ]* complete" "$results/backup.log" || fail "the backup point was not confirmed"
else
  echo "the older version predates the backup service (V0070); no backup point to wait for"
fi
dc stop app worker caddy > /dev/null
write_env "$UPDATE_CHECK_NEW_IMAGE"

if [ "$new_newest" != "$old_newest" ]; then
  step "O11: the new version refuses to start before migrate"
  set +e
  timeout 300 docker compose -p "$project" -f deploy/compose.yaml --env-file "$work/env" run --rm -T --no-deps app \
    > "$results/start-before-migrate.log" 2>&1
  code=$?
  set -e
  [ "$code" -ne 0 ] && [ "$code" -ne 124 ] || fail "the new version started although migrations were missing"
  grep -q "migration(s) are missing" "$results/start-before-migrate.log" \
    || { tail -40 "$results/start-before-migrate.log"; fail "the refusal does not name the missing migrations"; }
  [ "$(db "select max(version) from flyway_schema_history where success")" = "$old_newest" ] \
    || fail "the refused start changed the schema"
fi

step "migrate runs once, a second run changes nothing"
dc run --rm -T migrate > "$results/migrate.log" 2>&1 || { cat "$results/migrate.log"; fail "migrate failed"; }
cat "$results/migrate.log" | grep -E '^(Schema version|Migrations applied)'
grep -q "^Schema version now: $new_newest$" "$results/migrate.log" || fail "migrate did not reach V$new_newest"
dc run --rm -T migrate > "$results/migrate-again.log" 2>&1 || { cat "$results/migrate-again.log"; fail "the second migrate failed"; }
grep -q '^Migrations applied: 0$' "$results/migrate-again.log" || fail "the second migrate changed the schema"

step "Start the new version and check the data"
dc up -d > /dev/null
await_tls
after=$(fingerprint)
echo "after the update:  $after"
[ "$after" = "$before" ] || fail "the update changed the data: before '$before', after '$after'"
tickets=$(core_flows)
echo "login, ticket list ($tickets tickets) and search work through TLS"

step "O01: replaced containers keep data and configuration"
dc up -d --force-recreate > /dev/null
await_tls
[ "$(fingerprint)" = "$before" ] || fail "replacing the containers changed the data"
core_flows > /dev/null
echo "data and configuration kept"

step "The backup service of the template makes a point with its own role"
dc run --rm backup once > "$results/backup-after.log" 2>&1 || { cat "$results/backup-after.log"; fail "the backup service failed after the update"; }
grep -q "point [0-9TZ]* complete" "$results/backup-after.log" || fail "the backup point after the update was not confirmed"
tail -n 1 "$results/backup-after.log"

step "O01: no secret in the image, no privileged container"
for value in "$db_password" "$backup_db_password" "$backup_password" "$secret_key" "$demo_password"; do
  for image in "$UPDATE_CHECK_NEW_IMAGE" "$UPDATE_CHECK_BACKUP_IMAGE"; do
    if docker image inspect "$image" | grep -qF "$value" || docker history --no-trunc "$image" | grep -qF "$value"; then
      fail "the image $image holds a secret of this installation"
    fi
  done
done
if docker image inspect "$UPDATE_CHECK_NEW_IMAGE" --format '{{json .Config.Env}}' | grep -qiE 'password=.|secret_key=.'; then
  fail "the image environment sets a password or key"
fi
for container in $(dc ps -q); do
  name=$(docker inspect --format '{{.Name}}' "$container")
  [ "$(docker inspect --format '{{.HostConfig.Privileged}}' "$container")" = false ] || fail "$name runs privileged"
  if docker inspect --format '{{range .Mounts}}{{.Source}} {{end}}' "$container" | grep -q 'docker.sock'; then
    fail "$name has the Docker socket"
  fi
  docker inspect --format '{{json .HostConfig.SecurityOpt}}' "$container" | grep -q 'no-new-privileges' \
    || fail "$name may gain privileges"
done
for service in app worker caddy; do
  container=$(dc ps -q "$service")
  [ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$container")" = true ] || fail "$service is writable"
  docker inspect --format '{{json .HostConfig.CapDrop}}' "$container" | grep -q '"ALL"' || fail "$service keeps capabilities"
done
echo "no secret in the images; no container privileged, with the Docker socket or able to gain privileges"

cat > "$results/update-run.json" <<JSON
{
  "task": "TASK-132",
  "from": {"migrations": "V$old_newest", "image": "$UPDATE_CHECK_OLD_IMAGE"},
  "to": {"migrations": "V$new_newest", "image": "$UPDATE_CHECK_NEW_IMAGE"},
  "data": "$before",
  "ticketsListed": $tickets
}
JSON
echo
echo "Update check passed: V$old_newest to V$new_newest with all data kept. Report: $results/update-run.json"
