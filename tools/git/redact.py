#!/usr/bin/env python3
"""Keeps the secrets of a GitLab run out of its evidence (TASK-115, F2; TASK-116, F4).

    redact.py --diagnostics < ps.json > diagnostics.txt
        writes the state of the containers from the JSON of `docker compose ps --all --format json`, field by field:
        service, state, health and exit code, each checked against a fixed form. Nothing else of the input, and never
        a log line, reaches the output.
    redact.py --check FILE...
        fails when a file is not what the run writes: gitlab-run.json must have exactly the fields of the result, the step
        names and texts, codes and services of tools/git/evidence.json and values of a fixed form, diagnostics.txt only
        lines of the form above for the services of the run; and no file may hold a secret of the run, also not percent,
        base64 or JSON encoded, checked in the raw text and in every decoded JSON string (TASK-117, F3).

The secrets are the values of the variables the run sets for itself (passwords, the key of the application, the token
of root, the OAuth application and the one time password). Only the Python standard library is used.
"""

from __future__ import annotations

import base64
import json
import os
import re
import sys
import urllib.parse
from pathlib import Path

SECRET_VARIABLES = (
    "TASK_MANAGER_DB_PASSWORD", "TASK_MANAGER_DEMO_PASSWORD", "GITLAB_CHECK_ROOT_PASSWORD", "GITLAB_CHECK_SECRET_KEY",
    "GITLAB_CHECK_TOKEN", "GITLAB_CHECK_APP_SECRET", "GITLAB_CHECK_OTP",
)
EVIDENCE = json.loads((Path(__file__).resolve().parent / "evidence.json").read_text(encoding="utf-8"))
STEPS = [(step["name"], step["detail"]) for step in EVIDENCE["steps"]]
CODES = frozenset(EVIDENCE["answer_codes"]) | frozenset(EVIDENCE["check_codes"])
SERVICES = frozenset(EVIDENCE["services"])
STATES = {"created", "running", "paused", "restarting", "removing", "exited", "dead"}
HEALTH = {"", "starting", "healthy", "unhealthy"}
VERSION = re.compile(r"^16\.[0-9]{1,2}\.[0-9]{1,2}(-ce|-ee)?$")


def secrets() -> list[str]:
    values = [os.environ.get(name, "") for name in SECRET_VARIABLES]
    # Short values would match ordinary text; every secret of the run is long.
    return sorted({value for value in values if len(value) >= 8}, key=len, reverse=True)


def forms(value: str) -> set[str]:
    """A secret as it may appear in text: plain, percent encoded, in JSON and base64 encoded (TASK-116, F4)."""
    found = {value, urllib.parse.quote(value, safe=""), urllib.parse.quote_plus(value), json.dumps(value)[1:-1]}
    raw = value.encode()
    for encode in (base64.b64encode, base64.urlsafe_b64encode):
        found.add(encode(raw).decode().rstrip("="))
    return {form for form in found if len(form) >= 8}


def diagnostics(text: str) -> list[str]:
    """The state of each container from the output of `docker compose ps --format json`, from fixed fields only."""
    text = text.strip()
    if not text:
        return []
    try:
        parsed = json.loads(text) if text.startswith("[") else [json.loads(line) for line in text.splitlines() if line.strip()]
    except ValueError:
        return ["container state unreadable"]
    lines = []
    for entry in parsed if isinstance(parsed, list) else []:
        if not isinstance(entry, dict):
            continue
        service = entry.get("Service")
        state = entry.get("State")
        health = entry.get("Health") or ""
        code = entry.get("ExitCode")
        if not (isinstance(service, str) and service in SERVICES and state in STATES and health in HEALTH
                and isinstance(code, int) and -1 <= code <= 255):
            lines.append("container state unreadable")
            continue
        lines.append(f"container {service}: state {state}, health {health or '-'}, exit {code}")
    return sorted(set(lines))


def diagnostic_line(line: str) -> bool:
    """A line of diagnostics.txt: the state of one service of the run, or the fixed line for an unreadable state."""
    if line == "container state unreadable":
        return True
    match = re.fullmatch(r"container ([a-z][a-z0-9-]{0,39}): state ([a-z]+), health ([a-z-]+), exit (-?[0-9]{1,3})", line)
    return bool(match and match[1] in SERVICES and match[2] in STATES and (match[3] == "-" or match[3] in HEALTH)
                and -1 <= int(match[4]) <= 255)


def result_problems(text: str) -> list[str]:
    """gitlab-run.json may hold exactly the fields, step texts and codes the run writes (TASK-116, F4; TASK-117, F3)."""
    try:
        result = json.loads(text)
    except ValueError:
        return ["result not JSON"]
    problems = []
    if not isinstance(result, dict) or set(result) - {"gitlab", "passed", "steps", "failed"}:
        return ["result has other fields"]
    if not isinstance(result.get("passed"), bool):
        problems.append("passed")
    if result.get("gitlab") is not None and not (isinstance(result["gitlab"], str) and VERSION.match(result["gitlab"])):
        problems.append("gitlab")
    steps = result.get("steps")
    if not isinstance(steps, list) or len(steps) > len(STEPS):
        problems.append("steps")
    else:
        for index, step in enumerate(steps):
            if not (isinstance(step, dict) and set(step) == {"step", "name", "detail", "seconds"} and step["step"] == index + 1
                    and (step["name"], step["detail"]) == STEPS[index]
                    and isinstance(step["seconds"], (int, float)) and not isinstance(step["seconds"], bool) and step["seconds"] >= 0):
                problems.append("step")
    failed = result.get("failed")
    if failed is not None:
        known = isinstance(failed, dict) and set(failed) == {"step", "name", "status", "code"} and isinstance(failed["step"], int)
        if not (known and 0 <= failed["step"] <= len(STEPS)
                and failed["name"] == (STEPS[failed["step"] - 1][0] if failed["step"] > 0 and failed["name"] != "Start" else "Start")
                and (failed["status"] is None or (isinstance(failed["status"], int) and not isinstance(failed["status"], bool)
                                                  and 100 <= failed["status"] <= 599))
                and failed["code"] in CODES):
            problems.append("failed")
    if result.get("passed") is True and (failed is not None or not isinstance(steps, list) or len(steps) != len(STEPS)):
        problems.append("passed without all steps")
    return problems


def strings(value) -> list[str]:
    """Every key and string of a decoded JSON value, so an escaped secret is found in its decoded form (TASK-117, F3)."""
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        return [part for key, item in value.items() for part in [str(key), *strings(item)]]
    if isinstance(value, list):
        return [part for item in value for part in strings(item)]
    return []


def leaks(text: str, known: list[str], kind: str = "text") -> list[str]:
    """Where a file breaks its form or holds a secret of the run; names where, never what."""
    if kind == "result":
        found = result_problems(text)
    else:
        found = [f"line {number}: not a container state" for number, line in enumerate(text.splitlines(), 1)
                 if line and not diagnostic_line(line)]
    texts = [text]
    try:
        texts += strings(json.loads(text))
    except ValueError:
        pass
    if any(form in part for value in known for form in forms(value) for part in texts):
        found.append("secret of the run")
    return found


def main(arguments: list[str]) -> int:
    known = secrets()
    if arguments[:1] == ["--check"]:
        failed = False
        for path in arguments[1:]:
            with open(path, encoding="utf-8", errors="replace") as handle:
                problems = leaks(handle.read(), known, "result" if path.endswith(".json") else "text")
            if problems:
                failed = True
                print(f"{os.path.basename(path)}: {len(problems)} finding(s), first {problems[0]}", file=sys.stderr)
        return 1 if failed else 0
    if arguments[:1] == ["--diagnostics"]:
        sys.stdout.write("".join(line + "\n" for line in diagnostics(sys.stdin.read())))
        return 0
    print("usage: redact.py --diagnostics < ps.json | --check FILE...", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
