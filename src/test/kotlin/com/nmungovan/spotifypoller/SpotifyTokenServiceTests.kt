package com.nmungovan.spotifypoller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Real repository + Postgres, with Spotify's token endpoint mocked.
 * The interesting behavior is refresh-token rotation.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class SpotifyTokenServiceTests(
    @Autowired val service: SpotifyTokenService,
    @Autowired val repository: SpotifyAuthRepository,
    @Autowired val jdbc: JdbcTemplate,
) {

    @MockitoBean
    lateinit var authClient: SpotifyAuthClient

    private val user = "user-1"

    @BeforeEach
    fun setup() {
        // plays first: it references spotify_auth (ON DELETE RESTRICT)
        jdbc.execute("DELETE FROM plays")
        jdbc.execute("DELETE FROM spotify_auth")
    }

    @Test
    fun `returns a fresh access token and keeps the stored token when none is rotated`() {
        repository.save(user, "stored-token")
        Mockito.`when`(authClient.refresh("stored-token"))
            .thenReturn(TokenResponse("new-access", 3600, null, null))

        assertEquals("new-access", service.getAccessToken(user))
        assertEquals("stored-token", repository.findRefreshToken(user))
    }

    @Test
    fun `keeps the stored token when Spotify returns the same one`() {
        repository.save(user, "stored-token")
        Mockito.`when`(authClient.refresh("stored-token"))
            .thenReturn(TokenResponse("new-access", 3600, "stored-token", null))

        service.getAccessToken(user)

        assertEquals("stored-token", repository.findRefreshToken(user))
    }

    @Test
    fun `saves a rotated refresh token`() {
        repository.save(user, "old-token")
        Mockito.`when`(authClient.refresh("old-token"))
            .thenReturn(TokenResponse("new-access", 3600, "rotated-token", null))

        assertEquals("new-access", service.getAccessToken(user))
        assertEquals("rotated-token", repository.findRefreshToken(user))
    }

    @Test
    fun `fails clearly when the user has not authorized, without calling Spotify`() {
        assertFailsWith<IllegalStateException> { service.getAccessToken("nobody") }

        Mockito.verifyNoInteractions(authClient)
    }
}
