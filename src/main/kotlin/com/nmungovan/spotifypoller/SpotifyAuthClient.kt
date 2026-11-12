package com.nmungovan.spotifypoller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenResponse(
    @param:JsonProperty("access_token") val accessToken: String,
    @param:JsonProperty("expires_in") val expiresIn: Long,
    @param:JsonProperty("refresh_token") val refreshToken: String?,  // null on refresh if Spotify doesn't rotate it
    val scope: String?,
) {
    override fun toString() = "TokenResponse(expiresIn=$expiresIn, scope=$scope)"  // keep tokens out of logs
}

@Component
class SpotifyAuthClient(
    private val spotify: SpotifyProperties,
    builder: RestClient.Builder
) {
    private val client = builder
        .baseUrl(spotify.accountsBaseUrl)
        .build()

    /** Callback step: trade the one-time code for access + refresh tokens. */
    fun exchangeCode(code: String): TokenResponse =
        requestToken(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to spotify.redirectUri,
        )

    /** Poller step (M2 last item): trade the stored refresh token for a fresh access token. */
    fun refresh(refreshToken: String): TokenResponse =
        requestToken(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )

    private fun requestToken(vararg params: Pair<String, String>): TokenResponse {
        val form = LinkedMultiValueMap<String, String>()
        params.forEach { (k, v) -> form.add(k, v) }

        return client.post()
            .uri("/api/token")
            .headers { it.setBasicAuth(spotify.clientId, spotify.clientSecret) }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body<TokenResponse>()!!
    }
}