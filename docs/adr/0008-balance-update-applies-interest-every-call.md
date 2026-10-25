# Every balance update applies one monthly interest, with no idempotency guard

The README asks for an endpoint that updates all balances but doesn't say what a repeated call should do. Each call is one monthly run over all time deposits. It reads the cents stored by the previous call, adds one month of interest and stores the result; `days` never changes, just as `updateBalance` never changes it. There is no record of past runs and no check for a second call in the same month: a guard would need a runs table the README's schema doesn't have.

## Consequences
- Whoever calls the endpoint decides when a month has passed; calling it twice in a month pays interest twice.
- Because cents are stored between calls, repeated endpoint calls can differ from calling the frozen calculator repeatedly on one Double. After 24 calls from 17.77 (basic, day 100) the endpoint gives 18.01, against the calculator's 18.02; `BalanceUpdatesApiIT` pins the 24-call results for all three plans.
- Adding a once-per-month guard later would change API results and need new state, so it would be a new decision.
