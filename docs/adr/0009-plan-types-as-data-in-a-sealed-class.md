# Plan types are data in a sealed class

The README wants the interest design "extensible to accommodate future complexities", while `TimeDeposit` and `updateBalance` stay frozen (ADR 0001). The per-plan rules lived in a nested `if/else if` chain inside `TimeDepositCalculator`. Today every plan differs only in data: an annual rate and a range of eligible days. So the domain's `PlanType` is a sealed class whose `data object`s (`Basic`, `Student`, `Premium`) each carry a code, an annual rate and an `IntRange` of eligible days, plus one `monthlyInterest(balance, days)` that computes `balance * annualRate / 12` in the legacy order. `TimeDepositCalculator(plans: Set<PlanType> = planTypes)` owns the lookup: it builds a map by exact code from the active plans (a `Set`, so no plan is listed twice; a test keeps every code unique) and keeps the legacy `0.0 +`, HALF_UP rounding and in-place `+=`, so its results are bit-identical and the no-arg constructor still exists. A new plan is one more `data object`; a plan with genuinely different behaviour can override `monthlyInterest`.

## Considered options
- **Rules / Specification objects** (composable eligibility predicates): rejected as a poor fit; composing rule objects is overhead for three flat rules.
- **Template Method** (a base class with eligibility and rate hooks): a sound skeleton, but more types than the data needs today.
- **Strategy classes** (one class per plan): a class each for what is only a rate and a range.
- **Chain of Responsibility**: the old `if/else` dressed up as objects.
- **Decorator**: suits modifiers that change an amount, not deciding whether a plan pays at all.
- **enum**: closed to a future plan with different behaviour or extra state.

## Consequences
- The README's no-interest-in-the-first-30-days rule is repeated in every range, by choice: a new plan must include it, and `PlanTypeTest` checks that every plan's range starts at 31 or later.
- `planTypes` is a `Set` listed by hand; a test compares it with the sealed subclasses so a new plan can't be left out. It is a top-level value next to the sealed class, not a companion property: a companion that lists its own object subclasses captures `null` when a subclass is the first class touched (JVM class-init order). Outside the class there's no such cycle, so it needs neither `lazy` nor a guard test.
- Unknown or wrong-case codes find no plan and earn no interest, as before.
- Revisit when eligibility depends on something other than days, or a rate isn't flat: that plan becomes its own subclass overriding `monthlyInterest`, or the shared skeleton moves to a Template Method.
