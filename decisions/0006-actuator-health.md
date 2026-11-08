# 0006: Actuator health exposure and the dev profile

- Status: Accepted
- Date: 2026-11-08

## Context
`/actuator/health` is how Docker, Compose and later orchestration will judge the
service. Its details can leak internals; the disk-space check, for example,
exposes the host filesystem path. There is no Spring Security in the project.

## Decision
- `management.endpoint.health.show-details=when-authorized` in
  `application.properties` (hidden by default).
- `show-details=always` only in `application-dev.properties`. The `dev` profile
  is activated by `bootRun` only, via `systemProperty` in `build.gradle.kts`, so
  tests and packaged builds don't get it. (A profile file cannot activate
  itself.)
- The readiness group includes the database:
  `management.endpoint.health.group.readiness.include=readinessState,db`.
  Liveness stays app-only.

## Consequences
- With Postgres down, `/actuator/health` and readiness return 503 while liveness
  stays 200 UP: not ready, but restarting wouldn't help, so not restarted.
- Locally you see per-component detail; elsewhere only the status.
- The readiness property replaces the default list, so `readinessState` must stay
  in it.
