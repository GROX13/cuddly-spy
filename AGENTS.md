# AGENTS.md

Kotlin solution to the XA Bank time-deposit kata. The assignment lives in `README.md`: read it first and keep it unchanged.

## Scope
- Code lives in `kotlin/`. The root holds agent config and docs (`GLOSSARY.md`, `docs/`).
- Frozen contract: the `TimeDeposit` shape and the `TimeDepositCalculator.updateBalance` signature and behaviour stay as they are.
- Exactly two API endpoints: update all balances, list all time deposits.

## Commands (from repo root)
- One-time per clone: `git config core.hooksPath .githooks` (pre-commit guardrails)
- Test: `mvn -f kotlin/pom.xml test` (JDK 17 pinned in `.java-version`; Docker running for testcontainers)

## How to work
1. Unclear requirement or design choice: run the `grilling` skill and ask through the AskUserQuestion tool, one decision per question, recommended answer first. Record the assumption in a code comment, or in an ADR under `docs/adr/` when it is hard to reverse.
2. Library or framework APIs (Kotlin, Spring, testcontainers, springdoc): look them up in Context7 before writing code.
3. Build test-first with the `tdd` skill. Shape modules with `codebase-design`: hexagonal, with a framework-free domain.
4. Name things with the terms in `GLOSSARY.md`, and add new ones through `domain-modeling`.
5. Before each commit: tests green, `code-review` skill run, one logical change per commit.
6. Append every AI-assisted piece of work to the log in `docs/ai-harness.md`.

## Tooling
Skills: `.agents/skills/` (the Claude Code plugin supplies the same set). Context7 config for each agent: `.mcp.json`, `.codex/`, `.cursor/`, `.gemini/`. Setup and rationale: `docs/ai-harness.md`.

## Agent skills

### Issue tracker
GitHub Issues on GROX13/cuddly-spy via `gh`. See `docs/agents/issue-tracker.md`.

### Triage labels
Default five-role vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs
Single-context: root `GLOSSARY.md` and `docs/adr/`. See `docs/agents/domain.md`.
