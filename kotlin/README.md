# Time Deposit Kata: Kotlin

Kotlin solution to the assignment in the [root README](../README.md).

## Prerequisites

- JDK 17 (pinned in `../.java-version`; `jenv` and similar tools pick it up)
- Maven 3.9+
- Docker (for the local run and the integration tests)

## Build and test

From the repo root:

```sh
mvn -f kotlin/pom.xml verify
```

This runs the unit tests (Surefire), the `*IT` integration tests against a real Postgres (Failsafe and Testcontainers) and the Kover coverage gate. The coverage report is at `kotlin/target/site/kover/html/index.html`.

Docker must be running. Without it the integration tests are skipped, and the build then fails the coverage gate, because only those tests reach the persistence adapter. That also means the pre-commit hook rejects Kotlin changes while Docker is down.

## Running the API / Swagger

Docker must be running. From the repo root:

```sh
mvn -f kotlin/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile starts Postgres from `kotlin/compose.yaml`, applies the Liquibase changelogs and seeds six time deposits (an earning and a non-earning one per plan type) plus three withdrawals. Stopping the app stops the container. Other profiles leave the tables empty and don't start compose.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI document: http://localhost:8080/v3/api-docs

### List time deposits

`GET http://localhost:8080/time-deposits` returns every time deposit by id, each with its withdrawals by date:

```sh
curl -s localhost:8080/time-deposits
```

Money comes as two-decimal JSON numbers (`"balance":1500.00`), dates as ISO-8601 (`"date":"2026-03-15"`), and `withdrawals` is `[]` when there are none. You can also try it in Swagger UI under **Time deposits**.

### Update balances

`POST http://localhost:8080/time-deposits/balance-updates`, with no request body, adds one month of interest to every time deposit and returns all of them in the same shape as the list:

```sh
curl -s -X POST localhost:8080/time-deposits/balance-updates
```

**Every call adds another month.** There is no once-per-month guard, so calling it twice pays interest twice (ADR 0008). With the local seed, the first call takes basic 1500.00 to 1501.25, student 2000.00 to 2005.00 and premium 6000.00 to 6025.00; the other three earn nothing. Concurrent calls run one after another, so none is lost: each call locks the rows in its own transaction (ADR 0007). Restart with fresh balances with `docker compose -f kotlin/compose.yaml down -v`.

## Assumptions

Where the assignment is silent, these choices were made; each links to its reasoning.

- **Table and column names are snake_case** (`time_deposits`, `plan_type`, `time_deposit_id`). The README's camelCase is read as formatting, not as quoted identifiers (ADR 0002).
- **Withdrawals are history.** They are returned by `GET`, but they are already reflected in the balance, so they are never deducted again and don't affect interest (ADR 0003).
- **One call is one month.** `POST` is not idempotent, and `days` never changes, just as the frozen calculator never changes it (ADR 0008).
- **Unknown or wrong-case plan types earn nothing,** exactly as the original calculator behaves.
- **No input or error handling beyond the framework defaults,** since the assignment doesn't require it. An overflow returns 500 and rolls back the whole update.

## Code tour

Paths are under `src/main/kotlin/org/ikigaidigital/` unless they start with `src/`:

| Where | What |
|---|---|
| `TimeDeposit.kt`, `TimeDepositCalculator.kt` | The frozen legacy contract, in its original package (ADR 0001) |
| `timedeposit/domain/PlanType.kt` | The interest rules as data: code, annual rate, eligible days (ADR 0009) |
| `timedeposit/application/TimeDepositService.kt` | The two use cases: list, and lock-calculate-save-re-read in one transaction (ADR 0007) |
| `timedeposit/application/TimeDepositRepository.kt` | The outbound port |
| `timedeposit/adapter/web/` | Controller and response DTOs with the OpenAPI annotations |
| `timedeposit/adapter/persistence/` | JPA entities, the Spring Data repository and the adapter that maps them (ADR 0006) |
| `ApplicationConfiguration.kt` | Composition root: creates the calculator and the service |
| `src/main/resources/db/changelog/` | Liquibase schema, plus the local-only seed |

The tests that guard the frozen calculator are `TimeDepositCalculatorTest` (characterization), `TimeDepositCalculatorLegacyEdgesTest` and `TimeDepositCalculatorDifferentialTest` (against a copy of the upstream code). The `*IT` classes run against real Postgres.

## Limits

Balances are `NUMERIC(19,2)`, so the largest storable balance is `99999999999999999.99`. A deposit that earns no interest keeps its balance to the cent at any size, but an earning balance close to that maximum can overflow the column: the balance update then returns 500 and rolls back every balance. That is about 10^17 in one time deposit, which is unreasonably large in a currency like USD or EUR (ADR 0002).

## AI-assisted development

See [`docs/ai-harness.md`](../docs/ai-harness.md).
