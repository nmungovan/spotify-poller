# spotify-poller: Milestones

A Kotlin / Spring Boot service that polls Spotify's `recently-played` endpoint and stores every play in Postgres. It is the first service in a larger music-library analytics project (API, SPA, enrichment worker to follow).

Stack: Kotlin, JVM 21, Spring Boot 4.1.x, Gradle (Kotlin DSL), Postgres, Flyway, Docker Compose.

---

## Milestone 0: Setup

- [x] Generate the project from start.spring.io (Web, JDBC, Flyway, PostgreSQL, Validation)
- [x] `git init`, first commit, `.env` in `.gitignore`
- [x] Add Actuator (for `/actuator/health` later)
- [x] `compose.yaml` running Postgres; `docker compose up -d`
- [x] Configure the datasource in `application.properties`
- [x] App boots and connects to Postgres
- [x] Create a Spotify developer app; redirect URI `http://127.0.0.1:8080/callback`; secrets in `.env`

**Done when:** the app starts, Flyway connects to the DB, and nothing crashes.

## Milestone 1: Schema

- [x] `V1__create_plays.sql` migration
- [x] Uniqueness on `(user_id, track_uri, played_at)`: the natural key and the idempotency guarantee
- [x] Decide the timestamp type (`timestamptz`) and why
- [x] Decide what to store: IDs only vs. normalized tracks/artists/albums vs. raw `jsonb`
- [x] Table for the stored OAuth refresh token

**Design notes**
- Spotify gives no play ID. Identity is derived from track + played time (+ user).
- A random GUID per play is **not** idempotent. Re-polling would create duplicates. Use a surrogate PK plus a unique constraint, or a deterministic (UUIDv5) ID.
- Check whether `played_at` is the start or end of the play.

**Done when:** the migration applies cleanly and inserting the same play twice results in one row.

## Milestone 2: Spotify auth

- [x] Read up on the OAuth 2.0 authorization code flow (access vs. refresh tokens)
- [x] `/login` redirects to Spotify with the `user-read-recently-played` scope
- [x] `/callback` exchanges the code for tokens
- [x] Persist the refresh token
- [x] Function to get a fresh access token from the refresh token

**Done when:** you can authorize once and the service can mint access tokens without you.

## Milestone 3: The poller

- [x] `@Scheduled` job (every ~10-20 min; the endpoint returns at most 50 plays)
- [x] Refresh the token, call `GET /me/player/recently-played`
- [x] Use the `after` cursor from the latest stored `played_at`
- [x] `INSERT ... ON CONFLICT DO NOTHING`
- [x] Overlapping polls are harmless

**Done when:** leaving it running for a day yields a complete, duplicate-free play history.

## Milestone 4: Production-shaped

- [ ] Handle 429 rate limits (respect `Retry-After`) and expired/revoked tokens
- [ ] Structured logging
- [x] `/actuator/health`
- [x] Tests, including Testcontainers for Postgres
- [x] Dockerfile; service runs in Compose alongside Postgres
- [x] CI via GitHub Actions

**Done when:** the service survives failures without losing or duplicating data, and `docker compose up` runs the whole thing.

## Later

- Backfill from the Spotify Extended Streaming History export, feeding the same `plays` table
- Separate enrichment worker (queue + MusicBrainz/Spotify metadata)
- API service and React SPA for stats and discovery

## Caveats to verify

- Spotify has restricted parts of its Web API for new apps (e.g. audio features) and limits development-mode apps. Check the current docs before relying on a specific endpoint.
- `recently-played` is only a 50-play window. Plays are lost if the poller is down too long.
