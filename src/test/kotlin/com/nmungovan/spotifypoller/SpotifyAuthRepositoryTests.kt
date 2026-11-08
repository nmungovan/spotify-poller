package com.nmungovan.spotifypoller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies the refresh-token upsert against a real Postgres (see decisions/0007). */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class SpotifyAuthRepositoryTests(
    @Autowired val repository: SpotifyAuthRepository,
    @Autowired val jdbc: JdbcTemplate,
) {

    private val user = "user-1"

    @BeforeEach
    fun setup() {
        // plays first: it references spotify_auth (ON DELETE RESTRICT)
        jdbc.execute("DELETE FROM plays")
        jdbc.execute("DELETE FROM spotify_auth")
    }

    @Test
    fun `saving twice for the same user leaves one row with the latest token`() {
        repository.save(user, "first-token")
        repository.save(user, "second-token")

        assertEquals(1, countRows())
        assertEquals("second-token", repository.findRefreshToken(user))
    }

    @Test
    fun `saving again moves updated_at forward`() {
        repository.save(user, "first-token")
        val before = updatedAt(user)

        repository.save(user, "second-token")
        val after = updatedAt(user)

        assertTrue(after.isAfter(before), "expected $after to be after $before")
    }

    @Test
    fun `different users are stored separately`() {
        repository.save(user, "token-a")
        repository.save("user-2", "token-b")

        assertEquals(2, countRows())
        assertEquals("token-a", repository.findRefreshToken(user))
        assertEquals("token-b", repository.findRefreshToken("user-2"))
    }

    @Test
    fun `an unknown user has no refresh token`() {
        assertNull(repository.findRefreshToken("nobody"))
    }

    private fun countRows(): Int =
        jdbc.queryForObject("SELECT count(*) FROM spotify_auth", Int::class.java)!!

    private fun updatedAt(userId: String): OffsetDateTime =
        jdbc.queryForObject(
            "SELECT updated_at FROM spotify_auth WHERE user_id = ?",
            OffsetDateTime::class.java,
            userId,
        )!!
}
