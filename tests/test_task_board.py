"""Coordination regressions: never advertise reserved work or accept a stale overview."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from task_board import END, START, parse_tasks, render, updated_text  # noqa: E402


def task(number, status="OFFEN", owner="unbesetzt", title="Beispiel"):
    return f"""## TASK-{number:03d} {title}

**Status:** {status} · **Verantwortlich:** {owner} · **Aktualisiert:** 2026-10-18

**Abhängigkeiten:** Im Detail prüfen.
**Nächster Schritt:** Historischen Text unverändert erhalten.

"""


class TaskBoardTests(unittest.TestCase):
    def test_available_reserved_blocked_and_review_work_stay_separate(self):
        text = (task(1) + task(2, "IN ARBEIT", "codex-session")
                + task(3, "BLOCKIERT") + task(4, "BLOCKIERT", "claude-session")
                + task(5, "BEREIT ZUR PRÜFUNG", "review-owner"))
        board = render(parse_tasks(text))
        free = board.split("### Frei und bearbeitbar – OFFEN\n")[1].split("### ")[0]
        self.assertIn("[TASK-001]", free)
        for number in range(2, 6):
            self.assertNotIn(f"[TASK-{number:03d}]", free)
        self.assertIn("| Blockiert (BLOCKIERT) | 2 |", board)
        self.assertIn("claude-session", board)
        for number in range(1, 6):
            self.assertEqual(board.count(f"[TASK-{number:03d}]"), 1)

    def test_inconsistent_ownership_is_rejected(self):
        for text in (task(1, "OFFEN", "someone"), task(1, "IN ARBEIT"),
                     task(1, "BEREIT ZUR PRÜFUNG"), task(1, owner="")):
            with self.subTest(text=text), self.assertRaises(ValueError):
                updated_text(text)

    def test_unknown_status_duplicates_and_invalid_dates_are_rejected(self):
        for text in (task(1, "FREI"), task(1) + task(1),
                     task(1).replace("2026-10-18", "2026-02-30")):
            with self.subTest(text=text), self.assertRaises(ValueError):
                updated_text(text)

    def test_update_preserves_details_and_is_idempotent(self):
        prefix = "# TODO\n\nUnveränderte Einleitung.\n\n"
        details = task(1) + task(2, "IN ARBEIT", "worker")
        result = updated_text(prefix + details)
        self.assertEqual(result.split(START)[0], prefix)
        self.assertEqual(result.split(END)[1], "\n\n" + details)
        self.assertEqual(updated_text(result), result)

    def test_empty_ledger_and_removal_of_last_task_are_valid(self):
        empty = "# TODO\n\nAlle angenommenen Aufgaben stehen in DONE.md.\n"
        result = updated_text(empty)
        self.assertTrue(result.startswith(empty))
        self.assertEqual(result.count("| 0 |"), 4)
        self.assertEqual(updated_text(result), result)
        previously_populated = updated_text(task(1)).split("## TASK-001")[0]
        cleared = updated_text(previously_populated)
        self.assertEqual(cleared.count("| 0 |"), 4)
        self.assertNotIn("[TASK-001]", cleared)

    def test_malformed_headings_are_rejected_even_inside_generated_markers(self):
        for heading in ("## TASK-034", "## TASK-34 Titel", "### TASK-034 Titel", "## TASK-034OhneTrennung"):
            malformed = task(34, "IN ARBEIT", "worker").replace("## TASK-034 Beispiel", heading)
            for text in (malformed + task(1), START + "\n" + malformed + END + "\n" + task(1)):
                with self.subTest(heading=heading, text=text), self.assertRaisesRegex(ValueError, "invalid task heading"):
                    updated_text(text)

    def test_cli_refuses_to_overwrite_task_hidden_inside_markers(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TODO.md"
            original = START + "\n" + task(34).replace("## TASK-034 Beispiel", "## TASK-034") + END + "\n" + task(1)
            path.write_text(original, encoding="utf-8")
            command = [sys.executable, str(ROOT / "scripts/task_board.py"), "--root", directory, "--write"]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn("invalid task heading", result.stdout)
            self.assertEqual(path.read_text(), original)

    def test_status_change_moves_task_without_rewriting_other_details(self):
        text = updated_text(task(1))
        changed = text.replace("**Status:** OFFEN · **Verantwortlich:** unbesetzt",
                               "**Status:** IN ARBEIT · **Verantwortlich:** worker")
        result = updated_text(changed)
        free = result.split("### Frei und bearbeitbar – OFFEN\n")[1].split("### ")[0]
        reserved = result.split("### Durch eine KI reserviert – IN ARBEIT\n")[1].split("### ")[0]
        self.assertNotIn("[TASK-001]", free)
        self.assertIn("[TASK-001]", reserved)
        self.assertEqual(result.split(END)[1], changed.split(END)[1])

    def test_missing_reversed_duplicate_or_misplaced_markers_cannot_remove_tasks(self):
        for text in (START + task(1), END + START + task(1),
                     START + START + END + task(1), task(1) + START + END):
            with self.subTest(text=text), self.assertRaises(ValueError):
                updated_text(text)

    def test_markdown_cells_are_escaped_and_unicode_heading_links_are_preserved(self):
        board = render(parse_tasks(task(1, title="Prüfung: API | <Entwurf> [A]")))
        self.assertIn("#task-001-prüfung-api--entwurf-a", board)
        self.assertIn(r"API \| &lt;Entwurf&gt; \[A\]", board)

    def test_cli_detects_stale_board_without_writing_and_then_repairs_it(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TODO.md"
            original = task(1)
            path.write_text(original, encoding="utf-8")
            command = [sys.executable, str(ROOT / "scripts/task_board.py"), "--root", directory]
            check = subprocess.run(command + ["--check"], capture_output=True, text=True)
            self.assertEqual(check.returncode, 1)
            self.assertEqual(path.read_text(), original)
            self.assertEqual(subprocess.run(command + ["--write"], capture_output=True).returncode, 0)
            self.assertEqual(subprocess.run(command + ["--check"], capture_output=True).returncode, 0)
            changed = path.read_text().replace("**Aktualisiert:** 2026-10-18", "**Aktualisiert:** 2026-10-19")
            path.write_text(changed)
            self.assertEqual(subprocess.run(command + ["--check"], capture_output=True).returncode, 1)
            self.assertEqual(path.read_text(), changed)


if __name__ == "__main__":
    unittest.main()
