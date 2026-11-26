"""The archive and delete blocks of the way back in docs/INSTALL.md (TASK-140, finding N5 of the review of TASK-139),
run with a stand in for docker; tools/update/update-check.sh runs the same blocks against real volumes."""

import os
import pathlib
import shutil
import subprocess
import tempfile
import textwrap
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
GUIDE = (ROOT / "docs" / "INSTALL.md").read_text(encoding="utf-8")

# Volumes of the installation "task-manager"; "docker run ... tar -czf /to/NAME" writes a real archive into the mounted
# directory and fails like tar when that path cannot be written.
DOCKER = r'''#!/usr/bin/env bash
case "$1 $2" in
  "volume inspect")
    shift 2
    format=
    if [ "$1" = -f ]; then format=$2; shift 2; fi
    case "$1" in task-manager_db-data|task-manager_files-data) ;; *) echo "no such volume: $1" >&2; exit 1 ;; esac
    case "$format" in
      *com.docker.compose.project*) echo task-manager ;;
      *com.docker.compose.volume*) echo "${1#task-manager_}" ;;
    esac
    echo "$*" >> "$LOG.inspect"; exit 0 ;;
  "volume rm") shift 2; echo "$*" >> "$LOG.rm"; exit 0 ;;
  "compose config") echo '{"name": "task-manager"}'; exit 0 ;;
  "run --rm")
    to=; target=
    while [ $# -gt 0 ]; do
      case "$1" in
        -v) case "$2" in *:/to) to=${2%:/to} ;; esac; shift 2 ;;
        -czf) target=$2; shift 2 ;;
        *) shift ;;
      esac
    done
    echo "$target" >> "$LOG.run"
    file="$to/${target#/to/}"
    [ ! -d "$file" ] || { echo "tar: Cannot open: Is a directory" >&2; exit 2; }
    tar -czf "$file" -C "$EMPTY" . ;;
  *) echo "unexpected docker $*" >&2; exit 9 ;;
esac
'''


def block(heading):
    start = GUIDE.index(heading)
    text = GUIDE[GUIDE.index("```sh\n", start) + 6:]
    return textwrap.dedent(text[:text.index("```")])


class ReturnBlocksTest(unittest.TestCase):
    def setUp(self):
        self.directory = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.directory)
        self.bin = self.directory / "bin"
        self.bin.mkdir()
        (self.bin / "docker").write_text(DOCKER)
        (self.bin / "docker").chmod(0o755)
        self.here = self.directory / "here"
        self.here.mkdir()
        (self.directory / "empty").mkdir()
        (self.directory / "empty" / "PG_VERSION").write_text("18\n")
        self.log = self.directory / "log"

    def run_block(self, script):
        env = {**os.environ, "PATH": f"{self.bin}:{os.environ['PATH']}", "LOG": str(self.log),
               "EMPTY": str(self.directory / "empty")}
        return subprocess.run(["bash", "-c", script], cwd=self.here, env=env, capture_output=True, text=True)

    def lines(self, suffix):
        path = pathlib.Path(f"{self.log}.{suffix}")
        return path.read_text().splitlines() if path.exists() else []

    def test_the_check_stops_nothing(self):
        check = block("1. **Kontrollieren**")
        self.assertNotIn("down", check)
        self.assertNotIn("stop", check)
        self.assertNotIn("rm", check.split())

    def test_both_archives_are_made_and_confirmed(self):
        result = self.run_block(block("2. **Sichern"))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("beide Archive vollständig", result.stdout)
        for volume in ("db-data", "files-data"):
            self.assertTrue((self.here / f"gescheitert-{volume}.tar.gz").is_file())
            self.assertFalse((self.here / f"gescheitert-{volume}.tar.gz.neu").exists())
        deleted = self.run_block(block("3. **Löschen**"))
        self.assertEqual(0, deleted.returncode, deleted.stderr)
        self.assertEqual(["task-manager_db-data task-manager_files-data"], self.lines("rm"))

    def test_a_failed_first_archive_stops_the_block_and_the_delete(self):
        (self.here / "gescheitert-db-data.tar.gz.neu").mkdir()
        result = self.run_block(block("2. **Sichern"))
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("beide Archive vollständig", result.stdout)
        self.assertEqual(["/to/gescheitert-db-data.tar.gz.neu"], self.lines("run"))
        self.assertFalse((self.here / "gescheitert-files-data.tar.gz").exists())
        deleted = self.run_block(block("3. **Löschen**"))
        self.assertNotEqual(0, deleted.returncode)
        self.assertIn("Archiv gescheitert-db-data.tar.gz fehlt", deleted.stderr)
        self.assertEqual([], self.lines("rm"))

    def test_a_damaged_second_archive_refuses_the_delete(self):
        self.assertEqual(0, self.run_block(block("2. **Sichern")).returncode)
        (self.here / "gescheitert-files-data.tar.gz").write_bytes(b"\x1f\x8b kaputt")
        deleted = self.run_block(block("3. **Löschen**"))
        self.assertNotEqual(0, deleted.returncode)
        self.assertIn("Archiv gescheitert-files-data.tar.gz fehlt oder ist unvollständig", deleted.stderr)
        self.assertEqual([], self.lines("rm"))

    def test_without_archives_only_the_explicit_choice_deletes(self):
        self.assertNotEqual(0, self.run_block(block("3. **Löschen**")).returncode)
        skipped = block("3. **Löschen**").replace("  archive=ja ", "  archive=nein ")
        self.assertNotEqual(skipped, block("3. **Löschen**"))
        result = self.run_block(skipped)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, len(self.lines("rm")))

    def test_another_installation_is_refused_before_any_archive_check(self):
        other = block("3. **Löschen**").replace("  expected=task-manager ", "  expected=andere ")
        result = self.run_block(other)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("abgebrochen: Projekt task-manager statt andere", result.stderr)
        self.assertEqual([], self.lines("rm"))


if __name__ == "__main__":
    unittest.main()
