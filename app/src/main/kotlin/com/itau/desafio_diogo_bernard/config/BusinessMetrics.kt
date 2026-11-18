package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class BusinessMetrics internal constructor(
    private val registry: MeterRegistry,
    private val nanoTime: () -> Long,
) {
    @Autowired
    constructor(registry: MeterRegistry) : this(registry, System::nanoTime)

    private val notFoundReads = ConcurrentHashMap<String, Long>()

    init {
        registry.counter("notafiscal.leitura.404_falso")
    }

    fun requestReceived(clientId: String) {
        registry.counter("notafiscal.solicitacoes.recebidas", "cliente", clientId).increment()
    }

    fun invoiceIssued(
        issuanceRequest: IssuanceRequest,
        invoice: Invoice,
    ) {
        val duration = Duration.between(issuanceRequest.receivedAt, invoice.issuedAt)
        val series = invoice.reference.series.value
        val mode = invoice.calculated.mode.name
        registry.counter("notafiscal.notas.emitidas", "modo", mode, "serie", series.toString()).increment()
        timer("notafiscal.emissao.duracao", "modo", mode, "serie", series.toString()).record(duration)
        logger.info(
            "Nota fiscal emitida; solicitacaoId={} serie={} numero={} duracaoMs={}",
            invoice.requestId.value,
            series,
            invoice.reference.number.value,
            duration.toMillis(),
        )
    }

    fun requestFailed(reason: String): Unit =
        registry
            .counter("notafiscal.solicitacoes.falhas", "motivo", reason)
            .increment()

    fun numberingWait(
        series: Int,
        duration: Duration,
    ): Unit = timer("notafiscal.numeracao.espera_lock", "serie", series.toString()).record(duration)

    fun consumerDelay(
        consumer: String,
        duration: Duration,
    ): Unit = timer("notafiscal.consumidor.atraso", "consumidor", consumer).record(duration)

    fun dlq(consumer: String): Unit =
        registry
            .counter("notafiscal.consumidor.dlq", "consumidor", consumer)
            .increment()

    fun fallback(reason: String): Unit = registry.counter("notafiscal.leitura.fallback", "motivo", reason).increment()

    fun record404(id: String) {
        val now = nanoTime()
        notFoundReads.entries.removeIf { now - it.value > JANELA_404_NANOS }
        notFoundReads[id] = now
        if (notFoundReads.size > MAX_404_PENDENTES) {
            notFoundReads.remove(id)
        }
    }

    fun recordSuccessfulRead(id: String) {
        val foundAt = notFoundReads.remove(id) ?: return
        if (nanoTime() - foundAt <= JANELA_404_NANOS) {
            registry.counter("notafiscal.leitura.404_falso").increment()
        }
    }

    private fun timer(
        name: String,
        vararg tags: String,
    ): Timer =
        Timer
            .builder(name)
            .tags(*tags)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)

    private companion object {
        const val JANELA_404_NANOS = 60_000_000_000L
        const val MAX_404_PENDENTES = 10_000
        val logger = LoggerFactory.getLogger(BusinessMetrics::class.java)
    }
}
