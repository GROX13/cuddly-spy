# AI harness

How this repo is developed with AI agents, and how to reproduce the setup. Agent-facing rules are in [`AGENTS.md`](../AGENTS.md). The harness works with any model; pick whichever suits you.

## Tools

| Tool | Role | Configured in |
|---|---|---|
| Coding agent (Claude Code, Codex, Cursor or Gemini CLI) | Writes and refactors code under the `AGENTS.md` rules | see [Agents](#agents) |
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
| Codex CLI | `AGENTS.md` | `.codex/config.toml` | `.agents/skills/` | `.codex/rules/` + hook |
| Cursor | `AGENTS.md` | `.cursor/mcp.json` | `.agents/skills/` | hook |
| Gemini CLI | `AGENTS.md` (via `.gemini/settings.json`) | `.gemini/settings.json` | `.agents/skills/` | hook |

Each agent only loads project config after you trust the folder:

- Claude Code: accept the trust prompt.
- Codex: trust the project when prompted.
- Gemini: run `/permissions` → trust.
- Cursor: approve the MCP server.

## Setup

1. Install JDK 17, Maven, Docker, and at least one agent above.
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
- **Codex** (`.codex/rules/guardrails.rules`, enforced even in full-access mode):
  - forbidden: `git commit --no-verify`/`-n`, force push, `git reset --hard`, `git clean`
  - asks first: `git push`
  - check a command: `codex execpolicy check --rules .codex/rules/guardrails.rules git commit -n -m x`

Instructions in `AGENTS.md` are a soft layer: an agent may follow an explicit user request over them. The hook and the per-agent rules are the hard layer. An agent can still edit `README.md` in the working tree, but the edit can't be committed.

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
