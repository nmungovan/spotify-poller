package com.nmungovan.spotifypoller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder

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
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpotifyUser(val id: String)