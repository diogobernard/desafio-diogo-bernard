@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

@Component
class IngressMetrics(
    private val registry: MeterRegistry,
) {
    private val batchSize = DistributionSummary.builder("notafiscal.ingresso.lote.tamanho").register(registry)
    private val batchDuration =
        Timer
            .builder("notafiscal.ingresso.lote.duracao")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)

    init {
        IngressRejectionReason.entries.forEach { registry.counter(REJEITADAS, MOTIVO, it.tag) }
    }

    fun rejected(reason: IngressRejectionReason): Unit = registry.counter(REJEITADAS, MOTIVO, reason.tag).increment()

    fun recordBatchSize(size: Int): Unit = batchSize.record(size.toDouble())

    fun timeBatchWrite(action: Runnable): Unit = batchDuration.record(action)

    private companion object {
        const val REJEITADAS = "notafiscal.ingresso.rejeitadas"
        const val MOTIVO = "motivo"
    }
}
