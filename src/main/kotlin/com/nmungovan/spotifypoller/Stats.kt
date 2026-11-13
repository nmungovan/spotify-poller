package com.nmungovan.spotifypoller

import java.time.LocalDate
import java.time.OffsetDateTime

/** Result rows for the stats queries. Property names match the SQL column aliases. */
data class RecentPlay(val trackName: String, val playedAt: OffsetDateTime)

data class TrackCount(val trackName: String, val plays: Long)

data class DayCount(val day: LocalDate, val plays: Long)
