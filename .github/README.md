# XA Bank time deposits: Kotlin solution

A solution to the [XA Bank time-deposit kata](../README.md), built with AI coding agents. Spring Boot 4, Kotlin, PostgreSQL, Liquibase, springdoc and Testcontainers.
- **Two endpoints:** `GET /time-deposits` lists every time deposit with its withdrawals; `POST /time-deposits/balance-updates` applies one month of interest to all of them.
- **Untouched contract:** the original `TimeDeposit` and `TimeDepositCalculator.updateBalance` keep their package, shape, signature and behaviour.

The assignment text is kept unchanged in the root [`README.md`](../README.md).

## Quick start

Requires JDK 17, Maven 3.9+ and a running Docker.

```sh
mvn -f kotlin/pom.xml verify                                             # unit, architecture and Postgres integration tests, coverage gate
mvn -f kotlin/pom.xml spring-boot:run -Dspring-boot.run.profiles=local  # app with a seeded local Postgres
```

Then open Swagger UI at <http://localhost:8080/swagger-ui/index.html> and try **Time deposits**: list them, run a balance update, and list them again. The curl versions and the seeded results are in [`kotlin/README.md`](../kotlin/README.md).

## Where to look

- [`kotlin/README.md`](../kotlin/README.md): running the API, the assumptions made where the assignment is silent, and a code tour.
- [`docs/adr/`](../docs/adr/README.md): nine short design decisions.
- [`docs/ai-harness.md`](../docs/ai-harness.md): the AI setup, how to reproduce it, its guardrails and limits, and which parts were AI-assisted.
- [`AGENTS.md`](../AGENTS.md): the rules every agent followed.

## Key decisions

- **The legacy calculator is a frozen contract (ADR 0001).** Its internals now look plan types up by code. Four checks prove the results are bit-identical: characterization tests, edge cases from upstream, a differential test against a copy of the upstream code on about 220k inputs, and 100% coverage.
- **Plan types are data (ADR 0009).** A plan type is a code, an annual rate and a range of eligible days in a sealed class; a new plan type is one more object.
- **Balances stay exact (ADR 0002).** Money is `NUMERIC(19,2)`. The calculator's `Double` is used only as its input: the stored decimal gets the calculator's change, rounded to cents.
- **No lost months (ADR 0007).** A balance update locks the rows, calculates, saves and re-reads in one transaction, so concurrent calls run one after the other.
- **Hexagonal in one module (ADR 0006).** Domain, application and adapters live in separate packages, and ArchUnit tests enforce the dependency rules.

## Trade-offs and next steps

- **Sealed class vs enum:** an enum would serve today's three plans equally well and remove the hand-kept plan list. The sealed class only pays off once a plan needs its own fields.
- **Two read models:** `TimeDepositSnapshot` (the input to an update) and `TimeDepositStatement` (the output) could be merged.
- **The 100% coverage gate:** it guards the calculator well, but it also makes the adapters need trivial tests and makes every commit need Docker. 100% on the calculator and a lower bar elsewhere would be more proportionate.
- **Very large balances:** above 2^46 (about 7×10¹³) an updated balance can be off by cents, and near 10^17 the column overflows. Both are documented rather than handled (ADR 0002).
- **Every call is a month (ADR 0008):** there's no once-per-month guard, because that would need a runs table the specified schema doesn't have.
