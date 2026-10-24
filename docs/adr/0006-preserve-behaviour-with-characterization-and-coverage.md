# Prove the legacy calculator's behaviour is preserved before AI refactors it

ADR 0001 freezes `updateBalance`'s behaviour, and AI agents will refactor around it. We pin today's behaviour with characterization tests: expected balances are literal values compared with exact Double equality, legacy floating-point quirks included and commented as intentional (e.g. a basic deposit of 30.00 gets 0.02 interest, not 0.03, because the Double is just below 0.025). Hand-derived and independently re-derived values guard against tests that merely restate the code. A Kover rule fails `mvn verify` unless `TimeDepositCalculator` has 100% line and branch coverage, so a refactor can't hide behaviour the tests don't reach, and stale Kover data is deleted at the start of every build so old coverage can't make the gate pass.

## Consequences
- During a refactor the characterization tests are not edited; a failing one means behaviour changed. To make that hold for AI agents, `TimeDepositCalculatorTest.kt` is read-only for them: an `AGENTS.md` Hard rule plus an edit block in every agent's config, like the assignment `README.md`. The pre-commit hook doesn't block it, so the repo owner can still change it deliberately.
- The tests also pin that repeated calls compound, which the API relies on (ADR 0008).
- The gate covers whole branches, not individual rows: every branch is reached by at least two rows, so removing a single row doesn't fail it; removing every row that reaches a branch does (for the premium 31–45-day branch that is one parameterized test).
