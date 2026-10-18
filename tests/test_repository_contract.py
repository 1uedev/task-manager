"""Regression checks for coordination errors the validator must catch."""

from pathlib import Path
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from validate_repository import REQUIRED, validate  # noqa: E402


class RepositoryContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        # Copy source fixtures, including non-Markdown link targets. Generated
        # dependencies and local/private runtime state are not test inputs.
        shutil.copytree(
            ROOT, self.root, dirs_exist_ok=True,
            ignore=shutil.ignore_patterns(
                ".git", ".venv", "venv", "node_modules", "__pycache__",
                ".worktrees", "target", "dist", "build", "coverage",
                ".codex", ".aws", ".env", ".env.*", "secrets",
                "customer-data", "backups", "*.log",
            ),
        )

    def tearDown(self):
        self.temp.cleanup()

    def append(self, name, text):
        path = self.root / name
        path.write_text(path.read_text() + text)

    def test_current_contract_passes(self):
        self.assertEqual(validate(self.root), [])

    def test_unknown_requirement_is_rejected(self):
        self.append("TODO.md", "\nRelated: REQ-MISSING-999\n")
        self.assertTrue(any("undefined reference REQ-MISSING-999" in e for e in validate(self.root)))

    def test_duplicate_task_cannot_exist_in_todo_and_done(self):
        self.append("DONE.md", "\n## TASK-001 Duplicate\n")
        self.assertTrue(any("Duplicate TASK-001" in e for e in validate(self.root)))

    def test_duplicate_requirement_is_rejected(self):
        self.append("REQUIREMENTS.md", "\n### REQ-PROD-001 Duplicate\n")
        self.assertTrue(any("Duplicate REQ-PROD-001" in e for e in validate(self.root)))

    def test_active_task_requires_owner(self):
        path = self.root / "TODO.md"
        baseline = path.read_text()
        # Do not depend on an unclaimed task remaining in the live ledger.
        for status in ("IN ARBEIT", "BEREIT ZUR PRÜFUNG"):
            with self.subTest(status=status):
                fixture = f"""
## TASK-999 Synthetic owner check
**Status:** {status} · **Verantwortlich:** unbesetzt · **Aktualisiert:** 2026-10-16
**Bezug:** REQ-DEV-002. **Abhängigkeiten:** keine. **Arbeitsstand:** synthetic fixture.
**Umfang:** Check task ownership.
**Abnahmekriterien:** Active tasks require an owner.
**Nächster Schritt:** Assign a session owner.
"""
                path.write_text(baseline + fixture)
                self.assertEqual(
                    validate(self.root),
                    ["TASK-999: active/review task needs a session owner"],
                )
                path.write_text(baseline + fixture.replace(
                    "**Verantwortlich:** unbesetzt",
                    "**Verantwortlich:** synthetic-session",
                ))
                self.assertEqual(validate(self.root), [])

    def test_completion_requires_evidence_fields(self):
        self.append("DONE.md", "\n## TASK-999 Premature completion\n**Ergebnis:** drafted\n")
        self.assertTrue(any("missing completion field Abnahme" in e for e in validate(self.root)))

    def test_open_task_cannot_keep_a_reserved_owner(self):
        self.append("TODO.md", """
## TASK-999 Synthetic availability check
**Status:** OFFEN · **Verantwortlich:** another-session · **Aktualisiert:** 2026-10-18
**Bezug:** REQ-DEV-002. **Abhängigkeiten:** keine. **Arbeitsstand:** synthetic fixture.
**Umfang:** Check task availability.
**Abnahmekriterien:** An owned task must never be advertised as free.
**Nächster Schritt:** Coordinate the handoff.
""")
        self.assertEqual(validate(self.root), ["TASK-999: open task must be unassigned"])

    def test_task_heading_without_title_is_rejected_in_both_ledgers(self):
        for name in ("TODO.md", "DONE.md"):
            path = self.root / name
            baseline = path.read_text()
            for heading in ("## TASK-999", " ## TASK-999 Reserved", "  ## TASK-999 Reserved", "   ## TASK-999 Reserved"):
                with self.subTest(name=name, heading=heading):
                    path.write_text(baseline + "\n" + heading + "\n")
                    self.assertTrue(any(f"{name}: invalid task heading" in error for error in validate(self.root)))
            path.write_text(baseline)

    def test_pending_acceptance_must_stay_in_todo(self):
        self.append("DONE.md", "\n## TASK-999 Premature completion\n**Abnahme:** offen\n")
        self.assertTrue(any("pending acceptance belongs in TODO" in e for e in validate(self.root)))

    def test_broken_local_link_is_rejected(self):
        self.append("README.md", "\n[Missing](docs/missing.md)\n")
        self.assertTrue(any("broken local link" in e for e in validate(self.root)))

    def test_local_non_markdown_artifact_link_is_checked(self):
        artifact = self.root / "synthetic-contract.yaml"
        artifact.write_text("openapi: 3.1.1\n")
        self.append("README.md", "\n[Contract](synthetic-contract.yaml)\n")
        self.assertEqual(validate(self.root), [])
        artifact.unlink()
        self.assertTrue(any("broken local link synthetic-contract.yaml" in e for e in validate(self.root)))

    def test_claude_import_is_required(self):
        (self.root / "CLAUDE.md").write_text("Read the rules, but no explicit import.\n")
        self.assertTrue(any("must import @AGENTS.md" in e for e in validate(self.root)))

    def test_no_private_machine_path_in_public_docs(self):
        self.append("README.md", "\n/Users/example/private-project\n")
        self.assertTrue(any("private absolute machine path" in e for e in validate(self.root)))

    def test_missing_required_document_fails_cleanly(self):
        (self.root / REQUIRED[0]).unlink()
        self.assertTrue(any("Missing required document" in e for e in validate(self.root)))


if __name__ == "__main__":
    unittest.main()
