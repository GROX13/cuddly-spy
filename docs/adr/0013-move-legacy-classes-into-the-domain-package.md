# Move TimeDeposit and TimeDepositCalculator into the domain package

`TimeDeposit` and `TimeDepositCalculator` move from the root package `org.ikigaidigital` to `org.ikigaidigital.timedeposit.domain`, next to `PlanType`, `Withdrawal` and `TimeDepositWithWithdrawals`. The repo owner decided this because it is cleaner. The core domain object and the pure interest calculation now sit in the domain layer with the rest of the domain model, instead of beside the application's wiring, so ArchUnit's domain rules and the 100% domain coverage gate cover them.

Moving a class changes its fully qualified name, which is a breaking change for anything compiled against it. In production, if these classes were published as a library or consumed as an external dependency, they would stay where they are (or keep a deprecated alias in the old package for a release). This is a self-contained application, though: every caller is in this repository and compiles with it, so the move is a refactoring with no external consumers to break. That is why phase 2 first kept them in place (moving them looked like a change to a "shared" contract) and this decision now reverses it.

## Consequences
- ADR 0001 still holds: the shape of `TimeDeposit` and the signature and behaviour of `updateBalance` are unchanged. Only the package moved; the 53 characterization tests pass unchanged apart from two new import lines.
- The two locked test files (`TimeDepositCalculatorTest.kt`, `ArchitectureTest.kt`) each gained only those imports. The repo owner lifted their agent lock for this change and restored it afterwards.
- The Kover rule "Legacy calculator fully covered" now names `org.ikigaidigital.timedeposit.domain.TimeDepositCalculator`.
