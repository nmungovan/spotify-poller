package com.nmungovan.spotifypoller

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class PlayRepository(private val jdbc: JdbcClient) {

    /**
     * Stores a play unless the same (user, track, time) is already there (see decisions/0001).
     * Returns the number of rows inserted: 1 for a new play, 0 for a duplicate.
     */
    fun insertIgnoringDuplicates(userId: String, play: Play): Int =
        jdbc.sql(
            """
            INSERT INTO plays
              (user_id, track_uri, track_id, played_at, track_name, duration_ms,
               isrc, context_type, context_uri, raw)
            VALUES
              (:userId, :trackUri, :trackId, :playedAt, :trackName, :durationMs,
               :isrc, :contextType, :contextUri, CAST(:raw AS jsonb))
            ON CONFLICT (user_id, track_uri, played_at) DO NOTHING
            """.trimIndent()
        )
            .param("userId", userId)
            .param("trackUri", play.trackUri)
            .param("trackId", play.trackId)
            .param("playedAt", play.playedAt)
            .param("trackName", play.trackName)
            .param("durationMs", play.durationMs)
            .param("isrc", play.isrc)
            .param("contextType", play.contextType)
            .param("contextUri", play.contextUri)
            .param("raw", play.raw.toString())
            .update()

    /** The poller's cursor: the newest stored play for this user, or null if there are none yet. */
    fun latestPlayedAt(userId: String): OffsetDateTime? =
        jdbc.sql("SELECT max(played_at) FROM plays WHERE user_id = :userId")
            .param("userId", userId)
            .query(OffsetDateTime::class.java)
            .optional()
            .orElse(null)
}
