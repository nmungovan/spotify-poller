package com.nmungovan.spotifypoller

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpotifyPollerApplication

fun main(args: Array<String>) {
	runApplication<SpotifyPollerApplication>(*args)
}
