#!/usr/bin/env python3
"""Exercise hook interfaces without executing payload commands or changing this checkout."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
PROTECTED = (
    "README.md",
    "kotlin/src/test/kotlin/org/ikigaidigital/TimeDepositCalculatorTest.kt",
    "kotlin/src/test/kotlin/org/ikigaidigital/ArchitectureTest.kt",
)


class GuardrailTests(unittest.TestCase):
    def test_should_decide_scripted_tool_calls_without_executing_commands(self):
        self.assertIsNotNone(shutil.which("jq"), "Install jq to run hook fixtures")
        fixtures = json.loads(Path(__file__).with_name("fixtures.json").read_text())
        cursor_shell = ".cursor/hooks/guard-shell.sh"
        cursor_edit = ".cursor/hooks/guard-readme.sh"
        copilot = ".github/hooks/guardrails.sh"
        for fixture in fixtures["shell"]:
            command = fixture["command"]
            self.check_tool_hook(cursor_shell, {"command": command}, fixture["decision"])
            self.check_tool_hook(copilot, {"tool_name": "run_in_terminal", "tool_input": {"command": command}},
                                 fixture["decision"])
        for fixture in fixtures["edits"]:
            path = fixture["path"].replace("{root}", str(ROOT))
            payload = {"tool_name": "Write", "tool_input": {"file_path": path}}
            for hook in (cursor_edit, copilot):
                self.check_tool_hook(hook, payload, fixture["decision"])
        for fixture in fixtures["copilot"]:
            self.check_tool_hook(copilot, fixture["payload"], fixture["decision"])

    def check_tool_hook(self, hook, payload, expected):
        with self.subTest(hook=hook, payload=payload):
            result = subprocess.run(["sh", str(ROOT / hook)], cwd=ROOT,
                                    input=json.dumps(payload), capture_output=True, text=True)
            is_copilot = hook.startswith(".github/")
            self.assertEqual(result.returncode, 2 if is_copilot and expected == "deny" else 0,
                             result.stderr)
            output = json.loads(result.stdout) if result.stdout.strip() else {}
            decision = output.get("permissionDecision" if is_copilot else "permission", "allow")
            self.assertEqual(decision, expected, result.stdout)

    def test_should_reject_staged_protected_changes_before_running_maven(self):
        for protected in PROTECTED:
            for change in ("modify", "delete", "rename"):
                with self.subTest(path=protected, change=change):
                    self.check_pre_commit(protected, change, rejected=True)

    def test_should_allow_other_changes_and_verify_kotlin(self):
        for path in ("docs/example.md", "kotlin/src/test/kotlin/org/ikigaidigital/NewTest.kt"):
            with self.subTest(path=path):
                self.check_pre_commit(path, "modify", rejected=False)

    def check_pre_commit(self, path, change, rejected):
        with tempfile.TemporaryDirectory(prefix="kata-hook-") as temporary:
            directory = Path(temporary)
            env = dict(os.environ)
            # Isolate the synthetic repository from caller Git settings and identity.
            for key in list(env):
                if key.startswith("GIT_"):
                    del env[key]
            env.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                       GIT_AUTHOR_NAME="Hook fixture", GIT_AUTHOR_EMAIL="fixture@example.invalid",
                       GIT_COMMITTER_NAME="Hook fixture", GIT_COMMITTER_EMAIL="fixture@example.invalid")

            def git(*args):
                return subprocess.run(["git", *args], cwd=directory, env=env,
                                      check=True, capture_output=True, text=True)

            git("init", "--quiet")
            target = directory / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text("synthetic baseline\n")
            git("add", ".")
            git("commit", "--quiet", "-m", "Synthetic fixture baseline")
            if change == "modify":
                target.write_text("synthetic change\n")
            elif change == "delete":
                target.unlink()
            else:
                target.rename(target.with_name("renamed.txt"))
            git("add", "--all")

            # Only the hook's Maven invocation is replaced; index inspection uses real Git.
            binary = directory / "bin"
            binary.mkdir()
            maven_log = directory / "maven-called"
            maven = binary / "mvn"
            maven.write_text('#!/bin/sh\nprintf "%s\\n" "$*" > "$MAVEN_FIXTURE_LOG"\n')
            maven.chmod(0o755)
            env.update(PATH=str(binary) + os.pathsep + env["PATH"], MAVEN_FIXTURE_LOG=str(maven_log))
            result = subprocess.run(["sh", str(ROOT / ".githooks/pre-commit")],
                                    cwd=directory, env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 1 if rejected else 0, result.stderr)
            if rejected:
                self.assertIn(path, result.stderr)
                self.assertFalse(maven_log.exists(), "Protected paths must fail before Maven")
            elif path.startswith("kotlin/"):
                self.assertEqual(maven_log.read_text().strip(), "-q -f kotlin/pom.xml verify")
            else:
                self.assertFalse(maven_log.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
