#!/usr/bin/env bash
# The update check (TASK-132, DEC-162, rule 4; TASK-136, findings F3, F4, F5, F8 to F11 and hints H2 and H3 of the review
# of TASK-132; TASK-137, findings N7 to N10, N12 and hint H3 of the review of TASK-136). It runs the operations template
# exactly as docs/INSTALL.md describes:
#
#   1. installs the older version behind TLS with its own certificate, an administrator, synthetic demo data, an attachment
#      and a wiki image; a released older version with the template and the digests of its own release,
#   2. records a fingerprint of the content: every row of every table the seed filled, with the columns the older version
#      has, apart from a short named list of technical tables, and every file of the file store byte for byte,
#   3. updates the way the guide says: backup point, stop, template and images of this change (a tag is refused by the
#      template itself for pull, up and run with --no-deps), the roles (once with a synthetic error whose logs must not
#      show a password), the start before 'migrate' is refused (O11), 'migrate' once, a second 'migrate' changes nothing,
#      start,
#   4. checks the content and the core flows through TLS, uploads an attachment, replaces all containers (O01) and checks
#      content and download again,
#   5. shows that web process and worker run with the runtime role, which may not change the schema,
#   6. asks for a point next to a running point of the service, which waits for it (F5); that point is the point before
#      a change of version: the update to a synthetic next version whose second new migration really fails, then the
#      return path of docs/INSTALL.md with the kept package and settings of the previous version back to exactly that
#      point (N10),
#   7. checks the metadata of the images (configuration, environment, history) for the secrets of this run and that no
#      container runs privileged, with the Docker socket, writable or with more capabilities than the template gives it.
#
# Inputs (the workflow "Update" provides them):
#   UPDATE_CHECK_BASE              what the older version is: "release vX.Y.Z" or "commit SHA"
#   UPDATE_CHECK_BASE_COMMIT       the commit of the older version (for the report)
#   UPDATE_CHECK_OLD_IMAGE         image of the older version (a digest of the registry for a release)
#   UPDATE_CHECK_OLD_BACKUP_IMAGE  image of its backup service (for a commit before the first release: the one of this change)
#   UPDATE_CHECK_OLD_TEMPLATE      directory deploy/ of the release of the older version; empty for a commit
#   UPDATE_CHECK_OLD_SOURCE        checkout of the older version, for its demo data and migrations
#   UPDATE_CHECK_NEW_IMAGE         image of this change
#   UPDATE_CHECK_BACKUP_IMAGE      image of the backup service of this change
#   UPDATE_CHECK_TARGET            the commit of this change
# Only synthetic data and throw away passwords, none of them in a process argument; it removes its containers, volumes,
# networks and test images at the end and says when that fails.
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
# Host ports of their own per run (H3 of the review of TASK-136), so that local runs side by side do not collide.
free_port() { python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])'; }
https_port=${UPDATE_CHECK_HTTPS_PORT:-$(free_port)}
http_port=${UPDATE_CHECK_HTTP_PORT:-$(free_port)}
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
# A released older version: its template takes the digests of the registry, which the workflow pulled while its login
# held; the check itself pulls nothing from ghcr.io (N8). An older commit: the local identifiers, like this change.
case "$UPDATE_CHECK_OLD_IMAGE" in
  ghcr.io/*@sha256:*)
    [ -n "$old_template" ] || { echo "a released older version needs UPDATE_CHECK_OLD_TEMPLATE" >&2; exit 1; }
    old_image=$UPDATE_CHECK_OLD_IMAGE; old_backup_image=$UPDATE_CHECK_OLD_BACKUP_IMAGE
    docker image inspect "$old_image" "$old_backup_image" > /dev/null \
      || { echo "the images of the older release are not on this host; the workflow pulls them" >&2; exit 1; }
    old_digest=${old_image#*@}; old_backup_digest=${old_backup_image#*@} ;;
  *)
    old_image=$(image_id "$UPDATE_CHECK_OLD_IMAGE"); old_backup_image=$(image_id "$UPDATE_CHECK_OLD_BACKUP_IMAGE")
    old_digest=$old_image; old_backup_digest=$old_backup_image ;;
esac

mkdir -p "$work/tls" "$work/ssh" "$work/state"
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost" \
  -keyout "$work/tls/key.pem" -out "$work/tls/certificate.pem" 2> /dev/null
chmod 0644 "$work/tls/key.pem" "$work/tls/certificate.pem"
# The settings of the installation, written like deploy/.env of an operator; only the digests change during the update.
write_env() {
  cat > "${3:-$work/env}" <<ENV
TASK_MANAGER_IMAGE_DIGEST=$1
TASK_MANAGER_BACKUP_IMAGE_DIGEST=$2
TASK_MANAGER_DOMAIN=localhost
TASK_MANAGER_TLS=own
TASK_MANAGER_TLS_DIR=$work/tls
TASK_MANAGER_HTTP_BIND=127.0.0.1:$http_port
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
  chmod 600 "${3:-$work/env}"
}
# Every secret of this run, one per line, readable only for the check: the searches below read their patterns from this
# file, never from a process argument (N12).
printf '%s\n' "$db_password" "$migrate_db_password" "$app_db_password" "$backup_db_password" "$backup_password" \
  "$secret_key" "$TASK_MANAGER_DEMO_PASSWORD" > "$work/secrets"
chmod 600 "$work/secrets"
holds_secret() { grep -qF -f "$work/secrets" "$@"; }

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
# A kept package: the files of deploy/ of a version in a directory of their own, as an operator keeps the package of the
# previous version for the way back (docs/INSTALL.md, Update step 1).
use_package() { compose_files=(-f "$1/compose.yaml" -f tools/update/compose.test.yaml); }

dc() { docker compose -p "$project" "${compose_files[@]}" --env-file "$work/env" "$@"; }
# "up -d" with the host ports of this run (TASK-139, hint H2 of the review of TASK-137): free_port only finds a free port and
# cannot hold it until Compose binds it. When another process took one in between, the run takes two new ports, writes
# them into every env file of this run and tries again, at most three times; given ports (UPDATE_CHECK_*_PORT) are kept.
up_ports() {
  local attempt
  for attempt in 1 2 3; do
    if dc up -d "$@" > "$results/up.log" 2>&1; then
      return 0
    fi
    if [ -n "${UPDATE_CHECK_HTTPS_PORT:-}${UPDATE_CHECK_HTTP_PORT:-}" ] \
        || ! grep -Eq 'port is already allocated|address already in use' "$results/up.log"; then
      cat "$results/up.log"
      return 1
    fi
    echo "a host port was taken meanwhile (attempt $attempt); two new ports"
    https_port=$(free_port) http_port=$(free_port)
    url="https://localhost:$https_port"
    for file in "$work"/env*; do
      sed -i -e "s/^TASK_MANAGER_HTTP_BIND=.*/TASK_MANAGER_HTTP_BIND=127.0.0.1:$http_port/" \
        -e "s/^TASK_MANAGER_HTTPS_BIND=.*/TASK_MANAGER_HTTPS_BIND=127.0.0.1:$https_port/" "$file"
    done
  done
  cat "$results/up.log"
  return 1
}
step() { echo; echo "== $*"; }
fail() { echo "FAILED: $*" >&2; exit 1; }
cleanup() {
  if [ -f "$work/env" ]; then
    dc logs > "$results/containers.log" 2>&1 || echo "clean up: the logs of the containers could not be kept" >&2
    dc --profile maintenance down -v --remove-orphans > "$results/down.log" 2>&1 \
      || { echo "clean up: containers, volumes or networks of $project were not removed:" >&2; cat "$results/down.log" >&2; }
  fi
  docker image rm --force "$failing_image" "$tag_image" "$tag_backup_image" > /dev/null 2>&1 \
    || echo "clean up: a test image of $project was not removed" >&2
  rm -rf "$work" || echo "clean up: $work was not removed" >&2
}
failing_image="task-manager:update-failing-$project"
tag_image="ghcr.io/1uedev/task-manager:v0.0.0-$project"
tag_backup_image="ghcr.io/1uedev/task-manager-backup:v0.0.0-$project"
trap cleanup EXIT

# Never reads standard input: inside a "while read" loop it would swallow the rest of the list.
db() { dc exec -T db psql -X -A -t -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager -c "$1" < /dev/null | tr -d '\r'; }
# The job of a phase (TASK-139, F8 of the review of TASK-137): the recurring clean up of the file store is made due now, and
# the probe accepts only a run of it created and finished after this moment; prints that moment in UTC.
fresh_job() {
  local since due
  since=$(db "select to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')") \
    || fail "the time of the database could not be read"
  [ -n "$since" ] || fail "the database gave no time"
  due=$(db "update job_schedule set next_run_at = clock_timestamp() where name = 'files.cleanup' returning name") \
    || fail "the clean up could not be made due"
  [ "$due" = files.cleanup ] || fail "the installation has no schedule files.cleanup"
  printf '%s' "$since"
}
flows() {
  local since
  since=$(fresh_job) || exit 1
  probe flows --since "$since" "$@"
}
# The probe runs through TLS on the host address with the certificate of the installation; passwords only as names of
# environment variables (H2).
probe() {
  local command=$1
  shift
  # As the account of the check, so that the state it writes stays the check's own and the end can remove it.
  docker run --rm --network host --user "$(id -u):$(id -g)" -v "$PWD/tools:/tools:ro" -v "$work/tls/certificate.pem:/ca.pem:ro" \
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

# The content a person would miss after an update (F10; TASK-137, N9): every row of every table of the schema that holds
# rows when the first fingerprint is taken, with the columns the older version has, and every file of the file store with
# its checksum. The inventory is derived from the database the seed of the older version filled, so a table the seed fills
# cannot be forgotten; only these technical tables stay out, because sign ins, background jobs, their own clock or a
# rebuild change them without any change of content:
#   flyway_schema_history                         the update itself changes it
#   identity_session, identity_login_throttle,    every sign in of the probe
#   identity_audit_event, git_oauth_state
#   job, job_audit_event, job_schedule, job_worker the worker runs recurring tasks at every start
#   search_*                                      the projection of the search module, kept by it and rebuildable
#   sla_clock, sla_cycle, sla_escalation, sla_ticket  the clocks of service deadlines, moved on by sla.watch
#   ai_run, ai_suggestion                         records and suggestions with their own clean up
# These tables the seed of every version since V0069 fills must be part of the inventory and hold rows; one missing
# stops the check (the tables N9 named are among them).
technical_tables='^(flyway_schema_history|identity_session|identity_login_throttle|identity_audit_event|git_oauth_state|job|job_audit_event|job_schedule|job_worker|search_.*|sla_clock|sla_cycle|sla_escalation|sla_ticket|ai_run|ai_suggestion)$'
required_tables="platform_company identity_account permissions_group permissions_membership permissions_action permissions_field
  project project_member project_configuration ticket ticket_change tag tag_ticket file_attachment wiki_page wiki_variant
  wiki_revision requirement requirement_revision helpdesk_customer helpdesk_contact helpdesk_customer_project
  helpdesk_portal_post sla_calendar_version sla_rule_version git_provider appearance_mail_template"
inventory() {
  local listed table count
  listed=$(db "select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
               where n.nspname = 'public' and c.relkind in ('r', 'p') and not c.relispartition order by c.relname") \
    || fail "the tables of the schema could not be listed"
  : > "$work/inventory"
  for table in $listed; do
    printf '%s\n' "$table" | grep -Eq "$technical_tables" && continue
    count=$(db "select count(*) from public.$table") || fail "the rows of $table could not be counted"
    [ "$count" -gt 0 ] || continue
    printf '%s\n' "$table" >> "$work/inventory"
    db "select string_agg(quote_ident(column_name), ', ' order by ordinal_position) from information_schema.columns
        where table_schema = 'public' and table_name = '$table'" > "$work/columns-$table" \
      || fail "the columns of $table could not be read"
    [ -s "$work/columns-$table" ] || fail "the table $table has no columns"
  done
  for table in $required_tables; do
    grep -qx "$table" "$work/inventory" || fail "the seed left $table empty or the table is missing; the inventory needs it"
  done
  echo "inventory: $(wc -l < "$work/inventory") tables with rows, technical tables excluded"
}
# Writes one line per table and one for the file store into $work/fingerprint-LABEL and prints the summary; any failed
# read stops the check (N9).
fingerprint() {
  local label=$1 table columns line count sum rows=0 tables=0
  [ -f "$work/inventory" ] || inventory > "$results/inventory.log"
  : > "$work/fingerprint-$label"
  while read -r table; do
    columns=$(cat "$work/columns-$table") || fail "the columns of $table could not be read"
    line=$(db "select count(*) || ' ' || coalesce(md5(string_agg(r::text, E'\n' order by r::text)), '-')
               from (select $columns from public.$table) r") || fail "the rows of $table could not be read"
    read -r count sum <<< "$line"
    [ -n "$sum" ] || fail "no checksum for $table"
    printf '%s %s %s\n' "$table" "$count" "$sum" >> "$work/fingerprint-$label"
    rows=$((rows + count)); tables=$((tables + 1))
  done < "$work/inventory"
  local files
  # Every read of the file store is a step of its own (TASK-139, F8 of the review of TASK-137).
  files=$(docker run --rm --network none -v "${project}_files-data:/files:ro" \
    -v "$PWD/tools/update/files-fingerprint.sh:/check/files-fingerprint.sh:ro" --entrypoint bash "$python_image" \
    /check/files-fingerprint.sh /files/content) \
    || fail "the file store could not be read"
  read -r file_count file_bytes file_sum <<< "$files"
  [ -n "$file_sum" ] || fail "no checksum for the file store"
  printf 'file-store %s %s %s\n' "$file_count" "$file_bytes" "$file_sum" >> "$work/fingerprint-$label"
  [ "$tables" -eq "$(wc -l < "$work/inventory")" ] || fail "the fingerprint $label covers $tables of $(wc -l < "$work/inventory") tables"
  echo "$rows rows in $tables tables, $file_count files with $file_bytes bytes; $(sha256sum < "$work/fingerprint-$label" | cut -c1-16)"
}
# Names the tables whose rows differ between two fingerprints.
differing() { diff "$work/fingerprint-$1" "$work/fingerprint-$2" | sed -n 's/^> \([^ ]*\) .*/\1/p' | sort -u | tr '\n' ' '; }

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
write_env "$old_digest" "$old_backup_digest"
if [ "$old_has_migrate" = 1 ]; then
  dc run --rm migrate > "$results/old-migrate.log" 2>&1 || { cat "$results/old-migrate.log"; fail "migrate of the older version failed"; }
fi
# A version from before TASK-132 migrates at its first start, as the demonstration still does.
up_ports || fail "the containers did not start"
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
flows --state /state/probe.json --phase vorher --upload
before=$(fingerprint vorher)
cat "$results/inventory.log"
echo "before the update: $before"

step "Update: backup point, stop, template and images of this change"
# The backup service reads the list of contents that V0070 (TASK-131) brought; an older state of main from before it has
# no backup point to wait for. Every released version has it.
if [ "$((10#$old_newest))" -ge 70 ]; then
  dc exec -T backup /opt/backup/entrypoint.sh once > "$results/backup.log" 2>&1 \
    || { cat "$results/backup.log"; fail "no backup point before the update"; }
  grep -q "point [0-9TZ]* complete" "$results/backup.log" || fail "the backup point was not confirmed"
else
  echo "the older version predates the backup service (V0070); no backup point to wait for"
fi
dc stop app worker caddy > /dev/null
use_new_template

step "N7: the template takes no tag, for pull, up and run with --no-deps alike"
# Images that exist on this host under a tag of the registry name, so that nothing but the template can refuse them.
docker tag "$new_image" "$tag_image"
docker tag "$new_backup_image" "$tag_backup_image"
write_env "v0.0.0-$project" "v0.0.0-$project" "$work/env-tag"
project_containers() { docker ps -a --filter "label=com.docker.compose.project=$project" --format '{{.ID}} {{.State}} {{.CreatedAt}}' | sort; }
containers_before=$(project_containers)
for command in "pull app worker migrate backup" "up -d --no-deps app worker" "run --rm --no-deps migrate" \
    "run --rm --no-deps backup points" "run --rm --no-deps backup restore /restore"; do
  # shellcheck disable=SC2086
  if docker compose -p "$project" -f deploy/compose.yaml --env-file "$work/env-tag" $command > "$results/tag-command.log" 2>&1; then
    fail "the template ran 'docker compose $command' with a tag"
  fi
  grep -q "invalid reference format" "$results/tag-command.log" \
    || { cat "$results/tag-command.log"; fail "'docker compose $command' was not refused for its image reference"; }
done
[ "$(project_containers)" = "$containers_before" ] || fail "a command with a tag created or changed a container"
echo "pull, up --no-deps, run --no-deps of migrate and backup: refused with a tag, no container started"
# F7 of the review of TASK-137: one wrong value beside a valid second digest. The image of the command is valid and starts,
# its entry point checks both values together before any work, also with --no-deps and without the service "images". The
# test path names the valid image by its local identifier, so nothing is pulled from the registry.
write_env "v0.0.0-$project" "$new_backup_image" "$work/env-app-tag"
write_env "$new_image" "v0.0.0-$project" "$work/env-backup-tag"
for case in "env-app-tag:TASK_MANAGER_IMAGE_DIGEST:run --rm --no-deps backup points" \
    "env-app-tag:TASK_MANAGER_IMAGE_DIGEST:run --rm --no-deps backup restore /restore" \
    "env-backup-tag:TASK_MANAGER_BACKUP_IMAGE_DIGEST:run --rm --no-deps migrate" \
    "env-backup-tag:TASK_MANAGER_BACKUP_IMAGE_DIGEST:run --rm --no-deps app reset-admin-password chef"; do
  env_file=${case%%:*} rest=${case#*:} name=${rest%%:*} command=${rest#*:}
  # shellcheck disable=SC2086
  if docker compose -p "$project" "${compose_files[@]}" --env-file "$work/$env_file" $command > "$results/digest-command.log" 2>&1; then
    fail "'docker compose $command' ran with a wrong $name beside a valid digest"
  fi
  grep -q "Refused: $name must be the digest" "$results/digest-command.log" \
    || { cat "$results/digest-command.log"; fail "'docker compose $command' did not name the wrong $name"; }
done
[ "$(project_containers)" = "$containers_before" ] || fail "a command with one wrong digest left or changed a container"
echo "run --no-deps of backup, migrate and a command: one wrong digest beside a valid one is refused at the start"
echo "F8: the service images explains a wrong value before anything starts"
write_env "$UPDATE_CHECK_NEW_IMAGE" "$new_backup_image"
if dc run --rm images > "$results/tag-refused.log" 2>&1; then fail "the service images accepted a tag"; fi
grep -q "Refused: TASK_MANAGER_IMAGE_DIGEST must be the digest" "$results/tag-refused.log" \
  || { cat "$results/tag-refused.log"; fail "the refusal of a tag is not explained"; }
write_env "$new_image" "$new_backup_image"
dc run --rm images

step "F3: the roles of this template, also for an installation from before them"
# --wait: the server accepts connections before the next command (N10).
dc up -d --wait db > /dev/null
dc exec -T db sh /opt/taskmanager/db-roles.sh > "$results/db-roles.log" 2>&1 || { cat "$results/db-roles.log"; fail "db-roles.sh failed"; }
[ "$(db "select string_agg(distinct pg_get_userbyid(relowner), ',') from pg_class c join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and relkind in ('r', 'v', 'S')")" = taskmanager_migrate ] || fail "the objects did not move to the migration role"
echo "every table, view and sequence belongs to the migration role"

echo "N12: a synthetic error of db-roles.sh while the server logs every statement shows no password"
admin_sql() { dc exec -T -e PGOPTIONS='-c default_transaction_read_only=off' db psql -X -q -v ON_ERROR_STOP=1 -U taskmanager -d taskmanager "$@"; }
admin_sql -c "alter system set log_statement = 'all'" -c "alter system set log_min_error_statement = 'error'" \
  -c "select pg_reload_conf()" -c "alter database taskmanager set default_transaction_read_only = on" > /dev/null
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
sleep 1
if dc exec -T db sh /opt/taskmanager/db-roles.sh > "$results/db-roles-error.log" 2>&1; then
  fail "db-roles.sh succeeded in a read only database"
fi
grep -q "read-only transaction" "$results/db-roles-error.log" || { cat "$results/db-roles-error.log"; fail "the synthetic error did not happen"; }
dc logs --no-log-prefix --since "$since" db > "$results/db-roles-error-db.log" 2>&1
grep -q "read-only transaction" "$results/db-roles-error-db.log" || fail "the synthetic error is missing from the log of the database"
grep -q "statement: " "$results/db-roles-error-db.log" || fail "the database did not log statements during the synthetic error"
if holds_secret "$results/db-roles-error.log" "$results/db-roles-error-db.log"; then
  fail "the output of db-roles.sh or the log of the database holds a password"
fi
admin_sql -c "alter database taskmanager reset default_transaction_read_only" -c "alter system reset log_statement" \
  -c "alter system reset log_min_error_statement" -c "select pg_reload_conf()" > /dev/null
dc exec -T db sh /opt/taskmanager/db-roles.sh > "$results/db-roles-again.log" 2>&1 || { cat "$results/db-roles-again.log"; fail "db-roles.sh failed after the synthetic error"; }
echo "the error is in both logs, no password in either"

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

echo "H1: a stored failure of a migration this image does not know is named as such (synthetic, then removed)"
# TASK-139, hint H1 of the review of TASK-137: a newer image left a failed row of V(n+1); this image must refuse with its own
# diagnosis, in migrate and at the start. A row of its own, removed again; the real return further below stays.
future=$(printf '%04d' $((10#$new_newest + 1)))
db "insert into flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by,
      execution_time, success)
    select max(installed_rank) + 1, '$future', 'synthetic failure of a newer image', 'SQL', 'V${future}__synthetic.sql', 0,
      'taskmanager_migrate', 0, false from flyway_schema_history" > /dev/null || fail "the synthetic failed row was not written"
set +e
dc run --rm -T migrate > "$results/future-failed-migrate.log" 2>&1
code=$?
dc run --rm -T --no-deps app rebuild-search > "$results/future-failed-start.log" 2>&1
start_code=$?
set -e
db "delete from flyway_schema_history where version = '$future' and success = false" > /dev/null \
  || fail "the synthetic failed row was not removed"
[ "$code" = 2 ] || { cat "$results/future-failed-migrate.log"; fail "migrate did not refuse the failed newer migration with 2 ($code)"; }
for log in future-failed-migrate future-failed-start; do
  grep -Eq "The migration\(s\) 0*$((10#$future)), unknown to this image, failed in an earlier run of a newer image" "$results/$log.log" \
    || { cat "$results/$log.log"; fail "$log does not name the failed migration unknown to this image"; }
done
[ "$start_code" != 0 ] || fail "a command started on a database with a failed newer migration"

step "Start the new version and check the content"
up_ports || fail "the containers did not start"
await_tls
after=$(fingerprint nachher)
echo "after the update:  $after"
[ "$after" = "$before" ] || fail "the update changed the content of: $(differing vorher nachher)"
flows --state /state/probe.json --phase nachher --upload
current=$(fingerprint aktuell)

step "O01: replaced containers keep content and configuration"
up_ports --force-recreate || fail "the containers were not replaced"
await_tls
replaced=$(fingerprint ausgetauscht)
[ "$replaced" = "$current" ] || fail "replacing the containers changed the content of: $(differing aktuell ausgetauscht)"
flows --state /state/probe.json --phase ausgetauscht

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
# The probe changed nothing since the point; the fingerprint of now is the content of the point.
at_point=$(fingerprint punkt)
echo "the point $point of the operator waited for the service and holds $contents contents (attachments and the wiki image, F4)"
use_new_template
dc up -d backup > /dev/null

step "N10: an update that really fails after point $point, and the way back with the previous package"
# Writes after the point, which the way back must undo: an upload through TLS and, with the processes stopped, a deliberate
# loss in a table the inventory of TASK-136 missed (the counter test of N9).
flows --state /state/probe.json --phase nachdempunkt --upload
lost=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["attachments"][-1]["id"])' "$work/state/probe.json")
dc stop app worker caddy > /dev/null
fingerprint vorverlust > /dev/null
db "delete from appearance_mail_template"
fingerprint verlust > /dev/null
[ "$(differing vorverlust verlust)" = "appearance_mail_template " ] \
  || { diff "$work/fingerprint-vorverlust" "$work/fingerprint-verlust" || true; fail "the fingerprint did not name exactly the deliberate loss: '$(differing vorverlust verlust)'"; }
echo "N9 counter test: the deliberate loss in appearance_mail_template changes the fingerprint and is named"

# The packages as an operator keeps them (Update step 1): the previous version is this change, the next version a
# synthetic one whose first new migration succeeds and whose second really fails.
mkdir -p "$work/package-previous" "$work/package-next" "$work/next"
cp -a deploy/. "$work/package-previous/"
cp -a deploy/. "$work/package-next/"
cp "$work/env" "$work/env.previous"
next_applied=$(printf '%04d' $((10#$new_newest + 1)))
next_failing=$(printf '%04d' $((10#$new_newest + 2)))
# Labelled with the project, so that the clean up of the workflow also finds it after an abort before "docker rm" (H2).
container=$(docker create --label "com.docker.compose.project=$project" --label taskmanager.update-check=jar-copy "$new_image")
docker cp "$container:/app/app.jar" "$work/next/app.jar" > /dev/null
docker rm "$container" > /dev/null
# The archive comes out read only (0444 in the image); the check appends to its own copy.
chmod u+w "$work/next/app.jar"
python3 - "$work/next/app.jar" "$next_applied" "$next_failing" <<'PY'
import sys, zipfile
jar, applied, failing = sys.argv[1:]
with zipfile.ZipFile(jar, "a") as archive:
    archive.writestr(f"BOOT-INF/classes/db/migration/V{applied}__update_check_applied.sql",
                     "create table update_check_applied (id integer primary key);\ninsert into update_check_applied values (1);\n")
    archive.writestr(f"BOOT-INF/classes/db/migration/V{failing}__update_check_fails.sql",
                     "create table update_check_partial (id integer);\nselect 1 / 0;\n")
PY
printf 'FROM %s\nCOPY --chown=root:root --chmod=0444 app.jar /app/app.jar\n' "$UPDATE_CHECK_NEW_IMAGE" > "$work/next/Dockerfile"
docker build --quiet --tag "$failing_image" "$work/next" > /dev/null
next_image=$(image_id "$failing_image")

echo "Update steps 4 to 6 to the next version"
use_package "$work/package-next"
write_env "$next_image" "$new_backup_image"
dc run --rm images > /dev/null
dc up -d --wait db > /dev/null
dc exec -T db sh /opt/taskmanager/db-roles.sh > /dev/null
set +e
dc run --rm -T migrate > "$results/failed-migrate.log" 2>&1
code=$?
set -e
[ "$code" = 2 ] || { cat "$results/failed-migrate.log"; fail "migrate did not report the failed migration with 2 (got $code)"; }
grep -q "A migration failed in this run; the schema stays at version $next_applied" "$results/failed-migrate.log" \
  || { tail -40 "$results/failed-migrate.log"; fail "the report does not name the version the schema stays at"; }
grep -q "section 'Rückkehr'" "$results/failed-migrate.log" || fail "the report does not point to the way back"
[ "$(db "select count(*) from flyway_schema_history where version = '$next_applied' and success")" = 1 ] \
  || fail "the first migration of the next version is not confirmed"
[ "$(db "select count(*) from flyway_schema_history where version = '$next_failing' and success")" = 0 ] \
  || fail "the failing migration counts as applied"
set +e
timeout 300 docker compose -p "$project" "${compose_files[@]}" --env-file "$work/env" run --rm -T --no-deps app \
  > "$results/start-after-failed.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] && [ "$code" -ne 124 ] || fail "the web process of the next version started after the failed update"
grep -q "migration(s) are missing" "$results/start-after-failed.log" \
  || { tail -40 "$results/start-after-failed.log"; fail "the web process of the next version does not explain its refusal"; }
echo "the next version stopped at V$next_applied: migrate ended with 2 and named the way back, its web process does not start"

echo "Rückkehr step 1 and 2: stop, the kept package and settings of the previous version"
dc stop > /dev/null
use_package "$work/package-previous"
cp "$work/env.previous" "$work/env"
dc run --rm images > /dev/null
# Only to show the refusal: the previous image against the database the next version left behind.
dc up -d --wait db > /dev/null
set +e
timeout 300 docker compose -p "$project" "${compose_files[@]}" --env-file "$work/env" run --rm -T --no-deps app \
  > "$results/start-previous.log" 2>&1
code=$?
set -e
[ "$code" -ne 0 ] && [ "$code" -ne 124 ] || fail "the previous version started on the database of the next version"
grep -q "newer than this image" "$results/start-previous.log" \
  || { tail -40 "$results/start-previous.log"; fail "the previous version does not explain its refusal"; }
echo "the previous image refuses the newer database: there is no way back through an older image"
dc stop > /dev/null

echo "Rückkehr step 3: the point and its proof"
dc run --rm --no-deps backup points > "$results/points.log" 2>&1 || { cat "$results/points.log"; fail "the points could not be listed"; }
grep -q "$point" "$results/points.log" || fail "the point $point is not listed"
dc run --rm --no-deps backup restore /restore "$point" > "$results/restore.log" 2>&1 \
  || { tail -30 "$results/restore.log"; fail "the restore of $point failed"; }
grep -q "point $point restored and proven" "$results/restore.log" || fail "the restore did not take $point"

echo "Rückkehr step 4: the guarded delete block of docs/INSTALL.md, first with a wrong name, then with the right one"
dc down > /dev/null
# Exactly the block of the guide (TASK-139, F9 of the review of TASK-137), with "docker compose" bound to this run.
python3 -I - docs/INSTALL.md > "$work/delete-block.sh" <<'PY'
import sys, textwrap
text = open(sys.argv[1], encoding="utf-8").read()
start = text.index("3. **Löschen**")
block = text[text.index("```sh\n", start) + 6:]
print(textwrap.dedent(block[:block.index("```")]))
PY
grep -q '^  expected=task-manager ' "$work/delete-block.sh" || fail "the guide has no delete block with the expected name"
guarded_delete() {
  sed "s/^  expected=task-manager /  expected=$1 /" "$work/delete-block.sh" > "$work/delete-block-run.sh"
  # shellcheck disable=SC2329
  # Not through dc: dc itself calls "docker compose" and would call this function again without end.
  docker() {
    if [ "$1" = compose ]; then
      shift; command docker compose -p "$project" "${compose_files[@]}" --env-file "$work/env" "$@"
    else
      command docker "$@"
    fi
  }
  # shellcheck source=/dev/null
  . "$work/delete-block-run.sh"
}
if (guarded_delete "another-installation") > "$results/delete-wrong.log" 2>&1; then
  fail "the delete block of the guide deleted under a wrong expected name"
fi
grep -q "abgebrochen" "$results/delete-wrong.log" || { cat "$results/delete-wrong.log"; fail "the delete block gave no reason"; }
for volume in db-data files-data; do
  docker volume inspect "${project}_$volume" > /dev/null 2>&1 || fail "the refused delete block removed ${project}_$volume"
done
(guarded_delete "$project") > "$results/delete.log" 2>&1 || { cat "$results/delete.log"; fail "the delete block of the guide failed"; }
for volume in db-data files-data; do
  if docker volume inspect "${project}_$volume" > /dev/null 2>&1; then fail "${project}_$volume is still there"; fi
done

echo "Rückkehr step 5: in place, released, started"
dc run --rm restore-place > "$results/restore-place.log" 2>&1 || { cat "$results/restore-place.log"; fail "restore-place failed"; }
dc up -d --wait db > /dev/null
set +e
dc run --rm -T app after-restore "Updateprüfung" > "$results/after-restore.log" 2>&1
code=$?
set -e
case "$code" in
  0) ;;
  3) cat "$results/after-restore.log"; fail "after-restore found missing or damaged contents (3); do not start" ;;
  *) cat "$results/after-restore.log"; fail "after-restore did not release the restore ($code)" ;;
esac
grep -q "rights for the whole company\|company rights\|Unternehmen" "$results/after-restore.log" \
  || echo "note: after-restore printed no list of accounts with company rights"
up_ports || fail "the containers did not start"
await_tls
[ "$(db "select count(*) from flyway_schema_history where version in ('$next_applied', '$next_failing') or not success")" = 0 ] \
  || fail "the history still holds the next version"
[ "$(db "select to_regclass('public.update_check_applied') is null")" = t ] || fail "the table of the next version is still there"
[ "$(db "select count(*) from file_attachment where id = '$lost'")" = 0 ] || fail "the upload after the point is still there"
returned=$(fingerprint zurueck)
[ "$returned" = "$at_point" ] || fail "the way back did not reach the point; differing: $(differing punkt zurueck)"
cp "$work/state/probe-at-point.json" "$work/state/probe.json"
flows --state /state/probe.json --phase zurueck
echo "back at point $point with the previous version: content as at the point, the deliberate loss and the upload after it undone, core flows work"

step "O01: no secret in the image metadata, no privileged container"
# Scope (N12): configuration, environment and history of both images against the secrets of this run, read from the
# protected file; the files inside the images are not searched. Values generated for this run cannot have reached a
# build; the check proves that the template passes them only at run time.
# The metadata are read completely into files first, each read checked for itself, and searched afterwards: grep -q may
# end a pipe early, which pipefail would report as a failed check (TASK-139, hint H3 of the review of TASK-137).
for image in "$new_image" "$new_backup_image"; do
  docker image inspect "$image" > "$work/inspect" || fail "the configuration of the image $image could not be read"
  docker history --no-trunc "$image" > "$work/history" || fail "the history of the image $image could not be read"
  [ -s "$work/inspect" ] && [ -s "$work/history" ] || fail "the metadata of the image $image are empty"
  if holds_secret "$work/inspect" "$work/history"; then
    fail "the metadata of the image $image holds a secret of this installation"
  fi
done
docker image inspect "$new_image" --format '{{json .Config.Env}}' > "$work/image-env" \
  || fail "the environment of the image could not be read"
if grep -qiE 'password=.|secret_key=.' "$work/image-env"; then
  fail "the image environment sets a password or key"
fi
rm -f "$work/inspect" "$work/history" "$work/image-env"
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
echo "no secret in the image metadata; no container privileged, with the Docker socket or able to gain privileges"

# The report names references, never a path of the runner (N8).
REPORT_BASE="$UPDATE_CHECK_BASE" REPORT_BASE_COMMIT="${UPDATE_CHECK_BASE_COMMIT:-$(git -C "$old_source" rev-parse HEAD)}" \
REPORT_OLD_MIGRATIONS="V$old_newest" REPORT_OLD_IMAGE="$old_image" REPORT_OLD_BACKUP_IMAGE="$old_backup_image" \
REPORT_TEMPLATE="${old_template:+release}" REPORT_TARGET="$target" REPORT_NEW_MIGRATIONS="V$new_newest" \
REPORT_NEW_IMAGE="$new_image" REPORT_NEW_BACKUP_IMAGE="$new_backup_image" REPORT_CONTENT="$before" REPORT_POINT="$point" \
REPORT_CONTENTS="$contents" REPORT_NEXT="V$next_applied applied, V$next_failing failed" \
python3 - "$results/update-run.json" "$work/inventory" <<'PY'
import json, os, sys
e = os.environ
json.dump({
    "task": "TASK-137",
    "from": {"base": e["REPORT_BASE"], "commit": e["REPORT_BASE_COMMIT"], "migrations": e["REPORT_OLD_MIGRATIONS"],
             "image": e["REPORT_OLD_IMAGE"], "backupImage": e["REPORT_OLD_BACKUP_IMAGE"],
             "template": e["REPORT_TEMPLATE"] or "this change with tools/update/compose.old.yaml"},
    "to": {"commit": e["REPORT_TARGET"], "migrations": e["REPORT_NEW_MIGRATIONS"], "image": e["REPORT_NEW_IMAGE"],
           "backupImage": e["REPORT_NEW_BACKUP_IMAGE"]},
    "content": e["REPORT_CONTENT"],
    "checkedTables": open(sys.argv[2], encoding="utf-8").read().split(),
    "returnPoint": e["REPORT_POINT"],
    "returnPointContents": int(e["REPORT_CONTENTS"]),
    "failedNextVersion": e["REPORT_NEXT"],
}, open(sys.argv[1], "w", encoding="utf-8"), indent=2)
PY
echo
echo "Update check passed: $UPDATE_CHECK_BASE (V$old_newest) to $target (V$new_newest); content: $before. Report: $results/update-run.json"
