# One rule set for several AI agents

Contributors use different coding agents, so the rules live once in `AGENTS.md`: most agents read it natively, Claude Code through a one-line `CLAUDE.md` import and Gemini through `context.fileName`. The workflow skills are vendored once in `.agents/skills/`. Only what can't be shared, such as MCP config and each tool's permission format, lives in the per-agent folders (`.claude/`, `.codex/`, `.cursor/`, `.gemini/`, `.vscode/` and `.github/hooks/`).

## Consequences
- A rule change is one edit in `AGENTS.md`, but a new guardrail is written once per agent, in that agent's format.
- Testing depth differs: Claude Code and Codex ran in real sessions; Cursor, Copilot and Gemini were checked through hook fixtures and policy evaluation only (`docs/ai-harness.md`).
