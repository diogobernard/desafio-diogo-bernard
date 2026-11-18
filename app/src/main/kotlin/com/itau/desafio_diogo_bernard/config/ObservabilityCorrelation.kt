package com.itau.desafio_diogo_bernard.config

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.function.Supplier

@Component
class ObservabilityCorrelation(
    private val registry: ObservationRegistry,
) {
    fun <T> execute(
        name: String,
        requestId: String,
        action: () -> T,
    ): T =
        Observation
            .createNotStarted("notafiscal.$name", registry)
            .highCardinalityKeyValue(ATRIBUTO_SOLICITACAO, requestId)
            .observe(Supplier { correlated(requestId, action) })

    fun annotate(
        requestId: String,
        action: () -> Unit,
    ): Unit = correlated(requestId, action)

    fun <T> executeInContext(
        context: Context,
        name: String,
        requestId: String,
        action: () -> T,
    ): T = context.makeCurrent().use { execute(name, requestId, action) }

    private fun <T> correlated(
        requestId: String,
        action: () -> T,
    ): T {
        MDC.put(CHAVE_MDC, requestId)
        Span.current().setAttribute(ATRIBUTO_SOLICITACAO, requestId)
        try {
            return action()
        } finally {
            MDC.remove(CHAVE_MDC)
        }
    }

    private companion object {
        const val CHAVE_MDC = "solicitacaoId"
        const val ATRIBUTO_SOLICITACAO = "solicitacao_id"
    }
}
