#!/bin/sh
# Checks the operations package of a version the way an operator receives it (TASK-137, finding N11 of the review of
# TASK-136): unpacked into a fresh directory without the repository, with a synthetic .env made from its .env.example.
#
#   check-package.sh PACKAGE.tar.gz VERSION
#
# Checks: the package holds exactly task-manager-VERSION/ with deploy/ and docs/; .env.example names both digests;
# check-images.sh accepts them; "docker compose config" resolves the template alone and with compose.proxy.yaml, every
# file it mounts exists in the package, both images are the fixed names of the registry with the digests of the
# package, and with the proxy file Caddy is left out. Needs Docker Compose; changes nothing outside a temporary directory.
set -eu
package=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
version=${2:?version without v}
work=$(mktemp -d "${RUNNER_TEMP:-/tmp}/package.XXXXXX")
trap 'rm -rf "$work"' EXIT
refuse() { echo "package $version refused: $1" >&2; exit 1; }
tar -C "$work" -xzf "$package"
[ "$(ls -A "$work")" = "task-manager-$version" ] || refuse "it must hold exactly task-manager-$version/"
deploy="$work/task-manager-$version/deploy"
[ -f "$deploy/compose.yaml" ] && [ -d "$work/task-manager-$version/docs" ] || refuse "deploy/compose.yaml or docs/ is missing"
cd "$deploy"
app=$(sed -n 's/^TASK_MANAGER_IMAGE_DIGEST=//p' .env.example)
backup=$(sed -n 's/^TASK_MANAGER_BACKUP_IMAGE_DIGEST=//p' .env.example)
env TASK_MANAGER_IMAGE_DIGEST="$app" TASK_MANAGER_BACKUP_IMAGE_DIGEST="$backup" sh ./check-images.sh > /dev/null \
  || refuse "check-images.sh refuses the digests of .env.example"
# Synthetic settings: every empty value gets a placeholder; nothing here is a real secret.
sed -e 's/^\(TASK_MANAGER_[A-Z0-9_]*\)=$/\1=synthetic-placeholder/' \
    -e 's|^TASK_MANAGER_BACKUP_REPOSITORY=.*|TASK_MANAGER_BACKUP_REPOSITORY=/state/repository|' .env.example > .env
for files in "compose.yaml" "compose.yaml:compose.proxy.yaml"; do
  COMPOSE_FILE=$files docker compose --env-file .env --profile maintenance config --format json > "$work/config.json" 2> "$work/config.log" \
    || { cat "$work/config.log" >&2; refuse "docker compose config fails with $files"; }
  python3 -I - "$work/config.json" "$deploy" "$app" "$backup" "$files" <<'PY' || refuse "the resolved template with $files does not fit"
import json, os, sys
config, deploy, app, backup, files = sys.argv[1:]
services = json.load(open(config, encoding="utf-8"))["services"]
problems = []
for name in ("app", "worker", "migrate"):
    if services.get(name, {}).get("image") != f"ghcr.io/1uedev/task-manager@{app}":
        problems.append(f"{name} does not use the digest of the package")
if services.get("backup", {}).get("image") != f"ghcr.io/1uedev/task-manager-backup@{backup}":
    problems.append("backup does not use the digest of the package")
# The directories of certificate and SSH key are the operator's own (.env names them); every other mounted path is a file
# of the package.
own = {os.path.join(deploy, "tls"), os.path.join(deploy, "ssh")}
for name, service in services.items():
    for volume in service.get("volumes", []):
        source = volume.get("source", "")
        if volume.get("type") == "bind" and source.startswith(deploy) and source not in own and not os.path.exists(source):
            problems.append(f"{name} mounts {os.path.relpath(source, deploy)}, which the package lacks")
proxy = files.endswith("compose.proxy.yaml")
if proxy and "caddy" in services:
    problems.append("caddy is not left out with the proxy file")
if proxy and not services["app"].get("ports"):
    problems.append("the web process has no address for the proxy")
for problem in problems:
    print(problem, file=sys.stderr)
sys.exit(1 if problems else 0)
PY
done
echo "package $version: unpacked alone, digests accepted, template resolved with and without the proxy file"
