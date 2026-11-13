package com.nmungovan.spotifypoller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A bare-bones, phone-friendly stats page: one HTML document, no JavaScript, no template engine.
 * Covers the last 7 days for the first connected user (this app is single-user in practice).
 * Day boundaries use stats.timezone (default America/New_York), not UTC.
 */
@RestController
class StatsController(
    private val plays: PlayRepository,
    private val auth: SpotifyAuthRepository,
    @Value("\${stats.timezone:America/New_York}") timezone: String,
) {
    private val zone = ZoneId.of(timezone)
    private val timeFormat = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")

    @GetMapping("/stats", produces = [MediaType.TEXT_HTML_VALUE])
    fun stats(): String {
        val user = auth.findAllUserIds().firstOrNull()
            ?: return page("<p>No Spotify account connected yet. Visit /login first.</p>")

        val since = OffsetDateTime.now().minusDays(7)
        val totalMs = plays.totalDurationMs(user, since)

        val body = buildString {
            append("<h2>Last 7 days</h2>")
            append("<p>${formatDuration(totalMs)} of music</p>")

            append("<h2>Top tracks</h2><ol>")
            plays.topTracks(user, since, 10).forEach {
                append("<li>${esc(it.trackName)} <small>(${it.plays})</small></li>")
            }
            append("</ol>")

            append("<h2>Plays per day</h2><ul>")
            plays.playsPerDay(user, since, zone.id).forEach {
                append("<li>${it.day}: ${it.plays}</li>")
            }
            append("</ul>")

            append("<h2>Recent</h2><ul>")
            plays.recentPlays(user, 15).forEach {
                append("<li>${esc(it.trackName)} <small>${it.playedAt.atZoneSameInstant(zone).format(timeFormat)}</small></li>")
            }
            append("</ul>")
        }
        return page(body)
    }

    // Track names come from Spotify, so they are untrusted input: always escape them.
    private fun esc(s: String) = HtmlUtils.htmlEscape(s)

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        return "${minutes / 60}h ${minutes % 60}m"
    }

    private fun page(body: String) = """
        <!doctype html>
        <html><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Spotify stats</title>
        <style>body{font-family:system-ui,sans-serif;max-width:40rem;margin:1rem auto;padding:0 1rem}
        small{color:#666}</style>
        </head><body>$body</body></html>
    """.trimIndent()
}
