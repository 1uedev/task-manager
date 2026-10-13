#!/usr/bin/env python3
"""Validate the shared planning contract without third-party dependencies.

This checks structure, not the truth of acceptance claims or human identity.
"""

import argparse
from collections import Counter
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


REQ_ID = r"REQ-[A-Z0-9]+-\d{3}"
TASK_ID = r"TASK-\d{3}"
OPEN_ID = r"OPEN-\d{3}"
REQUIRED = (
    "README.md", "AGENTS.md", "CLAUDE.md", "REQUIREMENTS.md",
    "DECISIONS.md", "ROADMAP.md", "TODO.md", "DONE.md",
    "CONTRIBUTING.md", "docs/AI_WORKFLOW.md",
)
SKIP = {".git", ".venv", "venv", "node_modules", ".worktrees", "__pycache__"}
STATUSES = {"OFFEN", "IN ARBEIT", "BLOCKIERT", "BEREIT ZUR PRÜFUNG"}
TASK_FIELDS = (
    "Status", "Verantwortlich", "Aktualisiert", "Bezug", "Abhängigkeiten",
    "Arbeitsstand", "Umfang", "Abnahmekriterien", "Nächster Schritt",
)
DONE_FIELDS = (
    "Bezug", "Verantwortlich", "Abgeschlossen", "Abnahmekriterien",
    "Ergebnis", "Prüfungen", "Review", "Abnahme", "Referenz",
)


def sections(text, prefix, level):
    pattern = rf"^{re.escape('#' * level)} ({prefix})\b[^\n]*\n"
    matches = list(re.finditer(pattern, text, re.M))
    return [
        (match.group(1), text[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(text)])
        for i, match in enumerate(matches)
    ]


def field(body, name):
    match = re.search(rf"\*\*{re.escape(name)}:\*\*\s*([^\n]*)", body)
    if not match:
        return ""
    return re.split(r"\s+·\s+|\s+\*\*", match.group(1), maxsplit=1)[0].strip()


def validate(root):
    root = Path(root).resolve()
    errors = []
    docs = {}
    for path in sorted(root.rglob("*.md")):
        relative = path.relative_to(root)
        if set(relative.parts) & SKIP:
            continue
        try:
            docs[relative.as_posix()] = path.read_text(encoding="utf-8")
        except UnicodeError:
            errors.append(f"{relative}: invalid UTF-8")
    for name in REQUIRED:
        if name not in docs:
            errors.append(f"Missing required document: {name}")
    if errors:
        return errors

    requirements = sections(docs["REQUIREMENTS.md"], REQ_ID, 3)
    todo = sections(docs["TODO.md"], TASK_ID, 2)
    done = sections(docs["DONE.md"], TASK_ID, 2)
    questions = sections(docs["DECISIONS.md"], OPEN_ID, 3)
    req_ids = {key for key, _ in requirements}
    task_ids = {key for key, _ in todo + done}
    open_ids = {key for key, _ in questions}
    if not requirements:
        errors.append("REQUIREMENTS.md: no requirement definitions")

    for name, entries in [("requirements", requirements), ("tasks across TODO/DONE", todo + done), ("open questions", questions)]:
        for key, count in Counter(key for key, _ in entries).items():
            if count > 1:
                errors.append(f"Duplicate {key} in {name}")

    for key, body in requirements:
        if not re.search(r"^\*\*[BV] · .+?\*\*", body, re.M):
            errors.append(f"{key}: missing confirmed/proposed status and delivery scope")
        if not field(body, "Abnahme"):
            errors.append(f"{key}: missing acceptance criteria")

    for key, body in todo:
        for name in TASK_FIELDS:
            if not field(body, name):
                errors.append(f"{key}: missing task field {name}")
        status = field(body, "Status")
        if status not in STATUSES:
            errors.append(f"{key}: invalid status {status!r}")
        owner = field(body, "Verantwortlich")
        if status in {"IN ARBEIT", "BEREIT ZUR PRÜFUNG"} and owner.lower() in {"", "unbesetzt", "offen"}:
            errors.append(f"{key}: active/review task needs a session owner")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", field(body, "Aktualisiert")):
            errors.append(f"{key}: update date must use YYYY-MM-DD")
        if not re.search(REQ_ID, field(body, "Bezug")):
            errors.append(f"{key}: missing requirement traceability")

    for key, body in done:
        for name in DONE_FIELDS:
            if not field(body, name):
                errors.append(f"{key}: missing completion field {name}")
        acceptance = field(body, "Abnahme").lower()
        if re.match(r"(?:offen|pending|ausstehend|tbd)\b", acceptance):
            errors.append(f"{key}: pending acceptance belongs in TODO.md")

    for name, text in docs.items():
        for pattern, defined in [(REQ_ID, req_ids), (TASK_ID, task_ids), (OPEN_ID, open_ids)]:
            for reference in sorted(set(re.findall(pattern, text)) - defined):
                errors.append(f"{name}: undefined reference {reference}")
        for target in re.findall(r"\]\(([^)]+)\)", text):
            target = target.strip().strip("<>")
            parsed = urlsplit(target)
            if parsed.scheme or target.startswith("#"):
                continue
            local = (root / name).parent / unquote(parsed.path)
            if not local.exists():
                errors.append(f"{name}: broken local link {target}")
        if re.search(r"/Users/[^\s/]+/|[A-Z]:\\Users\\", text):
            errors.append(f"{name}: private absolute machine path in public document")

    if "@AGENTS.md" not in docs["CLAUDE.md"].splitlines():
        errors.append("CLAUDE.md must import @AGENTS.md on its own line")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = validate(args.root)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("Repository contract: OK (IDs, links, task metadata and completion evidence)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
