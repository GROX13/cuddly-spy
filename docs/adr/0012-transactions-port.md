# The use case owns its transaction through a Transactions port

Updating balances must lock the rows, run the frozen calculator, save the balances and re-read the list in one transaction: `loadAllForUpdate` holds its `FOR NO KEY UPDATE` row lock only until the transaction ends (ADR 0010), and the response must show exactly the state this call produced. The core is Spring-free (ADR 0011), so `UpdateAllBalances` can't carry `@Transactional`. It states the boundary itself through an outbound port in `timedeposit.application`, `Transactions.inTransaction(block)`, a plain interface. `TransactionTemplateTransactions` in `adapter.persistence` implements it with Spring's `TransactionTemplate`: one read-write `REQUIRED` transaction that commits when the block returns and rolls back on an unchecked exception. The repository's declared queries and default methods have no transaction attributes of their own, so they join it, and the dirty balances flush at commit.

## Considered options
- **`@Transactional` on the controller method**: works, but the web adapter would own a persistence concern, and a second caller of the use case (a scheduler) would have to remember to add it.
- **A transactional decorator around the use case in the composition root**: keeps the core clean, but the boundary is invisible where the use case is read, and the unit tests couldn't see which calls run inside it.
- **`@Transactional` on the use case**: not possible without breaking `CoreIsSpringFreeArchitectureTest`.

## Consequences
- Lock, calculate, save and re-read happen in one transaction. A concurrent update waits for the lock and then reads the committed balances, so two concurrent calls add exactly two months (`BalanceUpdatesApiIT`); without the lock, that test loses a month.
- An exception anywhere in the block rolls back every balance written so far (`TransactionTemplateTransactionsIT`).
- `UpdateAllBalancesTest` uses a fake `Transactions` that records `begin` and `commit`, so the order of the port calls and their place inside the boundary are unit-tested without Spring.
- Further write use cases take the same port; reads like `ListTimeDeposits` don't need it.
