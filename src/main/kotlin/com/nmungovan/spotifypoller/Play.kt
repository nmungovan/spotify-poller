package com.nmungovan.spotifypoller

import tools.jackson.databind.JsonNode
import java.time.OffsetDateTime

/** One item of Spotify's recently-played response, with the typed columns of the plays table. */
data class Play(
    val trackUri: String,
    val trackId: String?,      // null for local files
    val playedAt: OffsetDateTime,
    val trackName: String,
    val durationMs: Int,
    val isrc: String?,
    val contextType: String?,
    val contextUri: String?,
    val raw: JsonNode,         // the whole item -> plays.raw
)

fun JsonNode.toPlay(): Play {
    val track = path("track")
    return Play(
        trackUri = track.path("uri").asString(),
        trackId = track.path("id").textOrNull(),
        playedAt = OffsetDateTime.parse(path("played_at").asString()),
        trackName = track.path("name").asString(),
        durationMs = track.path("duration_ms").asInt(),
        isrc = track.path("external_ids").path("isrc").textOrNull(),
        contextType = path("context").path("type").textOrNull(),
        contextUri = path("context").path("uri").textOrNull(),
        raw = this,
    )
}

/** Null for JSON null and for absent fields; `path()` never returns a Kotlin null. */
private fun JsonNode.textOrNull(): String? =
    if (isNull || isMissingNode) null else asString()
