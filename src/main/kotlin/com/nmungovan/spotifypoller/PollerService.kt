package com.nmungovan.spotifypoller

import com.nmungovan.spotifypoller.types.toPlay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PollerService(
    private val tokenService: SpotifyTokenService,
    private val apiClient: SpotifyAPIClient,
    private val plays: PlayRepository,
    private val authRepository: SpotifyAuthRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Fetches the user's recent plays and stores the new ones. Returns how many were new.
     * Safe to run overlapping or repeatedly: duplicates are ignored by the database (decisions/0001).
     */
    fun poll(userId: String): Int {
        val accessToken = tokenService.getAccessToken(userId)
        val afterMillis = plays.latestPlayedAt(userId)?.toInstant()?.toEpochMilli()

        val items = apiClient.recentlyPlayed(accessToken, afterMillis)
        val inserted = items.sumOf { plays.insertIgnoringDuplicates(userId, it.toPlay()) }

        log.info("Polled user={} fetched={} new={}", userId, items.size, inserted)
        return inserted
    }

    /** Polls every authorized user. One user's failure must not stop the others. */
    fun pollAll() {
        for (userId in authRepository.findAllUserIds()) {
            try {
                poll(userId)
            } catch (e: Exception) {
                // Log the type only: exception messages can carry response bodies.
                log.error("Poll failed for user={} ({})", userId, e.javaClass.simpleName)
            }
        }
    }
}
