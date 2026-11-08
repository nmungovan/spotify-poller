# 0002: Typed columns plus raw jsonb

- Status: Accepted
- Date: 2026-11-08

## Context
`recently-played` only returns the last 50 plays. If the poller is down too long,
plays are lost and can't be re-fetched, so whatever we ingest is the only copy.
Each item is large and redundant (hrefs, external URLs and image URLs are all
derivable from IDs).

## Decision
`plays` stores a handful of typed columns for querying (`track_uri`, `track_id`,
`played_at`, `track_name`, `duration_ms`, `isrc`, `context_type`, `context_uri`)
plus the full item as `raw jsonb NOT NULL`.

## Alternatives considered
- **IDs only:** small, but anything not extracted is gone for good.
- **Normalized tracks/artists/albums tables now:** forces modelling problems
  (multiple artists per track; `release_date` can be just a year) before we
  need them, and ingestion becomes lossy if the model is wrong.

## Consequences
- The later enrichment worker can read everything from `raw`, and normalize then.
- Multiple artists and `release_date` precision are intentionally deferred.
- Storage is duplicated and larger than a normalized model. At personal scale
  (a few hundred plays a day) this is negligible.
- Typed columns and `raw` must be populated from the same item at ingestion.
