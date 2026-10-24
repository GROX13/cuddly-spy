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
  - denied: force push, `git reset --hard`, `git clean`, and editing `README.md`
- **Codex**:
  - `.codex/rules/guardrails.rules`, enforced even in full-access mode:
    - forbidden: `--no-verify`/`-n` (when placed before the message), changing `core.hooksPath`, force push, `git reset --hard`, `git clean`
    - asks first: `git push`, and git with global options (`-c`, `-C`, `--git-dir`)
    - allowed without prompting: `mvn -f kotlin/pom.xml …`
  - `.codex/config.toml` sandbox profile `kata`: workspace-write with `README.md` read-only. It's off in full-access mode.
  - check a command: `codex execpolicy check --rules .codex/rules/guardrails.rules git commit -n -m x`
- **Cursor** (`.cursor/hooks.json`, editor and CLI):
  - `guard-shell.sh` denies hook bypasses, `core.hooksPath` changes, force push, `git reset --hard` and `git clean`, and asks before `git push`
  - `guard-readme.sh` denies writes to `README.md`
  - `permissions.json`/`cli.json` allow `mvn`, read-only git and Context7; they can't deny anything, so all blocking lives in the hooks
  - check: `echo '{"command":"git push -f"}' | .cursor/hooks/guard-shell.sh`
- **Gemini CLI** (`.gemini/policies/guardrails.toml`):
  - loaded through `policyPaths` in `.gemini/settings.json`, because Gemini ignores workspace policies on its own
  - denies `README.md` writes, hook bypasses, force push, `git reset --hard` and `git clean`
  - asks before `git push`, even in YOLO mode
  - allows `mvn`, read-only git and Context7
  - check: `/policies list` shows the rules under the User tier
- **Copilot in VS Code**:
  - `.github/hooks/guardrails.sh` (PreToolUse) denies `README.md` edits, hook bypasses, force push, `git reset --hard` and `git clean`, and asks before `git push`
  - `.vscode/settings.json` auto-approves `mvn` and read-only git. Its `false` rules only require approval; the hook is what blocks, including under Allow all or Autopilot.
  - Context7 tools are approved once in the UI (**Chat: Manage Tool Approval**)

Known gaps, all caught by the pre-commit hook at commit time:
- shell writes such as `echo >> README.md` (only Codex's sandbox catches these)
- `git commit -m x --no-verify` in Codex (prefix rules can't match flags after the message)

`AGENTS.md` instructions are a soft layer: an agent may follow an explicit user request over them. The hook and the per-agent rules are the hard layer. A determined human can still bypass everything locally; a CI check on pull requests would be the next step if that matters.

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
