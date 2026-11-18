@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val POOL_TAG = "pool"
private const val ACTIVE = "hikaricp.connections.active"
private const val IDLE = "hikaricp.connections.idle"
private const val PENDING = "hikaricp.connections.pending"
private const val MAX = "hikaricp.connections.max"
private const val ACQUIRE = "hikaricp.connections.acquire"
private const val USAGE = "hikaricp.connections.usage"
private const val TIMEOUT = "hikaricp.connections.timeout"
private val logger = LoggerFactory.getLogger(PoolMetricsLogJob::class.java)

@Component
class PoolMetricsLogJob(
    private val registry: MeterRegistry,
    @Value("\${app.runtime.role:combined}") private val role: String,
    @Value("\${app.datasource.write.hikari.pool-name:notafiscal-write}") private val writePool: String,
    @Value("\${app.datasource.read.hikari.pool-name:notafiscal-read}") private val readPool: String,
) {
    @Scheduled(fixedDelayString = "\${app.runtime.pool-metrics-interval:2s}")
    fun register() {
        listOf(writePool, readPool).forEach { pool ->
            val snapshot = snapshot(pool)
            logger.info(
                "Pool Hikari: role={} pool={} active={} idle={} pending={} max={} " +
                    "acquire_max_ms={} usage_max_ms={} acquisition_count={} timeout_count={}",
                role,
                pool,
                snapshot.active,
                snapshot.idle,
                snapshot.pending,
                snapshot.max,
                snapshot.acquireMaxMillis,
                snapshot.usageMaxMillis,
                snapshot.acquisitionCount,
                snapshot.timeoutCount,
            )
        }
    }

    internal fun snapshot(pool: String): PoolSnapshot =
        PoolSnapshot(
            active = gauge(ACTIVE, pool),
            idle = gauge(IDLE, pool),
            pending = gauge(PENDING, pool),
            max = gauge(MAX, pool),
            acquireMaxMillis = timer(ACQUIRE, pool)?.max(MILLISECONDS) ?: 0.0,
            usageMaxMillis = timer(USAGE, pool)?.max(MILLISECONDS) ?: 0.0,
            acquisitionCount = timer(ACQUIRE, pool)?.count() ?: 0L,
            timeoutCount =
                registry
                    .find(TIMEOUT)
                    .tags(POOL_TAG, pool)
                    .counter()
                    ?.count()
                    ?.toLong() ?: 0L,
        )

    private fun gauge(
        name: String,
        pool: String,
    ): Long =
        registry
            .find(name)
            .tags(POOL_TAG, pool)
            .gauge()
            ?.value()
            ?.toLong() ?: 0L

    private fun timer(
        name: String,
        pool: String,
    ): Timer? = registry.find(name).tags(POOL_TAG, pool).timer()
}

internal data class PoolSnapshot(
    val active: Long,
    val idle: Long,
    val pending: Long,
    val max: Long,
    val acquireMaxMillis: Double,
    val usageMaxMillis: Double,
    val acquisitionCount: Long,
    val timeoutCount: Long,
)
