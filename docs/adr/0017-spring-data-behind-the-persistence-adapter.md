# Spring Data behind the persistence adapter

Supersedes ADR 0016's direct `EntityManager` implementation; its application interface, service and transaction decisions remain in effect.

At the owner's request, Spring Data JPA now supplies database operations through `SpringDataTimeDepositRepository : JpaRepository<TimeDepositEntity, Int>`. `JpaTimeDepositAdapter` implements the application's `TimeDepositRepository`, maps entities to exact decimal snapshots and statements, and assigns supplied balances using Spring Data's `findById` and `save`. This adds a repository interface but removes handwritten query execution and direct `EntityManager` use from production code. JPA and Hibernate remain underneath Spring Data.

The two read queries are declared with `@Query`; the update read also uses `@Lock(PESSIMISTIC_WRITE)`. The existing application transaction spans lock, calculation, save and response retrieval. Lookup of previously loaded entities uses the same persistence context, and saving managed entities leaves unchanged balances clean. Integration tests verify no per-deposit SELECTs, unchanged-row behavior, ordering, locking and rollback. Monetary calculation stays in the application under ADR 0015.

The Spring Data interface does not extend the application interface or contain mapping/default methods, avoiding the coupling rejected in ADR 0010. The mapping adapter keeps JPA entities out of the application and domain.
