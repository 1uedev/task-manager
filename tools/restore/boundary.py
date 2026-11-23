#!/usr/bin/env python3
"""The boundary check of the restore drill (TASK-135, findings F2 and F10 of the review of TASK-131).

  boundary.py WRITES RESTORED TIME TIME_AFTER

WRITES is the log of tools/restore/writer.py (confirmed moment, number, moment the request left, title), RESTORED the
numbers of the writer's tickets in the restored database, TIME and TIME_AFTER the moments before and after the restore
point from the manifest of the point. Every write confirmed before TIME must be restored, no write sent after TIME_AFTER
may be. Prints the number of writes checked before the boundary; exits with 1 and the reason on a violation.
"""
from __future__ import annotations

import datetime
import sys


def moment(text: str) -> datetime.datetime:
    return datetime.datetime.fromisoformat(text.replace("Z", "+00:00"))


def main(argv: list[str]) -> int:
    writes, restored, before, after = argv
    present = {int(line) for line in open(restored, encoding="utf-8") if line.strip()}
    time, time_after = moment(before), moment(after)
    checked = 0
    problems = []
    for line in open(writes, encoding="utf-8"):
        confirmed, number, sent = line.split(" ")[:3]
        number = int(number)
        if moment(confirmed) < time:
            checked += 1
            if number not in present:
                problems.append(f"ticket {number}, confirmed at {confirmed} before the boundary {before}, is missing")
        elif moment(sent) > time_after and number in present:
            problems.append(f"ticket {number}, sent at {sent} after the boundary {after}, is there")
    if checked == 0:
        problems.append("no write lay before the boundary")
    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        return 1
    print(checked)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
