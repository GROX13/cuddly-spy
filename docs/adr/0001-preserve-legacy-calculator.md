# Preserve the legacy calculator as a frozen contract

The `TimeDeposit` class and `TimeDepositCalculator.updateBalance(List<TimeDeposit>)` are shared and must keep their shape, signature and behaviour (assignment requirement 4 and guidelines). New design work, such as extensible interest rules, persistence and the API, is added around them through ports and adapters. Any internal refactor of `updateBalance` has to stay behaviour-identical, proven by characterization tests that pass before and after.

Amended by [ADR 0013](0013-move-legacy-classes-into-the-domain-package.md): both classes moved to `org.ikigaidigital.timedeposit.domain`. Their shape, signature and behaviour stay frozen.
