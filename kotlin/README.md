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

Docker must be running. Without it the integration tests are skipped, and the build then fails on the persistence adapter's coverage gate, which only those tests reach. That also means the pre-commit hook rejects Kotlin changes while Docker is down.

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

**Every call adds another month.** There is no once-per-month guard, so calling it twice pays interest twice (ADR 0008). With the local seed, the first call takes basic 1500.00 to 1501.25, student 2000.00 to 2005.00 and premium 6000.00 to 6025.00; the other three earn nothing. Concurrent calls run one after another, so none is lost (ADR 0012). Restart with fresh balances with `docker compose -f kotlin/compose.yaml down -v`.

## AI-assisted development

See [`docs/ai-harness.md`](../docs/ai-harness.md).
