# Enforce hexagonal boundaries with ArchUnit tests

Agents generate code quickly and readily import whatever compiles, so a package convention alone would erode. `ArchitectureTest` runs in every `mvn verify` and fails the build when:
- the domain (`timedeposit.domain`) or the legacy `TimeDeposit` / `TimeDepositCalculator` depend on Spring, JPA or adapters
- the application layer depends on adapters
- the web and persistence adapters depend on each other

We chose ArchUnit in one Maven module over a multi-module build: the same `verify` command, one coverage config, and the rule failure message tells an agent exactly which dependency to remove.

## Consequences
- Boundaries are checked at test time, not compile time, so a violation compiles but can't be committed (the pre-commit hook runs `verify`).
- The rules allow empty packages until phases 3–5 add code to them.
- `ArchitectureTest.kt` is read-only for AI agents (an `AGENTS.md` Hard rule plus an edit block in every agent's config), so an agent can't make a violation pass by loosening a rule. The repo owner changes the rules by hand when the architecture really changes.
