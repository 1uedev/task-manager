#!/usr/bin/env python3
"""Keeps the secrets of a GitLab run out of its evidence (TASK-115, F2).

    redact.py < log > diagnostics.txt   removes every secret of the run and drops every line that names an OAuth
                                        parameter, a token, a password or an authorization header
    redact.py --check FILE...           fails when one of the files still holds such a secret or line

The secrets are the values of the variables the run sets for itself (passwords, the key of the application, the token
of root, the OAuth application and the one time password). Only the Python standard library is used.
"""

from __future__ import annotations

import os
import re
import sys

SECRET_VARIABLES = (
    "TASK_MANAGER_DB_PASSWORD", "TASK_MANAGER_DEMO_PASSWORD", "GITLAB_CHECK_ROOT_PASSWORD", "GITLAB_CHECK_SECRET_KEY",
    "GITLAB_CHECK_TOKEN", "GITLAB_CHECK_APP_SECRET", "GITLAB_CHECK_OTP",
)
# Lines that may carry a secret the run did not choose itself: an authorization code, a state, a PKCE value, a token or a
# password of GitLab, or an authorization header.
SENSITIVE = re.compile(
    r"(?i)(\b(code|state|code_challenge|code_verifier|access_token|refresh_token|client_secret|id_token)=)"
    r"|glpat-|gloas-|\bauthorization\b|private-token|\bpassword\b|\bsecret\b|\bbearer\b")


def secrets() -> list[str]:
    values = [os.environ.get(name, "") for name in SECRET_VARIABLES]
    # Short values would match ordinary text; every secret of the run is long.
    return sorted({value for value in values if len(value) >= 8}, key=len, reverse=True)


def clean(lines: list[str], known: list[str]) -> list[str]:
    kept = []
    for line in lines:
        if SENSITIVE.search(line):
            continue
        for value in known:
            line = line.replace(value, "[entfernt]")
        kept.append(line)
    return kept


def leaks(text: str, known: list[str]) -> list[str]:
    found = [f"line {number}: secret pattern" for number, line in enumerate(text.splitlines(), 1) if SENSITIVE.search(line)]
    found += ["secret of the run" for value in known if value in text]
    return found


def main(arguments: list[str]) -> int:
    known = secrets()
    if arguments[:1] == ["--check"]:
        failed = False
        for path in arguments[1:]:
            with open(path, encoding="utf-8", errors="replace") as handle:
                problems = leaks(handle.read(), known)
            if problems:
                failed = True
                # Names where, never what.
                print(f"{path}: {len(problems)} finding(s), first {problems[0]}", file=sys.stderr)
        return 1 if failed else 0
    sys.stdout.writelines(clean(sys.stdin.readlines(), known))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
