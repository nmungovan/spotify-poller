package com.nmungovan.spotifypoller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Triggers the poll on a timer. recently-played returns at most 50 plays, so the interval
 * must stay short enough that a listener can't play more than that in between.
 * Disable with poller.enabled=false (the tests do).
 */
@Component
@ConditionalOnProperty(name = ["poller.enabled"], havingValue = "true", matchIfMissing = true)
class PollScheduler(private val poller: PollerService) {

    @Scheduled(
        initialDelayString = "\${poller.initial-delay:PT10S}",
        fixedDelayString = "\${poller.interval:PT15M}",
    )
    fun run() = poller.pollAll()
}
