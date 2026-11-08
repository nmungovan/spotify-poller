package com.nmungovan.spotifypoller

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Controller
class LoginController(
    private val spotify: SpotifyProperties,
    private val authClient: SpotifyAuthClient,
    private val spotifyApiClient: SpotifyAPIClient,
    private val authRepository: SpotifyAuthRepository,
) {
    private val random = SecureRandom()

    @GetMapping("/login")
    fun login(session: HttpSession): RedirectView {
        val state = newState()
        session.setAttribute(STATE_ATTR, state)

        val url = UriComponentsBuilder
            .fromUriString("https://accounts.spotify.com/authorize")
            .queryParam("client_id", spotify.clientId)
            .queryParam("response_type", "code")
            .queryParam("redirect_uri", spotify.redirectUri)
            .queryParam("scope", spotify.scope)
            .queryParam("state", state)
            .build()
            .encode()
            .toUriString()

        return RedirectView(url)
    }
    
    @GetMapping("/callback")
    fun callback(
        session: HttpSession,
        code: String?,
        state: String?,
        error: String?,
    ): ResponseEntity<String> {
        // Always consume the stored state, so a value can't be replayed.
        val expected = session.getAttribute(STATE_ATTR) as String?
        session.removeAttribute(STATE_ATTR)

        if (error != null) {
            return ResponseEntity.badRequest().body("Spotify authorization failed: $error")
        }
        if (expected == null || state == null || !MessageDigest.isEqual(expected.toByteArray(), state.toByteArray())) {
            return ResponseEntity.badRequest().body("Invalid state")
        }
        if (code == null) {
            return ResponseEntity.badRequest().body("Missing code")
        }

        return try {
            val tokens = authClient.exchangeCode(code)
            val refreshToken = tokens.refreshToken
                ?: return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Spotify did not return a refresh token")

            val userId = spotifyApiClient.currentUserId(tokens.accessToken)
            authRepository.save(userId, refreshToken)

            ResponseEntity.ok("Connected as $userId. You can close this tab.")
        } catch (e: RestClientResponseException) {
            // Don't echo e.message or the response body: keep Spotify's payload out of the page.
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Spotify rejected the request (${e.statusCode.value()}). Try /login again.")
        }
    }

    private fun newState(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val STATE_ATTR = "spotify_oauth_state"
    }
}
