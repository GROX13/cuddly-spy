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

When Docker isn't running, the integration tests are reported as skipped and the build still passes.

## Running the API / Swagger

Docker must be running. From the repo root:

```sh
mvn -f kotlin/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile starts Postgres from `kotlin/compose.yaml`, applies the Liquibase changelogs and seeds one time deposit per plan type. Stopping the app stops the container. Other profiles leave the tables empty and don't start compose.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI document: http://localhost:8080/v3/api-docs

## AI-assisted development

See [`docs/ai-harness.md`](../docs/ai-harness.md).
