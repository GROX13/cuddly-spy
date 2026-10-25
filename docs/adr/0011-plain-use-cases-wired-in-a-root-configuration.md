# Plain use cases wired in a root configuration

Use cases are plain Kotlin classes in `timedeposit.application`, with no Spring import: `ListTimeDeposits` takes the `TimeDepositRepository` port in its constructor and exposes `execute()`. Spring creates them in one composition root, `org.ikigaidigital.UseCaseConfiguration` (`@Configuration(proxyBeanMethods = false)`), next to `TimeDepositApplication`. Controllers in `adapter.web` depend on the use-case classes directly. The read use case has no transaction of its own: the repository fetch-joins the withdrawals and maps them inside Spring Data's transaction (ADR 0010).

## Considered options
- **Inbound port interfaces** (`ListTimeDepositsUseCase` implemented by a service): every use case has exactly one implementation, so the interface would be a hypothetical seam. Tests already swap the outbound port with an in-memory fake, which is the seam that varies.
- **`@Service` on the use cases**: less wiring, but it couples the core to Spring and lets component scanning decide what exists.
- **The configuration in a `timedeposit.config` package**: a reasonable home. The root package was chosen instead, next to the application class that already owns the Spring wiring. The locked `ArchitectureTest` constrains only `TimeDeposit` and `TimeDepositCalculator` there.

## Consequences
- `CoreIsSpringFreeArchitectureTest` fails the build if `timedeposit.application` or `timedeposit.domain` depends on anything under `org.springframework`, including a stray `@Service`.
- Each new use case needs one `@Bean` method in `UseCaseConfiguration`. A `@WebMvcTest` slice imports that configuration plus a fake port, so the real wiring is tested without mocking final classes.
- Use cases are unit-tested with plain constructors and an in-memory port; no Spring context is needed.
