# Every balance update applies one monthly interest, with no idempotency guard

The README asks for an endpoint that updates all balances but doesn't say what a repeated call should do. Each call is one monthly run over all time deposits, with no record of past runs and no check for a second call in the same period. It calculates interest from the exact cents persisted by the previous call, then persists the new result to cents. The frozen `updateBalance` still adds one rounded monthly interest to its current `Double` balance and never changes `days`; its behavior and signature are unchanged. Because the endpoint stores cents between calls, repeated endpoint updates can differ from running the legacy calculator repeatedly without persistence rounding. For basic 17.77 at day 100, after 24 calls those results are 18.01 and 18.02 respectively; student 17.08 gives 18.05 and 18.04; premium 3.37 gives 3.62 and 3.61. The endpoint follows persisted-cents calculation, and `BalanceUpdatesApiIT` pins its 24-call results. We chose no once-per-month guard because it needs no extra state beyond the README's schema.

## Consequences
- Whoever calls the endpoint decides when a month has passed; calling it twice in one month pays interest twice.
- Endpoint compounding uses the balance rounded and persisted after each call; it is not numerically identical to uninterrupted repeated calls to the frozen calculator.
- Adding an idempotency guard later would change API results and need a runs table, so it would be a new decision.
