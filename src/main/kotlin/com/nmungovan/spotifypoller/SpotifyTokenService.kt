package com.nmungovan.spotifypoller

import org.springframework.stereotype.Service

/** Mints short-lived access tokens from the stored refresh token. */
@Service
class SpotifyTokenService(
    private val authClient: SpotifyAuthClient,
    private val authRepository: SpotifyAuthRepository,
) {

    fun getAccessToken(userId: String): String {
        val stored = authRepository.findRefreshToken(userId)
            ?: throw IllegalStateException("No Spotify authorization stored for user $userId; visit /login")

        val tokens = authClient.refresh(stored)

        // Spotify only sometimes rotates the refresh token. When it does, the old one may stop working.
        val rotated = tokens.refreshToken
        if (rotated != null && rotated != stored) {
            authRepository.save(userId, rotated)
        }

        return tokens.accessToken
    }
}
