package com.nmungovan.spotifypoller

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Checks the real HTTP request shape against Spotify's token endpoint, with no network. */
class SpotifyAuthClientTests {

    private val props = SpotifyProperties(
        clientId = "test-id",
        clientSecret = "test-secret",
        redirectUri = "http://127.0.0.1:8080/callback",
        accountsBaseUrl = "https://accounts.test",
    )

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = SpotifyAuthClient(props, builder)

    private val basicAuth =
        "Basic " + Base64.getEncoder().encodeToString("test-id:test-secret".toByteArray())

    @Test
    fun `exchangeCode posts the code as a form with Basic auth and parses the tokens`() {
        server.expect(requestTo("https://accounts.test/api/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
            .andExpect(content().formDataContains(mapOf(
                "grant_type" to "authorization_code",
                "code" to "the-code",
                "redirect_uri" to "http://127.0.0.1:8080/callback",
            )))
            .andRespond(withSuccess(
                """{"access_token":"A","token_type":"Bearer","expires_in":3600,"refresh_token":"R","scope":"user-read-recently-played"}""",
                MediaType.APPLICATION_JSON,
            ))

        val tokens = client.exchangeCode("the-code")

        assertEquals("A", tokens.accessToken)
        assertEquals("R", tokens.refreshToken)
        assertEquals(3600, tokens.expiresIn)
        server.verify()
    }

    @Test
    fun `refresh sends the refresh_token grant and tolerates a response without a new refresh token`() {
        server.expect(requestTo("https://accounts.test/api/token"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth))
            .andExpect(content().formDataContains(mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to "stored-refresh",
            )))
            .andRespond(withSuccess(
                """{"access_token":"A2","token_type":"Bearer","expires_in":3600,"scope":"user-read-recently-played"}""",
                MediaType.APPLICATION_JSON,
            ))

        val tokens = client.refresh("stored-refresh")

        assertEquals("A2", tokens.accessToken)
        assertNull(tokens.refreshToken)
        server.verify()
    }

    @Test
    fun `an invalid_grant response surfaces as a client error`() {
        server.expect(requestTo("https://accounts.test/api/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":"invalid_grant","error_description":"Refresh token revoked"}"""))

        val e = assertFailsWith<RestClientResponseException> { client.refresh("revoked") }

        assertEquals(400, e.statusCode.value())
    }
}
