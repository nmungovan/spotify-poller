package com.nmungovan.spotifypoller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Verifies the idempotency guarantee of the plays table (see decisions/0001):
 * a play is identified by (user_id, track_uri, played_at), and re-inserting it is a no-op.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PlayDedupTests(@Autowired val jdbc: JdbcTemplate) {

    private val user = "user-1"
    private val otherUser = "user-2"
    private val trackUri = "spotify:track:2fHXAuXRcCDXbZYJIUZCGW"
    private val trackId = "2fHXAuXRcCDXbZYJIUZCGW"
    private val localUri = "spotify:local:Some+Artist:Some+Album:Some+Song:127"
    private val playedAt = OffsetDateTime.parse("2026-11-08T05:37:41.487Z")

    @BeforeEach
    fun setup() {
        // plays first: it references spotify_auth (ON DELETE RESTRICT)
        jdbc.execute("DELETE FROM plays")
        jdbc.execute("DELETE FROM spotify_auth")
        insertUser(user)
    }

    @Test
    fun `inserting the same play twice results in one row`() {
        assertEquals(1, insertPlay(user, trackUri, trackId, playedAt))
        assertEquals(0, insertPlay(user, trackUri, trackId, playedAt))

        assertEquals(1, countPlays())
    }

    @Test
    fun `a local file play with a null track_id is deduplicated`() {
        assertEquals(1, insertPlay(user, localUri, null, playedAt))
        assertEquals(0, insertPlay(user, localUri, null, playedAt))

        assertEquals(1, countPlays())
    }

    @Test
    fun `the same track at a different time is a separate play`() {
        assertEquals(1, insertPlay(user, trackUri, trackId, playedAt))
        assertEquals(1, insertPlay(user, trackUri, trackId, playedAt.plusMinutes(5)))

        assertEquals(2, countPlays())
    }

    @Test
    fun `the same track and time for a different user is a separate play`() {
        insertUser(otherUser)

        assertEquals(1, insertPlay(user, trackUri, trackId, playedAt))
        assertEquals(1, insertPlay(otherUser, trackUri, trackId, playedAt))

        assertEquals(2, countPlays())
    }

    @Test
    fun `a play for an unknown user is rejected`() {
        assertFailsWith<DataIntegrityViolationException> {
            insertPlay("no-such-user", trackUri, trackId, playedAt)
        }
    }

    @Test
    fun `deleting a user who has plays is rejected`() {
        insertPlay(user, trackUri, trackId, playedAt)

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update("DELETE FROM spotify_auth WHERE user_id = ?", user)
        }
    }

    private fun insertUser(userId: String) {
        jdbc.update(
            "INSERT INTO spotify_auth (user_id, refresh_token) VALUES (?, ?)",
            userId,
            "not-a-real-token",
        )
    }

    /** Same statement shape the poller will use. Returns the number of rows inserted. */
    private fun insertPlay(userId: String, uri: String, id: String?, at: OffsetDateTime): Int =
        jdbc.update(
            """
            INSERT INTO plays (user_id, track_uri, track_id, played_at, track_name, duration_ms, raw)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (user_id, track_uri, played_at) DO NOTHING
            """.trimIndent(),
            userId, uri, id, at, "Some Song", 121_386, "{}",
        )

    private fun countPlays(): Int =
        jdbc.queryForObject("SELECT count(*) FROM plays", Int::class.java)!!
}
