# Support multiple AI agents from one set of rules

Contributors use different AI coding tools (Claude Code, Codex, Cursor, Gemini CLI, Copilot), and a harness tied to one vendor would force everyone onto it or leave the others unguided. So the rules live once in `AGENTS.md`, which most agents read natively; Claude Code reads it through a one-line `CLAUDE.md` import, and Gemini through `context.fileName`. The workflow skills are vendored once in `.agents/skills/`, the shared folder Codex, Cursor, Gemini and Copilot discover. Only what can't be shared, such as MCP config and each tool's permission format, lives in per-agent folders (`.claude/`, `.codex/`, `.cursor/`, `.gemini/`, `.vscode/` + `.github/hooks/`); Claude Code and Copilot share the root `.mcp.json`.

## Consequences
- Changing a rule is a one-place edit in `AGENTS.md`, but a new guardrail has to be written once per agent in that agent's format.
- Agents get different depths of testing. Claude Code and Codex were exercised in real sessions; the others were checked by config parsing and scripted inputs (see `docs/ai-harness.md`, Validation evidence).
