"""The port retry of tools/update/update-check.sh (TASK-139, hint H2 of the review of TASK-137; TASK-140, hint H1 of the
review of TASK-139), with a stand in for Docker Compose."""

import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "update" / "update-check.sh"


def run(fixed, failures):
    """Runs up_ports with the given UPDATE_CHECK_*_PORT values; the first `failures` calls of up report a taken port.

    Returns (status, the bind lines of the env file afterwards, output)."""
    function = subprocess.run(["sed", "-n", "/^up_ports() {/,/^}/p", str(SCRIPT)], check=True, capture_output=True,
                              text=True).stdout
    with tempfile.TemporaryDirectory() as work:
        env_file = pathlib.Path(work, "env")
        env_file.write_text(f"TASK_MANAGER_HTTP_BIND=127.0.0.1:{fixed.get('HTTP', 40080)}\n"
                            f"TASK_MANAGER_HTTPS_BIND=127.0.0.1:{fixed.get('HTTPS', 40443)}\n")
        program = (f'work={work}\nresults={work}\nhttps_port={fixed.get("HTTPS", 40443)}\n'
                   f'http_port={fixed.get("HTTP", 40080)}\nnext=50000\n'
                   'free_port() { next=$((next + 1)); echo $next; }\n'
                   f'dc() {{ calls=$((${{calls:-0}} + 1)); if [ "$calls" -le {failures} ]; then '
                   'echo "Bind for 127.0.0.1:1: failed: port is already allocated"; return 1; fi; }\n'
                   f'{function}\nup_ports\n')
        environment = {**os.environ}
        for name in ("HTTP", "HTTPS"):
            environment.pop(f"UPDATE_CHECK_{name}_PORT", None)
            if name in fixed:
                environment[f"UPDATE_CHECK_{name}_PORT"] = str(fixed[name])
        result = subprocess.run(["bash", "-c", program], env=environment, capture_output=True, text=True)
        return result.returncode, env_file.read_text().split(), result.stdout + result.stderr


class UpPortsTest(unittest.TestCase):
    def test_both_chosen_ports_are_taken_anew(self):
        status, binds, output = run({}, 1)
        self.assertEqual(0, status, output)
        self.assertNotIn("TASK_MANAGER_HTTP_BIND=127.0.0.1:40080", binds)
        self.assertNotIn("TASK_MANAGER_HTTPS_BIND=127.0.0.1:40443", binds)

    def test_a_given_port_is_kept_and_the_chosen_one_is_taken_anew(self):
        status, binds, output = run({"HTTPS": 8443}, 2)
        self.assertEqual(0, status, output)
        self.assertIn("TASK_MANAGER_HTTPS_BIND=127.0.0.1:8443", binds)
        self.assertNotIn("TASK_MANAGER_HTTP_BIND=127.0.0.1:40080", binds)
        status, binds, output = run({"HTTP": 8080}, 1)
        self.assertEqual(0, status, output)
        self.assertIn("TASK_MANAGER_HTTP_BIND=127.0.0.1:8080", binds)
        self.assertNotIn("TASK_MANAGER_HTTPS_BIND=127.0.0.1:40443", binds)

    def test_two_given_ports_end_the_run_without_a_retry(self):
        status, binds, output = run({"HTTP": 8080, "HTTPS": 8443}, 1)
        self.assertEqual(1, status)
        self.assertEqual(["TASK_MANAGER_HTTP_BIND=127.0.0.1:8080", "TASK_MANAGER_HTTPS_BIND=127.0.0.1:8443"], binds)
        self.assertNotIn("attempt", output)

    def test_after_three_taken_attempts_the_run_ends(self):
        status, _, output = run({"HTTPS": 8443}, 3)
        self.assertEqual(1, status)
        self.assertEqual(3, output.count("attempt"))


if __name__ == "__main__":
    unittest.main()
