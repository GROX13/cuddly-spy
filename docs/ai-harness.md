# AI harness

How this repo is developed with AI agents, and how to reproduce the setup. Agent-facing rules are in [`AGENTS.md`](../AGENTS.md). The harness works with any model; pick whichever suits you.

## Tools

| Tool | Role | Configured in |
|---|---|---|
| Coding agent (Claude Code, Codex, Cursor, Gemini CLI or Copilot in VS Code) | Writes and refactors code under the `AGENTS.md` rules | see [Agents](#agents) |
| Context7 MCP | Current library docs (Kotlin, Spring, testcontainers, springdoc) | per-agent config, see [Agents](#agents) |
| mattpocock skills | Workflow skills: `grilling`, `tdd`, `codebase-design`, `domain-modeling`, `code-review`, `pr` | `.agents/skills/`; for Claude Code, the `mattpocock-skills` plugin |
| `kotlin-lsp` plugin (Claude Code) | Kotlin code intelligence (diagnostics, go-to-definition) | `.claude/settings.json` |
| `claude-md-management` plugin (Claude Code) | Keeps `AGENTS.md` accurate as the project changes | `.claude/settings.json` |
| AskUserQuestion (Claude Code built-in) | Structured multiple-choice clarification with a recommended answer. Other agents ask the same questions in chat | `AGENTS.md` |
| Git pre-commit hook | Agent-agnostic guardrails | `.githooks/pre-commit` |
| IntelliJ IDEA MCP (optional, personal) | IDE-backed inspections, refactors and run configs | user scope, not committed |

## Agents

All agents share `AGENTS.md`, the vendored skills, the hook and Context7. Only the config file format differs.

| Agent | Rules | Context7 | Skills | Guardrails |
|---|---|---|---|---|
| Claude Code | `CLAUDE.md` → `AGENTS.md` | `.mcp.json` | plugin via `.claude/settings.json` | `.claude/settings.json` permissions + hook |
| Codex CLI | `AGENTS.md` | `.codex/config.toml` | `.agents/skills/` | `.codex/rules/` + `kata` sandbox profile + hook |
| Cursor | `AGENTS.md` | `.cursor/mcp.json` | `.agents/skills/` | `.cursor/hooks.json` + `cli.json`/`permissions.json` + hook |
| Gemini CLI | `AGENTS.md` (via `.gemini/settings.json`) | `.gemini/settings.json` | `.agents/skills/` | `.gemini/policies/` + hook |
| Copilot (VS Code) | `AGENTS.md` | `.mcp.json` | `.agents/skills/` | `.github/hooks/` + `.vscode/settings.json` + hook |

Each agent only loads project config after you trust the folder. Start each one from the repo root: Codex's sandbox profile and Gemini's policy path are resolved relative to where it starts.

- Claude Code: accept the trust prompt.
- Codex: trust the project when prompted.
- Gemini: run `/permissions` → trust.
- Cursor: trust the workspace, approve the MCP server, and check that Settings → Hooks lists both hooks.
- Copilot: trust the workspace (hooks only run in trusted workspaces).

The Cursor and Copilot hook scripts need `jq` on the PATH.

## Setup

1. Install JDK 17, Maven, Docker, and at least one agent above.
   - Cursor and Copilot also need `jq` for their hook scripts. Recent macOS ships it at `/usr/bin/jq`; otherwise run `brew install jq` or `apt install jq`.
2. Enable the guardrails: `git config core.hooksPath .githooks`.
3. Optional local env: `cp .envrc.example .envrc && direnv allow`. `CONTEXT7_API_KEY` is optional because Context7 works anonymously; a key only raises rate limits.
4. Start your agent in the repo root and trust the folder.
5. Claude Code only:
   - Install the Kotlin LSP binary: `brew install JetBrains/utils/kotlin-lsp`.
   - If the plugins aren't installed automatically, run `/plugin install mattpocock-skills@claude-plugins-official` (and the same for `kotlin-lsp` and `claude-md-management`).
6. Check: the agent's MCP list shows `context7` as connected, and the `grilling` skill is listed.

Personal overrides go in gitignored files: `.claude/settings.local.json`, `CLAUDE.local.md`, `.envrc`.

## Guardrails

- **Every agent** (`.githooks/pre-commit`):
  - commits touching the assignment `README.md` are rejected
  - commits touching `kotlin/` run the tests first
- **Claude Code** (`.claude/settings.json`):
  - allowed without prompting: `mvn`, `docker ps`, read-only git, Context7
  - asks first: `git push`
  - denied: force push, `git reset --hard`, `git clean`, editing `README.md`, the characterization tests (`TimeDepositCalculatorTest.kt`) or `ArchitectureTest.kt`, `git commit` with `--no-verify` (anywhere in the command) or `-n`, and any git command mentioning `core.hooksPath` (reading it included)
- **Codex**:
  - `.codex/rules/guardrails.rules`, enforced even in full-access mode:
    - forbidden: `--no-verify`/`-n` (when placed before the message), changing `core.hooksPath`, force push, `git reset --hard`, `git clean`
    - asks first: `git push`, and git with global options (`-c`, `-C`, `--git-dir`)
    - allowed without prompting: `mvn -f kotlin/pom.xml …`
  - `.codex/config.toml` sandbox profile `kata`: workspace-write with `README.md`, the characterization tests and `ArchitectureTest.kt` read-only. It's off in full-access mode.
  - check a command: `codex execpolicy check --rules .codex/rules/guardrails.rules git commit -n -m x`
- **Cursor** (`.cursor/hooks.json`, editor and CLI):
  - `guard-shell.sh` denies hook bypasses, `core.hooksPath` changes, force push, `git reset --hard` and `git clean`, and asks before `git push`
  - `guard-readme.sh` denies writes to `README.md`, the characterization tests and `ArchitectureTest.kt`
  - `permissions.json`/`cli.json` allow `mvn`, read-only git and Context7; they can't deny anything, so all blocking lives in the hooks
  - check: `echo '{"command":"git push -f"}' | .cursor/hooks/guard-shell.sh`
- **Gemini CLI** (`.gemini/policies/guardrails.toml`):
  - loaded through `policyPaths` in `.gemini/settings.json`, because Gemini ignores workspace policies on its own
  - denies writes to `README.md`, the characterization tests and `ArchitectureTest.kt`, hook bypasses, force push, `git reset --hard` and `git clean`
  - asks before `git push`, even in YOLO mode
  - allows `mvn`, read-only git and Context7
  - check: `/policies list` shows the rules under the User tier
- **Copilot in VS Code**:
  - `.github/hooks/guardrails.sh` (PreToolUse) denies edits to `README.md`, the characterization tests and `ArchitectureTest.kt`, hook bypasses, force push, `git reset --hard` and `git clean`, and asks before `git push`
  - `.vscode/settings.json` auto-approves `mvn` and read-only git. Its `false` rules only require approval; the hook is what blocks, including under Allow all or Autopilot.
  - Context7 tools are approved once in the UI (**Chat: Manage Tool Approval**)

How these layers fit together, and why instructions alone aren't enough: [ADR 0005](adr/0005-layered-enforcement.md). Their gaps are listed under [Known limitations](#known-limitations).

## Design decisions

| ADR | Decision |
|---|---|
| [0004](adr/0004-support-multiple-agents.md) | One `AGENTS.md` and one skills folder for every agent; per-agent folders only for what can't be shared |
| [0005](adr/0005-layered-enforcement.md) | Rules enforced in layers: `AGENTS.md` → per-agent rules → pre-commit hook → tests |
| [0006](adr/0006-preserve-behaviour-with-characterization-and-coverage.md) | Characterization tests with pinned quirks plus a 100% line/branch gate protect the legacy calculator during AI refactoring |
| [0007](adr/0007-enforce-hexagonal-boundaries-with-archunit.md) | ArchUnit tests stop generated code from crossing hexagonal boundaries |
| [0008](adr/0008-balance-update-applies-interest-every-call.md) | Every balance update applies one monthly interest; no idempotency guard, matching the calculator |

## Failures and fixes

Real problems hit while building this harness, and what changed because of them.

| What happened | Fix |
|---|---|
| Asked to edit and commit `README.md`, **Codex** replied that the explicit request overrides `AGENTS.md` and set out to do it. The run was stopped before any change; the file was never modified or committed. | `AGENTS.md` got a **Hard rules** section that holds "even when a user explicitly asks otherwise", and `.codex/rules/` forbids bypassing the hook. The repeated test passed. |
| The **Kover** gate still passed after tests were removed, because the Kover coverage agent appends to `target/kover/test.ic` and reused coverage from an earlier run. Found by the phase 1 implementer. | The build deletes `target/kover/` at `initialize`, before Kover starts. |
| **Gemini CLI** silently ignored the project's `.gemini/policies/` folder; a deliberately broken policy file raised no error. | `policyPaths` in `.gemini/settings.json` loads the folder at the User tier. |
| The **Copilot** settings were written to `.vscode/`, which `.gitignore` excluded, so nobody else would have received them. | `.gitignore` excludes `.vscode/*` except `settings.json`. |
| The several-deposits test had two assertions that could never fail. Flagged by the validator. | Removed; the remaining assertions pin the in-place update. |
| Extending the **Copilot** hook to protect the characterization tests put a `case … )` pattern inside `$(…)`, which `sh` can't parse. The script crashed on every call, and Copilot treats a crashed hook as a deny, so it would have blocked every tool call. Caught by re-running the hook's sample inputs before committing. | The loop moved into a function; `sh -n` and `dash -n` pass, and all sample inputs give the expected decision. |
| A cleanup step deleted the backups after checking only that `main` matched `origin/main`, not that the rewritten commits had been pushed. Nothing was lost: the rewritten commits were still in the reflog. | Verify the content of a result (here, the commit emails) before deleting a backup. |

## Validation evidence

Adversarial checks run against the guardrails, and what they showed.

| Check | Result |
|---|---|
| Commit a change to `README.md` in a scratch clone with the hook enabled | Rejected by the pre-commit hook |
| Claude Code: a shell write to `README.md` during this work | Blocked by the `.claude/settings.json` deny rule |
| Claude Code: `git commit --dry-run -m x --no-verify` and `git config --get core.hooksPath` | Both denied; a plain `git commit` still runs |
| Codex: manual session test (`.tmp/codex-harness-test.md`) | First run failed the README step (see above); passed after the fix |
| Codex: `codex execpolicy check` on bypass and destructive commands | `--no-verify`/`-n` (before the message), `core.hooksPath` changes, force push variants, `git reset --hard` and `git clean` forbidden; `git push` prompts; plain `git commit` matches no rule (normal sandbox and approval apply) |
| Codex: shell write to `README.md` inside the `kata` sandbox | `Operation not permitted` |
| Protected-test lock (`TimeDepositCalculatorTest.kt`, `ArchitectureTest.kt`): Claude Code Edit, Codex `kata` sandbox write, Cursor and Copilot hooks (incl. Copilot `apply_patch`), Gemini policy pattern | All denied; another test file in the same folder stays editable |
| Cursor and Copilot hook scripts fed scripted tool-call inputs | Denies, asks and allows as intended; not run inside Cursor or VS Code |
| Gemini: rules evaluated with Gemini's own policy engine in default, auto-edit, YOLO and non-interactive modes | Denies and asks as intended, including in YOLO; skills and Context7 listed; no model session (no API key) |
| Kover: remove every test reaching one branch, run plain `verify` without cleaning | Fails at 92.86% branch coverage |
| ArchUnit: add a Spring class to `domain`, and a web→persistence dependency | Each fails its rule |
| Build with no Docker socket available (run inside a container) | Integration tests skipped, build green |
| Characterization values | The validator re-derived 10 expected values independently; all matched |

## Known limitations

- **Instructions are not guarantees.** `AGENTS.md` is read and usually followed, but an agent can put an explicit request first. That's why the hard layers exist.
- **Local hooks can be bypassed.** A human can commit with `--no-verify`, and each clone must run `git config core.hooksPath .githooks` once. There is no CI check yet.
- **Pattern-based rules have gaps.**
  - Codex prefix rules miss `git commit -m x --no-verify` (flag after the message), and Codex doesn't split shell scripts that use redirects, `$(...)`, `VAR=` prefixes or globs, so those match no rule.
  - Shell writes such as `echo >> README.md` (or to the characterization tests) are only stopped by Claude Code's deny rule and Codex's sandbox; everywhere else the commit-time hook is the backstop. Claude Code's blocking is itself pattern-based, and other write paths (`tee`, `sed -i`) weren't tested.
  - Bundled short flags such as `git commit -anm x` aren't denied by Claude Code's rules (matching `-n` inside bundles would also block ordinary messages); the hook still runs for those.
  - Claude Code's rules match the whole command text, so a commit whose inline `-m` message mentions the hook-bypass flag or the hooks-path setting is denied too. Commit with the message in a file (`git commit -F <file>`) instead.
- **Uneven testing across agents.** Claude Code and Codex were exercised in real sessions. Cursor and Copilot were tested only through their hook scripts; Gemini through its policy engine, without a model session.
- **Integration tests skip without Docker.** Chosen so commits never block on Docker, at the cost of integration failures going unnoticed while Docker is down.
- **Coverage gate scope.** The 100% gate covers `TimeDepositCalculator` only; the domain gate arrives with the refactor.

## Reproduce the checks

From the repo root, after the [Setup](#setup) steps:

```sh
mvn -f kotlin/pom.xml verify                       # tests, coverage gate, ArchUnit, integration tests
mvn -f kotlin/pom.xml spring-boot:run -Dspring-boot.run.profiles=local   # app + seeded Postgres, Swagger UI on :8080
codex execpolicy check --rules .codex/rules/guardrails.rules git commit -n -m x   # expect forbidden
echo '{"command":"git push -f"}' | .cursor/hooks/guard-shell.sh               # expect deny
echo '{"tool_name":"run_in_terminal","tool_input":{"command":"git push -f"}}' | sh .github/hooks/guardrails.sh   # expect deny
```

Gemini: start `gemini` in the repo root, trust the folder, then `/policies list`. Manual per-agent test scripts live in `.tmp/` locally and aren't committed.

## Keeping skills up to date

`.agents/skills/skills.lock` pins the upstream mattpocock/skills commit.
- `scripts/sync-skills.sh latest` bumps to upstream HEAD; `scripts/sync-skills.sh <sha>` pins a commit. Review the diff and commit it.
- `.github/workflows/sync-skills.yml` runs weekly and opens a PR only when skill content changed. It needs *Settings → Actions → General → Allow GitHub Actions to create and approve pull requests*.
- The Claude Code plugin updates separately through `/plugin update`.

Details: [`.agents/skills/README.md`](../.agents/skills/README.md).

## Workflow

1. **Clarify**: the `grilling` skill plus structured questions until the decision tree is settled. Assumptions go in code comments, or in ADRs (`docs/adr/`) when they're hard to reverse.
2. **Look up**: Context7 for any framework API before writing code.
3. **Build**: `tdd` red-green-refactor, with `codebase-design` for hexagonal seams.
4. **Review and commit**: `code-review`, tests green, atomic commits.

## AI-assisted log

| Area | Agent / model | What the AI did | Why |
|---|---|---|---|
| Harness setup | Claude Code / Claude Opus 5.5 | Interviewed for requirements (grilling + AskUserQuestion), checked the Claude Code config syntax via Context7, and wrote `AGENTS.md`, `.claude/settings.json`, `.mcp.json`, `GLOSSARY.md`, ADR 0001, `docs/agents/*` | Reproducible agent setup is a stated requirement. The interview surfaced decisions before any files were written |
| Multi-agent harness | Claude Code / Claude Opus 5.5 | Checked the Codex, Cursor and Gemini config formats via Context7, added their configs, vendored the skills into `.agents/skills/`, and added the pre-commit hook | Lets contributors use any agent with the same rules, docs lookup and skills |
| Per-agent guardrails and skill sync | Claude Code / Claude Opus 5.5 (one subagent per agent) | Researched each agent's guardrail mechanism via Context7, wrote the Cursor/Copilot hooks, Gemini policies and Codex rules plus sandbox profile, and tested them locally (Gemini's policy engine, `codex execpolicy check`, sample hook inputs). Added the pinned skill sync script and weekly CI | Same guardrails whatever agent is used; vendored skills stay current through reviewed PRs |
| Phase 1: characterization tests + coverage gate | Claude Code / Claude Opus 5.5 (grilling + plan, then an implementer and an independent validator subagent) | Pinned `updateBalance`'s current behaviour, floating-point rounding quirks included, in 52 tests. Gated `TimeDepositCalculator` at 100% line/branch with Kover. Found and fixed Kover reusing stale coverage data. The validator re-derived 10 values independently | The frozen calculator must behave identically through the framework upgrade and the refactor; the gate proves the tests reach every branch |
| Phase 2: Spring Boot foundation | Claude Code / Claude Opus 5.5 (grilling + plan, then an implementer and an independent validator subagent) | Upgraded the Maven build to Spring Boot 4.1 / Kotlin 2.3 and checked the Boot 4 module names, springdoc compatibility and Testcontainers 2 artifacts against Context7 and Maven Central. Added the Liquibase schema with a local-only seed, Postgres via Docker Compose and Testcontainers, Swagger UI and ArchUnit layer rules. Proved the ITs skip without Docker by running the build in a container with no Docker socket | Gives the API phases a real database, schema and enforced hexagonal boundaries while the 52 characterization tests stay unchanged and green |
| Harness decisions and evidence | Claude Code / Claude Opus 5.5 (written by the main session, fact-checked by an independent validator subagent) | Wrote ADRs 0004–0007 and the Failures and fixes, Validation evidence, Known limitations and Reproduce the checks sections from this session's real history | Makes the harness's reasoning, its tested guarantees and its gaps visible to reviewers and future contributors |
| Protected tests and repeated updates | Claude Code / Claude Opus 5.5 | Added a characterization test pinning that repeated `updateBalance` calls compound, locked `TimeDepositCalculatorTest.kt` and `ArchitectureTest.kt` against agent edits in every agent's config, wrote ADR 0008, and found and fixed a crash in the Copilot hook while testing the lock | Keeps AI refactors from changing the tests that prove behaviour and boundaries are preserved, and records how the update endpoint will behave |
