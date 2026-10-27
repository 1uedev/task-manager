#!/usr/bin/env bash
# Checks the demonstration image end to end (TASK-031): build, refusal without settings, first start with the
# compose file, setup with bootstrap-admin, synthetic demo data, rights, conflict and search through the API,
# the served interface with its headers, the separate worker process (TASK-054), and a restart that keeps data and
# the fixed session end.
# Uses throw-away synthetic passwords and removes its containers and volume at the end.
set -euo pipefail
cd "$(dirname "$0")/../.."

project=tm-image-check
export COMPOSE_PROJECT_NAME=$project
export TASK_MANAGER_PORT=${TASK_MANAGER_PORT:-18090}
export TASK_MANAGER_DB_PASSWORD="ci-$(head -c 18 /dev/urandom | base64 | tr -d '/+=')"
export TASK_MANAGER_DEMO_PASSWORD="ci-$(head -c 18 /dev/urandom | base64 | tr -d '/+=')"
state=$(mktemp)
base="http://127.0.0.1:$TASK_MANAGER_PORT"
cleanup() {
  docker compose logs app worker > "${IMAGE_CHECK_LOGS:-/dev/null}" 2>&1 || true
  docker compose --profile tools down -v --remove-orphans > /dev/null 2>&1 || true
  rm -f "$state"
}
trap cleanup EXIT
step() { echo; echo "== $*"; }

step "Build the image"
# IMAGE_CHECK_SKIP_BUILD=1 checks an image that was built before under the name task-manager:demo.
if [ "${IMAGE_CHECK_SKIP_BUILD:-0}" != 1 ]; then
  docker compose build app
fi

step "The image holds no password and refuses to start without settings"
if docker image inspect task-manager:demo --format '{{json .Config.Env}}' | grep -qi password; then
  echo "image environment names a password" >&2; exit 1
fi
set +e
out=$(timeout 120 docker run --rm task-manager:demo 2>&1)
code=$?
set -e
[ "$code" -ne 0 ] && [ "$code" -ne 124 ] || { echo "start without settings ended with $code" >&2; exit 1; }
grep -q "required settings are missing" <<< "$out" || { echo "$out"; echo "refusal does not name the settings" >&2; exit 1; }

step "First start"
docker compose up -d --no-build --wait --wait-timeout 180

step "Interface and headers"
headers=$(curl -fsS -D - -o /tmp/index.html "$base/")
grep -qi '^content-security-policy: default-src' <<< "$headers"
grep -qi '^cache-control: no-cache' <<< "$headers"
grep -q '<div id="root">' /tmp/index.html
asset=$(grep -o '/assets/index-[A-Za-z0-9_-]*\.js' /tmp/index.html | head -n 1)
curl -fsS -D - -o /dev/null "$base$asset" | grep -qi '^cache-control: public, max-age=31536000, immutable'

step "The worker process works off the recurring tasks without a port"
sql() { docker compose exec -T db psql -U taskmanager -d taskmanager -tAc "$1"; }
for _ in $(seq 1 60); do
  done_jobs=$(sql "select count(*) from job where kind = 'recurring.files.cleanup' and state = 'succeeded'")
  [ "$done_jobs" -ge 1 ] && break
  sleep 2
done
[ "$done_jobs" -ge 1 ] || { docker compose logs worker; echo "the worker ran no clean up" >&2; exit 1; }
[ "$(sql "select count(*) from job_worker")" -eq 1 ] || { echo "not exactly one executor polls" >&2; exit 1; }
[ -z "$(docker compose port worker 8080 2>/dev/null || true)" ] || { echo "the worker publishes a port" >&2; exit 1; }

step "Setup, demo data and checks"
otp=$(docker compose run --rm app bootstrap-admin admin | sed -n 's/^One time password (shown only now): //p')
[ -n "$otp" ] || { echo "no one time password" >&2; exit 1; }
docker compose --profile tools run --rm demo seed --profile demo --one-time-password "$otp"
docker compose --profile tools run --rm -v "$state:/state.json" demo verify --save /state.json

step "Restart keeps data and the fixed session end"
docker compose down
docker compose up -d --no-build --wait --wait-timeout 180
docker compose --profile tools run --rm -v "$state:/state.json" demo verify --resume /state.json

echo
echo "image check passed"
