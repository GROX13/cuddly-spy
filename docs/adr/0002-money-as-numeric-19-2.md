# Schema and money: exact decimals around the Double calculator

The README lists the tables as `timeDeposits` and `withdrawals` with camelCase columns. We read that as formatting: Liquibase creates unquoted snake_case names (`time_deposits`, `plan_type`, `time_deposit_id`), Hibernate maps them by Boot's default naming and only validates the schema. Money columns are `NUMERIC(19,2)`.

The frozen `TimeDeposit.balance` is a `Double`, which can't hold every cent of a large balance. So the Double is only the calculator's input. Both endpoints return the stored decimal (`TimeDepositStatement`). A balance update stores `stored + (after − before)`, where `before` is the Double given to the calculator and `after` the one it returned; the change is rounded HALF_UP to cents, which only removes binary noise. A deposit whose balance didn't change is not written.

## Consequences
- Below 2^45 (about 35 trillion) the stored result equals upstream's balance rounded to cents (`TimeDepositServiceUpstreamBalancesTest`).
- Between 2^45 and 2^46 it equals the stored balance plus upstream's rounded interest.
- Above 2^46 the change can be off by cents, up to 8.00 near 10^17. Writing the Double back would be worse there: about 24.
- An earning balance close to `99999999999999999.99` overflows the column. The update returns 500 and rolls back every balance (ADR 0007). That much money in one account is out of scope for a currency like EUR or USD.

## Considered options
- **Write the calculator's Double back, rounded to cents**: simpler, but a non-earning deposit at 90071992547409.91 would come back as .90, and one at the column's maximum would fail with an overflow.
