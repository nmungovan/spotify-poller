# Decisions

Short records of non-obvious choices and *why* they were made, so future-us
doesn't have to rediscover the reasoning. One file per decision, numbered in the
order they were made.

Don't edit an accepted record to change history. If a decision changes, add a
new record and mark the old one `Superseded by NNNN`.

## Index

| #    | Decision                                                      | Status   |
|------|---------------------------------------------------------------|----------|
| 0001 | [Play identity is (user, track_uri, played_at)](0001-play-identity.md) | Accepted |
| 0002 | [Typed columns plus raw jsonb](0002-typed-columns-plus-raw-jsonb.md)   | Accepted |
| 0003 | [timestamptz, and played_at is the end of a play](0003-timestamps.md)  | Accepted (inference unconfirmed) |
| 0004 | [Foreign key uses ON DELETE RESTRICT](0004-fk-on-delete-restrict.md)   | Accepted |
| 0005 | [Local DB via Compose, config via .env](0005-compose-and-env.md)       | Accepted |
| 0006 | [Actuator health exposure and the dev profile](0006-actuator-health.md) | Accepted |
| 0007 | [Testcontainers for integration tests](0007-testcontainers.md)         | Accepted |

## Template

```markdown
# NNNN: Title

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
What forced a choice?

## Decision
What we chose.

## Alternatives considered
What else, and why not.

## Consequences
What this costs us, and what it commits us to.
```

## Open questions

- Is storing the Spotify refresh token in plaintext acceptable? (Currently
  `spotify_auth.refresh_token` is plain `text`; no decision has been made.)
