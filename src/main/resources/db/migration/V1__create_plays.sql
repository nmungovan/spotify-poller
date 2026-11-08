-- Stores the OAuth refresh token for each connected Spotify user.
CREATE TABLE spotify_auth (
    user_id       text        PRIMARY KEY,
    refresh_token text        NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- One row per listen, as reported by Spotify's recently-played endpoint.
CREATE TABLE plays (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      text        NOT NULL,
    track_uri    text        NOT NULL, -- always present, including local files
    track_id     text,                 -- null for local files
    played_at    timestamptz NOT NULL,
    track_name   text        NOT NULL,
    duration_ms  integer     NOT NULL,
    isrc         text,
    context_type text,
    context_uri  text,
    raw          jsonb       NOT NULL, -- full item as returned by Spotify
    ingested_at  timestamptz NOT NULL DEFAULT now(),

    -- Spotify gives no play ID, so identity is derived from user + track + time.
    -- track_uri (not track_id) is used because track_id is null for local files,
    -- and Postgres treats NULLs as distinct in unique constraints.
    -- The poller relies on this for INSERT ... ON CONFLICT DO NOTHING.
    CONSTRAINT plays_natural_key UNIQUE (user_id, track_uri, played_at),

    -- RESTRICT: play history is the product, so deleting a user's auth row
    -- must not silently delete it. To change this later, add a V2 migration
    -- that drops and re-creates plays_user_fk.
    CONSTRAINT plays_user_fk FOREIGN KEY (user_id)
        REFERENCES spotify_auth (user_id) ON DELETE RESTRICT
);

-- Serves the poller's "latest played_at for this user" cursor query.
CREATE INDEX plays_user_played_at_idx ON plays (user_id, played_at DESC);

COMMENT ON COLUMN plays.played_at IS
    'Timestamp provided by Spotify for when the track was played. This appears to be the END of the play.';
