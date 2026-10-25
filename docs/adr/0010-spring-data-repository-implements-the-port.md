# A Spring Data repository implements the persistence port

Hexagonal dependencies point inward (ADR 0007), so the application layer owns a plain-Kotlin port, `TimeDepositRepository` (`loadAllWithWithdrawals`, `loadAllForUpdate`, `saveBalances`), with no JPA or Spring import. It is implemented by `TimeDepositJpaRepository`, a Spring Data `Repository<TimeDepositEntity, Int>` that also extends the port. Declared `@Query` methods do the database work: a left fetch-join for the read, and a `PESSIMISTIC_WRITE` select for the update. Kotlin default methods on the same interface map entities to the domain and write balances back as `BigDecimal.valueOf(balance).setScale(2, HALF_UP)` on the locked, managed entities. Spring Data generates the implementation and runs the default methods, so there is no hand-written adapter class. JPA entities stay separate from the domain, and the frozen `TimeDeposit` is untouched (ADR 0001).

## Considered options
- **Use cases call Spring Data directly**: the application would depend on the adapter and JPA; not hexagonal, and ArchUnit rejects it.
- **A hand-written adapter class** delegating to a Spring Data repository: sound, but an extra class whose only job is the mapping.

## Consequences
- The port's verbs must avoid Spring Data's names (`findAll`, `save…`): the repository extends both interfaces, and a clash with a different return type wouldn't compile.
- The mapping lives in default methods, and Spring Data's proxy translates their exceptions: an unknown id in `saveBalances` arrives as `InvalidDataAccessApiUsageException`.
- `loadAllForUpdate` locks the rows until the caller's transaction ends, so concurrent balance updates run one after another and no monthly run is lost (pairs with ADR 0008). On PostgreSQL, Hibernate 7 issues it as `FOR NO KEY UPDATE`, which conflicts with every other write lock on the row but lets foreign-key checks through.
- Only the balance is written (`plan_type` and `days` are not updatable). Hibernate runs with `ddl-auto: validate`, because Liquibase owns the schema.
