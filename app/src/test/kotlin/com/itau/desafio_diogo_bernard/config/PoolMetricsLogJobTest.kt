@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

class PoolMetricsLogJobTest {
    @Test
    fun `consulta gauges timers e timeouts do pool`() {
        val registry = SimpleMeterRegistry()
        val active = AtomicLong(4)
        val idle = AtomicLong(2)
        val pending = AtomicLong(1)
        val max = AtomicLong(20)
        registry.gauge(
            "hikaricp.connections.active",
            listOf(Tag.of("pool", "write")),
            active,
        )
        registry.gauge(
            "hikaricp.connections.idle",
            listOf(Tag.of("pool", "write")),
            idle,
        )
        registry.gauge(
            "hikaricp.connections.pending",
            listOf(Tag.of("pool", "write")),
            pending,
        )
        registry.gauge(
            "hikaricp.connections.max",
            listOf(Tag.of("pool", "write")),
            max,
        )
        registry.timer("hikaricp.connections.acquire", "pool", "write").record(25, MILLISECONDS)
        registry.timer("hikaricp.connections.usage", "pool", "write").record(10, MILLISECONDS)
        registry.counter("hikaricp.connections.timeout", "pool", "write").increment(3.0)

        val snapshot = PoolMetricsLogJob(registry, "api", "write", "read").snapshot("write")

        assertEquals(4, snapshot.active)
        assertEquals(2, snapshot.idle)
        assertEquals(1, snapshot.pending)
        assertEquals(20, snapshot.max)
        assertEquals(25.0, snapshot.acquireMaxMillis)
        assertEquals(10.0, snapshot.usageMaxMillis)
        assertEquals(1, snapshot.acquisitionCount)
        assertEquals(3, snapshot.timeoutCount)
    }

    @Test
    fun `usa zeros quando pool ainda nao publicou metricas`() {
        val job = PoolMetricsLogJob(SimpleMeterRegistry(), "worker", "write", "read")
        job.register()
        val snapshot = job.snapshot("read")

        assertEquals(0, snapshot.active)
        assertEquals(0, snapshot.idle)
        assertEquals(0, snapshot.pending)
        assertEquals(0, snapshot.max)
        assertEquals(0.0, snapshot.acquireMaxMillis)
        assertEquals(0.0, snapshot.usageMaxMillis)
        assertEquals(0, snapshot.acquisitionCount)
        assertEquals(0, snapshot.timeoutCount)
    }
}
