package com.itau.desafio_diogo_bernard.config

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.trace.Span
import org.springframework.stereotype.Component

@Component
class OutboxTracing(
    private val registry: ObservationRegistry,
) {
    fun register(
        requestId: String,
        action: () -> Unit,
    ) {
        Observation
            .createNotStarted("notafiscal.outbox.registrar", registry)
            .highCardinalityKeyValue("solicitacao_id", requestId)
            .observe(action)
    }

    fun traceparent(): String? {
        val context = Span.current().spanContext
        if (!context.isValid) return null
        val flags =
            context.traceFlags
                .asByte()
                .toUByte()
                .toString(16)
                .padStart(2, '0')
        return "00-${context.traceId}-${context.spanId}-$flags"
    }
}
