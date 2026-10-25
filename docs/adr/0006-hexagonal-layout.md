# Hexagonal layout in one module, checked by ArchUnit

The README asks for hexagonal architecture. `org.ikigaidigital.timedeposit` has three layers:
- **`domain`** is plain Kotlin: `PlanType`, `TimeDepositStatement` and `Withdrawal`.
- **`application`** holds `TimeDepositService`, with one method per endpoint, and its outbound port `TimeDepositRepository`.
- **`adapter`** has two parts:
  - `web`: the controller and response DTOs;
  - `persistence`: `JpaTimeDepositAdapter`, which implements the port by mapping JPA entities to application values and delegating queries to `SpringDataTimeDepositRepository`.

`ApplicationConfiguration` is the composition root: it creates the calculator and the service as plain beans, so the core carries no stereotype annotations. There are no inbound port interfaces, since each would have exactly one implementation. The seam that varies is the outbound port, which the service and web-slice tests replace with `InMemoryTimeDepositRepository`.

## Considered options
- **Spring Data interface extending the port, with mapping in default methods**: tried first. It tangled two interfaces into one type, forced port names to avoid Spring Data's verbs and needed `@JvmDefaultWithoutCompatibility`.
- **An adapter over `EntityManager`**: tried second. It worked, but hand-wrote what Spring Data generates.
- **The service using Spring Data directly**: the application would depend on an adapter.
- **Maven modules per layer**: compile-time boundaries, but one `verify` and one coverage config were worth more at this size.

## Consequences
- `ArchitectureTest` (read-only for agents) and `CoreIsSpringFreeArchitectureTest` fail the build when the domain or the legacy classes depend on Spring, JPA or adapters, when the application depends on adapters, or when the two adapters depend on each other. The application may use only `org.springframework.transaction.annotation` (ADR 0007).
- `@Repository` on the adapter adds Spring's exception translation; an unknown id arrives as `InvalidDataAccessApiUsageException`.
