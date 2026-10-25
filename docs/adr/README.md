# Architecture decisions

Each record describes the decision as it stands. Earlier versions are in the git history.

| ADR | Decision |
|---|---|
| [0001](0001-preserve-legacy-calculator.md) | `TimeDeposit` and `updateBalance` stay frozen in `org.ikigaidigital`, proven by characterization, differential and coverage checks |
| [0002](0002-money-as-numeric-19-2.md) | Snake_case schema, `NUMERIC(19,2)` money, exact decimals around the Double calculator |
| [0003](0003-withdrawals-are-history.md) | Withdrawals are history, already reflected in the balance |
| [0004](0004-support-multiple-agents.md) | One `AGENTS.md` and one skills folder for every AI agent |
| [0005](0005-layered-enforcement.md) | Agent rules enforced in layers: instructions, per-agent rules, pre-commit hook, tests and CI |
| [0006](0006-hexagonal-layout.md) | Hexagonal packages in one module, one outbound port, Spring Data behind an adapter, ArchUnit checks |
| [0007](0007-balance-update-transaction.md) | The balance update runs in one `@Transactional` call with ordered row locks |
| [0008](0008-balance-update-applies-interest-every-call.md) | Every balance update applies one month; no idempotency guard |
| [0009](0009-plan-types-as-data-in-a-sealed-class.md) | Plan types are data in a sealed class |
