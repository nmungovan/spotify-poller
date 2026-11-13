package com.nmungovan.spotifypoller

import com.nmungovan.spotifypoller.types.Play
import com.nmungovan.spotifypoller.types.toPlay
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PlayRepositoryTests(
    @Autowired val plays: PlayRepository,
    @Autowired val auth: SpotifyAuthRepository,
    @Autowired val jdbc: JdbcTemplate,
) {

    private val mapper = JsonMapper.builder().build()
    private val user = "user-1"
    private val t1 = OffsetDateTime.parse("2026-11-08T05:00:00Z")
    private val t2 = OffsetDateTime.parse("2026-11-08T06:00:00Z")

    @BeforeEach
    fun setup() {
        // plays first: it references spotify_auth (ON DELETE RESTRICT)
        jdbc.execute("DELETE FROM plays")
        jdbc.execute("DELETE FROM spotify_auth")
        auth.save(user, "refresh-token")
    }

    @Test
    fun `inserting the same play twice stores it once and reports 1 then 0`() {
        assertEquals(1, plays.insertIgnoringDuplicates(user, play(t1)))
        assertEquals(0, plays.insertIgnoringDuplicates(user, play(t1)))

        assertEquals(1, countPlays())
    }

    @Test
    fun `a local file play with a null track id is deduplicated`() {
        val local = play(t1, uri = "spotify:local:Artist:Album:Song:127", id = null)

        assertEquals(1, plays.insertIgnoringDuplicates(user, local))
        assertEquals(0, plays.insertIgnoringDuplicates(user, local))
    }

    @Test
    fun `stores the typed columns and the raw item`() {
        plays.insertIgnoringDuplicates(user, play(t1))

        val row = jdbc.queryForMap(
            "SELECT track_name, duration_ms, isrc, context_type, raw->>'marker' AS marker, raw->'track'->>'name' AS raw_name FROM plays"
        )
        assertEquals("Some Song", row["track_name"])
        assertEquals(215000, row["duration_ms"])
        assertEquals("USABC1234567", row["isrc"])
        assertEquals("playlist", row["context_type"])
        assertEquals("kept", row["marker"])        // a field we don't map is still in raw
        assertEquals("Some Song", row["raw_name"])
    }

    @Test
    fun `latestPlayedAt is null with no plays and the newest time otherwise`() {
        assertNull(plays.latestPlayedAt(user))

        plays.insertIgnoringDuplicates(user, play(t1))
        plays.insertIgnoringDuplicates(user, play(t2))

        assertEquals(t2.toInstant(), plays.latestPlayedAt(user)!!.toInstant())
    }

    private fun countPlays(): Int =
        jdbc.queryForObject("SELECT count(*) FROM plays", Int::class.java)!!

    private fun play(
        playedAt: OffsetDateTime,
        uri: String = "spotify:track:2fHXAuXRcCDXbZYJIUZCGW",
        id: String? = "2fHXAuXRcCDXbZYJIUZCGW",
    ): Play {
        val item = mapper.readTree(
            """
            {
              "marker": "kept",
              "track": {
                "id": ${if (id == null) "null" else "\"$id\""},
                "uri": "$uri",
                "name": "Some Song",
                "duration_ms": 215000,
                "external_ids": { "isrc": "USABC1234567" }
              },
              "played_at": "$playedAt",
              "context": { "type": "playlist", "uri": "spotify:playlist:abc" }
            }
            """.trimIndent()
        )
        return item.toPlay()
    }
}
