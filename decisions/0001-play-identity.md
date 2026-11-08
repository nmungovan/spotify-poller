# 0001: Play identity is (user, track_uri, played_at)

- Status: Accepted
- Date: 2026-11-08

## Context
Spotify's `recently-played` endpoint gives no ID for a play. The poller will
re-read overlapping windows (it returns at most 50 plays), so the same play will
arrive more than once. Inserts must be idempotent.

Local files have `id: null` in Spotify's playlist docs. We could not confirm the
shape for `recently-played`, so we assume the worst. Postgres treats NULLs as
distinct in a `UNIQUE` constraint, so a key containing a nullable `track_id`
would silently allow duplicates for those rows.

## Decision
- Surrogate primary key: `id bigint GENERATED ALWAYS AS IDENTITY`.
- Natural key: `CONSTRAINT plays_natural_key UNIQUE (user_id, track_uri, played_at)`.
- `track_uri` is `NOT NULL` and always present (including `spotify:local:...`),
  so it is used instead of `track_id`, which is nullable.

## Alternatives considered
- **Random UUID per play:** not idempotent; re-polling creates duplicates.
- **Deterministic UUIDv5 as PK:** works, but the constraint is simpler and
  states the rule where the data lives.
- **Key on `track_id` with `UNIQUE NULLS NOT DISTINCT`:** works on Postgres 15+,
  but needs a nullable column in the key.
- **Skip local files:** a deliberate data loss we didn't want.

## Consequences
- The poller inserts with `ON CONFLICT (user_id, track_uri, played_at) DO NOTHING`.
  The conflict target must match the constraint's columns.
- Two genuine plays of one track at the identical millisecond would collapse to
  one row. We accept this.
- `track_id` stays as a nullable column for enrichment joins on regular tracks.
