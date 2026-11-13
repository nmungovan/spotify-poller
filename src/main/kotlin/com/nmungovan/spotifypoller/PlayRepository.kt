package com.nmungovan.spotifypoller

import com.nmungovan.spotifypoller.types.DayCount
import com.nmungovan.spotifypoller.types.Play
import com.nmungovan.spotifypoller.types.RecentPlay
import com.nmungovan.spotifypoller.types.TrackCount
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

    /** The user's most recent plays, newest first. Selects only what the stats page shows. */
    fun recentPlays(userId: String, limit: Int): List<RecentPlay> =
        jdbc.sql(
            """
            SELECT track_name, played_at
            FROM plays
            WHERE user_id = :userId
            ORDER BY played_at DESC
            LIMIT :limit
            """.trimIndent()
        )
            .param("userId", userId)
            .param("limit", limit)
            .query(RecentPlay::class.java)
            .list()
            .filterNotNull()  // JdbcClient returns platform types; rows are never null

    /** Most-played tracks since [since]. Grouped by URI so same-named tracks stay separate. */
    fun topTracks(userId: String, since: OffsetDateTime, limit: Int): List<TrackCount> =
        jdbc.sql(
            """
            SELECT track_name, count(*) AS plays
            FROM plays
            WHERE user_id = :userId AND played_at >= :since
            GROUP BY track_uri, track_name
            ORDER BY plays DESC, track_name
            LIMIT :limit
            """.trimIndent()
        )
            .param("userId", userId)
            .param("since", since)
            .param("limit", limit)
            .query(TrackCount::class.java)
            .list()
            .filterNotNull()  // JdbcClient returns platform types; rows are never null

    /** Plays per calendar day since [since], newest day first. Days are cut in [zone], not UTC. */
    fun playsPerDay(userId: String, since: OffsetDateTime, zone: String): List<DayCount> =
        jdbc.sql(
            """
            SELECT CAST(played_at AT TIME ZONE :zone AS date) AS day, count(*) AS plays
            FROM plays
            WHERE user_id = :userId AND played_at >= :since
            GROUP BY day
            ORDER BY day DESC
            """.trimIndent()
        )
            .param("userId", userId)
            .param("since", since)
            .param("zone", zone)
            .query(DayCount::class.java)
            .list()
            .filterNotNull()  // JdbcClient returns platform types; rows are never null

    /** Sum of full track lengths since [since]. Skipped tracks still count their full length. */
    fun totalDurationMs(userId: String, since: OffsetDateTime): Long =
        jdbc.sql(
            "SELECT coalesce(sum(duration_ms), 0) FROM plays WHERE user_id = :userId AND played_at >= :since"
        )
            .param("userId", userId)
            .param("since", since)
            .query(Long::class.java)
            .single()
}
