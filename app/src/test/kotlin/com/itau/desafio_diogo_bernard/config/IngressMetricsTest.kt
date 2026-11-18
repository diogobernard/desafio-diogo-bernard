@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IngressMetricsTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = IngressMetrics(registry)

    @Test
    fun `registra um contador por motivo de rejeicao desde o inicio`() {
        IngressRejectionReason.entries.forEach {
            assertEquals(0.0, registry.counter("notafiscal.ingresso.rejeitadas", "motivo", it.tag).count())
        }
    }

    @Test
    fun `conta rejeicoes separadas por motivo`() {
        metrics.rejected(IngressRejectionReason.ESPERA_EXCEDIDA)
        metrics.rejected(IngressRejectionReason.ESPERA_EXCEDIDA)
        metrics.rejected(IngressRejectionReason.FILA_CHEIA)

        assertEquals(2.0, registry.counter("notafiscal.ingresso.rejeitadas", "motivo", "espera_excedida").count())
        assertEquals(1.0, registry.counter("notafiscal.ingresso.rejeitadas", "motivo", "fila_cheia").count())
        assertEquals(0.0, registry.counter("notafiscal.ingresso.rejeitadas", "motivo", "encerrando").count())
    }

    @Test
    fun `registra tamanho e duracao dos lotes`() {
        var executedCount = 0

        metrics.recordBatchSize(3)
        metrics.recordBatchSize(5)
        metrics.timeBatchWrite { executedCount++ }

        assertEquals(1, executedCount)
        assertEquals(2L, registry.find("notafiscal.ingresso.lote.tamanho").summary()?.count())
        assertEquals(8.0, registry.find("notafiscal.ingresso.lote.tamanho").summary()?.totalAmount())
        assertEquals(1L, registry.find("notafiscal.ingresso.lote.duracao").timer()?.count())
    }
}
