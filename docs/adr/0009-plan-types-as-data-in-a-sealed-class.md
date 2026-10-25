# Plan types are data in a sealed class

The README wants the interest rules to be "extensible to accommodate future complexities", while `updateBalance` stays frozen (ADR 0001). The legacy `if/else` chain differed per plan only in data. So `PlanType` is a sealed class whose `data object`s (`Basic`, `Student`, `Premium`) carry a code, an annual rate and an `IntRange` of eligible days, with one `monthlyInterest(balance, days)` that keeps the legacy `balance * rate / 12` order. The calculator looks plans up by exact code and keeps the legacy `0.0 +`, HALF_UP rounding and in-place `+=`, so its results are bit-identical.

A sealed class over an enum because a future plan can be a subclass with its own state, such as balance tiers, and its own `monthlyInterest`; enum constants all share one constructor. For today's three plans an enum would work just as well. Neither is open to plans defined outside this file.

## Considered options
- **Keep the `if/else`**: fine for three flat rules, but every new plan edits the calculator's branching.
- **One Strategy class per plan**: a class each for what is only a rate and a range.
- **An enum**: equivalent today and gives `entries` for free; see above.

## Consequences
- The README's no-interest-in-the-first-30-days rule is part of every range, so `PlanTypeTest` checks that each range starts at 31 or later.
- `planTypes` is listed by hand as a top-level value, and a test compares it with the sealed subclasses. A companion property listing its own subclasses would capture `null` when a subclass is the first class loaded.
- `TimeDepositCalculator(plans = planTypes)` lets tests run with a subset of plans; production uses the default.
- Unknown or wrong-case codes find no plan and earn no interest, as before.
