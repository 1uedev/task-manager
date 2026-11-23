"""Names of bases and points of the backup service (TASK-137, finding N4 of the review of TASK-136)."""

import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "deploy" / "backup" / "backup.sh"


def names(count, last=None):
    """Runs unique_name of backup.sh count times on a fresh state directory, optionally after a given last second."""
    with tempfile.TemporaryDirectory() as state:
        if last is not None:
            pathlib.Path(state, "last-name-epoch").write_text(str(last))
        function = subprocess.run(
            ["sed", "-n", "/^unique_name() {/,/^}/p", str(SCRIPT)], check=True, capture_output=True, text=True
        ).stdout
        program = f'last_name_file="$STATE/last-name-epoch"\n{function}\nfor i in $(seq {count}); do unique_name; echo; done\n'
        result = subprocess.run(
            ["bash", "-c", program], check=True, capture_output=True, text=True, env={**os.environ, "STATE": state}
        )
        return result.stdout.split()


class UniqueNameTest(unittest.TestCase):
    def test_runs_in_the_same_second_get_different_rising_names(self):
        result = names(5)
        self.assertEqual(len(set(result)), 5, result)
        self.assertEqual(result, sorted(result))
        for name in result:
            self.assertRegex(name, r"^[0-9]{8}T[0-9]{6}Z$")

    def test_a_clock_that_went_back_never_repeats_a_name(self):
        # The last name of the volume lies in the future (the clock went back): the next names follow it.
        result = names(2, last=4102444800)  # 2100-01-01T00:00:00Z
        self.assertEqual(result, ["21000101T000001Z", "21000101T000002Z"])

    def test_a_damaged_state_file_starts_from_the_clock(self):
        result = names(1, last="kaputt")
        self.assertRegex(result[0], r"^20[0-9]{6}T[0-9]{6}Z$")


if __name__ == "__main__":
    unittest.main()
