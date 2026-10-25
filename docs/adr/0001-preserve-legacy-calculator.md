# Preserve the legacy calculator as a frozen contract

`TimeDeposit` and `TimeDepositCalculator.updateBalance(List<TimeDeposit>)` are shared, so the assignment forbids breaking them. Both stay in `org.ikigaidigital` with the same shape, signature and behaviour; persistence, the API and the extensible plan rules are built around them. The calculator may change inside, as long as every result stays bit-identical to upstream `c4ea358`.

## Consequences
- The proof is four layers, all in `mvn verify`:
  - `TimeDepositCalculatorTest` pins about 50 hand-checked rows, floating-point quirks included, and is read-only for agents (ADR 0005);
  - `TimeDepositCalculatorLegacyEdgesTest` pins signed zero, non-finite balances, Java nulls and aliased entries;
  - `TimeDepositCalculatorDifferentialTest` runs a test-only copy of the upstream code next to it on about 220k seeded inputs and compares bit for bit;
  - a Kover rule requires 100% line and branch coverage.
- The calculator gained a constructor parameter with a default, so the no-arg constructor still exists and the JVM API is a superset of the original.
- The two classes were once moved into `timedeposit.domain` for tidiness. That changes their fully qualified names, which is a breaking change, so they were moved back.
