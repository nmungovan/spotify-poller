# 0004: Foreign key uses ON DELETE RESTRICT

- Status: Accepted
- Date: 2026-11-08

## Context
`plays.user_id` references `spotify_auth(user_id)`. We had to choose what happens
when an auth row is deleted (for example when a user disconnects).

## Decision
`CONSTRAINT plays_user_fk FOREIGN KEY (user_id) REFERENCES spotify_auth (user_id) ON DELETE RESTRICT`.
Deleting an auth row that still has plays fails.

## Alternatives considered
- **CASCADE:** convenient, but silently deletes accumulated history, which is the
  product.
- **SET NULL:** impossible; `user_id` is `NOT NULL` and part of the natural key.

## Consequences
- A plays row can't exist without a `spotify_auth` row, so tests and the OAuth
  callback must create the auth row first.
- Disconnecting a user needs an explicit decision about their history.
- Cheap to reverse: add a `V2__` migration that drops and re-creates
  `plays_user_fk`. Never edit `V1` after it has been applied (Flyway checksum).
