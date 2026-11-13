package com.nmungovan.spotifypoller

import com.nmungovan.spotifypoller.types.toPlay
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayParsingTests {

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `parses a normal track play`() {
        val item = mapper.readTree(
            """
            {
              "track": {
                "id": "2fHXAuXRcCDXbZYJIUZCGW",
                "uri": "spotify:track:2fHXAuXRcCDXbZYJIUZCGW",
                "name": "Some Song",
                "duration_ms": 215000,
                "external_ids": { "isrc": "USABC1234567" },
                "artists": [{ "name": "Some Artist" }]
              },
              "played_at": "2026-11-08T05:37:41.487Z",
              "context": { "type": "playlist", "uri": "spotify:playlist:abc" }
            }
            """.trimIndent()
        )

        val play = item.toPlay()

        assertEquals("spotify:track:2fHXAuXRcCDXbZYJIUZCGW", play.trackUri)
        assertEquals("2fHXAuXRcCDXbZYJIUZCGW", play.trackId)
        assertEquals(OffsetDateTime.parse("2026-11-08T05:37:41.487Z"), play.playedAt)
        assertEquals("Some Song", play.trackName)
        assertEquals(215000, play.durationMs)
        assertEquals("USABC1234567", play.isrc)
        assertEquals("playlist", play.contextType)
        assertEquals("spotify:playlist:abc", play.contextUri)
        assertEquals(item, play.raw)  // nothing is lost: the full item is kept
    }

    @Test
    fun `parses a local file play with a null id, no isrc and no context`() {
        val item = mapper.readTree(
            """
            {
              "track": {
                "id": null,
                "uri": "spotify:local:Some+Artist:Some+Album:Some+Song:127",
                "name": "Some Song",
                "duration_ms": 127000
              },
              "played_at": "2026-11-08T05:40:00.000Z",
              "context": null
            }
            """.trimIndent()
        )

        val play = item.toPlay()

        assertEquals("spotify:local:Some+Artist:Some+Album:Some+Song:127", play.trackUri)
        assertNull(play.trackId)
        assertNull(play.isrc)
        assertNull(play.contextType)
        assertNull(play.contextUri)
    }
}
