package com.nmungovan.spotifypoller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode

@Component
class SpotifyAPIClient(builder: RestClient.Builder) {
    private val client = builder
        .baseUrl("https://api.spotify.com")
        .build()

    fun currentUserId(accessToken: String): String =
        client.get()
            .uri("/v1/me")
            .headers { it.setBearerAuth(accessToken) }
            .retrieve()
            .body<SpotifyUser>()!!.id

    /**
     * One page of GET /v1/me/player/recently-played (max 50 plays, newest first).
     * [afterMillis] is a Unix timestamp in milliseconds: only plays after it are returned.
     * Items stay as raw JSON so each can be stored in plays.raw (see decisions/0002).
     */
    fun recentlyPlayed(accessToken: String, afterMillis: Long? = null): List<JsonNode> {
        val body = client.get()
            .uri { uri ->
                uri.path("/v1/me/player/recently-played")
                    .queryParam("limit", 50)
                    .apply { if (afterMillis != null) queryParam("after", afterMillis) }
                    .build()
            }
            .headers { it.setBearerAuth(accessToken) }
            .retrieve()
            .body<JsonNode>()!!

        return body.path("items").toList()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpotifyUser(val id: String)
