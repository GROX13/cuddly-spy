# AI harness

How this repo was built with AI coding agents, and how to reproduce the setup. The rules the agents follow are in [`AGENTS.md`](../AGENTS.md); the design decisions are in [`docs/adr/`](adr/README.md).

## Tools

| Tool | Role | Configured in |
|---|---|---|
| Coding agent: Claude Code (main), Codex (reviews and fixes); Cursor, Gemini CLI and Copilot configured too | Writes, tests and refactors code under the `AGENTS.md` rules | see [Agents](#agents) |
| Context7 MCP | Current library docs (Kotlin, Spring Boot 4, Testcontainers, springdoc) before writing code | per-agent config |
| mattpocock skills | Workflow skills: `grilling`, `tdd`, `codebase-design`, `domain-modeling`, `code-review` | `.agents/skills/` (pinned); Claude Code plugin `mattpocock-skills` |
| `kotlin-lsp`, `claude-md-management` plugins | Kotlin diagnostics; keeping `AGENTS.md` accurate | `.claude/settings.json` |
| Git pre-commit hook | Agent-agnostic guardrails and `mvn verify` | `.githooks/pre-commit` |
| GitHub Actions | `Verify` runs `clean verify`; `Harness fixtures` checks the hook decisions | `.github/workflows/` |

## Agents

All agents share `AGENTS.md`, the vendored skills, the hook and Context7; only the config format differs (ADR 0004).

| Agent | Rules | Guardrails | Tested in a real session |
|---|---|---|---|
| Claude Code | `CLAUDE.md` → `AGENTS.md` | `.claude/settings.json` permissions | Yes, used for most of the work |
| Codex CLI | `AGENTS.md` | `.codex/rules/guardrails.rules`, `kata` sandbox profile in `.codex/config.toml` | Yes, used for reviews and fixes |
| Cursor | `AGENTS.md` | `.cursor/hooks.json` hook scripts | No: hook scripts checked with fixtures |
| Gemini CLI | `AGENTS.md` via `.gemini/settings.json` | `.gemini/policies/guardrails.toml` | No: rules evaluated with Gemini's policy engine |
| Copilot (VS Code) | `AGENTS.md` | `.github/hooks/guardrails.sh`, `.vscode/settings.json` | No: hook script checked with fixtures |

## Setup

1. Install JDK 17 (pinned in `.java-version`), Maven 3.9+, Docker and at least one agent. The Cursor and Copilot hooks also need `jq`.
2. Enable the hook once per clone: `git config core.hooksPath .githooks`.
3. Optional: `cp .envrc.example .envrc && direnv allow`. A `CONTEXT7_API_KEY` only raises rate limits.
4. Start the agent in the repo root and trust the folder (Gemini: `/permissions` → trust; Cursor: also approve the MCP server and check that both hooks are listed).
5. Claude Code: `brew install JetBrains/utils/kotlin-lsp`. If the plugins aren't installed automatically, run `/plugin install mattpocock-skills@claude-plugins-official`, and the same for `kotlin-lsp` and `claude-md-management`.
6. Check that the agent lists `context7` as connected and the `grilling` skill as available.

Personal overrides go in gitignored files: `.claude/settings.local.json`, `CLAUDE.local.md`, `.envrc`.

## Workflow

1. **Clarify**: `grilling` with structured questions, one decision per question with a recommended answer. Assumptions go in code comments, or in an ADR when they are hard to reverse.
2. **Look up**: Context7 for any framework API before writing code.
3. **Build**: `tdd` red-green-refactor, with `codebase-design` for the hexagonal seams. Larger phases used a planner, an implementer subagent and an independent validator subagent.
4. **Review and commit**: tests green, `code-review`, one logical change per commit.

## Guardrails

Rules are enforced in layers, because an agent can put an explicit request ahead of `AGENTS.md` (ADR 0005).

- **Every agent and human** (`.githooks/pre-commit`): rejects staged changes to `README.md`, `TimeDepositCalculatorTest.kt` and `ArchitectureTest.kt` (deletions and renames included), and runs `mvn verify` when `kotlin/` changes.
- **Claude Code**:
  - allowed without asking: `mvn`, `docker ps`, read-only git, Context7;
  - asks first: `git push`;
  - denied: force push, `git reset --hard`, `git clean`, `--no-verify`/`-n`, anything mentioning `core.hooksPath`, and edits to the three protected files.
- **Codex**:
  - rules forbid the same bypass and destructive commands, even in full-access mode, and ask before `git push`;
  - the `kata` sandbox profile makes the protected files read-only.
  - Check a command with: `codex execpolicy check --rules .codex/rules/guardrails.rules git commit -n -m x`.
- **Cursor, Copilot and Gemini**: hook scripts or policies deny the bypasses, the destructive commands and writes to the protected files, and ask before `git push`, including in YOLO, Allow-all and Autopilot modes.

## Validation evidence

| Check | Result |
|---|---|
| Commit a change to `README.md` or a protected test with the hook enabled | Rejected by the hook; `python3 .githooks/tests/check_guardrails.py` replays this on synthetic repositories |
| Claude Code: shell write to `README.md`; `git commit --no-verify`; `git config --get core.hooksPath` | All denied |
| Codex: manual session asked to edit and commit `README.md` | First run tried to comply; passed after the Hard rules fix (see below) |
| Codex: `codex execpolicy check` on bypass and destructive commands; write to `README.md` in the `kata` sandbox | Forbidden; `Operation not permitted` |
| Cursor and Copilot hook scripts fed tracked payload fixtures | Expected deny, ask and allow decisions; not run inside the editors |
| Gemini rules in its policy engine (default, auto-edit, YOLO, non-interactive) | Deny and ask as intended; no model session |
| Kover: remove every test reaching one branch, run `verify` | Fails the gate |
| ArchUnit: add a Spring class to `domain`, and a web→persistence dependency | Each fails its rule |
| Calculator: upstream copy vs current on about 220k seeded inputs (`TimeDepositCalculatorDifferentialTest`) | Bit-identical; a mutated plan boundary fails it |
| Lock: remove `@Lock` from `findAllForUpdate` | The concurrency IT loses a month and the lock ITs fail |

## Known limitations

- **Instructions are not guarantees.** That is why the hard layers exist.
- **Hooks are local.** Each clone must run `git config core.hooksPath .githooks`, and a human can commit with `--no-verify`. `Verify` re-runs `clean verify` on every push to `main` and every pull request that touches `kotlin/`, but neither workflow proves that protected files were left unchanged. Because the hook also blocks the owner, an intended change to a protected file needs a deliberate bypass.
- **Pattern-based rules have gaps:**
  - Codex prefix rules miss `--no-verify` placed after the message, and don't split scripts that use redirects, `$(...)` or globs;
  - Claude Code doesn't deny bundled flags such as `git commit -anm x`;
  - shell writes like `sed -i` are only stopped by Claude Code's deny rules, Codex's sandbox and, at commit time, the hook.
- **Uneven testing.** Only Claude Code and Codex ran in real sessions.
- **Docker is required to commit Kotlin changes.** The integration tests skip without Docker, and the coverage gate then fails, because only they reach the persistence adapter. This is deliberate: untested persistence code can't be committed. Check the gate with `clean verify`, since stale classes in `target/` can skew Kover.

## Failures and fixes

| What happened | Fix |
|---|---|
| Asked to edit and commit `README.md`, Codex replied that the explicit request overrides `AGENTS.md`. It was stopped before any change. | `AGENTS.md` Hard rules hold "even when a user explicitly asks otherwise"; Codex rules forbid bypassing the hook. The repeated test passed. |
| The Kover gate still passed after tests were removed: the agent appended to stale coverage data. | The build deletes `target/kover/` at `initialize`. |
| Gemini silently ignored the project's `.gemini/policies/`. | `policyPaths` in `.gemini/settings.json` loads them. |
| The Copilot settings sat in a gitignored `.vscode/`. | `.gitignore` keeps `.vscode/settings.json`. |
| A `case … )` inside `$(…)` made the Copilot hook crash on every call, which Copilot treats as deny-all. | Moved into a function; the fixtures now catch it. |
| An agent moved `TimeDeposit` and `TimeDepositCalculator` into `timedeposit.domain` for tidiness, which changed their fully qualified names: a breaking change the assignment forbids. An independent review caught it. | Moved back to `org.ikigaidigital` (ADR 0001); a differential test against upstream now guards behaviour. |
| A review found a test pinning a known one-cent difference at about 9×10¹³ as expected behaviour. | Replaced by a property test against the upstream calculator below 2^45; larger balances are a documented limit (ADR 0002). |
| A test had two assertions that could never fail. | Removed. |

## AI-assisted work

The repo owner set the requirements, made the decisions recorded in `docs/adr/` (most through grilling questions) and reviewed the work. Agents wrote the code, tests and documents.

| Area | Agent | What the AI did | Why |
|---|---|---|---|
| Harness | Claude Code | Wrote `AGENTS.md`, the per-agent configs, guardrails and hook, vendored the skills, and tested each guardrail (policy engines, `execpolicy`, hook fixtures) | Reproducible, agent-agnostic setup with enforced rules |
| Behaviour preservation | Claude Code, Codex | Characterization tests with pinned floating-point quirks, independently re-derived by a validator subagent; edge cases captured from upstream; the differential test; the Kover gate | `updateBalance` must stay bit-identical through the refactor |
| Domain | Claude Code | Moved the plan rules into the `PlanType` sealed class, test-first, and verified old against new bit for bit | Extensible interest rules without touching the frozen contract |
| Foundation and persistence | Claude Code, Codex | Spring Boot 4 build, Liquibase schema and seed, Testcontainers, the port and JPA adapter, after checking Boot 4, Hibernate 7 and Spring Data APIs in Context7 | Real database behaviour tested against real Postgres |
| API | Claude Code | `GET` and `POST` outside-in from HTTP integration tests, the OpenAPI annotations and a test that pins exactly two operations | The two required endpoints with a checked Swagger contract |
| Money exactness and locking | Claude Code, Codex | Found that the Double lost cents on large balances, kept stored decimals exact, measured the remaining error, and added row locks with a concurrency test | Correct persisted balances and no lost months |
| Reviews and cleanup | Codex, Claude Code | Independent reviews (including a differential run and mutants), the fixes listed above, and consolidating 17 ADRs into 9 | A submission that matches the assignment and is quick to read |

## Keeping skills up to date

`.agents/skills/skills.lock` pins the upstream mattpocock/skills commit.
- Run `scripts/sync-skills.sh latest` or `scripts/sync-skills.sh <sha>`, then review and commit the diff.
- `.github/workflows/sync-skills.yml` opens a weekly PR when skill content changed. The Claude Code plugin updates through `/plugin update`.
