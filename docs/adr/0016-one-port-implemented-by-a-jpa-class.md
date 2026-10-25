# One persistence port implemented by a JPA class

Supersedes ADR 0010. Amends ADR 0011 and ADR 0014.

ADR 0010 made the Spring Data repository `TimeDepositJpaRepository` extend the application's port `TimeDepositRepository`, with the port's methods as Kotlin default methods on top of declared `@Query` methods. On review, the repo owner disliked the result: two repository interfaces tangled into one type, port methods mixed with Spring Data query methods, a naming rule to avoid Spring Data's verbs, and `@JvmDefaultWithoutCompatibility` to make it compile cleanly.

The port stays. The README asks to "Embrace Hexagonal Architecture", the locked `ArchitectureTest` forbids the application layer from depending on an adapter, and `InMemoryTimeDepositRepository` fakes the port in the service and web-slice tests. `TimeDepositRepository` keeps its three methods and their semantics.

It is now implemented by one hand-written class, `JpaTimeDepositRepository` in `adapter.persistence`: `@Repository`, constructed with JPA's `EntityManager` (the shared, transaction-bound proxy Spring's `LocalContainerEntityManagerFactoryBean` exposes for injection).
- `loadAllWithWithdrawals` runs the same fetch-join JPQL as before and maps straight to `TimeDepositStatement` with the exact stored balance (ADR 0015).
- `loadAllForUpdate` runs `select d from TimeDepositEntity d order by d.id` with `LockModeType.PESSIMISTIC_WRITE`, which Hibernate 7.4 still issues as `FOR NO KEY UPDATE` on PostgreSQL.
- `saveBalances` gets each managed entity with `entityManager.find` and sets `balanceAfterUpdate(stored, stored.toDouble(), balance)`. An unknown id fails with `IllegalArgumentException`, which `@Repository` exception translation turns into `InvalidDataAccessApiUsageException`, as Spring Data's proxy did.

At the owner's request, the two use cases `ListTimeDeposits` and `UpdateAllBalances` are merged into one class, `TimeDepositService` (`listAll()`, `updateAllBalances()`), and the composition root `UseCaseConfiguration` is renamed `ApplicationConfiguration`. It wires one `timeDepositService` bean next to `timeDepositCalculator`. The service keeps the class-level `@Transactional` (the Kotlin `spring` plugin opens only classes that carry it, and the proxy needs an open class), and `listAll` adds `@Transactional(readOnly = true)`: Spring Data's own transaction around the query is gone, so the read needs one from the service.

## Considered options
- **Keep the Spring Data interface extending the port (ADR 0010)**: no hand-written class, but it is the tangle the owner rejected.
- **The port, an adapter class and a private Spring Data interface behind it**: the adapter delegates to Spring Data for the queries. It untangles the types, but there are still two interfaces for three queries, and the adapter adds a layer only to call them.
- **No port; the service uses the JPA repository directly**: one type fewer, but the application would depend on an adapter, which the locked `ArchitectureTest` rejects, and the in-memory fake would have nothing to implement.

## Consequences
- One interface (the port) and one class implement persistence. The port's verbs no longer need to avoid Spring Data's names.
- Every adapter method joins the caller's transaction. Both service methods open one, so a call through the controller always runs inside it.
- `@DataJpaTest` doesn't scan `@Repository` classes or apply exception translation, so `TimeDepositRepositoryIT` imports the class and `PersistenceExceptionTranslationAutoConfiguration`. `ApplicationIT` checks the translation in the full application.
- Spring Boot 4 has no JPA starter without Spring Data, so `spring-boot-starter-data-jpa` stays as the source of Hibernate and Spring's JPA support. Its repository scanning finds nothing and does no harm.
- `TimeDepositServiceTest` covers both methods with the in-memory port, and `TimeDepositServiceTransactionIT` keeps the proxy and rollback checks.
