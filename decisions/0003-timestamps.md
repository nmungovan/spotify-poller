# 0003: timestamptz, and played_at is the end of a play

- Status: Accepted (the end-of-play inference is unconfirmed)
- Date: 2026-11-08

## Context
Spotify returns `played_at` as ISO 8601 UTC with millisecond precision (for
example `2026-11-08T05:37:41.487Z`). The docs say only "The date and time the
track was played", not whether that is the start or the end.

In a 5-play sample, the gap between consecutive `played_at` values was always
at least the duration of the later track, for example 121.8 s of gap against a
121.4 s track. That fits "end of play". Under "start of play" the first pair would be
impossible unless the earlier track was skipped.

The paging cursors (`before`/`after`) are the oldest/newest `played_at` as epoch
milliseconds, and `after` is exclusive.

## Decision
- All instants are `timestamptz`, which stores an absolute instant regardless of
  server or JVM timezone.
- Treat `played_at` as the **end** of the play, and say so in a column comment.
- The poller's cursor is the latest stored `played_at` converted to epoch ms.

## Consequences
- An approximate start time is `played_at - duration_ms`, but only if the
  inference holds (and skips make it an upper bound).
- Re-check this against a track we time ourselves; if it's wrong, supersede this
  record and fix the column comment in a new migration.
