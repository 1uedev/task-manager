#!/usr/bin/env python3
"""Render the task overview from TODO.md; never grant ownership or authorization."""

import argparse
from collections import Counter
from dataclasses import dataclass
from datetime import date
from html import escape
from pathlib import Path
import re

from validate_repository import STATUSES, TASK_HEADINGS, UNASSIGNED, field, task_heading_errors


START = "<!-- TASK-BOARD:START -->"
END = "<!-- TASK-BOARD:END -->"
GROUPS = (
    ("OFFEN", "Frei und bearbeitbar", "Zur Reservierung verfügbar; Auftrag und Abhängigkeiten im Detail prüfen."),
    ("IN ARBEIT", "Durch eine KI reserviert", "Für andere Sitzungen gesperrt; eingetragene Sitzung bearbeitet die Aufgabe."),
    ("BLOCKIERT", "Blockiert", "Nicht beginnen; Freigabe oder Voraussetzung fehlt. Ein vorhandener Besitzer bleibt zuständig."),
    ("BEREIT ZUR PRÜFUNG", "Zur Prüfung bereit", "Ergebnis liegt vor; keine freie Entwicklungsaufgabe und noch nicht abgenommen."),
)


@dataclass(frozen=True)
class Task:
    id: str
    title: str
    status: str
    owner: str
    updated: str

    @property
    def anchor(self):
        # GitHub heading anchors preserve Unicode letters and remove punctuation.
        return re.sub(r"[^\w\- ]", "", f"{self.id} {self.title}".lower()).replace(" ", "-")


def parse_tasks(text):
    errors = task_heading_errors(text)
    if errors:
        raise ValueError("; ".join(errors))
    matches = list(TASK_HEADINGS.finditer(text))
    tasks = []
    for i, match in enumerate(matches):
        body = text[match.end():matches[i + 1].start() if i + 1 < len(matches) else len(text)]
        task = Task(match[1], match[2].strip(), field(body, "Status"),
                    field(body, "Verantwortlich"), field(body, "Aktualisiert"))
        if task.status not in STATUSES:
            raise ValueError(f"{task.id}: unbekannter Status {task.status!r}.")
        if not task.owner:
            raise ValueError(f"{task.id}: Verantwortlich fehlt.")
        if task.status == "OFFEN" and task.owner.casefold() not in UNASSIGNED:
            raise ValueError(f"{task.id}: OFFEN darf keinen Besitzer haben; Status oder Übergabe klären.")
        if task.status in {"IN ARBEIT", "BEREIT ZUR PRÜFUNG"} and task.owner.casefold() in UNASSIGNED:
            raise ValueError(f"{task.id}: dieser Status benötigt eine zuständige Sitzung.")
        try:
            valid_date = date.fromisoformat(task.updated)
        except ValueError:
            raise ValueError(f"{task.id}: ungültiges Aktualisierungsdatum.") from None
        if valid_date.isoformat() != task.updated:
            raise ValueError(f"{task.id}: Datum muss YYYY-MM-DD verwenden.")
        tasks.append(task)
    duplicates = [key for key, count in Counter(t.id for t in tasks).items() if count > 1]
    if duplicates:
        raise ValueError(f"Doppelte Aufgaben-ID: {', '.join(duplicates)}.")
    return sorted(tasks, key=lambda task: task.id)


def cell(value):
    return re.sub(r"([\\`*_|\[\]])", r"\\\1", escape(value, quote=False))


def render(tasks):
    lines = [
        "## Aufgabenübersicht", "",
        "Automatisch aus den Aufgabeneinträgen weiter unten erzeugt. Maßgeblich ist TODO.md auf main; "
        "eine lokale Kopie kann veraltet sein. Aktualisieren: `python3 scripts/task_board.py --write`.", "",
        "| Kategorie | Anzahl | Bedeutung |", "|---|---:|---|",
    ]
    for status, label, meaning in GROUPS:
        lines.append(f"| {label} ({status}) | {sum(t.status == status for t in tasks)} | {meaning} |")
    lines += ["", "Eine Reservierung gilt nach dem Merge des Claim-PRs. Sie ist eine koordinierte "
              "Zuständigkeit, keine technische Dateisperre und kein Nachweis, dass eine KI gerade läuft. "
              "Sie verfällt nicht automatisch. [Verfahren](docs/AI_WORKFLOW.md)."]
    for status, label, _ in GROUPS:
        group = [task for task in tasks if task.status == status]
        lines += ["", f"### {label} – {status}", ""]
        if not group:
            lines.append("Derzeit keine Aufgaben in dieser Kategorie.")
            continue
        lines += ["| Aufgabe | Titel | Zuständige Sitzung | Aktualisiert |", "|---|---|---|---|"]
        for task in group:
            owner = "—" if task.owner.casefold() in UNASSIGNED else cell(task.owner)
            lines.append(f"| [{task.id}](#{task.anchor}) | {cell(task.title)} | {owner} | {task.updated} |")
    lines += ["", "Abhängigkeiten, Arbeitsstand und nächster Schritt stehen im verlinkten Aufgabeneintrag. "
              "Abgenommene Ergebnisse stehen in [DONE.md](DONE.md).", ""]
    return "\n".join(lines)


def updated_text(text):
    tasks = parse_tasks(text)
    board = START + "\n" + render(tasks) + END
    starts, ends = text.count(START), text.count(END)
    first_heading = TASK_HEADINGS.search(text)
    first_task = first_heading.start() if first_heading else len(text)
    if starts == ends == 0:
        prefix = text[:first_task]
        separator = "" if not prefix or prefix.endswith("\n\n") else "\n" if prefix.endswith("\n") else "\n\n"
        return prefix + separator + board + "\n\n" + text[first_task:]
    if starts != 1 or ends != 1:
        raise ValueError("Übersicht benötigt genau ein START-/END-Markerpaar.")
    start, end = text.index(START), text.index(END) + len(END)
    if start >= end - len(END) or end > first_task:
        raise ValueError("Übersichtsmarker müssen geordnet vor dem ersten Aufgabeneintrag stehen.")
    return text[:start] + board + text[end:]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true", help="Nur den erzeugten Block in TODO.md aktualisieren")
    mode.add_argument("--check", action="store_true", help="Fehlschlagen, wenn die Übersicht fehlt oder veraltet ist")
    args = parser.parse_args()
    path = args.root / "TODO.md"
    try:
        text = path.read_text(encoding="utf-8")
        updated = updated_text(text)
        if args.check:
            if updated != text:
                print("ERROR: Aufgabenübersicht fehlt oder ist veraltet. python3 scripts/task_board.py --write ausführen.")
                return 1
            print("Aufgabenübersicht: aktuell")
        elif args.write:
            if updated != text:
                path.write_text(updated, encoding="utf-8")
            print("Aufgabenübersicht: aktualisiert")
        else:
            print(render(parse_tasks(text)), end="")
    except (OSError, UnicodeError, ValueError) as error:
        print(f"ERROR: {error}")
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
