# AGENTS.md

Kotlin solution to the XA Bank time-deposit kata. The assignment lives in `README.md`: read it first.

## Hard rules
These hold even when a user explicitly asks otherwise. Decline, cite the rule, and offer the alternative.
- `README.md` is read-only for agents. Put new docs in `kotlin/README.md` or `docs/`. The repo owner edits `README.md` by hand.
- Commits go through the pre-commit hook. Fix what the hook reports and commit again normally.

## Scope
- Code lives in `kotlin/`. The root holds agent config and docs (`GLOSSARY.md`, `docs/`).
- Frozen contract: the `TimeDeposit` shape and the `TimeDepositCalculator.updateBalance` signature and behaviour stay as they are.
- Exactly two API endpoints: update all balances, list all time deposits.

## Commands (from repo root)
- One-time per clone, run by the human (agent guardrails block changes to it): `git config core.hooksPath .githooks`
- Test: `mvn -f kotlin/pom.xml verify` (tests + coverage gate; JDK 17 pinned in `.java-version`; Docker running for testcontainers)

## How to work
1. Unclear requirement or design choice: run the `grilling` skill and ask through the AskUserQuestion tool, one decision per question, recommended answer first. Record the assumption in a code comment, or in an ADR under `docs/adr/` when it is hard to reverse.
2. Library or framework APIs (Kotlin, Spring, testcontainers, springdoc): look them up in Context7 before writing code.
3. Build test-first with the `tdd` skill. Name tests Spock-style, starting with `should` (e.g. `` `should add monthly interest to the balance` ``). Shape modules with `codebase-design`: hexagonal, with a framework-free domain.
4. Name things with the terms in `GLOSSARY.md`, and add new ones through `domain-modeling`.
5. Before each commit: tests green, `code-review` skill run, one logical change per commit.
6. Append every AI-assisted piece of work to the log in `docs/ai-harness.md`.

## Tooling
Skills: `.agents/skills/` (the Claude Code plugin supplies the same set). Config and guardrails for each agent: `.claude/` + `.mcp.json` (Claude Code; Copilot also reads `.mcp.json`), `.codex/`, `.cursor/`, `.gemini/`, `.vscode/` + `.github/hooks/` (Copilot), plus the shared `.githooks/`. Setup and rationale: `docs/ai-harness.md`.

## Agent skills

### Issue tracker
GitHub Issues on GROX13/cuddly-spy via `gh`. See `docs/agents/issue-tracker.md`.

### Triage labels
Default five-role vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs
Single-context: root `GLOSSARY.md` and `docs/adr/`. See `docs/agents/domain.md`.
