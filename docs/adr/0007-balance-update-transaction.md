# One transaction with row locks for the balance update

A balance update must not lose a month when two calls overlap, and its response must show exactly the state it produced. `TimeDepositService.updateAllBalances` runs four steps in one `@Transactional` call:
1. lock every row (`select … order by id` with `PESSIMISTIC_WRITE`, which Hibernate issues as `FOR NO KEY UPDATE` on PostgreSQL);
2. run the frozen calculator;
3. assign the changed balances to the managed entities;
4. re-read the list.

A concurrent update waits for the locks, then reads the committed balances. Any exception rolls back every balance.

## Considered options
- **A Spring-free `Transactions` port** with a `TransactionTemplate` adapter: tried. It kept the application free of Spring, but cost a port, an adapter, a test fake and their tests for what one annotation declares.
- **`@Transactional` on the controller**: the web adapter would own the boundary, and any other caller would have to remember it.
- **Optimistic locking with a version column**: needs a column the README doesn't define, plus retry handling.

## Consequences
- `org.springframework.transaction.annotation` is the only Spring dependency the application layer may have.
- The annotation sits on the class: the Kotlin `spring` plugin opens only annotated classes, and the proxy needs an open class. `listAll` is `readOnly`. Calls from inside the class would bypass the proxy, so the two methods don't call each other.
- Rows are locked in id order, so two updates can't deadlock each other.
- Tests:
  - `TimeDepositServiceTransactionIT` checks rollback after the UPDATEs have been flushed;
  - `TimeDepositRepositoryIT` checks lock exclusion with `NOWAIT`;
  - `BalanceUpdatesApiIT` checks that concurrent HTTP calls add exactly two months.
