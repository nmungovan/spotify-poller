package com.nmungovan.spotifypoller

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spotify")
data class SpotifyProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scope: String = "user-read-recently-played",
    // Overridable so tests and the fake-Spotify Compose overlay can point elsewhere.
    val accountsBaseUrl: String = "https://accounts.spotify.com",
    val apiBaseUrl: String = "https://api.spotify.com",
)
