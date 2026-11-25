"""Names of bases and points of the backup service (TASK-137, finding N4 of the review of TASK-136; TASK-139, finding F6
of the review of TASK-137)."""

import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "deploy" / "backup" / "backup.sh"


def run(count, files=None, clock=None, unreadable=()):
    """Runs unique_name of backup.sh count times on a fresh state directory with the given state files.

    clock: a fixed second for date -u +%s (a frozen or reset clock). Returns (status, names, error).
    """
    with tempfile.TemporaryDirectory() as state:
        for name, content in (files or {}).items():
            pathlib.Path(state, name).write_text(content)
        for name in unreadable:
            pathlib.Path(state, name).mkdir()
        functions = "".join(subprocess.run(["sed", "-n", f"/^{name}() {{/,/^}}/p", str(SCRIPT)], check=True,
                                           capture_output=True, text=True).stdout for name in ("name_epoch", "unique_name"))
        frozen = "" if clock is None else (
            f'date() {{ if [ "$*" = "-u +%s" ]; then echo {clock}; else command date "$@"; fi; }}\n')
        program = (f'last_name_file="$STATE/last-name-epoch"\nbase_name_file="$STATE/base-name"\n'
                   f'latest_file="$STATE/latest-point"\n{frozen}{functions}\n'
                   f'for i in $(seq {count}); do unique_name || exit 1; echo; done\n')
        result = subprocess.run(["bash", "-c", program], capture_output=True, text=True, env={**os.environ, "STATE": state})
        return result.returncode, result.stdout.split(), result.stderr


def names(count, **arguments):
    status, result, error = run(count, **arguments)
    assert status == 0, error
    return result


class UniqueNameTest(unittest.TestCase):
    def test_runs_in_the_same_second_get_different_rising_names(self):
        result = names(5)
        self.assertEqual(len(set(result)), 5, result)
        self.assertEqual(result, sorted(result))
        for name in result:
            self.assertRegex(name, r"^[0-9]{8}T[0-9]{6}Z$")

    def test_a_frozen_clock_still_gives_rising_names(self):
        self.assertEqual(names(3, clock=1767225600),  # 2026-01-01T00:00:00Z
                         ["20260101T000000Z", "20260101T000001Z", "20260101T000002Z"])

    def test_a_clock_that_went_back_never_repeats_a_name(self):
        # The last name of the volume lies in the future (the clock went back): the next names follow it.
        result = names(2, files={"last-name-epoch": "4102444800"})  # 2100-01-01T00:00:00Z
        self.assertEqual(result, ["21000101T000001Z", "21000101T000002Z"])

    def test_a_volume_of_task_136_without_counter_takes_over_its_base_and_point(self):
        # F6: the state of the template of TASK-136 has base-name and latest-point but no counter; a clock at or before
        # them never gives one of their names again.
        files = {"base-name": "20261120T101500Z", "latest-point": "20261124T080000Z 2026-11-24 08:00:00+00\n"}
        for clock in (1795507200, 1795507199, 1763631300):  # exactly the point, a second before, a year back
            with self.subTest(clock=clock):
                self.assertEqual(names(2, files=files, clock=clock), ["20261124T080001Z", "20261124T080002Z"])
        # Only a base, without a point yet.
        self.assertEqual(names(1, files={"base-name": "20261120T101500Z"}, clock=1795169700), ["20261120T101501Z"])
        # A counter behind the names of the state does not win either.
        self.assertEqual(names(1, files={**files, "last-name-epoch": "1700000000"}, clock=1700000000),
                         ["20261124T080001Z"])

    def test_an_unreadable_or_damaged_state_ends_without_a_name(self):
        for files, unreadable, reason in (
                ({"last-name-epoch": "kaputt"}, (), "holds no second"),
                ({"last-name-epoch": ""}, (), "holds no second"),
                ({"base-name": "kaputt"}, (), "base-name holds no name"),
                ({"latest-point": ""}, (), "latest-point"),
                ({}, ("last-name-epoch",), "cannot be read"),
                ({}, ("base-name",), "base-name")):
            with self.subTest(files=files, unreadable=unreadable):
                status, result, error = run(1, files=files, unreadable=unreadable, clock=1767225600)
                self.assertEqual(1, status, result)
                self.assertEqual([], result)
                self.assertIn(reason, error)


if __name__ == "__main__":
    unittest.main()
