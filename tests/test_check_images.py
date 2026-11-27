"""The check of the image references of the operations template, deploy/check-images.sh (TASK-136, TASK-137; TASK-140,
hint H2 of the review of TASK-139: values of several lines are refused like at the start of both images)."""

import os
import pathlib
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "deploy" / "check-images.sh"
GOOD = "sha256:" + "a" * 64


def check(**values):
    environment = {key: value for key, value in os.environ.items() if not key.startswith("TASK_MANAGER_")}
    environment.update(values)
    return subprocess.run(["sh", str(SCRIPT)], env=environment, capture_output=True, text=True)


class CheckImagesTest(unittest.TestCase):
    def test_two_digests_are_accepted(self):
        result = check(TASK_MANAGER_IMAGE_DIGEST=GOOD, TASK_MANAGER_BACKUP_IMAGE_DIGEST="sha256:" + "b" * 64)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("both images are referenced by digest", result.stdout)

    def test_a_value_of_several_lines_is_refused_also_when_one_line_is_a_digest(self):
        for value in (GOOD + "\nlatest", "latest\n" + GOOD, GOOD + "\n", "\n" + GOOD, GOOD + "\n" + GOOD):
            for name, other in (("TASK_MANAGER_IMAGE_DIGEST", "TASK_MANAGER_BACKUP_IMAGE_DIGEST"),
                                ("TASK_MANAGER_BACKUP_IMAGE_DIGEST", "TASK_MANAGER_IMAGE_DIGEST")):
                with self.subTest(name=name, value=value):
                    result = check(**{name: value, other: GOOD})
                    self.assertEqual(1, result.returncode, result.stdout)
                    self.assertIn(f"{name} holds more than one line", result.stderr)

    def test_a_tag_or_an_empty_value_is_refused(self):
        for value in ("", "latest", "v1.0.0", "sha256:" + "A" * 64, "sha256:" + "a" * 63, " " + GOOD):
            with self.subTest(value=value):
                result = check(TASK_MANAGER_IMAGE_DIGEST=value, TASK_MANAGER_BACKUP_IMAGE_DIGEST=GOOD)
                self.assertEqual(1, result.returncode)
                self.assertIn("TASK_MANAGER_IMAGE_DIGEST must be the digest", result.stderr)

    def test_the_settings_before_task_137_are_refused(self):
        result = check(TASK_MANAGER_IMAGE="ghcr.io/1uedev/task-manager:latest", TASK_MANAGER_IMAGE_DIGEST=GOOD,
                       TASK_MANAGER_BACKUP_IMAGE_DIGEST=GOOD)
        self.assertEqual(1, result.returncode)
        self.assertIn("no longer read", result.stderr)


if __name__ == "__main__":
    unittest.main()
