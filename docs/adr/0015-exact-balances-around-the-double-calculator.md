# Exact balances around the Double calculator

Amends ADR 0002 and ADR 0010.

Balances are stored as exact `NUMERIC(19,2)` decimals (ADR 0002), but until now every balance passed through the frozen `TimeDeposit.balance: Double` (ADR 0001), on reads as well as on writes. A Double can't hold every cent of a large balance, so the stored value changed even when nothing should have:

- **Lost cents.** A basic deposit on day 0 with balance `90071992547409.91` becomes the Double `90071992547409.90625` (ulp 1/64). `GET /time-deposits` showed `90071992547409.90`, and a balance update wrote `.90` back, although that deposit earns no interest.
- **A 500 on a deposit that earns nothing.** At `99999999999999999.99`, the column's maximum, the Double rounds to `1e17`. `GET` showed `100000000000000000.00`, and the balance update failed with a numeric overflow and returned 500.

The read model now carries the stored decimal itself and never goes through a Double. It was also renamed from `TimeDepositWithWithdrawals` to `TimeDepositStatement`: like a bank statement, it is a balance plus its movements (the withdrawals). Older ADRs keep the old name as history. `TimeDeposit` exists only as the calculator's input, so the frozen contract is untouched.

The write keeps the stored decimal and adds only the change the calculator made: `balanceAfterUpdate(stored, before, after)` returns `stored` plus the change `after - before` rounded HALF_UP to cents (via `BigDecimal.valueOf`, so 2.675 gives 2.68), or `stored` itself when the change is zero. The persistence adapter takes `before` from the same managed, locked entity it loaded for the calculator, so it is exactly the Double the calculator was given. An unchanged balance is the same instance, so Hibernate issues no UPDATE for it. The subtraction is exact (Sterbenz lemma), because one month's interest keeps `after` within a factor of two of `before`.

## How close it stays to the legacy result
Measured in Python by simulating binary64 over random balances for all three plan types; `R` is the interest the legacy calculator rounds to cents:
- While the updated balance stays below 2^45 (about 35.2 trillion), the new write is identical to the old one, `BigDecimal.valueOf(after).setScale(2, HALF_UP)`.
- Below 2^46 (about 70.4 trillion), it is exactly the stored balance plus `R`. Between 2^45 and 2^46 the old write could already be one cent off, because it rounded two Doubles (the loaded balance and the result).
- Above 2^46, the change can differ from `R` by half an ulp of the new balance, and rounding to cents adds at most half a cent. That is at most one cent while the updated balance stays below 2^47 (about 140.7 trillion), and it grows with the balance, up to 8.00 just below 10^17 (ulp 16). The old write was worse at those sizes: near 10^17 its error reaches about 24, because it rounds both the loaded balance and the result.

## The overflow limit
An earning balance near 10^17 can still pass `99999999999999999.99` and overflow the column. The UPDATE then fails at flush, the whole balance update returns 500, and the transaction rolls back every balance in the batch (ADR 0014). The repo owner accepts this: about 10^17 in a currency like USD or EUR is unreasonably large for a single account. In a weak currency such a balance could be plausible, and this decision would have to be revisited.

## Considered options
- **Skip unchanged deposits and add a range check constraint**: stops the 500 on a non-earning deposit, but GET still shows the Double's rounding, an earning deposit still loses or gains the cents its Double can't hold, and the constraint only moves the overflow into a different error.
- **Skip unchanged deposits only**: fixes both reproductions for deposits that earn nothing, but an earning large deposit is still rewritten from its Double, and GET still shows the rounded value.

## Consequences
- `TimeDepositStatement` has its own fields (`id`, `planType`, `balance: BigDecimal`, `days`, `withdrawals`); the web adapter sends `balance` as stored.
- `saveBalances` writes nothing for a deposit whose balance did not change.
- `BalanceUpdatesApiIT` replays both reproductions over HTTP and SQL, and pins the overflow limit: an earning deposit at the maximum returns 500 and leaves every balance unchanged.
