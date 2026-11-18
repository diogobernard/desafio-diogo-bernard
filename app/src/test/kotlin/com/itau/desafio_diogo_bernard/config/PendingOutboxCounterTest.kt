@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.repository.OutboxRepository
import com.itau.desafio_diogo_bernard.repository.PendingByTopic
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

class PendingOutboxCounterTest {
    private val events = Mockito.mock(OutboxRepository::class.java)
    private val fixedClock = MutableClock(Instant.parse("2026-11-16T12:00:00Z"))
    private val pending = PendingOutboxCounter(events, fixedClock)

    @Test
    fun `le uma vez por janela e informa zero para topico sem pendentes`() {
        Mockito.`when`(events.countPendingByTopic()).thenReturn(listOf(contagem("notas", 7)))

        val first = pending.pending("notas")
        val second = pending.pending("solicitacoes")

        assertEquals(7L, first)
        assertEquals(0L, second)
        Mockito.verify(events, Mockito.times(1)).countPendingByTopic()
    }

    @Test
    fun `le novamente depois que a janela expira`() {
        Mockito
            .`when`(events.countPendingByTopic())
            .thenReturn(listOf(contagem("notas", 7)), listOf(contagem("notas", 3)))

        val before = pending.pending("notas")
        fixedClock.avancar(Duration.ofSeconds(1))
        val withinWindow = pending.pending("notas")
        fixedClock.avancar(Duration.ofSeconds(1))
        val after = pending.pending("notas")

        assertEquals(listOf(7L, 7L, 3L), listOf(before, withinWindow, after))
        Mockito.verify(events, Mockito.times(2)).countPendingByTopic()
    }

    private fun contagem(
        topic: String,
        total: Long,
    ): PendingByTopic =
        object : PendingByTopic {
            override val topic = topic
            override val total = total
        }

    private class MutableClock(
        private var now: Instant,
    ) : Clock() {
        fun avancar(duration: Duration) {
            now = now.plus(duration)
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this
    }
}
