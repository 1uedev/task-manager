#!/bin/sh
# Proves that no log of the Caddy of deploy/Caddyfile keeps a query, a referrer or a cookie (TASK-136, finding F6 of the
# review of TASK-132; TASK-137, finding N5 of the review of TASK-136). The Caddy of the template with a certificate of its
# own receives synthetic OAuth returns with code and state in the query and in the referrer and a session cookie in four
# cases:
#   1. success: the stand in of the web process answers;
#   2. redirect of the web process: the stand in answers 302 with the code in the Location header and sets a cookie;
#   3. redirect to HTTPS: the request comes over plain HTTP on port 80;
#   4. 502: the stand in is stopped, so the reverse proxy writes its error log.
# The combined output of Caddy (stdout and stderr) must name the path but none of the values. The values never appear in an
# argument of a process: the clients take them by name from the environment, the search takes them from a file readable
# only for this script, and a finding names only the case and the kind of field, never the line (TASK-139, finding F10 of
# the review of TASK-137). Needs Docker; leaves nothing behind. With the argument "old" it uses the Caddyfile of TASK-136 from git as the counter check, which must fail.
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
caddyfile="$here/deploy/Caddyfile"
if [ "${1:-}" = old ]; then
  git -C "$here" show "${CADDY_OLD_REVISION:-135a2c3}:deploy/Caddyfile" > "$work/Caddyfile.old"
  caddyfile="$work/Caddyfile.old"
fi
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=tasks.example.org" -keyout "$work/key.pem" \
  -out "$work/certificate.pem" >/dev/null 2>&1
chmod 644 "$work/key.pem"
umask 077
CODE="code-SYNTHETIC-$$"; STATE="state-SYNTHETIC-$$"; COOKIE="cookie-SYNTHETIC-$$"; REFERRER="referrer-SYNTHETIC-$$"
export CODE STATE COOKIE REFERRER
# One file per kind of field with its value as a fixed string, readable only for this script.
mkdir "$work/patterns"
printf '%s\n' "$CODE" > "$work/patterns/code"
printf '%s\n' "$STATE" > "$work/patterns/state"
printf '%s\n' "$COOKIE" > "$work/patterns/cookie"
printf '%s\n' "$REFERRER" > "$work/patterns/referrer"
umask 022
# The stand in of the web process: a redirect that carries the code in its Location and sets a cookie, otherwise "ok".
cat > "$work/app.caddyfile" <<EOF
{
	admin off
	auto_https off
}
:8080 {
	handle /api/v1/git/oauth/redirect {
		header Set-Cookie "TM_SESSION={env.COOKIE}; Secure; HttpOnly"
		redir * "/api/v1/git/oauth/done?code={env.CODE}&state={env.STATE}" 302
	}
	respond "ok"
}
EOF
docker network create "$run" >/dev/null
docker run -d --name "$run-app" --network "$run" --network-alias app -e CODE -e STATE -e COOKIE \
  -v "$work/app.caddyfile:/etc/caddy/Caddyfile:ro" "$image" >/dev/null
docker run -d --name "$run-caddy" --network "$run" --network-alias tasks.example.org -e TASK_MANAGER_DOMAIN=tasks.example.org \
  -e TASK_MANAGER_TLS=own -v "$caddyfile:/etc/caddy/Caddyfile:ro" -v "$work:/certs:ro" "$image" >/dev/null

# One request through Caddy without following a redirect; prints the status of the answer. The values travel in the
# environment of the client, not in its arguments.
python_image=$(sed -n 's/^ *image: \(python:[^ ]*\)$/\1/p' "$here/compose.yaml")
cat > "$work/request.py" <<'EOF'
import os, ssl, sys, urllib.error, urllib.request
class Keep(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None
context = ssl.create_default_context()
context.check_hostname = False
context.verify_mode = ssl.CERT_NONE
opener = urllib.request.build_opener(Keep, urllib.request.HTTPSHandler(context=context))
url = sys.argv[1].replace("CODE", os.environ["CODE"]).replace("STATE", os.environ["STATE"])
headers = {"Referer": "https://git.example.org/callback?code=" + os.environ["CODE"] + "&ref=" + os.environ["REFERRER"],
           "Cookie": "TM_SESSION=" + os.environ["COOKIE"]}
try:
    print(opener.open(urllib.request.Request(url, headers=headers), timeout=10).status)
except urllib.error.HTTPError as answer:
    print(answer.code)
except Exception:
    print("")
EOF
request() {
  docker run --rm --network "$run" -e CODE -e STATE -e COOKIE -e REFERRER \
    -v "$work/request.py:/request.py:ro" "$python_image" python -I /request.py "$1" 2>/dev/null || true
}

answer=""
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  answer=$(request "https://tasks.example.org/api/v1/git/oauth/callback?code=CODE&state=STATE")
  [ "$answer" = 200 ] && break
  sleep 1
done
[ "$answer" = 200 ] || { echo "1. the stand in was not reached through Caddy ($answer)" >&2; exit 1; }
# The number of log lines after each case, so that a finding names its case.
mark() { sleep 1; docker logs "$run-caddy" 2>&1 | wc -l > "$work/mark-$1"; }
docker logs "$run-caddy" 2>&1 | wc -l > "$work/mark-0"
# Case 1 again from a known start: the attempts above may have reached Caddy before it listened.
answer=$(request "https://tasks.example.org/api/v1/git/oauth/callback?code=CODE&state=STATE")
[ "$answer" = 200 ] || { echo "1. the stand in answered $answer the second time" >&2; exit 1; }
mark 1
answer=$(request "https://tasks.example.org/api/v1/git/oauth/redirect?code=CODE&state=STATE")
[ "$answer" = 302 ] || { echo "2. the redirect of the web process did not come through ($answer)" >&2; exit 1; }
mark 2
answer=$(request "http://tasks.example.org/api/v1/git/oauth/callback?code=CODE&state=STATE")
[ "$answer" = 308 ] || [ "$answer" = 301 ] || [ "$answer" = 302 ] || { echo "3. no redirect to HTTPS ($answer)" >&2; exit 1; }
mark 3
docker stop "$run-app" >/dev/null
answer=$(request "https://tasks.example.org/api/v1/git/oauth/callback?code=CODE&state=STATE")
[ "$answer" = 502 ] || { echo "4. the stopped web process did not answer 502 ($answer)" >&2; exit 1; }
mark 4

# stdout and stderr together, as "docker compose logs" shows them.
docker logs "$run-caddy" > "$work/log" 2>&1
grep -q '/api/v1/git/oauth/callback?' "$work/log" || { echo "the request is missing from the access log" >&2; exit 1; }
grep -q '"status": *502\|"status":502' "$work/log" || { echo "the 502 is missing from the access log" >&2; exit 1; }
grep -qi 'error' "$work/log" || { echo "the error log of the reverse proxy is missing" >&2; exit 1; }
# The whole log first (also lines before the first mark), then each case for itself; only case and field are named.
found=0
for field in code state cookie referrer; do
  if grep -qF -f "$work/patterns/$field" "$work/log"; then
    named=0
    for case in 1 2 3 4; do
      from=$(( $(cat "$work/mark-$((case - 1))") + 1 )) to=$(cat "$work/mark-$case")
      if sed -n "${from},${to}p" "$work/log" | grep -qF -f "$work/patterns/$field"; then
        echo "case $case: a log of Caddy contains the field $field" >&2
        named=1
      fi
    done
    [ "$named" = 1 ] || echo "a log of Caddy contains the field $field outside the four cases" >&2
    found=1
  fi
done
[ "$found" = 0 ] || { echo "a log of Caddy keeps a query, a referrer or a cookie; the lines are not shown" >&2; exit 1; }
echo "access and error log without query, referrer and cookies in success, redirect of the web process, redirect to HTTPS and 502"
