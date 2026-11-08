package com.nmungovan.spotifypoller

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class SpotifyAuthRepository(private val jdbc: JdbcClient) {

    /** Insert, or replace the stored refresh token if this user has reconnected. */
    fun save(userId: String, refreshToken: String) {
        jdbc.sql(
            """
            INSERT INTO spotify_auth (user_id, refresh_token)
            VALUES (:userId, :refreshToken)
            ON CONFLICT (user_id) DO UPDATE
              SET refresh_token = EXCLUDED.refresh_token,
                  updated_at    = now()
            """.trimIndent()
        )
            .param("userId", userId)
            .param("refreshToken", refreshToken)
            .update()
    }

    /** Every user who has authorized; these are the users the poller covers. */
    fun findAllUserIds(): List<String> =
        jdbc.sql("SELECT user_id FROM spotify_auth ORDER BY user_id")
            .query(String::class.java)
            .list()
            .filterNotNull()  // user_id is NOT NULL; this just fixes the platform type

    fun findRefreshToken(userId: String): String? =
        jdbc.sql("SELECT refresh_token FROM spotify_auth WHERE user_id = :userId")
            .param("userId", userId)
            .query(String::class.java)
            .optional()
            .orElse(null)
}
