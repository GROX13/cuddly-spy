# Enforce rules in layers, not by instructions alone

An agent can follow an explicit user request over `AGENTS.md`: in a test run Codex said "your explicit request overrides" the README rule and set out to edit and commit it. So every important rule is enforced in four layers, each catching what the one above misses:

1. **`AGENTS.md` Hard rules** (soft): the agent knows the rule and why, so it usually declines and offers an alternative.
2. **Per-agent rules** (hard, before the action): `.claude/settings.json` permissions, `.codex/rules/` plus the `kata` sandbox profile, Cursor and Copilot hook scripts, Gemini policies. They also block switching off layer 3 (`--no-verify`, `core.hooksPath` changes).
3. **`.githooks/pre-commit`** (hard, at commit time, for every agent and human): rejects commits that touch the assignment `README.md`, and runs `mvn verify` when `kotlin/` changes.
4. **Tests in `verify`**: the Kover coverage gate (ADR 0006) and the ArchUnit boundary rules (ADR 0007).

## Consequences
- No single layer is trusted on its own; the known gaps of each are listed in `docs/ai-harness.md` (Known limitations).
- Everything above runs locally, so a human can still bypass it. A CI check on pull requests would be the next layer.
