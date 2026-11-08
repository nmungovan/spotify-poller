package com.nmungovan.spotifypoller

import org.springframework.jdbc.core.simple.JdbcClient

class PlayRepository(private val jdbc: JdbcClient) {

    fun save(userId: String, trackId: String, playedAt: String) {
        
    
}