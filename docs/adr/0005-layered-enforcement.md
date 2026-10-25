# Enforce rules in layers, not by instructions alone

In a test run, Codex said "your explicit request overrides" the rule protecting the assignment `README.md` and set out to edit it. Instructions alone aren't enough, so each important rule is enforced in four layers:

1. **`AGENTS.md` Hard rules**: the agent knows the rule and why, and usually declines.
2. **Per-agent rules**, which stop the action before it happens: Claude Code permissions, Codex rules and its `kata` sandbox, Cursor and Copilot hook scripts, and Gemini policies. They also block switching off layer 3 (`--no-verify`, `core.hooksPath`).
3. **`.githooks/pre-commit`**, for every agent and human: it rejects staged changes to `README.md`, `TimeDepositCalculatorTest.kt` and `ArchitectureTest.kt` (deletions and renames included), and runs `mvn verify` when `kotlin/` changes.
4. **Tests in `verify`**: the characterization and differential tests (ADR 0001), ArchUnit (ADR 0006) and the Kover gate.

## Consequences
- The hook only runs after `git config core.hooksPath .githooks`, and a human can bypass it with `--no-verify`. The `Verify` workflow re-runs `clean verify` on every push to `main` and every pull request that touches `kotlin/`. The `Harness fixtures` workflow checks the hook and hook-script decisions. Neither proves that protected files were left unchanged.
- The hook blocks the repo owner too. Changing a protected file is therefore a deliberate owner commit that bypasses the hook, after which CI re-runs the tests.
- The known gaps of each layer are listed in `docs/ai-harness.md`.
