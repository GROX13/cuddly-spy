# AI harness

How this repo is developed with AI agents, and how to reproduce the setup. Agent-facing rules are in [`AGENTS.md`](../AGENTS.md).

## Tools

| Tool | Role | Configured in |
|---|---|---|
| Claude Code (Claude Opus 5.5) | Primary coding agent | `AGENTS.md`, `CLAUDE.md` (imports `AGENTS.md`) |
| Context7 MCP | Current library docs (Kotlin, Spring, testcontainers, springdoc) | `.mcp.json` |
| `mattpocock-skills` plugin | Workflow skills: `grilling`, `tdd`, `codebase-design`, `domain-modeling`, `code-review`, `pr`, `to-spec`/`to-tickets`, `diagnosing-bugs` | `.claude/settings.json` |
| `kotlin-lsp` plugin | Kotlin code intelligence (diagnostics, go-to-definition) for the agent | `.claude/settings.json` |
| `claude-md-management` plugin | Keeps `AGENTS.md` accurate as the project changes | `.claude/settings.json` |
| AskUserQuestion (built-in tool) | Structured multiple-choice clarification with a recommended answer | `AGENTS.md` |
| IntelliJ IDEA MCP (optional, personal) | IDE-backed inspections, refactors and run configs | user scope, not committed |

## Setup

1. Install [Claude Code](https://code.claude.com), JDK 17, Maven and Docker.
2. Kotlin LSP binary, needed by the `kotlin-lsp` plugin: `brew install JetBrains/utils/kotlin-lsp`.
3. Optional local env: `cp .envrc.example .envrc && direnv allow`. `CONTEXT7_API_KEY` is optional because Context7 works anonymously; a key only raises rate limits.
4. Run `claude` in the repo root and accept the workspace trust prompt. Allow rules and the plugin marketplace only take effect after trust.
5. If the plugins aren't installed automatically, run `/plugin install mattpocock-skills@claude-plugins-official` (and the same for `kotlin-lsp`, `claude-md-management`).
6. Check: `/mcp` lists `context7` as connected, and `/mattpocock-skills:grilling` resolves.

Personal overrides go in gitignored files: `.claude/settings.local.json`, `CLAUDE.local.md`, `.envrc`.

## Guardrails (`.claude/settings.json`)

- Allowed without prompting: `mvn`, `docker ps`, read-only git, Context7 tools.
- Asks first: `git push`.
- Denied: force push, `git reset --hard`, `git clean`, and editing the assignment `README.md`.

## Workflow

1. **Clarify**: `grilling` plus AskUserQuestion until the decision tree is settled. Assumptions go in code comments, or in ADRs (`docs/adr/`) when they're hard to reverse.
2. **Look up**: Context7 for any framework API before writing code.
3. **Build**: `tdd` red-green-refactor, with `codebase-design` for hexagonal seams.
4. **Review and commit**: `code-review`, tests green, atomic commits.

## AI-assisted log

| Area | What the AI did | Why |
|---|---|---|
| Harness setup | Interviewed for requirements (grilling + AskUserQuestion), checked the Claude Code config syntax via Context7, and wrote `AGENTS.md`, `.claude/settings.json`, `.mcp.json`, `GLOSSARY.md`, ADR 0001, `docs/agents/*` | Reproducible agent setup is a stated requirement. The interview surfaced decisions before any files were written |
