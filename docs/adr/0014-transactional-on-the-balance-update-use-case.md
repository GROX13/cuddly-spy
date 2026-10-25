# @Transactional on the balance-update use case

Supersedes ADR 0012.

Amended by [ADR 0016](0016-one-port-implemented-by-a-jpa-class.md): the annotation now sits on `TimeDepositService`, which also carries `@Transactional(readOnly = true)` on `listAll`, and the tests are `TimeDepositServiceTransactionIT` and `TimeDepositServiceTest`.

Updating balances still has to lock the rows, run the frozen calculator, save the balances and re-read the list in one transaction (ADR 0010, ADR 0012). ADR 0012 did that through a Spring-free `Transactions` port with a `TransactionTemplate` adapter, because the core had to stay Spring-free (ADR 0011). On review, the repo owner chose Spring's `@Transactional` on `UpdateAllBalances` instead. It's one annotation in place of a port, an adapter, a test fake and their tests, and it is the idiomatic way to declare a transaction in a Spring application. The cost is that the application layer now depends on one Spring package, `org.springframework.transaction.annotation`. Everything else in the core stays Spring-free.

The annotation sits on the class, not the method. The Kotlin `spring` compiler plugin opens classes annotated with `@Transactional`, and Spring needs an open class to build the proxy. Spring applies it to the bean created in `UseCaseConfiguration`, which is not component-scanned.

## Considered options
- **Keep the `Transactions` port (ADR 0012)**: keeps the core fully Spring-free and the boundary visible in the code, at the cost of about 40 lines and an extra concept.
- **`@Transactional` on the controller method**: no Spring in the core, but the web adapter would own the boundary, and a second caller would have to remember it.

## Consequences
- `CoreIsSpringFreeArchitectureTest` allows exactly `org.springframework.transaction.annotation..` in the application layer; the domain stays fully Spring-free.
- `UpdateAllBalancesTransactionIT` checks the use case runs through a transactional proxy, and rolls back every saved balance when it fails after saving. Without the annotation, every balance update fails with `No active transaction` (the row lock needs one).
- `UpdateAllBalancesTest` checks only the order of the port calls. A plain unit test can't see the transaction, so the IT covers it.
- Spring's proxy rules apply: a call from inside the same class bypasses the transaction. `execute()` is only called from outside, by the controller.
