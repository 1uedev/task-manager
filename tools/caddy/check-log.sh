#!/bin/sh
# Proves that the access log of deploy/Caddyfile keeps no query and no referrer (TASK-136, finding F6 of the review of
# TASK-132): the Caddy of the template with a certificate of its own receives a synthetic OAuth return with code and state
# in the query and in the referrer, and its log must name the path and the status but none of the values. A stand in for
# the web process answers every request. Needs Docker; leaves nothing behind.
set -eu
here=$(cd "$(dirname "$0")/../.." && pwd)
image=$(sed -n 's/^ *image: \(caddy:[^ ]*\)$/\1/p' "$here/deploy/compose.yaml")
run="taskmanager-caddylog-$$"
work=$(mktemp -d "${RUNNER_TEMP:-/tmp}/caddylog.XXXXXX")
cleanup() {
  docker rm -f "$run-app" "$run-caddy" >/dev/null 2>&1 || echo "clean up: a container of $run was not removed" >&2
  docker network rm "$run" >/dev/null 2>&1 || echo "clean up: the network $run was not removed" >&2
  rm -rf "$work"
}
trap cleanup EXIT
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=tasks.example.org" -keyout "$work/key.pem" \
  -out "$work/certificate.pem" >/dev/null 2>&1
chmod 644 "$work/key.pem"
docker network create "$run" >/dev/null
docker run -d --name "$run-app" --network "$run" --network-alias app "$image" \
  caddy respond --listen :8080 --body ok >/dev/null
docker run -d --name "$run-caddy" --network "$run" --network-alias tasks.example.org -e TASK_MANAGER_DOMAIN=tasks.example.org -e TASK_MANAGER_TLS=own \
  -v "$here/deploy/Caddyfile:/etc/caddy/Caddyfile:ro" -v "$work:/certs:ro" "$image" >/dev/null
code="code-SYNTHETIC-$$"; state="state-SYNTHETIC-$$"
answer=""
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  answer=$(docker run --rm --network "$run" "$image" sh -c "wget -q -O - --no-check-certificate \
    --header 'Referer: https://git.example.org/callback?code=$code' \
    'https://tasks.example.org/api/git/oauth/callback?code=$code&state=$state' 2>/dev/null" || true)
  [ "$answer" = ok ] && break
  sleep 1
done
[ "$answer" = ok ] || { echo "the stand in was not reached through Caddy" >&2; docker logs "$run-caddy" >&2; exit 1; }
sleep 1
docker logs "$run-caddy" > "$work/log" 2>&1
grep -q '/api/git/oauth/callback?' "$work/log" || { echo "the request is missing from the access log" >&2; exit 1; }
if grep -Eq "$code|$state" "$work/log"; then
  echo "the access log contains the query or the referrer of the OAuth return" >&2
  exit 1
fi
echo "access log without query and referrer: path and status kept, code and state not written"
