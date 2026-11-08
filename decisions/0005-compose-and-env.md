# 0005: Local DB via Compose, config via .env

- Status: Accepted
- Date: 2026-11-08

## Context
We need a local Postgres, and a place for credentials that does not end up in git.

## Decision
- `compose.yaml` runs `postgres:17`. The version is pinned to match the tests
  (see 0007), not `latest`.
- Spring Boot's Docker Compose support reads `compose.yaml` and builds the
  datasource (including the mapped port) at `bootRun`, so
  `application.properties` has no datasource entries.
- DB name, user and password come from `.env` through `${VAR:?message}` in
  `compose.yaml`, so a missing variable fails loudly.
- `.env` is gitignored. `.env.example` is committed with placeholder values.
  Spotify client ID/secret follow the same pattern.
- The host port is not fixed (`ports: - '5432'`); Spring looks it up.

## Consequences
- A fresh clone needs `cp .env.example .env` before `docker compose up`.
- Postgres reads `POSTGRES_*` only when it first initializes its data
  directory. After changing them, run `docker compose down -v` (this deletes the
  data) or you get auth failures against the old values.
- Connecting a DB client needs the current port from `docker compose ps`.
- Compose support is skipped in tests by default; tests use Testcontainers.
