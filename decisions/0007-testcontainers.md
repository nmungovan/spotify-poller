# 0007: Testcontainers for integration tests

- Status: Accepted
- Date: 2026-11-08

## Context
We want tests to run against a real Postgres (Flyway migrations, constraints,
`ON CONFLICT`), not an in-memory substitute, and without depending on the dev
Compose database.

## Decision
- Tests start a throwaway `postgres:17` through a `@TestConfiguration`
  (`TestcontainersConfiguration`) with `@ServiceConnection`, so Spring wires the
  datasource to the container with no properties.
- Dependencies are `spring-boot-testcontainers` plus
  `org.testcontainers:testcontainers-postgresql` and
  `org.testcontainers:testcontainers-junit-jupiter`. Spring Boot 4.1 manages
  Testcontainers 2.x, where the artifacts have the `testcontainers-` prefix and
  `PostgreSQLContainer` lives in `org.testcontainers.postgresql` and isn't
  generic.

## Consequences
- Docker must be running to run the tests.
- On macOS with Colima (the dev setup), Testcontainers needs two env vars, set in
  `~/.zshrc`, and the Gradle daemon must be restarted after changing them:
  - `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`
  - `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` (so the Ryuk
    cleanup container can mount the socket from inside the VM)
  IDE-launched tests need the same variables.
- CI on `ubuntu-latest` needs neither, since Docker is preinstalled there.
- The first run pulls the image; later runs use the cache.
