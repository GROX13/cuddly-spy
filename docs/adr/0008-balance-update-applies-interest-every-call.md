# Every balance update applies one monthly interest, with no idempotency guard

The README asks for an endpoint that updates all balances but doesn't say what a repeated call should do. The frozen `updateBalance` adds one monthly interest to the current balance on every call and never changes `days`, so calling it twice compounds (basic 1000.00 at 31 days: 1000.83, then 1001.66, then 1002.49). The balance-update endpoint behaves exactly the same: each call is one monthly run over all time deposits, with no record of past runs and no check for a second call in the same period. We chose this over the earlier idea of a once-per-month guard because it keeps the API identical to the protected calculator and needs no extra state outside the README's schema. A characterization test pins the compounding.

## Consequences
- Whoever calls the endpoint decides when a month has passed; calling it twice in one month pays interest twice.
- Adding an idempotency guard later would change API results and need a runs table, so it would be a new decision.
