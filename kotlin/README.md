# Time Deposit Kata: Kotlin

Kotlin solution to the assignment in the [root README](../README.md).

## Prerequisites

- JDK 17 (pinned in `../.java-version`; `jenv` and similar tools pick it up)
- Maven 3.9+
- Docker (for testcontainers-based tests)

## Build and test

From the repo root:

```sh
mvn -f kotlin/pom.xml verify
```

This runs the tests and the Kover coverage gate. The coverage report is at `kotlin/target/site/kover/html/index.html`.

## Running the API / Swagger

TBD: added once the endpoints are implemented.

## AI-assisted development

See [`docs/ai-harness.md`](../docs/ai-harness.md).
