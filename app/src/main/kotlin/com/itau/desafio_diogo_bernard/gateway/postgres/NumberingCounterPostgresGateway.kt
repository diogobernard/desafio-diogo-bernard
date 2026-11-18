package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.exception.NumberingExhaustedException
import org.springframework.stereotype.Component
import java.time.Duration
import com.itau.desafio_diogo_bernard.repository.NumberingCounterRepository as Contadores

@Component
class NumberingCounterPostgresGateway(
    private val counters: Contadores,
    private val metrics: BusinessMetrics,
) : NumberingCounter {
    override fun reserve(series: Int): Int {
        val startedAt = System.nanoTime()
        return try {
            counters.reserve(series) ?: throw NumberingExhaustedException()
        } finally {
            metrics.numberingWait(series, Duration.ofNanos(System.nanoTime() - startedAt))
        }
    }
}
