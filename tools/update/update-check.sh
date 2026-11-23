#!/usr/bin/env bash
# The update check (TASK-132, DEC-162, rule 4; TASK-136, findings F3, F4, F5, F8 to F11 and hints H2 and H3 of the review
# of TASK-132). It runs the operations template exactly as docs/INSTALL.md describes:
#
#   1. installs the older version behind TLS with its own certificate, an administrator, synthetic demo data, an attachment
#      and a wiki image; a released older version with the template and the digests of its own release,
#   2. records a fingerprint of the content: every row of the domain tables with the columns the older version has, and
#      every file of the file store byte for byte,
#   3. updates the way the guide says: backup point, stop, template and images of this change (a tag is refused), the roles,
#      the start before 'migrate' is refused (O11), 'migrate' once, a second 'migrate' changes nothing, start,
#   4. checks the content and the core flows through TLS, uploads an attachment, replaces all containers (O01) and checks
#      content and download again,
#   5. shows that web process and worker run with the runtime role, which may not change the schema,
#   6. asks for a point next to a running point of the service, which waits for it (F5), and returns to exactly that point
#      after a synthetically failed update along the return path of docs/INSTALL.md (F11),
#   7. checks that the images hold no secret and that no container runs privileged, with the Docker socket, writable or
#      with more capabilities than the template gives it.
#
# Inputs (the workflow "Update" provides them):
#   UPDATE_CHECK_BASE              what the older version is: "release vX.Y.Z" or "commit SHA"
#   UPDATE_CHECK_OLD_IMAGE         image of the older version (a digest of the registry for a release)
#   UPDATE_CHECK_OLD_BACKUP_IMAGE  image of its backup service (for a commit before the first release: the one of this change)
#   UPDATE_CHECK_OLD_TEMPLATE      directory deploy/ of the release of the older version; empty for a commit
#   UPDATE_CHECK_OLD_SOURCE        checkout of the older version, for its demo data and migrations
#   UPDATE_CHECK_NEW_IMAGE         image of this change
#   UPDATE_CHECK_BACKUP_IMAGE      image of the backup service of this change
#   UPDATE_CHECK_TARGET            the commit of this change
# Only synthetic data and throw away passwords, none of them in a process argument; it removes its containers and volumes
# at the end and says when that fails.
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${UPDATE_CHECK_BASE:?set UPDATE_CHECK_BASE}"
: "${UPDATE_CHECK_OLD_IMAGE:?set UPDATE_CHECK_OLD_IMAGE}"
: "${UPDATE_CHECK_OLD_BACKUP_IMAGE:?set UPDATE_CHECK_OLD_BACKUP_IMAGE}"
: "${UPDATE_CHECK_OLD_SOURCE:?set UPDATE_CHECK_OLD_SOURCE}"
: "${UPDATE_CHECK_NEW_IMAGE:?set UPDATE_CHECK_NEW_IMAGE}"
: "${UPDATE_CHECK_BACKUP_IMAGE:?set UPDATE_CHECK_BACKUP_IMAGE}"
old_source=$(cd "$UPDATE_CHECK_OLD_SOURCE" && pwd)
old_template=${UPDATE_CHECK_OLD_TEMPLATE:-}
[ -z "$old_template" ] || old_template=$(cd "$old_template" && pwd)
target=${UPDATE_CHECK_TARGET:-$(git rev-parse HEAD)}

results=${UPDATE_CHECK_RESULTS:-update-results}
mkdir -p "$results"
results=$(cd "$results" && pwd)
# Every temporary file, also the cookie and state files of the probe, lies below this directory, which the end removes.
work=$(mktemp -d "${RUNNER_TEMP:-/tmp}/update-check.XXXXXX")
chmod 700 "$work"
# A project name of its own per run (H3): parallel or left over runs never share containers or volumes.
project="tm-update-${GITHUB_RUN_ID:-local}-$$"
https_port=${UPDATE_CHECK_HTTPS_PORT:-18443}
python_image=python:3.13.16-slim@sha256:cf50a4f6ac4f026458b08d5222023d700ac806410dff5e9ca8728fa44f2b850f
url="https://localhost:$https_port"

secret() { head -c 24 /dev/urandom | base64 | tr -d '/+='; }
export TASK_MANAGER_DEMO_PASSWORD="update-$(secret)"
db_password=$(secret)
migrate_db_password=$(secret)
app_db_password=$(secret)
backup_db_password=$(secret)
backup_password=$(secret)
secret_key=$(head -c 32 /dev/urandom | base64)

# The images of this change by their local identifier: the separate test path of F8 (tools/update/compose.test.yaml).
image_id() { docker image inspect --format '{{.Id}}' "$1"; }
new_image=$(image_id "$UPDATE_CHECK_NEW_IMAGE")
new_backup_image=$(image_id "$UPDATE_CHECK_BACKUP_IMAGE")
case "$UPDATE_CHECK_OLD_IMAGE" in
  ghcr.io/*@sha256:*) old_image=$UPDATE_CHECK_OLD_IMAGE; old_backup_image=$UPDATE_CHECK_OLD_BACKUP_IMAGE ;;
  *) old_image=$(image_id "$UPDATE_CHECK_OLD_IMAGE"); old_backup_image=$(image_id "$UPDATE_CHECK_OLD_BACKUP_IMAGE") ;;
esac

mkdir -p "$work/tls" "$work/ssh" "$work/state"
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost" \
  -keyout "$work/tls/key.pem" -out "$work/tls/certificate.pem" 2> /dev/null
chmod 0644 "$work/tls/key.pem" "$work/tls/certificate.pem"
# The settings of the installation, written like deploy/.env of an operator; only the images change during the update.
write_env() {
  cat > "$work/env" <<ENV
TASK_MANAGER_IMAGE=$1
TASK_MANAGER_BACKUP_IMAGE=$2
TASK_MANAGER_DOMAIN=localhost
TASK_MANAGER_TLS=own
TASK_MANAGER_TLS_DIR=$work/tls
TASK_MANAGER_HTTP_BIND=127.0.0.1:18180
TASK_MANAGER_HTTPS_BIND=127.0.0.1:$https_port
TASK_MANAGER_DB_PASSWORD=$db_password
TASK_MANAGER_MIGRATE_DB_PASSWORD=$migrate_db_password
TASK_MANAGER_APP_DB_PASSWORD=$app_db_password
TASK_MANAGER_BACKUP_DB_PASSWORD=$backup_db_password
TASK_MANAGER_SECRET_KEY=$secret_key
TASK_MANAGER_BACKUP_PASSWORD=$backup_password
TASK_MANAGER_BACKUP_REPOSITORY=/state/repository
TASK_MANAGER_BACKUP_SSH_DIR=$work/ssh
TASK_MANAGER_APP_MEMORY=1536m
TASK_MANAGER_DB_MEMORY=1g
ENV
  chmod 600 "$work/env"
}

# The template in use: the one of the older release, the one of this change with the older commit, or this change.
compose_files=()
use_old_template() {
  if [ -n "$old_template" ]; then
    compose_files=(-f "$old_template/compose.yaml")
  else
    compose_files=(-f deploy/compose.yaml -f tools/update/compose.test.yaml -f tools/update/compose.old.yaml)
  fi
}
use_new_template() { compose_files=(-f deploy/compose.yaml -f tools/update/compose.test.yaml "$@"); }

dc() { docker compose -p "$project" "${compose_files[@]}" --env-file "$work/env" "$@"; }
step() { echo; echo "== $*"; }
fail() { echo "FAILED: $*" >&2; exit 1; }
cleanup() {
  if [ -f "$work/env" ]; then
    dc logs > "$results/containers.log" 2>&1 || echo "clean up: the logs of the containers could not be kept" >&2
    dc --profile maintenance down -v --remove-orphans > "$results/down.log" 2>&1 \
      || { echo "clean up: containers or volumes of $project were not removed:" >&2; cat "$results/down.log" >&2; }
  fi
  rm -rf "$work" || echo "clean up: $work was not removed" >&2
}
trap cleanup EXIT

db() { dc exec -T db psql -X -A -t -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager -c "$1" | tr -d '\r'; }
# The probe runs through TLS on the host address with the certificate of the installation; passwords only as names of
# environment variables (H2).
probe() {
  local command=$1
  shift
  docker run --rm --network host -v "$PWD/tools:/tools:ro" -v "$work/tls/certificate.pem:/ca.pem:ro" \
    -v "$work/state:/state" -e SSL_CERT_FILE=/ca.pem -e TASK_MANAGER_DEMO_PASSWORD -e TASK_MANAGER_ONE_TIME_PASSWORD \
    "$python_image" python -I -u /tools/update/probe.py "$command" --url "$url" "$@"
}
await_tls() {
  local deadline=$((SECONDS + 300))
  until curl -fsS --max-time 10 --cacert "$work/tls/certificate.pem" --resolve "localhost:$https_port:127.0.0.1" \
      "$url/actuator/health/readiness" 2> /dev/null | grep -q '"UP"'; do
    [ "$SECONDS" -lt "$deadline" ] || { dc ps; dc logs app caddy | tail -60; fail "the installation did not answer through TLS"; }
    sleep 3
  done
}
newest_migration() { find "$1" -name 'V*__*.sql' -printf '%f\n' | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n | tail -n 1; }

# The content a person would miss after an update (F10): every row of these tables with the columns the older version
# has, and every file of the file store with its checksum. The column lists are fixed at the first fingerprint, so that a
# column a migration adds does not count as a change.
content_tables="platform_company project project_member project_configuration permissions_group permissions_membership
  ticket ticket_change tag tag_ticket tag_project file_attachment file_image wiki_page wiki_variant wiki_revision
  wiki_revision_image wiki_image requirement requirement_revision requirement_ticket_link requirement_page_link time_entry
  time_entry_revision helpdesk_customer helpdesk_contact helpdesk_ticket_customer"
fingerprint() {
  local table columns rows=0 tables=0 sums="" count
  for table in $content_tables; do
    if [ ! -f "$work/columns-$table" ]; then
      db "select string_agg(quote_ident(column_name), ', ' order by ordinal_position) from information_schema.columns
          where table_schema = 'public' and table_name = '$table'" > "$work/columns-$table"
      [ -s "$work/columns-$table" ] && [ "$(cat "$work/columns-$table")" != "" ] || fail "the table $table is missing"
    fi
    columns=$(cat "$work/columns-$table")
    read -r count sum <<< "$(db "select count(*) || ' ' || coalesce(md5(string_agg(r::text, E'\n' order by r::text)), '-')
      from (select $columns from $table) r")"
    rows=$((rows + count)); tables=$((tables + 1)); sums="$sums $table:$sum"
  done
  local files
  files=$(docker run --rm --network none -v "${project}_files-data:/files:ro" --entrypoint sh "$python_image" -c \
    'mkdir -p /tmp/none && cd /files/content 2> /dev/null || cd /tmp/none; find . -type f -print0 | sort -z | xargs -0r sha256sum | tee /tmp/sums > /dev/null;
     printf "%s %s %s" "$(wc -l < /tmp/sums)" "$(find . -type f -print0 | xargs -0r cat | wc -c)" "$(sha256sum < /tmp/sums | cut -c1-64)"')
  read -r file_count file_bytes file_sum <<< "$files"
  echo "$rows rows in $tables tables, $file_count files with $file_bytes bytes; $(printf '%s' "$sums $file_sum" | sha256sum | cut -c1-16)"
}

old_newest=$(newest_migration "$old_source/backend/src/main/resources/db/migration")
new_newest=$(newest_migration backend/src/main/resources/db/migration)
old_has_migrate=0
grep -q 'static final String MIGRATE = "migrate"' "$old_source/backend/src/main/java/taskmanager/TaskManagerApplication.java" \
  && old_has_migrate=1
echo "older version: $UPDATE_CHECK_BASE, migrations up to V$old_newest, command migrate: $old_has_migrate"
echo "this change: $target, migrations up to V$new_newest"
echo "older images: $old_image, $old_backup_image"
echo "images of this change: $new_image, $new_backup_image"

step "Install the older version with its template"
use_old_template
write_env "$old_image" "$old_backup_image"
[ -z "$old_template" ] || dc pull --quiet images db app backup > /dev/null
if [ "$old_has_migrate" = 1 ]; then
  dc run --rm migrate > "$results/old-migrate.log" 2>&1 || { cat "$results/old-migrate.log"; fail "migrate of the older version failed"; }
fi
# A version from before TASK-132 migrates at its first start, as the demonstration still does.
dc up -d > /dev/null
await_tls
bootstrap=$(dc run --rm -T app bootstrap-admin admin)
TASK_MANAGER_ONE_TIME_PASSWORD=$(printf '%s' "$bootstrap" | sed -n 's/^One time password (shown only now): //p' | tr -d '\r')
export TASK_MANAGER_ONE_TIME_PASSWORD
[ -n "$TASK_MANAGER_ONE_TIME_PASSWORD" ] || fail "bootstrap-admin printed no one time password"
probe first-login
unset TASK_MANAGER_ONE_TIME_PASSWORD
docker run --rm --network "${project}_default" -v "$old_source/tools/demo:/demo:ro" -e TASK_MANAGER_URL=http://app:8080 \
  -e TASK_MANAGER_DEMO_PASSWORD "$python_image" python -I -u /demo/demo.py seed > "$results/seed.log" 2>&1 \
  || { tail -20 "$results/seed.log"; fail "the demo data of the older version could not be created"; }
probe prepare --state /state/probe.json
probe flows --state /state/probe.json --phase vorher --upload
before=$(fingerprint)
echo "before the update: $before"

step "Update: backup point, stop, template and images of this change"
# The backup service reads the list of contents that V0070 (TASK-131) brought; an older state of main from before it has
# no backup point to wait for. Every released version has it.
if [ "$((10#$old_newest))" -ge 70 ]; then
  dc run --rm backup once > "$results/backup.log" 2>&1 || { cat "$results/backup.log"; fail "no backup point before the update"; }
  grep -q "point [0-9TZ]* complete" "$results/backup.log" || fail "the backup point was not confirmed"
else
  echo "the older version predates the backup service (V0070); no backup point to wait for"
fi
dc stop app worker caddy > /dev/null
use_new_template

echo "F8: a tag instead of the digest is refused before anything starts"
write_env "$UPDATE_CHECK_NEW_IMAGE" "$new_backup_image"
if dc run --rm images > "$results/tag-refused.log" 2>&1; then fail "the template accepted a tag"; fi
grep -q "Refused: TASK_MANAGER_IMAGE must be the digest" "$results/tag-refused.log" || fail "the refusal of a tag is not explained"
if dc run --rm -T migrate > "$results/tag-refused-migrate.log" 2>&1; then fail "migrate started with a tag"; fi
write_env "$new_image" "$new_backup_image"
dc run --rm images

step "F3: the roles of this template, also for an installation from before them"
dc up -d db > /dev/null
dc exec -T db sh /opt/taskmanager/db-roles.sh > "$results/db-roles.log" 2>&1 || { cat "$results/db-roles.log"; fail "db-roles.sh failed"; }
[ "$(db "select string_agg(distinct pg_get_userbyid(relowner), ',') from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and relkind in ('r', 'v', 'S')")" = taskmanager_migrate ] || fail "the objects did not move to the migration role"
echo "every table, view and sequence belongs to the migration role"

if [ "$new_newest" != "$old_newest" ]; then
  step "O11: the new version refuses to start before migrate"
  set +e
  timeout 300 docker compose -p "$project" "${compose_files[@]}" --env-file "$work/env" run --rm -T --no-deps app \
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
grep -E '^(Schema version|Migrations applied)' "$results/migrate.log"
grep -q "^Schema version now: $new_newest$" "$results/migrate.log" || fail "migrate did not reach V$new_newest"
dc run --rm -T migrate > "$results/migrate-again.log" 2>&1 || { cat "$results/migrate-again.log"; fail "the second migrate failed"; }
grep -q '^Migrations applied: 0$' "$results/migrate-again.log" || fail "the second migrate changed the schema"

step "Start the new version and check the content"
dc up -d > /dev/null
await_tls
after=$(fingerprint)
echo "after the update:  $after"
[ "$after" = "$before" ] || fail "the update changed the content: before '$before', after '$after'"
probe flows --state /state/probe.json --phase nachher --upload
current=$(fingerprint)

step "O01: replaced containers keep content and configuration"
dc up -d --force-recreate > /dev/null
await_tls
[ "$(fingerprint)" = "$current" ] || fail "replacing the containers changed the content"
probe flows --state /state/probe.json --phase ausgetauscht

step "F3: web process and worker run with the runtime role, which may not change the schema"
roles=$(db "select string_agg(distinct usename, ',' order by usename) from pg_stat_activity
            where datname = 'taskmanager' and backend_type = 'client backend' and pid <> pg_backend_pid()")
echo "connected roles: $roles"
case ",$roles," in *,taskmanager_app,*) ;; *) fail "no process runs with the runtime role" ;; esac
case ",$roles," in *,taskmanager_migrate,*|*,taskmanager,*) fail "a process runs with the migration or administration role" ;; esac
if dc exec -T db psql -X -q -v ON_ERROR_STOP=1 -U taskmanager_app -d taskmanager -c "create table refused (id int)" \
    > "$results/ddl-refused.log" 2>&1; then
  fail "the runtime role created a table"
fi
grep -q "permission denied" "$results/ddl-refused.log" || { cat "$results/ddl-refused.log"; fail "the refusal is not a missing right"; }
echo "the runtime role may not create a table"

step "F5: a point of the operator next to a running point of the service"
use_new_template -f tools/update/compose.loop.yaml
dc up -d backup > /dev/null
at_point=$(fingerprint)
cp "$work/state/probe.json" "$work/state/probe-at-point.json"
waited=0
for attempt in 1 2 3 4 5; do
  # Waits until a point of the loop is in its base backup, then asks for one of its own.
  since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  deadline=$((SECONDS + 120))
  until dc logs --since "$since" backup 2> /dev/null | grep -q "backup: base backup [0-9]"; do
    [ "$SECONDS" -lt "$deadline" ] || { dc logs backup | tail -30; fail "the service made no point"; }
    sleep 0.2
  done
  dc exec -T backup /opt/backup/entrypoint.sh once > "$results/manual-point.log" 2>&1 \
    || { cat "$results/manual-point.log"; fail "the point of the operator failed"; }
  if grep -q "another point is being made; this one waits for it" "$results/manual-point.log"; then
    waited=1
    break
  fi
done
[ "$waited" = 1 ] || fail "no point of the operator ran into a point of the service"
point=$(sed -n 's/.*point \([0-9TZ]*\) complete.*/\1/p' "$results/manual-point.log" | tail -n 1)
contents=$(sed -n 's/.*point [0-9TZ]* complete (.*, \([0-9]*\) contents).*/\1/p' "$results/manual-point.log" | tail -n 1)
[ -n "$point" ] || { cat "$results/manual-point.log"; fail "the point of the operator was not confirmed"; }
expected=$(db "select count(*) from file_attachment")
[ "$contents" -ge "$expected" ] || fail "the point holds $contents contents, the database has $expected attachments"
echo "the point $point of the operator waited for the service and holds $contents contents (attachments and the wiki image, F4)"
use_new_template
dc up -d backup > /dev/null

step "F11: a synthetically failed update and the way back to point $point"
probe flows --state /state/probe.json --phase nachdempunkt --upload
lost=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["attachments"][-1]["id"])' "$work/state/probe.json")
failed_version=$(printf '%04d' $((10#$new_newest + 1)))
db "insert into flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
    select max(installed_rank) + 1, '$failed_version', 'synthetic failed update', 'SQL', 'V${failed_version}__synthetic.sql', 0,
           'update-check', 1, false from flyway_schema_history"
set +e
dc run --rm -T migrate > "$results/failed-migrate.log" 2>&1
code=$?
set -e
[ "$code" = 2 ] || { cat "$results/failed-migrate.log"; fail "migrate did not refuse the failed update with 2 (got $code)"; }
dc stop app worker > /dev/null
set +e
timeout 300 docker compose -p "$project" "${compose_files[@]}" --env-file "$work/env" run --rm -T --no-deps app \
  > "$results/start-after-failed.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] && [ "$code" -ne 124 ] || fail "the web process started after the failed update"
grep -Eq "newer than this image|failed in an earlier run" "$results/start-after-failed.log" \
  || { tail -40 "$results/start-after-failed.log"; fail "the refusal after the failed update is not explained"; }
# The return path of docs/INSTALL.md, "Rückkehr", step by step with the commands printed there.
dc stop > /dev/null
dc run --rm --no-deps backup points > "$results/points.log" 2>&1 || { cat "$results/points.log"; fail "the points could not be listed"; }
grep -q "$point" "$results/points.log" || fail "the point $point is not listed"
dc run --rm --no-deps backup restore /restore "$point" > "$results/restore.log" 2>&1 \
  || { tail -30 "$results/restore.log"; fail "the restore of $point failed"; }
grep -q "point $point restored and proven" "$results/restore.log" || fail "the restore did not take $point"
dc down > /dev/null
for volume in db-data files-data; do
  docker volume rm "$(docker volume ls -q --filter "label=com.docker.compose.project=$project" \
    --filter "label=com.docker.compose.volume=$volume")" > /dev/null
done
dc run --rm restore-place > "$results/restore-place.log" 2>&1 || { cat "$results/restore-place.log"; fail "restore-place failed"; }
dc up -d db > /dev/null
dc run --rm -T app after-restore "Updateprüfung" > "$results/after-restore.log" 2>&1 \
  || { cat "$results/after-restore.log"; fail "after-restore did not release the restore"; }
dc up -d > /dev/null
await_tls
[ "$(db "select count(*) from flyway_schema_history where not success")" = 0 ] || fail "the failed update is still in the history"
[ "$(db "select count(*) from file_attachment where id = '$lost'")" = 0 ] || fail "the upload after the point is still there"
returned=$(fingerprint)
[ "$returned" = "$at_point" ] || fail "the way back did not reach the point: at the point '$at_point', now '$returned'"
cp "$work/state/probe-at-point.json" "$work/state/probe.json"
probe flows --state /state/probe.json --phase zurueck
echo "back at point $point: content as at the point, the upload after it is gone, core flows work"

step "O01: no secret in the image, no privileged container"
for value in "$db_password" "$migrate_db_password" "$app_db_password" "$backup_db_password" "$backup_password" "$secret_key" \
    "$TASK_MANAGER_DEMO_PASSWORD"; do
  for image in "$new_image" "$new_backup_image"; do
    if docker image inspect "$image" | grep -qF "$value" || docker history --no-trunc "$image" | grep -qF "$value"; then
      fail "the image $image holds a secret of this installation"
    fi
  done
done
if docker image inspect "$new_image" --format '{{json .Config.Env}}' | grep -qiE 'password=.|secret_key=.'; then
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

python3 - "$results/update-run.json" <<PY
import json, sys
json.dump({
    "task": "TASK-136",
    "from": {"base": "$UPDATE_CHECK_BASE", "migrations": "V$old_newest", "image": "$old_image", "backupImage": "$old_backup_image",
             "template": "${old_template:+release}${old_template:-this change with tools/update/compose.old.yaml}"},
    "to": {"commit": "$target", "migrations": "V$new_newest", "image": "$new_image", "backupImage": "$new_backup_image"},
    "content": "$before",
    "checkedTables": "$(echo $content_tables)".split(),
    "returnPoint": "$point",
    "returnPointContents": int("$contents"),
}, open(sys.argv[1], "w"), indent=2)
PY
echo
echo "Update check passed: $UPDATE_CHECK_BASE (V$old_newest) to $target (V$new_newest); content: $before. Report: $results/update-run.json"
