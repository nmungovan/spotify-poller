package com.nmungovan.spotifypoller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import kotlin.test.assertEquals

/** The poll flow against a real Postgres, with Spotify (token + API) mocked. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PollerServiceTests(
    @Autowired val poller: PollerService,
    @Autowired val auth: SpotifyAuthRepository,
    @Autowired val jdbc: JdbcTemplate,
) {

    @MockitoBean
    lateinit var tokenService: SpotifyTokenService

    @MockitoBean
    lateinit var apiClient: SpotifyAPIClient

    private val mapper = JsonMapper.builder().build()
    private val t1 = OffsetDateTime.parse("2026-11-08T05:00:00Z")
    private val t2 = OffsetDateTime.parse("2026-11-08T06:00:00Z")

    @BeforeEach
    fun setup() {
        // plays first: it references spotify_auth (ON DELETE RESTRICT)
        jdbc.execute("DELETE FROM plays")
        jdbc.execute("DELETE FROM spotify_auth")
        auth.save("user-1", "refresh-1")
        Mockito.`when`(tokenService.getAccessToken("user-1")).thenReturn("access-1")
    }

    @Test
    fun `first poll has no cursor and stores every play`() {
        Mockito.`when`(apiClient.recentlyPlayed("access-1", null))
            .thenReturn(listOf(item("a", t2), item("b", t1)))

        assertEquals(2, poller.poll("user-1"))
        assertEquals(2, countPlays())
    }

    @Test
    fun `polling again with the same plays stores nothing new`() {
        Mockito.`when`(apiClient.recentlyPlayed("access-1", null))
            .thenReturn(listOf(item("a", t2), item("b", t1)))
        poller.poll("user-1")

        // second poll sends the cursor; Spotify (mock) returns an overlapping page
        Mockito.`when`(apiClient.recentlyPlayed("access-1", t2.toInstant().toEpochMilli()))
            .thenReturn(listOf(item("a", t2), item("b", t1)))

        assertEquals(0, poller.poll("user-1"))
        assertEquals(2, countPlays())
    }

    @Test
    fun `the after cursor is the newest stored play in epoch milliseconds`() {
        Mockito.`when`(apiClient.recentlyPlayed("access-1", null))
            .thenReturn(listOf(item("a", t1)))
        poller.poll("user-1")

        Mockito.`when`(apiClient.recentlyPlayed("access-1", t1.toInstant().toEpochMilli()))
            .thenReturn(listOf(item("b", t2)))

        assertEquals(1, poller.poll("user-1"))
        Mockito.verify(apiClient).recentlyPlayed("access-1", t1.toInstant().toEpochMilli())
    }

    @Test
    fun `pollAll keeps going when one user fails`() {
        auth.save("user-2", "refresh-2")
        Mockito.`when`(tokenService.getAccessToken("user-1")).thenThrow(IllegalStateException("boom"))
        Mockito.`when`(tokenService.getAccessToken("user-2")).thenReturn("access-2")
        Mockito.`when`(apiClient.recentlyPlayed("access-2", null)).thenReturn(listOf(item("a", t1)))

        poller.pollAll()

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM plays WHERE user_id = 'user-2'", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM plays WHERE user_id = 'user-1'", Int::class.java))
    }

    private fun countPlays(): Int =
        jdbc.queryForObject("SELECT count(*) FROM plays", Int::class.java)!!

    private fun item(trackId: String, playedAt: OffsetDateTime): JsonNode =
        mapper.readTree(
            """
            {
              "track": { "id": "$trackId", "uri": "spotify:track:$trackId", "name": "Song $trackId", "duration_ms": 1000 },
              "played_at": "$playedAt",
              "context": null
            }
            """.trimIndent()
        )
}
