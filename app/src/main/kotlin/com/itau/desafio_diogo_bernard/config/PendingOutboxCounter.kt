@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.repository.OutboxRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val VALIDADE_DA_LEITURA: Duration = Duration.ofSeconds(2)

@Component
class PendingOutboxCounter(
    private val events: OutboxRepository,
    private val clock: Clock,
) {
    private val latch = ReentrantLock()
    private var reading = Reading(Instant.MIN, emptyMap())

    fun pending(topic: String): Long = current().counts[topic] ?: 0L

    private fun current(): Reading =
        latch.withLock {
            val now = clock.instant()
            if (now.isBefore(reading.readAt.plus(VALIDADE_DA_LEITURA))) return reading
            reading = Reading(now, events.countPendingByTopic().associate { it.topic to it.total })
            reading
        }

    private class Reading(
        val readAt: Instant,
        val counts: Map<String, Long>,
    )
}
