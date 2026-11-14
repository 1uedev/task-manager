#!/usr/bin/env bash
# Run of the Git link against a real GitLab 16 in a container (TASK-113, DEC-143, rule 1): a CA of its own and a
# certificate for gitlab.test, GitLab CE 16.11 with HTTPS, the task manager of the demonstration image, the administrator
# from bootstrap-admin, a token of root and a trusted OAuth application in GitLab, then tools/git/gitlab_check.py.
# Every password, key and token is made up for the run; containers, volumes and certificates are removed at the end.
#   GITLAB_CHECK_SKIP_BUILD=1   uses an image task-manager:demo built before
#   GITLAB_CHECK_RESULTS=<dir>  empty or new folder for gitlab-run.json and diagnostics.txt (default
#                               ./gitlab-results/<compose project>)
#   GITLAB_CHECK_FAIL_AT=<n>    lets step n of gitlab_check.py fail on purpose (evidence that no secret leaks)
# Each run has a compose project and a results folder of its own (TASK-115, F3). Raw logs never leave the run: on a
# failure diagnostics.txt holds the state of the containers and the last log lines without any secret of the run and
# without lines that name OAuth parameters or tokens (TASK-115, F2); tools/git/redact.py checks it.
set -euo pipefail
cd "$(dirname "$0")/../.."

random() { head -c 18 /dev/urandom | base64 | tr -d '/+='; }
run_id=$(head -c 6 /dev/urandom | od -An -tx1 | tr -d ' \n')
export COMPOSE_PROJECT_NAME="tm-gitlab-check-$run_id"
compose=(docker compose -f compose.yaml -f tools/git/compose.gitlab.yaml)
GITLAB_CHECK_CERTS=""
started=0
cleanup() {
  status=$?
  trap - EXIT
  if [ "$started" = 1 ]; then
    if [ "$status" != 0 ] && [ -n "${GITLAB_CHECK_RESULTS:-}" ] && [ -d "$GITLAB_CHECK_RESULTS" ]; then
      { "${compose[@]}" ps --all 2>&1 || true; "${compose[@]}" logs --no-color --tail 200 app gitlab 2>&1 || true; } \
        | python3 -I tools/git/redact.py > "$GITLAB_CHECK_RESULTS/diagnostics.txt" || echo "diagnostics withheld" >&2
    fi
    # Nothing leaves the run that holds one of its secrets (TASK-115, F2).
    if [ -n "${GITLAB_CHECK_RESULTS:-}" ] && [ -n "$(ls -A "$GITLAB_CHECK_RESULTS" 2> /dev/null)" ] \
      && ! python3 -I tools/git/redact.py --check "$GITLAB_CHECK_RESULTS"/*; then
      rm -f "$GITLAB_CHECK_RESULTS"/*
      echo "the evidence held a secret of the run and was removed" >&2
      status=4
    fi
    if ! "${compose[@]}" --profile tools down -v --remove-orphans > /dev/null 2>&1; then
      echo "cleanup of compose project $COMPOSE_PROJECT_NAME failed; remove it with: docker compose -p $COMPOSE_PROJECT_NAME down -v" >&2
      [ "$status" != 0 ] || status=3
    fi
  fi
  if [ -n "$GITLAB_CHECK_CERTS" ]; then rm -rf "$GITLAB_CHECK_CERTS"; fi
  exit "$status"
}
trap cleanup EXIT
GITLAB_CHECK_CERTS=$(mktemp -d)
export GITLAB_CHECK_CERTS
export TASK_MANAGER_PORT=${TASK_MANAGER_PORT:-18091}
export TASK_MANAGER_DB_PASSWORD="run-$(random)"
export TASK_MANAGER_DEMO_PASSWORD="run-$(random)"
export GITLAB_CHECK_ROOT_PASSWORD="Run-$(random)"
GITLAB_CHECK_SECRET_KEY=$(head -c 32 /dev/urandom | base64)
export GITLAB_CHECK_SECRET_KEY
export GITLAB_CHECK_RESULTS=${GITLAB_CHECK_RESULTS:-$PWD/gitlab-results/$COMPOSE_PROJECT_NAME}
mkdir -p "$GITLAB_CHECK_RESULTS"
if [ -n "$(ls -A "$GITLAB_CHECK_RESULTS")" ]; then
  echo "the results folder $GITLAB_CHECK_RESULTS is not empty; an old result would stay" >&2
  exit 2
fi
step() { echo; echo "== $*"; }
echo "compose project $COMPOSE_PROJECT_NAME, results in $GITLAB_CHECK_RESULTS"

step "A CA of its own and the certificate of gitlab.test"
mkdir -p "$GITLAB_CHECK_CERTS/ca" "$GITLAB_CHECK_CERTS/ssl"
(
  cd "$GITLAB_CHECK_CERTS"
  openssl req -x509 -newkey rsa:2048 -nodes -keyout ca.key -out ca/ca.crt -days 2 -subj "/CN=Task Manager Run CA" \
    -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign" 2> /dev/null
  openssl req -newkey rsa:2048 -nodes -keyout ssl/gitlab.test.key -out gitlab.csr -subj "/CN=gitlab.test" 2> /dev/null
  printf 'subjectAltName=DNS:gitlab.test\nbasicConstraints=CA:FALSE\nextendedKeyUsage=serverAuth\n' > ext.cnf
  openssl x509 -req -in gitlab.csr -CA ca/ca.crt -CAkey ca.key -CAcreateserial -out ssl/gitlab.test.crt -days 2 \
    -extfile ext.cnf 2> /dev/null
  # GitLab reads the key as its own user; the folder is mounted read only and removed after the run.
  chmod 0644 ssl/gitlab.test.key ssl/gitlab.test.crt ca/ca.crt
  rm -f ca.key gitlab.csr ext.cnf
)

step "Build the image"
if [ "${GITLAB_CHECK_SKIP_BUILD:-0}" != 1 ]; then
  "${compose[@]}" build app
fi

step "Start PostgreSQL, the task manager and GitLab (GitLab needs several minutes)"
started=1
"${compose[@]}" up -d --no-build --wait --wait-timeout 1200 db app gitlab
"${compose[@]}" exec -T gitlab head -n 1 /opt/gitlab/version-manifest.txt

step "Administrator of the task manager"
GITLAB_CHECK_OTP=$("${compose[@]}" run --rm --no-deps app bootstrap-admin admin | sed -n 's/^One time password (shown only now): //p')
[ -n "$GITLAB_CHECK_OTP" ] || { echo "no one time password" >&2; exit 1; }
export GITLAB_CHECK_OTP

step "Token of root and trusted OAuth application in GitLab"
setup=$("${compose[@]}" exec -T -e TASK_MANAGER_CALLBACK=http://app:8080/api/v1/git/oauth/callback gitlab \
  gitlab-rails runner - < tools/git/gitlab-setup.rb | tail -n 1)
# The answer goes to Python on its standard input, never in an argument list that other processes can read (F2).
field() { printf '%s' "$setup" | python3 -I -c 'import json, sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }
GITLAB_CHECK_TOKEN=$(field token)
GITLAB_CHECK_APP_ID=$(field app_id)
GITLAB_CHECK_APP_SECRET=$(field app_secret)
GITLAB_CHECK_APP_DB_ID=$(field app_db_id)
export GITLAB_CHECK_TOKEN GITLAB_CHECK_APP_ID GITLAB_CHECK_APP_SECRET GITLAB_CHECK_APP_DB_ID

step "Connect, release, link, merge, rename and revoke"
"${compose[@]}" --profile tools run --rm -e GITLAB_CHECK_FAIL_AT="${GITLAB_CHECK_FAIL_AT:-}" gitlab-check

echo
echo "gitlab check passed"
