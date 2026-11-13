package com.nmungovan.spotifypoller

import com.nmungovan.spotifypoller.types.Play
import com.nmungovan.spotifypoller.types.TrackCount
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class StatsTests(
    @Autowired val plays: PlayRepository,
    @Autowired val auth: SpotifyAuthRepository,
    @Autowired val controller: StatsController,
    @Autowired val jdbc: JdbcTemplate,
) {
    private val mapper = JsonMapper.builder().build()
    private val user = "user-1"
    private val now = OffsetDateTime.now()

    @BeforeEach
    fun setup() {
        jdbc.execute("DELETE FROM plays")
        jdbc.execute("DELETE FROM spotify_auth")
        auth.save(user, "refresh-token")
    }

    @Test
    fun `top tracks counts plays, orders by count and respects the window`() {
        add("Song A", "uri:a", now.minusHours(1))
        add("Song A", "uri:a", now.minusHours(2))
        add("Song B", "uri:b", now.minusHours(3))
        add("Old Song", "uri:old", now.minusDays(30))

        val top = plays.topTracks(user, now.minusDays(7), 10)

        assertEquals(listOf(TrackCount("Song A", 2), TrackCount("Song B", 1)), top)
    }

    @Test
    fun `total duration sums the window only`() {
        add("A", "uri:a", now.minusHours(1), durationMs = 60_000)
        add("B", "uri:b", now.minusHours(2), durationMs = 120_000)
        add("Old", "uri:old", now.minusDays(30), durationMs = 999_000)

        assertEquals(180_000L, plays.totalDurationMs(user, now.minusDays(7)))
        assertEquals(0L, plays.totalDurationMs("nobody", now.minusDays(7)))
    }

    @Test
    fun `plays per day cuts days in the given zone, not UTC`() {
        // 03:30 UTC on Nov 9 is still the evening of Nov 8 in New York (UTC-5)
        add("Late", "uri:late", OffsetDateTime.parse("2026-11-09T03:30:00Z"))

        val days = plays.playsPerDay(user, OffsetDateTime.parse("2026-11-01T00:00:00Z"), "America/New_York")

        assertEquals(1, days.size)
        assertEquals("2026-11-08", days.single().day.toString())
    }

    @Test
    fun `recent plays are newest first and limited`() {
        add("First", "uri:1", now.minusHours(3))
        add("Second", "uri:2", now.minusHours(2))
        add("Third", "uri:3", now.minusHours(1))

        assertEquals(listOf("Third", "Second"), plays.recentPlays(user, 2).map { it.trackName })
    }

    @Test
    fun `the page escapes track names`() {
        add("<script>alert(1)</script>", "uri:x", now.minusHours(1))

        val html = controller.stats()

        assertFalse(html.contains("<script>alert"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `the page explains itself when nobody has connected`() {
        jdbc.execute("DELETE FROM plays")
        jdbc.execute("DELETE FROM spotify_auth")

        assertTrue(controller.stats().contains("Visit /login"))
    }

    private fun add(name: String, uri: String, at: OffsetDateTime, durationMs: Int = 200_000) {
        plays.insertIgnoringDuplicates(
            user,
            Play(
                trackUri = uri, trackId = uri.removePrefix("uri:"), playedAt = at, trackName = name,
                durationMs = durationMs, isrc = null, contextType = null, contextUri = null,
                raw = mapper.createObjectNode(),
            ),
        )
    }
}
