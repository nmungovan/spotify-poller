package com.nmungovan.spotifypoller

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Checks the real HTTP request shape against Spotify's Web API, with no network. */
class SpotifyAPIClientTests {

    private val props = SpotifyProperties(
        clientId = "test-id",
        clientSecret = "test-secret",
        redirectUri = "http://127.0.0.1:8080/callback",
        apiBaseUrl = "https://api.test",
    )

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = SpotifyAPIClient(props, builder)

    private val page = """
        {
          "items": [
            { "track": { "id": "a", "uri": "spotify:track:a", "name": "A", "duration_ms": 1000 },
              "played_at": "2026-11-08T06:00:00.000Z", "context": null },
            { "track": { "id": "b", "uri": "spotify:track:b", "name": "B", "duration_ms": 2000 },
              "played_at": "2026-11-08T05:00:00.000Z", "context": null }
          ],
          "limit": 50
        }
    """.trimIndent()

    @Test
    fun `currentUserId calls me with a bearer token and returns the id`() {
        server.expect(requestTo("https://api.test/v1/me"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
            .andRespond(withSuccess("""{"id":"someone","display_name":"Someone"}""", MediaType.APPLICATION_JSON))

        assertEquals("someone", client.currentUserId("tok"))
        server.verify()
    }

    @Test
    fun `recentlyPlayed sends limit and after and returns each item as raw json`() {
        server.expect(requestTo("https://api.test/v1/me/player/recently-played?limit=50&after=1762581600000"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok"))
            .andRespond(withSuccess(page, MediaType.APPLICATION_JSON))

        val items = client.recentlyPlayed("tok", 1762581600000)

        assertEquals(2, items.size)
        assertEquals("spotify:track:a", items[0].path("track").path("uri").asString())
        server.verify()
    }

    @Test
    fun `recentlyPlayed omits after when there is no cursor`() {
        server.expect(requestTo("https://api.test/v1/me/player/recently-played?limit=50"))
            .andRespond(withSuccess(page, MediaType.APPLICATION_JSON))

        assertEquals(2, client.recentlyPlayed("tok").size)
        server.verify()
    }

    @Test
    fun `a 429 surfaces as a client error carrying the status`() {
        server.expect(requestTo("https://api.test/v1/me/player/recently-played?limit=50"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "30"))

        val e = assertFailsWith<RestClientResponseException> { client.recentlyPlayed("tok") }

        assertEquals(429, e.statusCode.value())
        assertEquals("30", e.responseHeaders?.getFirst(HttpHeaders.RETRY_AFTER))
    }
}
